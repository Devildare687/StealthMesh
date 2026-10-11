package io.github.devildare687.stealthmesh.util

import androidx.annotation.StringRes
import io.github.devildare687.stealthmesh.BuildConfig
import io.github.devildare687.stealthmesh.R
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A trusted location that serves the latest signed universal StealthMesh APK.
 *
 * Sources are tried in order. A source may list compatibility filenames, which
 * are only used when the preferred asset is absent. Adding a mirror should only
 * require another entry; resume, retry, and verification do not depend on the host.
 */
data class ApkDownloadSource(
    val id: String,
    val displayName: String,
    val latestApkUrls: List<String>
) {
    constructor(id: String, displayName: String, latestApkUrl: String) : this(
        id = id,
        displayName = displayName,
        latestApkUrls = listOf(latestApkUrl)
    )

    init {
        require(id.isNotBlank()) { "Download source id must not be blank" }
        require(displayName.isNotBlank()) { "Download source name must not be blank" }
        require(latestApkUrls.isNotEmpty()) { "Download source must have at least one URL" }
        require(latestApkUrls.distinct().size == latestApkUrls.size) {
            "Download source URLs must be unique"
        }
        require(latestApkUrls.all { it.startsWith("https://") }) {
            "APK download sources must use HTTPS"
        }
    }
}

internal object OfficialStealthMeshRelease {
    const val OWNER = "Devildare687"
    const val REPOSITORY = "StealthMesh"
    const val APPLICATION_ID = "io.github.devildare687.stealthmesh"
    const val UNIVERSAL_APK_NAME = "app-universal-debug.apk"
    const val RELEASES_API_URL =
        "https://api.github.com/repos/$OWNER/$REPOSITORY/releases?per_page=30"

    fun isExpectedAsset(name: String, url: String, tagName: String): Boolean {
        if (name != UNIVERSAL_APK_NAME || tagName.isBlank()) return false
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.isHttps &&
            parsed.host.equals("github.com", ignoreCase = true) &&
            parsed.query == null &&
            parsed.fragment == null &&
            parsed.pathSegments == listOf(
                OWNER,
                REPOSITORY,
                "releases",
                "download",
                tagName,
                UNIVERSAL_APK_NAME
            )
    }

    fun isExpectedPackage(packageName: String): Boolean = packageName == APPLICATION_ID
}

data class ApkReleaseTarget(
    val versionName: String,
    val tagName: String,
    val assetName: String,
    val assetUrl: String
) {
    init {
        require(AppVersion.parse(versionName) != null) { "Release version must be valid" }
        require(OfficialStealthMeshRelease.isExpectedAsset(assetName, assetUrl, tagName)) {
            "Release asset must belong to the official StealthMesh repository"
        }
    }

    fun downloadSource(): ApkDownloadSource = ApkDownloadSource(
        id = DefaultApkDownloadSources.GITHUB_ID,
        displayName = "GitHub Releases",
        latestApkUrl = assetUrl
    )
}

internal object DefaultApkDownloadSources {
    const val GITHUB_ID = "github-releases"

    val all = listOf(
        ApkDownloadSource(
            id = GITHUB_ID,
            displayName = "GitHub Releases",
            latestApkUrl =
                "https://github.com/Devildare687/StealthMesh/releases/download/" +
                    "v${BuildConfig.VERSION_NAME}/" +
                    "app-universal-debug.apk"
        )
    )
}

/**
 * Why a download failed, and which string says so.
 *
 * Crosses a WorkManager `Data` boundary by [name], never by resource id: WorkManager keeps failed
 * records in its own database across app updates, and AAPT2 reassigns `R.string` ids on every
 * build, so a persisted id would resolve against the wrong resource table after an update. Same
 * reasoning as [ApkDownloader.DownloadPhase.fromKey].
 */
