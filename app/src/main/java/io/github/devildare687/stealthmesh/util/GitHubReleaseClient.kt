package io.github.devildare687.stealthmesh.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import io.github.devildare687.stealthmesh.BuildConfig
import io.github.devildare687.stealthmesh.net.ArtiTorManager
import io.github.devildare687.stealthmesh.net.OkHttpProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal interface LatestReleaseProvider {
    suspend fun latestRelease(): Result<GitHubReleaseClient.ReleaseSnapshot>
}

/** Fetches GitHub release metadata without participating in APK availability. */
internal class GitHubReleaseClient(
    context: Context,
    private val apiUrl: String = GITHUB_API_URL,
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val routedClient: () -> OkHttpProvider.RoutedClient = OkHttpProvider::routedHttpClient,
    private val awaitRoute: suspend () -> Boolean = {
        ArtiTorManager.getInstance().awaitSelectedRoute(ROUTE_READY_TIMEOUT_MILLIS)
    },
    private val rateLimits: ApkRateLimitStore = ApkRateLimitStore(context)
) : LatestReleaseProvider {
    companion object {
        private const val TAG = "GitHubRelease"
        private const val GITHUB_API_URL = OfficialStealthMeshRelease.RELEASES_API_URL
        private const val ROUTE_READY_TIMEOUT_MILLIS = 60_000L
        private const val CACHE_TTL_MILLIS = 30 * 60_000L
        private const val PREFS_NAME = "apk_release_metadata"
        private const val RATE_LIMIT_SCOPE = "github_release_metadata"
        private const val USER_AGENT = "StealthMesh-Android"

        private val SOURCE = ApkDownloadSource(
            id = DefaultApkDownloadSources.GITHUB_ID,
            displayName = "GitHub Releases",
            latestApkUrl = "https://github.com/Devildare687/StealthMesh/releases/latest/" +
                "download/app-universal-debug.apk"
        )

        internal fun selectRelease(
            jsonString: String,
            currentVersionName: String
        ): Release? = runCatching {
            val current = AppVersion.parse(currentVersionName) ?: return null
            val acceptsPrereleases = current.prerelease.isNotEmpty()
            val releases = JSONArray(jsonString)
            var selected: Release? = null

            for (releaseIndex in 0 until releases.length()) {
                val json = releases.optJSONObject(releaseIndex) ?: continue
                if (json.optBoolean("draft", false)) continue

                val tagName = json.optString("tag_name").trim()
                val versionName = tagName.removePrefix("v").trim()
                val version = AppVersion.parse(versionName) ?: continue
                if (version <= current) continue

                val prerelease = json.optBoolean("prerelease", false) ||
                    version.prerelease.isNotEmpty()
                if (!acceptsPrereleases && prerelease) continue

                val assets = json.optJSONArray("assets") ?: continue
                var candidate: Release? = null
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.optJSONObject(assetIndex) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (OfficialStealthMeshRelease.isExpectedAsset(name, url, tagName)) {
                        candidate = Release(
                            versionName = versionName,
                            tagName = tagName,
                            prerelease = prerelease,
                            universalApkSize = asset.optLong("size", 0L).coerceAtLeast(0L),
                            universalApkUrl = url,
                            universalApkName = name
                        )
                        break
                    }
                }

                if (candidate != null &&
                    (selected == null ||
                        AppVersion.isNewer(selected.versionName, candidate.versionName))
                ) {
                    selected = candidate
                }
            }
            selected
        }.getOrNull()

        private fun Release.isCompatibleWith(currentVersionName: String): Boolean {
            val current = AppVersion.parse(currentVersionName) ?: return false
            val candidate = AppVersion.parse(versionName) ?: return false
            if (candidate <= current) return false
            return current.prerelease.isNotEmpty() || !prerelease
        }
    }

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun latestRelease(): Result<ReleaseSnapshot> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cached = readCache()?.takeIf {
                it.release.isCompatibleWith(currentVersionName)
            }
            val now = nowMillis()
            val cacheAge = cached?.let { now - it.fetchedAtMillis }
            if (cached != null && cacheAge != null && cacheAge in 0 until CACHE_TTL_MILLIS) {
                return@withLock Result.success(ReleaseSnapshot(cached.release, isStale = false))
            }

            if (!awaitRoute()) return@withLock cached.orRouteFailure()

            val routeSnapshot = routedClient()
            // Route readiness can take longer than a cooldown. Judge an existing deadline at the
            // point where the request can actually start, not with the pre-wait cache timestamp.
            val routeReadyNow = nowMillis()
            rateLimits.retryAtMillis(
                RATE_LIMIT_SCOPE,
                routeSnapshot.route,
                routeReadyNow
            )?.let { deadline ->
                return@withLock cached.orFailure(
                    rateLimits.blockedException(SOURCE, deadline)
                )
            }

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .apply { cached?.etag?.let { addHeader("If-None-Match", it) } }
                .build()
            val client = routeSnapshot.client.newBuilder()
                .callTimeout(45, TimeUnit.SECONDS)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            try {
                client.newCall(request).awaitResponse().use { response ->
                    // The route wait and the call itself can each take a minute, so `now` is too
                    // old to interpret a relative Retry-After: anchoring the cooldown there can
                    // date it into the past and let the very next check reach GitHub.
                    val responseNow = nowMillis()
                    if (apiUrl.startsWith("https://") && !response.request.url.isHttps) {
                        return@withLock cached.orFailure(
                            IOException("GitHub redirected release metadata to an insecure URL")
                        )
                    }
                    if (response.code == 304 && cached != null) {
                        val refreshed = cached.copy(fetchedAtMillis = responseNow)
                        writeCache(refreshed)
                        rateLimits.clear(RATE_LIMIT_SCOPE, routeSnapshot.route)
                        return@withLock Result.success(
                            ReleaseSnapshot(refreshed.release, isStale = false)
                        )
                    }
                    if (!response.isSuccessful) {
                        val failure = ApkDownloadHttpErrors.fromResponse(
                            source = SOURCE,
                            code = response.code,
                            responseMessage = response.message,
                            retryAfter = response.header("Retry-After"),
                            rateLimitRemaining = response.header("X-RateLimit-Remaining"),
                            rateLimitResetEpochSeconds = response.header("X-RateLimit-Reset"),
                            nowMillis = responseNow
                        )
                        val persistedFailure = if (
                            failure.reason == ApkDownloadFailureReason.RateLimited
                        ) {
                            val deadline = rateLimits.recordRateLimit(
                                RATE_LIMIT_SCOPE,
                                routeSnapshot.route,
                                failure.retryAtMillis,
                                responseNow
                            )
                            rateLimits.blockedException(SOURCE, deadline)
                        } else {
                            failure
                        }
                        return@withLock cached.orFailure(persistedFailure)
                    }

                    val rawBody = response.body.string()
                    val release = selectRelease(rawBody, currentVersionName)
                        ?: return@withLock cached.orFailure(
                            IOException(
                                "GitHub has no compatible StealthMesh release with the expected APK"
                            )
                        )
                    val entry = CachedRelease(
                        release = release,
                        etag = response.header("ETag"),
                        fetchedAtMillis = responseNow
                    )
                    writeCache(entry)
                    rateLimits.clear(RATE_LIMIT_SCOPE, routeSnapshot.route)
                    Result.success(ReleaseSnapshot(release, isStale = false))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not refresh release metadata; using cache when available", error)
                cached.orFailure(error)
            }
        }
    }

    private fun CachedRelease?.orRouteFailure(): Result<ReleaseSnapshot> = orFailure(
        IOException("The selected network route is not ready")
    )

    private fun CachedRelease?.orFailure(error: Throwable): Result<ReleaseSnapshot> =
        if (this != null) {
            Result.success(ReleaseSnapshot(release, isStale = true))
        } else {
            Result.failure(error)
        }

    private fun readCache(): CachedRelease? = runCatching {
        val version = preferences.getString("version", null)?.takeIf { it.isNotBlank() } ?: return null
        val url = preferences.getString("url", null)?.takeIf { it.startsWith("https://") } ?: return null
        val name = preferences.getString("name", null)?.takeIf { it.isNotBlank() } ?: return null
        CachedRelease(
            release = Release(
                versionName = version,
                tagName = preferences.getString("tag", null)
                    ?.takeIf { it.isNotBlank() }
                    ?: return null,
                prerelease = preferences.getBoolean("prerelease", false),
                universalApkSize = preferences.getLong("size", 0L),
                universalApkUrl = url,
                universalApkName = name
            ),
            etag = preferences.getString("etag", null),
            fetchedAtMillis = preferences.getLong("fetched_at", 0L)
        )
    }.getOrNull()

    private fun writeCache(entry: CachedRelease) {
        preferences.edit(commit = true) {
            putString("version", entry.release.versionName)
            putString("tag", entry.release.tagName)
            putBoolean("prerelease", entry.release.prerelease)
            putLong("size", entry.release.universalApkSize)
            putString("url", entry.release.universalApkUrl)
            putString("name", entry.release.universalApkName)
            putString("etag", entry.etag)
            putLong("fetched_at", entry.fetchedAtMillis)
        }
    }

    private suspend fun Call.awaitResponse(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(response))
                    } else {
                        response.close()
                    }
                }
            })
        }

    data class Release(
        val versionName: String,
        val tagName: String,
        val prerelease: Boolean,
        val universalApkSize: Long,
        val universalApkUrl: String,
        val universalApkName: String
    ) {
        fun downloadTarget(): ApkReleaseTarget = ApkReleaseTarget(
            versionName = versionName,
            tagName = tagName,
            assetName = universalApkName,
            assetUrl = universalApkUrl
        )
    }

    data class ReleaseSnapshot(
        val release: Release,
        val isStale: Boolean
    )

    private data class CachedRelease(
        val release: Release,
        val etag: String?,
        val fetchedAtMillis: Long
    )
}
