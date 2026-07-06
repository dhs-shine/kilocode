package ai.kilocode.backend.cli

import ai.kilocode.backend.testing.TestLog
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KiloCliDownloaderTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `downloads extracts and caches pinned cli`() = runBlocking {
        MockWebServer().use { server ->
            val bytes = archive()
            server.enqueue(metadata(bytes))
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))
            val seen = mutableListOf<CliDownload>()
            val log = TestLog()
            val cli = KiloCliDownloader(
                log = log,
                root = dir,
                baseUrl = server.url("/release").toString(),
                api = server.url("/api").toString(),
            ).resolve("1.2.3", onProgress = { seen.add(it) })

            assertTrue(cli.isFile)
            assertEquals(File(File(dir, "1.2.3"), KiloCliPlatform.current()).absolutePath, cli.parentFile.parentFile.absolutePath)
            assertEquals("#!/bin/sh\n", cli.readText())
            assertTrue(File(cli.parentFile, "kilo-sandbox-mutation-worker.js").isFile)
            assertEquals("/api/v1.2.3", server.takeRequest().path)
            assertEquals("/release/v1.2.3/kilo-${KiloCliPlatform.current()}.${KiloCliPlatform.archive()}", server.takeRequest().path)
            assertEquals(CliDownload(0, "1.2.3", KiloCliPlatform.current()), seen.first())
            assertTrue(seen.any { it.percent == 100 && it.version == "1.2.3" && it.platform == KiloCliPlatform.current() })
            assertContains(log.messages, "INFO: Kilo CLI 1.2.3 for ${KiloCliPlatform.current()} is not cached; downloading new release into ${cli.parentFile.parentFile.absolutePath}")

            val cachedProgress = mutableListOf<CliDownload>()
            val cached = KiloCliDownloader(
                log = log,
                root = dir,
                baseUrl = server.url("/release").toString(),
                api = server.url("/api").toString(),
            ).resolve("1.2.3", onProgress = { cachedProgress.add(it) })
            assertEquals(cli.absolutePath, cached.absolutePath)
            assertEquals(2, server.requestCount)
            assertTrue(cachedProgress.isEmpty())
            assertContains(log.messages, "INFO: Using cached Kilo CLI 1.2.3 for ${KiloCliPlatform.current()} at ${cli.absolutePath}")

            File(cli.parentFile.parentFile, ".complete").writeText("ok\n")
            server.enqueue(metadata(bytes))
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))
            val stale = KiloCliDownloader(
                log = log,
                root = dir,
                baseUrl = server.url("/release").toString(),
                api = server.url("/api").toString(),
            ).resolve("1.2.3")
            assertEquals(cli.absolutePath, stale.absolutePath)
            assertEquals(4, server.requestCount)

            server.enqueue(metadata(bytes))
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))
            val forced = KiloCliDownloader(
                log = log,
                root = dir,
                baseUrl = server.url("/release").toString(),
                api = server.url("/api").toString(),
            ).resolve("1.2.3", force = true)
            assertEquals(cli.absolutePath, forced.absolutePath)
            assertEquals(6, server.requestCount)
        }
    }

    @Test
    fun `rejects cli archive with mismatched digest`() = runBlocking {
        MockWebServer().use { server ->
            val bytes = archive()
            server.enqueue(metadata("sha256:${sha256("different".toByteArray())}"))
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))

            val ex = assertFailsWith<IllegalStateException> {
                KiloCliDownloader(
                    root = dir,
                    baseUrl = server.url("/release").toString(),
                    api = server.url("/api").toString(),
                ).resolve("1.2.3")
            }

            assertContains(ex.message.orEmpty(), "digest mismatch")
            assertFalse(File(File(File(dir, "1.2.3"), KiloCliPlatform.current()), ".complete").exists())
        }
    }

    private fun archive(): ByteArray {
        val files = mapOf(
            "bin/${KiloCliPlatform.exe()}" to "#!/bin/sh\n".toByteArray(),
            "bin/kilo-sandbox-mutation-worker.js" to "worker\n".toByteArray(),
        )
        if (KiloCliPlatform.archive() == "zip") return zip(files)
        return tar(files)
    }

    private fun metadata(bytes: ByteArray) = metadata("sha256:${sha256(bytes)}")

    private fun metadata(digest: String) = MockResponse().setResponseCode(200).setBody(
        """{"assets":[{"name":"kilo-${KiloCliPlatform.current()}.${KiloCliPlatform.archive()}","digest":"$digest"}]}"""
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.key))
                zip.write(entry.value)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun tar(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        GzipCompressorOutputStream(out).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                files.forEach { entry ->
                    val item = TarArchiveEntry(entry.key)
                    item.size = entry.value.size.toLong()
                    tar.putArchiveEntry(item)
                    tar.write(entry.value)
                    tar.closeArchiveEntry()
                }
            }
        }
        return out.toByteArray()
    }
}