enum class ApkDownloadFailureReason(@StringRes val messageRes: Int) {
    Generic(R.string.prepare_apk_error_generic),
    Cancelled(R.string.prepare_apk_download_cancelled),
    RateLimited(R.string.prepare_apk_error_rate_limited),
    NoUniversalApk(R.string.prepare_apk_error_no_universal),
    HttpFailure(R.string.prepare_apk_error_http),
    InsufficientStorage(R.string.prepare_apk_error_storage_needed),
    NoSources(R.string.prepare_apk_error_no_sources),
    TorConnecting(R.string.prepare_apk_error_tor_connecting),
    NoUsableUrl(R.string.prepare_apk_error_no_url),
    Unreachable(R.string.prepare_apk_error_unreachable),
    InsecureRedirect(R.string.prepare_apk_error_insecure_redirect),
    ResumeRejected(R.string.prepare_apk_error_resume_rejected),
    Incomplete(R.string.prepare_apk_error_incomplete),
    InvalidResume(R.string.prepare_apk_error_invalid_resume),
    UntrustedKey(R.string.prepare_apk_error_untrusted_key),
    NotUniversal(R.string.prepare_apk_error_not_universal),
    ApkUnreadable(R.string.prepare_apk_error_apk_unreadable),
    NotBitchat(R.string.prepare_apk_error_not_bitchat),
    NoVersion(R.string.prepare_apk_error_no_version),
    SourceFailed(R.string.prepare_apk_error_source_failed),
    AllSourcesFailed(R.string.prepare_apk_error_all_sources);

    companion object {
        /** Work enqueued by an older build may name a reason this build no longer has. */
        fun fromKey(key: String?): ApkDownloadFailureReason =
            entries.firstOrNull { it.name == key } ?: Generic
    }
}

/**
 * A host-neutral download failure that tells the worker whether backoff can help.
 *
 * [reason] and [messageArgs] name what the user should be told without saying it in any
 * particular language. This layer has no Context by design — that is what keeps its tests plain
 * JUnit — so the ViewModel resolves them. The inherited [message] stays English for logs and
 * stack traces, and is never shown.
 */
class ApkDownloadException(
    message: String,
    val reason: ApkDownloadFailureReason,
    val messageArgs: List<String> = emptyList(),
    val retryable: Boolean,
    val sourceId: String? = null,
    val httpCode: Int? = null,
    val retryAtMillis: Long? = null,
    cause: Throwable? = null
) : IOException(message, cause)

internal object ApkDownloadRetryPolicy {
    const val MAX_ATTEMPTS = 3

    fun shouldRetry(runAttemptCount: Int, error: Throwable?): Boolean {
        val retryable = when (error) {
            is ApkDownloadException -> error.retryable
            is IOException -> true
            else -> false
        }
        val attemptNumber = runAttemptCount + 1
        return retryable && attemptNumber < MAX_ATTEMPTS
    }
}

internal fun shouldTryNextSourceUrl(
    error: ApkDownloadException,
    hasMoreUrls: Boolean
): Boolean = hasMoreUrls && error.httpCode == 404

internal object ApkDownloadHttpErrors {
    fun fromResponse(
        source: ApkDownloadSource,
        code: Int,
        responseMessage: String,
        retryAfter: String?,
        rateLimitRemaining: String?,
        rateLimitResetEpochSeconds: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): ApkDownloadException {
        val retryAt = retryAtMillis(
            retryAfter = retryAfter,
            rateLimitResetEpochSeconds = rateLimitResetEpochSeconds,
            nowMillis = nowMillis
        )
        // X-RateLimit-Reset rides on every GitHub response, an ordinary 403 included, so it
        // cannot tell an exhausted quota from a permissions failure. Only a spent quota or an
        // explicit Retry-After says this request was the one that got limited. The reset header
        // still supplies the deadline below, once being limited is established some other way.
        val retryAfterMillis = retryAtMillis(
            retryAfter = retryAfter,
            rateLimitResetEpochSeconds = null,
            nowMillis = nowMillis
        )
        val rateLimited = code == 429 ||
            (code == 403 && (rateLimitRemaining?.trim() == "0" || retryAfterMillis != null))

        if (rateLimited) {
            return ApkDownloadException(
                message = "${source.id} rate limited: HTTP $code, retryAt=$retryAt",
                reason = ApkDownloadFailureReason.RateLimited,
                messageArgs = listOf(source.displayName),
                retryable = false,
                sourceId = source.id,
                httpCode = code,
                retryAtMillis = retryAt
            )
        }

        val retryable = code == 408 || code == 425 || code >= 500
        return ApkDownloadException(
            message = "${source.id} failed: HTTP $code $responseMessage",
            reason = if (code == 404) {
                ApkDownloadFailureReason.NoUniversalApk
            } else {
                ApkDownloadFailureReason.HttpFailure
            },
            messageArgs = if (code == 404) {
                listOf(source.displayName)
            } else {
                listOf(source.displayName, code.toString(), responseMessage)
            },
            retryable = retryable,
            sourceId = source.id,
            httpCode = code
        )
    }

