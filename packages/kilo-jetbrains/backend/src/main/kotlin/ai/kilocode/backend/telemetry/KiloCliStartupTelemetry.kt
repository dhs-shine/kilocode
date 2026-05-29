package ai.kilocode.backend.telemetry

import ai.kilocode.backend.dev.KiloDevMode
import ai.kilocode.log.KiloLog
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface CliStartupTelemetry {
    suspend fun report(properties: Map<String, String>)
}

class KiloCliStartupTelemetry(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val url: String = "https://us.i.posthog.com/capture/",
    private val log: KiloLog = KiloLog.create(KiloCliStartupTelemetry::class.java),
) : CliStartupTelemetry {
    override suspend fun report(properties: Map<String, String>) {
        withContext(Dispatchers.IO) {
            try {
                val props = base() + properties
                val body = JsonObject(
                    mapOf(
                        "api_key" to JsonPrimitive("phc_GK2Pxl0HPj5ZPfwhLRjXrtdz8eD7e9MKnXiFrOqnB6z"),
                        "event" to JsonPrimitive("JetBrains CLI Startup Failed"),
                        "distinct_id" to JsonPrimitive(props["machineId"] ?: "jetbrains-unknown"),
                        "properties" to JsonObject(props.mapValues { JsonPrimitive(it.value) }),
                    ),
                ).toString()
                if (KiloDevMode.enabled()) {
                    log.debug { "startup telemetry dev-mode: $body" }
                    return@withContext
                }
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) log.warn("startup telemetry failed: HTTP ${res.code}")
                }
            } catch (e: Exception) {
                log.warn("startup telemetry failed: ${e.message}", e)
            }
        }
    }

    private fun base(): Map<String, String> = buildMap {
        put("appName", "kilo-code")
        put("platform", "jetbrains")
        put("client", "jetbrains")
        put("feature", "jetbrains-plugin")
        put("os", os())
        put("arch", CpuArch.CURRENT.name)
        runCatching {
            val info = ApplicationInfo.getInstance()
            put("editorName", info.fullApplicationName)
            put("jetbrainsBuild", info.build.asString())
        }.onFailure { log.info("Could not read ApplicationInfo for startup telemetry: ${it.message}") }
        runCatching {
            val version = PluginManagerCore.getPlugin(PluginId.getId("ai.kilocode.jetbrains"))?.version
            if (version != null) {
                put("pluginVersion", version)
                put("appVersion", version)
            }
        }.onFailure { log.info("Could not read plugin version for startup telemetry: ${it.message}") }
    }

    private fun os(): String = when {
        SystemInfo.isMac -> "darwin"
        SystemInfo.isLinux -> "linux"
        SystemInfo.isWindows -> "windows"
        else -> "unknown"
    }
}
