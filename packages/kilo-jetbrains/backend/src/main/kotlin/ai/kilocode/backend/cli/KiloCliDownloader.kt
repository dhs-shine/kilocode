package ai.kilocode.backend.cli

import ai.kilocode.log.KiloLog
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

class KiloCliDownloader(
    private val http: OkHttpClient = OkHttpClient(),
    private val log: KiloLog = KiloLog.create(KiloCliDownloader::class.java),
    private val root: File = File(PathManager.getSystemPath(), "kilo/cli"),
    private val baseUrl: String = "https://github.com/Kilo-Org/kilocode/releases/download",
) {
    suspend fun resolve(version: String, force: Boolean = false, onProgress: (CliDownload) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val platform = KiloCliPlatform.current()
            val dir = File(File(root, version), platform)
            val exe = File(dir, "bin/${KiloCliPlatform.exe()}")
            val done = File(dir, ".complete")

            if (force && dir.exists()) {
                log.info("Deleting cached CLI $version under ${dir.absolutePath}")
                dir.deleteRecursively()
            }

            if (exe.isFile && done.isFile) {
                log.info("Using cached Kilo CLI $version for $platform at ${exe.absolutePath}")
                if (!SystemInfo.isWindows) exe.setExecutable(true)
                return@withContext exe
            }

            log.info("Kilo CLI $version for $platform is not cached; downloading new release into ${dir.absolutePath}")
            dir.mkdirs()
            val ext = KiloCliPlatform.archive(platform)
            val archive = File(dir, "kilo-$platform.$ext")
            onProgress(CliDownload(0, version, platform))
            download(version, platform, ext, archive, onProgress)
            log.info("Downloaded Kilo CLI $version for $platform to ${archive.absolutePath} (size=${archive.length()} bytes)")
            extract(archive, dir)
            if (!exe.isFile) throw IllegalStateException("Downloaded CLI archive did not contain bin/${KiloCliPlatform.exe()}")
            if (!SystemInfo.isWindows) exe.setExecutable(true)
            done.writeText("ok\n")
            onProgress(CliDownload(100, version, platform))
            exe
        }

    private fun download(version: String, platform: String, ext: String, file: File, onProgress: (CliDownload) -> Unit) {
        val url = url(version, platform, ext)
        log.info("Downloading Kilo CLI $version for $platform from $url")
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download Kilo CLI $version for $platform: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Failed to download Kilo CLI $version: empty response body")
            val total = body.contentLength()
            var read = 0L
            var last = 0
            file.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = ((read.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
                            if (pct != last) {
                                last = pct
                                onProgress(CliDownload(pct, version, platform))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extract(file: File, dir: File) {
        log.info("Extracting Kilo CLI archive ${file.absolutePath}")
        if (file.name.endsWith(".zip")) {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    write(dir, entry.name, entry.isDirectory) { out -> zip.copyTo(out) }
                    zip.closeEntry()
                }
            }
            return
        }

        TarArchiveInputStream(GzipCompressorInputStream(file.inputStream().buffered())).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                write(dir, entry.name, entry.isDirectory) { out -> tar.copyTo(out) }
            }
        }
    }

    private fun write(dir: File, name: String, directory: Boolean, copy: (java.io.OutputStream) -> Unit) {
        val path = if (name.startsWith("bin/")) name else "bin/$name"
        val target = File(dir, path).canonicalFile
        val base = dir.canonicalFile
        if (target != base && !target.path.startsWith(base.path + File.separator)) {
            throw IllegalStateException("Archive entry escapes target directory: $name")
        }
        if (directory) {
            target.mkdirs()
            return
        }
        target.parentFile.mkdirs()
        target.outputStream().use(copy)
        if (!SystemInfo.isWindows && (target.name == "kilo" || target.name == "bwrap")) {
            target.setExecutable(true)
        }
    }

    private fun url(version: String, platform: String, ext: String) =
        "${baseUrl.trimEnd('/')}/v$version/kilo-$platform.$ext"
}
