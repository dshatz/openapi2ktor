package {{ client }}

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate

class JavaLocalDateSerializer: KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(LocalDate::class.qualifiedName!!, PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString())
    }
    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }
}

class JavaInstantSerializer: KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(Instant::class.qualifiedName!!, PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
}