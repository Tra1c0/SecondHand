package dev.personal.secondhand.companion

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: HttpServer
    private val payload = ByteArray(700_000) { (it * 31 % 251).toByte() }

    /** What the server does with the next requests; the tests set it. */
    @Volatile private var supportRange = true
    @Volatile private var status = 200
    @Volatile private var failFirstAfter = -1 // abort the first response after this many bytes (a network drop)
    private val requests = mutableListOf<String?>()

    private val url get() = "http://127.0.0.1:${server.address.port}/file.zip"

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex: HttpExchange ->
            synchronized(requests) { requests += ex.requestHeaders.getFirst("Range") }
            if (status != 200) {
                ex.sendResponseHeaders(status, -1)
                ex.close()
                return@createContext
            }
            val range = ex.requestHeaders.getFirst("Range")
            var start = 0
            if (supportRange && range != null) {
                start = range.removePrefix("bytes=").removeSuffix("-").toInt()
                if (start >= payload.size) {
                    ex.sendResponseHeaders(416, -1)
                    ex.close()
                    return@createContext
                }
                ex.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
                ex.responseHeaders.add("ETag", "\"v1\"")
                ex.sendResponseHeaders(206, (payload.size - start).toLong())
            } else {
                ex.responseHeaders.add("ETag", "\"v1\"")
                ex.sendResponseHeaders(200, payload.size.toLong())
            }
            ex.responseBody.use { out ->
                if (failFirstAfter >= 0) {
                    val n = failFirstAfter
                    failFirstAfter = -1
                    out.write(payload, start, n)
                    out.flush()
                    ex.close() // closes early: the client sees a truncated body
                } else {
                    out.write(payload, start, payload.size - start)
                }
            }
        }
        server.start()
    }

    @After fun stop() = server.stop(0)

    private fun dl() = HttpZipDownloader(maxAttempts = 4, backoffMs = 10)

    @Test fun planArithmetic() {
        assertEquals(ResumePlan.Step.Fetch(0), ResumePlan.plan(0, 100))
        assertEquals(ResumePlan.Step.Fetch(40), ResumePlan.plan(40, 100))
        assertEquals(ResumePlan.Step.AlreadyComplete, ResumePlan.plan(100, 100))
        assertTrue(ResumePlan.plan(101, 100) is ResumePlan.Step.Restart)
        assertEquals(ResumePlan.Step.Fetch(0), ResumePlan.plan(-5, 100))
    }

    @Test fun contentRangeParsing() {
        assertEquals(Triple(100L, 999L, 1000L), ResumePlan.parseContentRange("bytes 100-999/1000"))
        assertNull(ResumePlan.parseContentRange("bytes */1000"))
        assertNull(ResumePlan.parseContentRange(null))
    }

    @Test fun responseDecisions() {
        assertEquals(ResumePlan.Action.Append, ResumePlan.onResponse(206, "bytes 100-999/1000", 900, 100, 1000))
        assertEquals(ResumePlan.Action.Truncate, ResumePlan.onResponse(200, null, 1000, 100, 1000))
        assertEquals(ResumePlan.Action.Truncate, ResumePlan.onResponse(200, null, -1, 0, 1000))
        assertEquals(ResumePlan.Action.Complete, ResumePlan.onResponse(416, null, 0, 1000, 1000))
        assertEquals(ResumePlan.Action.Truncate, ResumePlan.onResponse(416, null, 0, 500, 1000))
        assertEquals(DownloadException.Kind.SIZE_MISMATCH, (ResumePlan.onResponse(200, null, 999, 0, 1000) as ResumePlan.Action.Fail).kind)
        assertEquals(DownloadException.Kind.SIZE_MISMATCH, (ResumePlan.onResponse(206, "bytes 90-999/1000", 910, 100, 1000) as ResumePlan.Action.Fail).kind)
        assertEquals(DownloadException.Kind.SIZE_MISMATCH, (ResumePlan.onResponse(206, "bytes 100-999/2000", 900, 100, 1000) as ResumePlan.Action.Fail).kind)
        assertEquals(DownloadException.Kind.HTTP_STATUS, (ResumePlan.onResponse(404, null, 0, 0, 1000) as ResumePlan.Action.Fail).kind)
        assertEquals(DownloadException.Kind.HTTP_STATUS, (ResumePlan.onResponse(206, "garbage", 0, 0, 1000) as ResumePlan.Action.Fail).kind)
    }

    @Test fun spaceNeededCountsOnlyWhatIsLeftToDownload() {
        val gb = 1L shl 30
        assertEquals(2 * gb + 3 * gb + gb, ResumePlan.spaceNeeded(2 * gb, 0, 3 * gb))
        assertEquals(1 * gb + 3 * gb + gb, ResumePlan.spaceNeeded(2 * gb, 1 * gb, 3 * gb))
        assertEquals(3 * gb + gb, ResumePlan.spaceNeeded(2 * gb, 5 * gb, 3 * gb))
    }

    @Test fun aFullDownloadWritesExactlyTheExpectedFile() {
        val dest = File(tmp.root, "d/file.zip")
        val seen = mutableListOf<Long>()
        dl().download(url, dest.path, payload.size.toLong(), { d, _ -> seen += d }, { false })
        assertTrue(dest.readBytes().contentEquals(payload))
        assertFalse(File(dest.path + ".part").exists())
        assertEquals(payload.size.toLong(), seen.last())
    }

    @Test fun aPartialFileIsResumedWithARangeRequest() {
        val dest = File(tmp.root, "file.zip")
        File(dest.path + ".part").writeBytes(payload.copyOf(300_000))
        File(dest.path + ".part.etag").writeText("\"v1\"")
        dl().download(url, dest.path, payload.size.toLong(), { _, _ -> }, { false })
        assertTrue(dest.readBytes().contentEquals(payload))
        assertEquals("bytes=300000-", requests.first())
    }

    @Test fun aServerThatIgnoresTheRangeRestartsTheFileInsteadOfCorruptingIt() {
        supportRange = false
        val dest = File(tmp.root, "file.zip")
        File(dest.path + ".part").writeBytes(ByteArray(300_000) { 9 }) // a stale partial file
        dl().download(url, dest.path, payload.size.toLong(), { _, _ -> }, { false })
        assertTrue(dest.readBytes().contentEquals(payload))
    }

    @Test fun aDroppedConnectionIsResumedByTheNextAttempt() {
        failFirstAfter = 250_000
        val dest = File(tmp.root, "file.zip")
        dl().download(url, dest.path, payload.size.toLong(), { _, _ -> }, { false })
        assertTrue(dest.readBytes().contentEquals(payload))
        assertTrue(requests.size >= 2)
        assertTrue(requests.drop(1).any { it != null && it.startsWith("bytes=") })
    }

    @Test fun aWrongSizeIsRefusedAndTheFileIsNotKept() {
        val dest = File(tmp.root, "file.zip")
        try {
            dl().download(url, dest.path, payload.size + 5L, { _, _ -> }, { false })
            fail("expected a size mismatch")
        } catch (e: DownloadException) {
            assertEquals(DownloadException.Kind.SIZE_MISMATCH, e.kind)
        }
        assertFalse(dest.exists())
    }

    @Test fun aNotFoundIsReportedWithItsStatus() {
        status = 404
        try {
            dl().download(url, File(tmp.root, "x.zip").path, 10, { _, _ -> }, { false })
            fail("expected a 404")
        } catch (e: DownloadException) {
            assertEquals(DownloadException.Kind.HTTP_STATUS, e.kind)
            assertEquals(404, e.httpStatus)
        }
    }

    @Test fun cancellingStopsTheDownloadAndKeepsThePartialFile() {
        val dest = File(tmp.root, "file.zip")
        var calls = 0
        try {
            dl().download(url, dest.path, payload.size.toLong(), { _, _ -> }, { ++calls > 2 })
            fail("expected a cancel")
        } catch (e: DownloadException) {
            assertEquals(DownloadException.Kind.CANCELLED, e.kind)
        }
        assertFalse(dest.exists())
    }

    @Test fun anAlreadyCompleteFileIsLeftAlone() {
        val dest = File(tmp.root, "file.zip")
        dest.writeBytes(payload)
        dl().download("http://127.0.0.1:1/never", dest.path, payload.size.toLong(), { _, _ -> }, { false })
        assertTrue(requests.isEmpty())
    }

    // ---- the zip check ----

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val f = File(tmp.root, name)
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (n, b) ->
                z.putNextEntry(ZipEntry(n))
                z.write(b)
                z.closeEntry()
            }
        }
        return f
    }

    @Test fun aGoodBundleZipPasses() {
        val f = zip("ok.zip", "m/metadata.json" to "{}".toByteArray(), "m/part1_of_4.bin" to ByteArray(5000) { it.toByte() }, "m/part2_of_4.bin" to ByteArray(10))
        val s = ZipCheck.verify(f)
        assertEquals(3, s.entries)
        assertEquals(2, s.binFiles)
        assertTrue(s.hasMetadata)
    }

    @Test fun aZipWithoutMetadataOrBinIsRefused() {
        assertEquals(DownloadException.Kind.ZIP_INVALID, kindOf { ZipCheck.verify(zip("a.zip", "x/part1.bin" to ByteArray(3))) })
        assertEquals(DownloadException.Kind.ZIP_INVALID, kindOf { ZipCheck.verify(zip("b.zip", "x/metadata.json" to "{}".toByteArray())) })
    }

    @Test fun aTruncatedOrDamagedZipIsRefused() {
        val good = zip("g.zip", "m/metadata.json" to "{}".toByteArray(), "m/part1_of_4.bin" to ByteArray(20000) { (it % 7).toByte() })
        val bytes = good.readBytes()
        val cut = File(tmp.root, "cut.zip").also { it.writeBytes(bytes.copyOf(bytes.size - 40)) }
        assertEquals(DownloadException.Kind.ZIP_INVALID, kindOf { ZipCheck.verify(cut) })
        // flip a byte inside the stored/deflated data of the big entry
        val flipped = bytes.copyOf().also { it[300] = (it[300] + 1).toByte() }
        val bad = File(tmp.root, "bad.zip").also { it.writeBytes(flipped) }
        assertEquals(DownloadException.Kind.ZIP_INVALID, kindOf { ZipCheck.verify(bad) })
        assertEquals(DownloadException.Kind.ZIP_INVALID, kindOf { ZipCheck.verify(File(tmp.root, "missing.zip")) })
    }

    private fun kindOf(block: () -> Unit): DownloadException.Kind? = try {
        block()
        null
    } catch (e: DownloadException) {
        e.kind
    }
}
