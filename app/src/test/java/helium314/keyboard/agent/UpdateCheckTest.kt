// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import dev.agentkeyboard.core.UpdateReleases
import helium314.keyboard.agent.updates.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdateCheckTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val requests = mutableListOf<Request>()
    private val apkBytes = ByteArray(200_000) { (it % 251).toByte() }
    private val own = ApkIdentity(app.packageName, 6, setOf("aa".repeat(32)))

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun releasesJson(vararg numbers: Long, apkSize: Long = apkBytes.size.toLong(),
                             body: (Long) -> String = { "notes of build-$it" }) = buildJsonArray {
        for (n in numbers) add(buildJsonObject {
            put("tag_name", "build-$n"); put("draft", false); put("prerelease", false)
            put("body", body(n))
            put("assets", buildJsonArray {
                for ((name, size) in listOf(UpdateReleases.apkName(n) to apkSize, UpdateReleases.checksumName(n) to 100L))
                    add(buildJsonObject {
                        put("id", n * 10); put("name", name); put("size", size)
                        put("browser_download_url", UpdateReleases.downloadUrl("build-$n", name))
                    })
            })
        })
    }.toString()

    private fun response(request: Request, code: Int, body: ByteArray = ByteArray(0), headers: Map<String, String> = emptyMap()) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .body(body.toResponseBody()).build()

    /** Offline fake of GitHub: the API, the public download URL and the storage host it redirects to. */
    /**
     * Stage 56: [etag] is the ETag of [releases] (sent with each 200; a matching `If-None-Match` gets
     * 304, also any `If-None-Match` with [alwaysNotModified]); [listCode] other than 200 answers the
     * list with that code and [listHeaders].
     */
    private fun client(releases: String = releasesJson(7, 5), checksum: String? = null, apk: ByteArray = apkBytes,
                       redirect: String = "https://release-assets.example.net/signed?x=1", etag: String? = null,
                       listCode: Int = 200, listHeaders: Map<String, String> = emptyMap(), alwaysNotModified: Boolean = false): GitHubUpdateClient {
        val http = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val url = request.url.toString()
            val sent = request.header("If-None-Match")
            when {
                url == GitHubUpdateClient.RELEASES_URL && listCode != 200 -> response(request, listCode, headers = listHeaders)
                url == GitHubUpdateClient.RELEASES_URL && (alwaysNotModified || (sent != null && sent == etag)) ->
                    response(request, 304, headers = etag?.let { mapOf("ETag" to it) }.orEmpty())
                url == GitHubUpdateClient.RELEASES_URL ->
                    response(request, 200, releases.toByteArray(), headers = etag?.let { mapOf("ETag" to it) }.orEmpty())
                url.endsWith(".sha256") -> response(request, 200,
                    (checksum ?: "${sha(apk)}  ${request.url.pathSegments.last().removeSuffix(".sha256")}\n").toByteArray())
                url.startsWith(UpdateReleases.RELEASES_PAGE + "/download/") -> response(request, 302, headers = mapOf("Location" to redirect))
                url == redirect -> response(request, 200, apk)
                else -> response(request, 404)
            }
        }.build()
        return GitHubUpdateClient(http)
    }

    private var installedFiles = 0
    private val session = ApkInstallSession { file -> assertTrue(file.exists()); installedFiles++ }
    private fun inspector(identity: ApkIdentity?) = ApkInspector { identity }

    @Before fun reset() {
        UpdateSettings(app).apply { daily = false; status = UpdateStatus.Unknown; notifiedBuild = 0; remember(null, 0) }
        UpdateScheduler.cancel(app)
    }

    @Test fun checkAsksOnlyThePublicReleasesApiWithoutAnyCredential() {
        val status = Updates.check(app, { client() }, installed = 6)
        assertEquals(UpdateStatus.Available(7), status)
        assertEquals(status, UpdateSettings(app).status)
        val request = requests.single()
        assertEquals("https://api.github.com/repos/Tra1c0/SecondHandAgenticKeyboard/releases?per_page=10", request.url.toString())
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
        assertEquals("application/vnd.github+json", request.header("Accept"))
        assertEquals(GitHubUpdateClient.API_VERSION, request.header("X-GitHub-Api-Version"))
        assertEquals(UpdateStatus.Current(7), Updates.check(app, { client() }, installed = 7))
    }

    /** Stage 54: the dialog and the keyboard chip read what the check stored, never the network. */
    @Test fun checkStoresTheNotesOfTheNewestBuildAndTheVersionItComparedWith() {
        Updates.check(app, { client() }, installed = 6)
        UpdateSettings(app).let {
            assertEquals(7L, it.knownBuild)
            assertEquals("notes of build-7", it.knownNotes)
            assertFalse(it.knownNotesTruncated)
            assertEquals(6L, it.comparedVersion)
            assertEquals(7L, it.newerBuild(6))
            assertNull(it.newerBuild(7))
        }
        // a failed check knows nothing new: build, notes and compared version stay
        assertEquals(UpdateStatus.Failed(UpdateError.INVALID_RESPONSE), Updates.check(app, { client(releases = "[broken") }, installed = 6))
        UpdateSettings(app).let { assertEquals(7L, it.knownBuild); assertEquals("notes of build-7", it.knownNotes); assertEquals(6L, it.comparedVersion) }
        // a long body is capped
        Updates.check(app, { client(releases = releasesJson(9, 7, body = { "- Punkt $it\n".repeat(5000) })) }, installed = 6)
        UpdateSettings(app).let {
            assertEquals(9L, it.knownBuild)
            assertTrue(it.knownNotesTruncated)
            assertTrue(it.knownNotes.toByteArray().size <= dev.agentkeyboard.core.ReleaseNotes.MAX_STORED_BYTES)
            assertTrue(it.knownNotes.startsWith("- Punkt 9"))
        }
        // nothing newer than the installed build: forgotten, with the new compared version
        assertEquals(UpdateStatus.Current(9), Updates.check(app, { client(releases = releasesJson(9)) }, installed = 9))
        UpdateSettings(app).let { assertEquals(0L, it.knownBuild); assertEquals("", it.knownNotes); assertEquals(9L, it.comparedVersion) }
        // a release without text or with null is no text; any other type fails the whole check
        val withoutBody = releasesJson(3).replace(""""body":"notes of build-3",""", "")
        assertEquals("", GitHubUpdateClient.parseReleases(withoutBody).single().body)
        assertEquals("", GitHubUpdateClient.parseReleases(releasesJson(3).replace(""""notes of build-3"""", "null")).single().body)
        assertEquals(UpdateError.INVALID_RESPONSE, assertFailsWith<UpdateFailure> {
            GitHubUpdateClient.parseReleases(releasesJson(3).replace(""""notes of build-3"""", "5"))
        }.error)
        assertEquals(4, requests.size, "only the four checks asked")
    }

    /** Stage 56: the ETag of a 200 is stored and sent back; a 304 keeps the stored result, a failure the known state. */
    @Test fun theETagIsStoredAndSentAndA304KeepsTheResult() {
        val first = "W/\"aaa111\""
        assertEquals(UpdateStatus.Available(7), Updates.check(app, { client(etag = first) }, installed = 6))
        assertNull(requests.last().header("If-None-Match"), "nothing stored yet")
        UpdateSettings(app).let { assertEquals(first, it.etag); assertEquals(7L, it.knownBuild) }

        // unchanged: 304, the result stays and only the time of the check is new
        UpdateSettings(app).checkedAt = 0
        assertEquals(UpdateStatus.Available(7), Updates.check(app, { client(releases = "[broken", etag = first) }, installed = 6))
        assertEquals(first, requests.last().header("If-None-Match"))
        UpdateSettings(app).let {
            assertEquals(UpdateStatus.Available(7), it.status)
            assertEquals(7L, it.knownBuild)
            assertEquals("notes of build-7", it.knownNotes)
            assertEquals(6L, it.comparedVersion)
            assertEquals(first, it.etag)
            assertTrue(it.checkedAt > 0)
        }

        // rate limits (403 with no requests left, 429) and other failures keep the known state and the ETag
        for ((code, headers) in listOf(403 to mapOf("x-ratelimit-remaining" to "0"), 429 to emptyMap()))
            assertEquals(UpdateStatus.Failed(UpdateError.RATE_LIMIT),
                Updates.check(app, { client(etag = first, listCode = code, listHeaders = headers) }, installed = 6))
        assertEquals(UpdateStatus.Failed(UpdateError.SERVICE), Updates.check(app, { client(etag = first, listCode = 403) }, installed = 6))
        UpdateSettings(app).let { assertEquals(7L, it.knownBuild); assertEquals(first, it.etag); assertEquals(6L, it.comparedVersion) }
        // a 304 after the failure brings back the stored result
        assertEquals(UpdateStatus.Available(7), Updates.check(app, { client(etag = first) }, installed = 6))
        assertEquals(UpdateStatus.Available(7), UpdateSettings(app).status)

        // the list changed: a new ETag replaces the old one
        val second = "\"bbb222\""
        assertEquals(UpdateStatus.Available(9), Updates.check(app, { client(releases = releasesJson(9, 7), etag = second) }, installed = 6))
        assertEquals(first, requests.last().header("If-None-Match"))
        UpdateSettings(app).let { assertEquals(second, it.etag); assertEquals(9L, it.knownBuild) }

        // another installed version: the stored result was compared with 6, so the ETag is not sent
        assertEquals(UpdateStatus.Current(9), Updates.check(app, { client(releases = releasesJson(9, 7), etag = second) }, installed = 9))
        assertNull(requests.last().header("If-None-Match"))
        UpdateSettings(app).let { assertEquals(0L, it.knownBuild); assertEquals(9L, it.comparedVersion); assertEquals(second, it.etag) }

        // an ETag that may not be sent back (a space, a line break) is not kept; without one nothing is sent
        Updates.check(app, { client(releases = releasesJson(9), etag = "\"x y\"") }, installed = 9)
        assertNull(UpdateSettings(app).etag)
        Updates.check(app, { client(releases = releasesJson(9)) }, installed = 9)
        assertNull(requests.last().header("If-None-Match"))
        // a 304 to a request without If-None-Match is no answer to trust
        assertEquals(UpdateStatus.Failed(UpdateError.SERVICE), Updates.check(app, { client(alwaysNotModified = true) }, installed = 9))
        assertEquals(UpdateStatus.Failed(UpdateError.SERVICE), UpdateSettings(app).status)
        // the download asks for the full list again, never with If-None-Match
        UpdateSettings(app).remember(null, 6, first)
        Updates.downloadAndInstall(app, client(etag = first), inspector(own.copy(versionCode = 7)), own, session)
        assertEquals(1, installedFiles)
        assertNull(requests.last { it.url.toString() == GitHubUpdateClient.RELEASES_URL }.header("If-None-Match"))
    }

    @Test fun parsingIsStrictAndErrorsBecomeNeutralStatus() {
        assertEquals(listOf("build-3"), GitHubUpdateClient.parseReleases(releasesJson(3)).map { it.tag })
        for (body in listOf("{}", "[broken", """[{"draft":false}]""", """[{"tag_name":"build-1","assets":[{"name":"a","size":"x"}]}]"""))
            assertEquals(UpdateError.INVALID_RESPONSE, assertFailsWith<UpdateFailure> { GitHubUpdateClient.parseReleases(body) }.error)
        assertEquals(UpdateStatus.Failed(UpdateError.INVALID_RESPONSE), Updates.check(app, { client(releases = "[broken") }, installed = 1))
        assertEquals(UpdateStatus.Current(9), Updates.check(app, { client(releases = "[]") }, installed = 9))
    }

    @Test fun downloadFollowsTheRedirectVerifiesAndHandsTheFileToTheInstaller() {
        var lastProgress = 0L
        val build = Updates.downloadAndInstall(app, client(), inspector(own.copy(versionCode = 7)), own, session) { done, _ -> lastProgress = done }
        assertEquals(7L, build.number)
        assertEquals(1, installedFiles)
        assertEquals(apkBytes.size.toLong(), lastProgress)
        assertTrue(requests.none { it.header("Authorization") != null })
        assertTrue(requests.all { it.url.isHttps })
        assertTrue(requests.any { it.url.host == "release-assets.example.net" })
        assertTrue(Updates.downloadDirectory(app).listFiles().isNullOrEmpty(), "downloaded file must be deleted")
    }

    @Test fun redirectToPlainHttpIsRefused() {
        val failure = assertFailsWith<UpdateFailure> {
            Updates.downloadAndInstall(app, client(redirect = "http://release-assets.example.net/x"), inspector(own.copy(versionCode = 7)), own, session)
        }
        assertEquals(UpdateError.INVALID_RESPONSE, failure.error)
        assertEquals(0, installedFiles)
        assertTrue(requests.none { it.url.scheme == "http" })
    }

    @Test fun checksumMismatchRefusesAndDeletesTheDownload() {
        val failure = assertFailsWith<UpdateFailure> {
            Updates.downloadAndInstall(app, client(checksum = "${"0".repeat(64)}  ${UpdateReleases.apkName(7)}\n"),
                inspector(own.copy(versionCode = 7)), own, session)
        }
        assertEquals(UpdateError.CHECKSUM, failure.error)
        assertEquals(0, installedFiles)
        assertTrue(Updates.downloadDirectory(app).listFiles().isNullOrEmpty())
    }

    @Test fun wrongPackageOrCertificateRefuses() {
        for ((identity, error) in listOf(
            own.copy(packageName = "com.example.other", versionCode = 7) to UpdateError.WRONG_PACKAGE,
            own.copy(signers = setOf("bb".repeat(32)), versionCode = 7) to UpdateError.WRONG_SIGNATURE,
            own.copy(signers = own.signers + "bb".repeat(32), versionCode = 7) to UpdateError.WRONG_SIGNATURE,
            own.copy(signers = emptySet(), versionCode = 7) to UpdateError.WRONG_SIGNATURE,
            own.copy(versionCode = 6) to UpdateError.NOT_NEWER,
            null to UpdateError.WRONG_PACKAGE,
        )) {
            val failure = assertFailsWith<UpdateFailure> { Updates.downloadAndInstall(app, client(), inspector(identity), own, session) }
            assertEquals(error, failure.error, "$identity")
            assertTrue(Updates.downloadDirectory(app).listFiles().isNullOrEmpty())
        }
        assertEquals(0, installedFiles)
        // an installed app without readable certificates never accepts anything
        assertEquals(UpdateError.WRONG_SIGNATURE, ApkCheck.refusal(own.copy(versionCode = 7), own.copy(signers = emptySet())))
    }

    @Test fun sizeMismatchAndOversizedBodiesAreRefused() {
        val failure = assertFailsWith<UpdateFailure> {
            Updates.downloadAndInstall(app, client(releases = releasesJson(7, apkSize = 100), apk = apkBytes),
                inspector(own.copy(versionCode = 7)), own, session)
        }
        // the listed size does not match what arrives
        assertEquals(UpdateError.INVALID_RESPONSE, failure.error)
        assertEquals(0, installedFiles)
        assertNull(UpdateReleases.newest(GitHubUpdateClient.parseReleases(releasesJson(7, apkSize = UpdateReleases.MAX_APK_BYTES + 1))))
        val tooLarge = assertFailsWith<UpdateFailure> {
            Updates.downloadAndInstall(app, client(checksum = "x".repeat(5000)), inspector(own.copy(versionCode = 7)), own, session)
        }
        assertEquals(UpdateError.TOO_LARGE, tooLarge.error)
        assertEquals(0, installedFiles)
    }

    @Suppress("DEPRECATION") // JobInfo.networkType, still the value set by setRequiredNetworkType
    @Test fun switchingOffCancelsTheJobAndAScheduledRunWithoutSwitchMakesNoRequest() {
        Updates.setDaily(app, true)
        val job = assertNotNull(UpdateScheduler.pending(app))
        assertEquals(UpdateScheduler.PERIOD_MILLIS, job.intervalMillis)
        assertTrue(job.isPersisted)
        assertEquals(android.app.job.JobInfo.NETWORK_TYPE_ANY, job.networkType)
        Updates.setDaily(app, false)
        assertNull(UpdateScheduler.pending(app))

        // switch off but a job left over (e.g. from an older install): the run cancels it, no request
        UpdateScheduler.schedule(app)
        Updates.runScheduled(app, { client() }) { fail("must not notify") }
        assertTrue(requests.isEmpty())
        assertNull(UpdateScheduler.pending(app))
    }

    @Test fun scheduledRunNotifiesOncePerBuild() {
        Updates.setDaily(app, true)
        val notified = mutableListOf<Long>()
        // the installed debug build of the tests has version code 1
        Updates.runScheduled(app, { client() }) { notified += it }
        Updates.runScheduled(app, { client() }) { notified += it }
        assertEquals(listOf(7L), notified)
        Updates.runScheduled(app, { client(releases = releasesJson(8, 7)) }) { notified += it }
        assertEquals(listOf(7L, 8L), notified)
        assertNotNull(UpdateScheduler.pending(app))
    }

    @Test fun notificationOpensTheUpdatesPageNotADownload() {
        val intent = UpdateNotifier.pageIntent(app)
        assertEquals(AgentPreferences.UPDATES_DESTINATION, intent.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertTrue(AgentPreferences.UPDATES_DESTINATION in AgentPreferences.SETTINGS_DESTINATIONS)
        assertEquals(helium314.keyboard.settings.SettingsActivity::class.java.name, intent.component?.className)
        assertNull(intent.data)
    }

    @Test fun leftoverDownloadIsRemovedOnCleanup() {
        val dir = Updates.downloadDirectory(app).apply { mkdirs() }
        File(dir, "update.apk").writeBytes(apkBytes)
        Updates.cleanup(app)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }
}
