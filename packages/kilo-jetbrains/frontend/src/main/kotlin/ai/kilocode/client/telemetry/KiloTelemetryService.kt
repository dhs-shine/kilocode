@file:Suppress("UnstableApiUsage")

package ai.kilocode.client.telemetry

import ai.kilocode.log.KiloLog
import ai.kilocode.rpc.KiloAppRpcApi
import ai.kilocode.rpc.dto.TelemetryCaptureDto
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object Telemetry {
    fun send(event: String, properties: Map<String, String> = emptyMap()) {
        KiloTelemetryService.getInstance().send(event, properties)
    }
}

@Service(Service.Level.APP)
class KiloTelemetryService internal constructor(
    private val cs: CoroutineScope,
    private val rpc: KiloAppRpcApi?,
) {
    constructor(cs: CoroutineScope) : this(cs, null)

    companion object {
        private val LOG = KiloLog.create(KiloTelemetryService::class.java)

        fun getInstance(): KiloTelemetryService = service()
    }

    fun capture(event: String, properties: Map<String, String> = emptyMap()) {
        send(event, properties)
    }

    fun send(event: String, properties: Map<String, String> = emptyMap()) {
        cs.launch {
            try {
                val dto = TelemetryCaptureDto(event, properties)
                val api = rpc
                if (api != null) api.captureTelemetry(dto)
                else durable { KiloAppRpcApi.getInstance().captureTelemetry(dto) }
            } catch (e: Exception) {
                LOG.warn("telemetry capture failed: ${e.message}", e)
            }
        }
    }
}
