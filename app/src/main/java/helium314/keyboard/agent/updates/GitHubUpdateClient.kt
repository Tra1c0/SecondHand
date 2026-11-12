// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.updates

import android.content.Context
import dev.agentkeyboard.core.AvailableBuild
import dev.agentkeyboard.core.ReleaseAsset
import dev.agentkeyboard.core.ReleaseInfo
import dev.agentkeyboard.core.UpdateReleases
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class UpdateError {
    LOCKED, NOT_FOUND, RATE_LIMIT, SERVICE, NETWORK, TIMEOUT, INVALID_RESPONSE, TOO_LARGE, STORAGE,
    CHECKSUM, WRONG_PACKAGE, WRONG_SIGNATURE, NOT_NEWER, INSTALL,
}
class UpdateFailure(val error: UpdateError) : Exception(error.name)

/** HTTP client of the update check, separate from the AI client so a download never blocks a model call. */
object UpdateHttp {
    private val delegate = lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
            // GitHubUpdateClient follows redirects itself, HTTPS only and with a fixed limit.
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .build()
    }

    /** Only from an explicit action or the opt-in daily job, never while typing. Call off the main thread. */
    fun calls(context: Context): Call.Factory {
        OkHttp.initialize(context.applicationContext)
        return delegate.value
    }
}

/**
 * Reads the public releases of [UpdateReleases.REPOSITORY]: no token, no cookies, no
 * Authorization header on any request. Blocking, call on IO. Response bodies are never logged.
 */
class GitHubUpdateClient(private val calls: Call.Factory) {
    fun newest(): AvailableBuild? = UpdateReleases.newest(releases())

    fun releases(): List<ReleaseInfo> {
        val request = Request.Builder().url(RELEASES_URL).get()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", USER_AGENT)
            .build()
        val body = execute(request, CHECK_TIMEOUT_SECONDS).use { readBounded(it, MAX_RELEASES_BYTES) }
        return parseReleases(body.toString(Charsets.UTF_8))
    }

    /** The expected lowercase SHA-256 of the build's APK, from its `.sha256` asset. */
    fun expectedChecksum(build: AvailableBuild): String {
        val bytes = download(build.checksum, CHECK_TIMEOUT_SECONDS).use { readBounded(it, UpdateReleases.MAX_CHECKSUM_BYTES) }
        return UpdateReleases.parseChecksum(bytes.toString(Charsets.UTF_8), build.apk.name)
            ?: throw UpdateFailure(UpdateError.INVALID_RESPONSE)
    }

    /**
     * Streams the APK into [target] and returns its lowercase SHA-256. The file is deleted again on
     * any failure. [onProgress] gets (bytes so far, expected size); [checkCancelled] may throw.
     */
    fun downloadApk(build: AvailableBuild, target: File, checkCancelled: () -> Unit = {},
                    onProgress: (Long, Long) -> Unit = { _, _ -> }): String {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            download(build.apk, DOWNLOAD_TIMEOUT_SECONDS).use { response ->
                target.outputStream().use { output ->
                    val total = copyBounded(response, UpdateReleases.MAX_APK_BYTES) { buffer, count, sofar ->
                        checkCancelled()
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        onProgress(sofar, build.apk.size)
                    }
                    if (total != build.apk.size) throw UpdateFailure(UpdateError.INVALID_RESPONSE)
                }
            }
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Starts at the asset's public download URL (already checked by [UpdateReleases.newest]) and
     * follows at most [MAX_REDIRECTS] redirects, each to HTTPS only.
     */
    private fun download(asset: ReleaseAsset, timeoutSeconds: Long): Response {
        var url: HttpUrl = asset.downloadUrl.toHttpUrl()
        repeat(MAX_REDIRECTS + 1) {
            if (!url.isHttps) throw UpdateFailure(UpdateError.INVALID_RESPONSE)
            val request = Request.Builder().url(url).get()
                .header("Accept", "application/octet-stream").header("User-Agent", USER_AGENT).build()
            val response = execute(request, timeoutSeconds, allowRedirect = true)
            if (response.code !in REDIRECTS) return response
            val location = response.header("Location")
            response.close()
            url = location?.let { url.resolve(it) } ?: throw UpdateFailure(UpdateError.INVALID_RESPONSE)
        }
        throw UpdateFailure(UpdateError.INVALID_RESPONSE)
    }

    private fun execute(request: Request, timeoutSeconds: Long, allowRedirect: Boolean = false): Response {
        val response = try {
            calls.newCall(request).apply { timeout().timeout(timeoutSeconds, TimeUnit.SECONDS) }.execute()
        } catch (_: InterruptedIOException) { throw UpdateFailure(UpdateError.TIMEOUT) }
        catch (_: IOException) { throw UpdateFailure(UpdateError.NETWORK) }
        if (response.code in 200..299 || (allowRedirect && response.code in REDIRECTS)) return response
        val error = when (response.code) {
            403, 429 -> if (response.code == 429 || response.header("x-ratelimit-remaining") == "0")
                UpdateError.RATE_LIMIT else UpdateError.SERVICE
            404 -> UpdateError.NOT_FOUND
            else -> UpdateError.SERVICE
        }
        response.close()
        throw UpdateFailure(error)
    }

    private fun readBounded(response: Response, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        copyBounded(response, limit) { buffer, count, _ -> output.write(buffer, 0, count) }
        return output.toByteArray()
    }

    private fun copyBounded(response: Response, limit: Long, sink: (ByteArray, Int, Long) -> Unit): Long {
        if (response.body.contentLength() > limit) throw UpdateFailure(UpdateError.TOO_LARGE)
        try {
            val input = response.body.byteStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return total
                total += count
                if (total > limit) throw UpdateFailure(UpdateError.TOO_LARGE)
                sink(buffer, count, total)
            }
        } catch (_: InterruptedIOException) { throw UpdateFailure(UpdateError.TIMEOUT) }
        catch (_: IOException) { throw UpdateFailure(UpdateError.NETWORK) }
    }

    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/${UpdateReleases.REPOSITORY}/releases?per_page=10"
        const val API_VERSION = "2022-11-28"
        private const val USER_AGENT = "AgentKeyboard-Updater"
        private const val MAX_REDIRECTS = 5
        private const val MAX_RELEASES_BYTES = 2L * 1024 * 1024
        private const val CHECK_TIMEOUT_SECONDS = 30L
        /** Per request; the read timeout still ends a stalled download after 30 s without data. */
        private const val DOWNLOAD_TIMEOUT_SECONDS = 15L * 60
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)

        /** Strict: any release or asset without the expected fields fails the whole check. */
        internal fun parseReleases(body: String): List<ReleaseInfo> = try {
            (Json.parseToJsonElement(body) as JsonArray).map { element ->
                val release = element as JsonObject
                ReleaseInfo(
                    tag = release.string("tag_name"),
                    draft = (release["draft"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    assets = ((release["assets"] as? JsonArray) ?: JsonArray(emptyList())).map { item ->
                        val asset = item as JsonObject
                        ReleaseAsset(
                            name = asset.string("name"),
                            size = (asset["size"] as JsonPrimitive).longOrNull!!,
                            downloadUrl = asset.string("browser_download_url"),
                        )
                    },
                )
            }
        } catch (_: Exception) { throw UpdateFailure(UpdateError.INVALID_RESPONSE) }

        private fun JsonObject.string(key: String): String =
            (this[key] as JsonPrimitive).also { require(it.isString) }.content
    }
}
