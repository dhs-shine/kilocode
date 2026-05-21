package ai.kilocode.jetbrains.api.infrastructure

import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonElement
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object Serializer {
    private var isAdaptersInitialized = false

    @JvmStatic
    val kotlinxSerializationAdapters: SerializersModule by lazy {
        isAdaptersInitialized = true
        SerializersModule {
            contextual(BigDecimal::class, BigDecimalAdapter)
            contextual(BigInteger::class, BigIntegerAdapter)
            contextual(LocalDate::class, LocalDateAdapter)
            contextual(LocalDateTime::class, LocalDateTimeAdapter)
            contextual(OffsetDateTime::class, OffsetDateTimeAdapter)
            contextual(UUID::class, UUIDAdapter)
            contextual(AtomicInteger::class, AtomicIntegerAdapter)
            contextual(AtomicLong::class, AtomicLongAdapter)
            contextual(AtomicBoolean::class, AtomicBooleanAdapter)
            contextual(URI::class, URIAdapter)
            contextual(URL::class, URLAdapter)
            contextual(StringBuilder::class, StringBuilderAdapter)
            contextual(Any::class, AnySerializer)

            apply(kotlinxSerializationAdaptersConfiguration)
        }
    }

    var kotlinxSerializationAdaptersConfiguration: SerializersModuleBuilder.() -> Unit = {}
        set(value) {
            check(!isAdaptersInitialized) {
                "Cannot configure kotlinxSerializationAdaptersConfiguration after kotlinxSerializationAdapters has been initialized."
            }
            field = value
        }

    private var isJsonInitialized = false

    @JvmStatic
    val kotlinxSerializationJson: Json by lazy {
        isJsonInitialized = true
        Json {
            serializersModule = kotlinxSerializationAdapters
            encodeDefaults = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true

            apply(kotlinxSerializationJsonConfiguration)
        }
    }

    var kotlinxSerializationJsonConfiguration: JsonBuilder.() -> Unit = {}
        set(value) {
            check(!isJsonInitialized) {
                "Cannot configure kotlinxSerializationJsonConfiguration after kotlinxSerializationJson has been initialized."
            }
            field = value
        }
}

internal object AnySerializer : KSerializer<Any> {
    private val delegate = JsonElement.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: Any) {
        val json = (encoder as JsonEncoder).json
        encoder.encodeSerializableValue(delegate, json.encodeToJsonElement(delegate, value as? JsonElement ?: return))
    }
    override fun deserialize(decoder: Decoder): Any {
        return (decoder as JsonDecoder).decodeJsonElement()
    }
}
