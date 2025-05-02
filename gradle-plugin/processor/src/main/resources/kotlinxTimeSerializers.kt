package {{ client }}

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.datetime.Instant
import java.time.LocalDate


class KotlinxInstantSerializer: KSerializer<Instant> {
    private fun Instant.Companion.parseWithBasicOffset(string: String): Instant {
        var lastDigit = string.length
        while (lastDigit > 0 && string[lastDigit - 1].isDigit()) { --lastDigit }
        val digits = string.length - lastDigit // how many digits are there at the end of the string
        if (digits <= 2)
            return parse(string) // no issue in any case
        var newString = string.substring(0, lastDigit + 2)
        lastDigit += 2
        while (lastDigit < string.length) {
            newString += ":" + string.substring(lastDigit, lastDigit + 2)
            lastDigit += 2
        }
        return parse(newString)
    }

    override val descriptor:
            SerialDescriptor = PrimitiveSerialDescriptor(Instant::class.qualifiedName!!, PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parseWithBasicOffset(decoder.decodeString())
    }
    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
}
