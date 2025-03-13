package {{ client }}

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import {{responseName}}

class {{serializerName}}: KSerializer<{{responseName}}> {
    override val descriptor: SerialDescriptor = serializer<{{innerType}}>().descriptor
    override fun deserialize(decoder: Decoder): {{responseName}} {
        return {{responseName}}(decoder.decodeSerializableValue(serializer()))
    }
    override fun serialize(encoder: Encoder, value: {{responseName}}) {
        encoder.encodeSerializableValue(serializer(), value.data)
    }
}