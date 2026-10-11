package io.github.devildare687.stealthmesh.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.devildare687.stealthmesh.net.OkHttpProvider
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GitHubReleaseClientTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private var nowMillis = 1_700_000_000_000L
    private var route = OkHttpProvider.Route.DIRECT

    /** How far the clock advances while awaitRoute() waits for Tor to finish bootstrapping. */
    private var routeWaitMillis = 0L

    /** How far the clock advances after the server responds but before the client observes it. */
    private var responseWaitMillis = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("apk_release_metadata", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("apk_network_cooldowns", Context.MODE_PRIVATE)
            .edit().clear().commit()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `installed alpha selects a newer alpha release`() {
        val selected = GitHubReleaseClient.selectRelease(
            releaseList(
                release("v0.2.2-alpha", prerelease = true),
                release("v0.2.3-alpha", prerelease = true)
            ),
            currentVersionName = "0.2.2-alpha"
        )

        assertEquals("0.2.3-alpha", selected?.versionName)
        assertTrue(selected?.prerelease == true)
    }

    @Test
    fun `prerelease ordering prefers beta and numeric suffixes`() {
        val selected = GitHubReleaseClient.selectRelease(
            releaseList(
                release("v0.3.0-alpha", prerelease = true),
                release("v0.3.0-beta.2", prerelease = true),
                release("v0.3.0-beta.10", prerelease = true)
            ),
            currentVersionName = "0.2.9-alpha"
        )

        assertEquals("0.3.0-beta.10", selected?.versionName)
    }

    @Test
    fun `drafts are ignored even when they have the highest version`() {
        val selected = GitHubReleaseClient.selectRelease(
            releaseList(
                release("v9.0.0-alpha", prerelease = true, draft = true),
                release("v0.2.3-alpha", prerelease = true)
            ),
            currentVersionName = "0.2.2-alpha"
        )

        assertEquals("0.2.3-alpha", selected?.versionName)
    }

    @Test
    fun `older and equal releases are not update candidates`() {
        val selected = GitHubReleaseClient.selectRelease(
            releaseList(
                release("v0.2.1-alpha", prerelease = true),
                release("v0.2.2-alpha", prerelease = true)
            ),
            currentVersionName = "0.2.2-alpha"
        )

        assertNull(selected)
    }

    @Test
    fun `stable build ignores prereleases and selects newer stable`() {
        val selected = GitHubReleaseClient.selectRelease(
            releaseList(
                release("v2.0.0-beta", prerelease = true),
                release("v1.1.0", prerelease = false)
            ),
            currentVersionName = "1.0.0"
        )

        assertEquals("1.1.0", selected?.versionName)
        assertFalse(selected?.prerelease ?: true)
    }

    @Test
    fun `only the exact official universal APK asset is selected`() {
        val selected = GitHubReleaseClient.selectRelease(
            """
            [
              {
                "tag_name": "v0.2.3-alpha",
                "draft": false,
                "prerelease": true,
                "assets": [
                  {
                    "name": "app-arm64-v8a-debug.apk",
                    "browser_download_url": "https://github.com/Devildare687/StealthMesh/releases/download/v0.2.3-alpha/app-arm64-v8a-debug.apk",
                    "size": 10
                  },
                  {
                    "name": "stealthmesh-universal.apk",
                    "browser_download_url": "https://github.com/Devildare687/StealthMesh/releases/download/v0.2.3-alpha/stealthmesh-universal.apk",
                    "size": 20
                  },
                  {
                    "name": "app-universal-debug.apk",
                    "browser_download_url": "https://github.com/Devildare687/StealthMesh/releases/download/v0.2.3-alpha/app-universal-debug.apk",
                    "size": 30
                  }
                ]
              }
            ]
            """.trimIndent(),
            currentVersionName = "0.2.2-alpha"
        )

        assertEquals("app-universal-debug.apk", selected?.universalApkName)
        assertEquals(30L, selected?.universalApkSize)
    }

    @Test
    fun `release without the expected universal APK is ignored`() {
        val selected = GitHubReleaseClient.selectRelease(
            """
            [
              {
                "tag_name": "v0.2.3-alpha",
                "draft": false,
                "prerelease": true,
                "assets": [
                  {
                    "name": "app-arm64-v8a-debug.apk",
                    "browser_download_url": "https://github.com/Devildare687/StealthMesh/releases/download/v0.2.3-alpha/app-arm64-v8a-debug.apk",
                    "size": 10
                  }
                ]
              }
            ]
            """.trimIndent(),
            currentVersionName = "0.2.2-alpha"
        )

        assertNull(selected)
    }

    @Test
    fun `wrong repository and malformed release data are ignored safely`() {
        val wrongRepository = release("v0.2.3-alpha", prerelease = true)
            .replace("Devildare687/StealthMesh", "someone/else")

        assertNull(
            GitHubReleaseClient.selectRelease(
                "[null, {}, \"irrelevant\", $wrongRepository]",
                currentVersionName = "0.2.2-alpha"
            )
        )
        assertNull(
            GitHubReleaseClient.selectRelease(
                "not-json",
                currentVersionName = "0.2.2-alpha"
            )
        )
    }

    @Test
    fun `cached metadata is conditionally refreshed with its etag`() = runTest {
        server.enqueue(successResponse(etag = "release-v1"))
        val client = client()

        val first = client.latestRelease().getOrThrow()
        assertEquals("1.7.6", first.release.versionName)
        assertFalse(first.isStale)

        nowMillis += 31 * 60_000L
        server.enqueue(
            MockResponse.Builder()
                .code(304)
                .build()
        )
        val refreshed = client.latestRelease().getOrThrow()

        assertFalse(refreshed.isStale)
        server.takeRequest()
        assertEquals("release-v1", server.takeRequest().headers["If-None-Match"])
    }

    @Test
    fun `rate limit serves stale metadata and suppresses repeated requests`() = runTest {
        server.enqueue(successResponse(etag = "release-v1"))
        val client = client()
        client.latestRelease().getOrThrow()

        nowMillis += 31 * 60_000L
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .build()
        )
        val stale = client.latestRelease().getOrThrow()
        val stillStale = client.latestRelease().getOrThrow()

        assertTrue(stale.isStale)
        assertTrue(stillStale.isStale)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cooldown follows the actual client route`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "120")
                .build()
        )
        val client = client()
        assertTrue(client.latestRelease().isFailure)
        assertTrue(client.latestRelease().isFailure)
        assertEquals(1, server.requestCount)

        route = OkHttpProvider.Route.TOR
        server.enqueue(successResponse(etag = "release-v1"))
        assertTrue(client.latestRelease().isSuccess)
        assertEquals(2, server.requestCount)
    }

    /**
     * A Tor cold start can hold the request for the full 60-second route timeout, which is longer
     * than the relative delay GitHub asks for. The cooldown has to outlast the wait that preceded
     * it, so it is anchored at the response rather than at the start of the attempt.
     */
    @Test
    fun `a slow route wait does not shorten a relative retry-after cooldown`() = runTest {
        routeWaitMillis = 90_000L
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "60")
                .build()
        )
        val client = client()

        assertTrue(client.latestRelease().isFailure)
        routeWaitMillis = 0L
        assertTrue(client.latestRelease().isFailure)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a slow route wait does not shorten the header-less fallback cooldown`() = runTest {
        routeWaitMillis = 90_000L
        server.enqueue(MockResponse.Builder().code(429).build())
        val client = client()

        assertTrue(client.latestRelease().isFailure)
        routeWaitMillis = 0L
        assertTrue(client.latestRelease().isFailure)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a slow response does not shorten a relative retry-after cooldown`() = runTest {
        responseWaitMillis = 90_000L
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "60")
                .build()
        )
        val client = client()

        assertTrue(client.latestRelease().isFailure)
        responseWaitMillis = 0L
        assertTrue(client.latestRelease().isFailure)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a cooldown that expires while the route becomes ready does not suppress the request`() =
        runTest {
            server.enqueue(
                MockResponse.Builder()
                    .code(429)
                    .addHeader("Retry-After", "60")
                    .build()
            )
            val client = client()

            assertTrue(client.latestRelease().isFailure)

            routeWaitMillis = 90_000L
            server.enqueue(successResponse(etag = "release-after-cooldown"))
            assertTrue(client.latestRelease().isSuccess)

            assertEquals(2, server.requestCount)
        }

    private fun client() = GitHubReleaseClient(
        context = context,
        apiUrl = server.url("/releases").toString(),
        nowMillis = { nowMillis },
        routedClient = {
            OkHttpProvider.RoutedClient(
                client = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(chain.request()).also {
                            nowMillis += responseWaitMillis
                        }
                    }
                    .build(),
                route = route
            )
        },
        awaitRoute = {
            nowMillis += routeWaitMillis
            true
        }
    )

    private fun successResponse(etag: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("ETag", etag)
        .body(
            """
            [${release("v1.7.6", prerelease = false)}]
            """.trimIndent()
        )
        .build()

    private fun releaseList(vararg releases: String): String =
        releases.joinToString(prefix = "[", postfix = "]")

    private fun release(
        tag: String,
        prerelease: Boolean,
        draft: Boolean = false
    ): String =
        """
        {
          "tag_name": "$tag",
          "draft": $draft,
          "prerelease": $prerelease,
          "assets": [
            {
              "name": "app-universal-debug.apk",
              "browser_download_url": "https://github.com/Devildare687/StealthMesh/releases/download/$tag/app-universal-debug.apk",
              "size": 25165824
            }
          ]
        }
        """.trimIndent()
}