    internal fun retryAtMillis(
        retryAfter: String?,
        rateLimitResetEpochSeconds: String?,
        nowMillis: Long
    ): Long? {
        retryAfter?.trim()?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?.let { seconds ->
                runCatching {
                    Math.addExact(nowMillis, Math.multiplyExact(seconds, 1000L))
                }.getOrNull()?.let { return it }
            }

        retryAfter?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            val parsed = runCatching {
                ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
            if (parsed != null && parsed > nowMillis) return parsed
        }

        return rateLimitResetEpochSeconds?.trim()?.toLongOrNull()
            ?.let { runCatching { Instant.ofEpochSecond(it).toEpochMilli() }.getOrNull() }
            ?.takeIf { it > nowMillis }
    }
}

internal object AppVersion {
    internal data class Parsed(
        val major: Long,
        val minor: Long,
        val patch: Long,
        val prerelease: List<String>
    ) : Comparable<Parsed> {
        override fun compareTo(other: Parsed): Int {
            compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
            compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
            compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }

            if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
            if (prerelease.isEmpty()) return 1
            if (other.prerelease.isEmpty()) return -1

            val length = maxOf(prerelease.size, other.prerelease.size)
            for (index in 0 until length) {
                val left = prerelease.getOrNull(index) ?: return -1
                val right = other.prerelease.getOrNull(index) ?: return 1
                val leftNumber = left.toLongOrNull()
                val rightNumber = right.toLongOrNull()
                val compared = when {
                    leftNumber != null && rightNumber != null ->
                        compareValues(leftNumber, rightNumber)
                    leftNumber != null -> -1
                    rightNumber != null -> 1
                    else -> left.compareTo(right, ignoreCase = true)
                }
                if (compared != 0) return compared
            }
            return 0
        }
    }

    private val VERSION_PATTERN = Regex(
        """^(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z][0-9A-Za-z.-]*))?(?:\+[0-9A-Za-z.-]+)?$"""
    )

    internal fun parse(version: String): Parsed? {
        val normalized = version.removePrefix("v").trim()
        val match = VERSION_PATTERN.matchEntire(normalized) ?: return null
        return Parsed(
            major = match.groupValues[1].toLongOrNull() ?: return null,
            minor = match.groupValues[2].toLongOrNull() ?: 0L,
            patch = match.groupValues[3].toLongOrNull() ?: 0L,
            prerelease = match.groupValues[4]
                .takeIf { it.isNotEmpty() }
                ?.split('.', '-')
                .orEmpty()
        )
    }

    fun isNewer(currentVersion: String, candidateVersion: String): Boolean {
        val current = parse(currentVersion) ?: return false
        val candidate = parse(candidateVersion) ?: return false
        return candidate > current
    }

    fun isEquivalent(firstVersion: String, secondVersion: String): Boolean {
        val first = parse(firstVersion) ?: return false
        val second = parse(secondVersion) ?: return false
        return first.compareTo(second) == 0
    }

    fun isPrerelease(version: String): Boolean = parse(version)?.prerelease?.isNotEmpty() == true
}

internal fun isAcceptableReleaseApkVersion(
    installedVersionName: String,
    installedVersionCode: Long,
    downloadedVersionName: String,
    downloadedVersionCode: Long,
    expectedVersionName: String
): Boolean {
    if (!AppVersion.isEquivalent(downloadedVersionName, expectedVersionName)) return false
    return when {
        AppVersion.isNewer(installedVersionName, downloadedVersionName) ->
            downloadedVersionCode > installedVersionCode
        AppVersion.isEquivalent(installedVersionName, downloadedVersionName) ->
            downloadedVersionCode >= installedVersionCode
        else -> false
    }
}

internal data class ContentRange(
    val start: Long,
    val endInclusive: Long,
    val total: Long?
)

/**
 * Makes a response body safe to append after resume metadata is updated.
 * A full 200 replacement must discard bytes from the release that supplied the Range request.
 */
internal fun prepareApkTempFileForResponse(tempFile: File, appendResponse: Boolean) {
    if (!appendResponse) FileOutputStream(tempFile, false).use { }
}

internal fun parseContentRange(value: String?): ContentRange? {
    if (value == null) return null
    val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
        .matchEntire(value.trim())
        ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    if (end < start) return null
    val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
    if (total != null && end >= total) return null
    return ContentRange(
        start = start,
        endInclusive = end,
        total = total
    )
}

internal fun parseUnsatisfiedContentRangeTotal(value: String?): Long? {
    if (value == null) return null
    return Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)
        .matchEntire(value.trim())
        ?.groupValues
        ?.get(1)
        ?.toLongOrNull()
}
