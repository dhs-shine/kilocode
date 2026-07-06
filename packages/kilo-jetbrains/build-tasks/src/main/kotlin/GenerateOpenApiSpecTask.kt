import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Generates the CLI OpenAPI spec from the pinned release binary.
 */
abstract class GenerateOpenApiSpecTask : DefaultTask() {
    @get:Input
    abstract val cliVersion: Property<String>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputFile
    abstract val spec: RegularFileProperty

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun run() {
        val kilo = resolve()
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val result = exec.exec {
            commandLine(kilo.absolutePath, "generate")
            standardOutput = out
            errorOutput = err
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException(
                "kilo generate failed with exit code ${result.exitValue}.\n" +
                    err.toString(Charsets.UTF_8).take(2000)
            )
        }
        val json = out.toString(Charsets.UTF_8)
        if (!json.trimStart().startsWith("{")) {
            throw GradleException(
                "kilo generate did not produce JSON.\n" +
                    "stdout: ${json.take(200)}\n" +
                    "stderr: ${err.toString(Charsets.UTF_8).take(500)}"
            )
        }
        spec.get().asFile.also { it.parentFile.mkdirs() }.writeText(json)
    }

    private fun resolve(): File {
        val version = cliVersion.get()
        val platform = platform()
        val ext = if (platform.startsWith("linux-")) "tar.gz" else "zip"
        val dir = cacheDir.dir(version).map { it.dir(platform) }.get().asFile
        val exe = File(dir, "bin/${exe()}")
        val done = File(dir, ".complete")
        if (exe.isFile && done.isFile) {
            if (!windows()) exe.setExecutable(true)
            return exe
        }
        dir.mkdirs()
        val archive = File(dir, "kilo-$platform.$ext")
        download("https://github.com/Kilo-Org/kilocode/releases/download/v$version/kilo-$platform.$ext", archive)
        extract(archive, dir)
        if (!exe.isFile) throw GradleException("Downloaded CLI archive did not contain bin/${exe()}")
        if (!windows()) exe.setExecutable(true)
        done.writeText("ok\n")
        return exe
    }

    private fun download(url: String, file: File) {
        logger.lifecycle("Downloading pinned Kilo CLI from $url")
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw GradleException("Failed to download pinned Kilo CLI: HTTP $code from $url")
            conn.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun extract(file: File, dir: File) {
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
            throw GradleException("Archive entry escapes target directory: $name")
        }
        if (directory) {
            target.mkdirs()
            return
        }
        target.parentFile.mkdirs()
        target.outputStream().use(copy)
        if (!windows() && (target.name == "kilo" || target.name == "bwrap")) target.setExecutable(true)
    }

    private fun platform(): String {
        val os = System.getProperty("os.name").lowercase()
        val name = when {
            os.contains("mac") || os.contains("darwin") -> "darwin"
            os.contains("linux") -> "linux"
            os.contains("windows") -> "windows"
            else -> throw GradleException("Unsupported OS: ${System.getProperty("os.name")}")
        }
        val arch = when (System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64" -> "x64"
            else -> throw GradleException("Unsupported architecture: ${System.getProperty("os.arch")}")
        }
        return "$name-$arch"
    }

    private fun exe() = if (windows()) "kilo.exe" else "kilo"

    private fun windows() = System.getProperty("os.name").lowercase().contains("windows")
}
