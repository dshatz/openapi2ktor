import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import kotlin.reflect.typeOf

internal val testJson = """
    {
        "name": "Orange",
        "color": 2,
        "length": 999
    }
""".trimIndent()


open class PropsSerializer<T: WithAdditionalProps>(private val baseSerializer: KSerializer<T>): JsonTransformingSerializer<T>(baseSerializer) {
    override val descriptor: SerialDescriptor = baseSerializer.descriptor

    @OptIn(InternalSerializationApi::class)
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val additionalProps = mutableMapOf<String, JsonElement>()
        val modelNames = baseSerializer.descriptor.elementNames
        return if (element is JsonObject) {
            element.forEach { (key, value) ->
                if (key !in modelNames) {
                    additionalProps[key] = value
                }
            }
            JsonObject(element.toMutableMap().apply {
                put("additionalProps", JsonObject(additionalProps))
            })
        }
        else return element
    }
}

interface WithAdditionalProps {
    val additionalProps: Map<String, JsonElement>
}

/*@OptIn(InternalSerializationApi::class)
inline fun <reified T: WithAdditionalProps> T.adjustAdditionalProps() {

    val serializer = T::class.serializer()
    val modelNames = serializer.elementNames()
    val realSerializer = Json.serializersModule.getPolymorphic((serializer as SealedClassSerializer).baseClass, this)
    modelNames.forEach {
        additionalProps.remove(it)
    }
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
fun <T> KSerializer<T>.elementNames(): Set<String> {
    return this.descriptor.elementNames.toSet()
}*/

/*
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
fun <T : Any> processAdditionalProps(serializer: KSerializer<T>, element: JsonElement): JsonElement {
//    val kind = serializer.descriptor.kind
    val finalData: JsonElement = when (element) {
        is JsonObject -> {
            val props = mutableMapOf<String, JsonElement>()
            val definedNames = if (descriptor.kind is PolymorphicKind.SEALED) {
                val discriminator = descriptor.elementNames.first()
                val subTypeSerializer = Json.serializersModule.getPolymorphic(serializer.baseClass, element[discriminator].toString())
                subTypeSerializer?.descriptor?.elementNames
            } ?: serializer.descriptor.elementNames
            for (propName in element.keys) {
                if (propName !in definedNames) {
                    props[propName] = element[propName]!!
                }
            }
            if (props.isNotEmpty()) {
                JsonObject(element.toMutableMap().apply {
                    put("additionalProps", Json.encodeToJsonElement(props.toMap()))
                })
            } else {
                element
            }
        }
        is JsonPrimitive -> {
            element
        }
        is JsonArray -> {
            println("Array")
            (serializer as? AbstractCollectionSerializer<*, *, *>)?.let { listSerializer ->
                listSerializer.descriptor.elementDescriptors.map {
                    processAdditionalProps(it, )
                }
            }
            element
        }
    }
    return finalData
}
*/

/*suspend inline fun <reified T> HttpResponse.read(): T {
    val originalSerializer = serializer<T>()
    *//*Json.decodeFromJsonElement(object: DeserializationStrategy<T> {
        override val descriptor: SerialDescriptor
            get() = TODO("Not yet implemented")

        override fun deserialize(decoder: Decoder): T {
            if (originalSerializer.descriptor.kind is PolymorphicKind) {
                originalSerializer.deserialize(decoder)
            }
        }

    }, body())*//*
    return Json.decodeFromJsonElement<T>(processAdditionalProps(originalSerializer.descriptor, body<JsonElement>()))
}*/


class LargeObjectPropsSerializer: PropsSerializer<LargeObject>(LargeObject.generatedSerializer())

@OptIn(ExperimentalSerializationApi::class)
@Serializable(LargeObjectPropsSerializer::class)
@KeepGeneratedSerializer
data class LargeObject(val name: String, val color: Int, override val additionalProps: MutableMap<String, JsonElement> = mutableMapOf())
    :WithAdditionalProps

class Type1Serializer: PropsSerializer<PolyObject.Type1>(PolyObject.Type1.generatedSerializer())
class Type2Serializer: PropsSerializer<PolyObject.Type2>(PolyObject.Type2.generatedSerializer())


@Serializable
sealed class PolyObject: WithAdditionalProps {
    @OptIn(ExperimentalSerializationApi::class)
    @SerialName("type1")
    @Serializable(Type2Serializer::class)
    @KeepGeneratedSerializer
    data class Type1(val type1Prop: String, override val additionalProps: MutableMap<String, JsonElement> = mutableMapOf()): PolyObject()


    @OptIn(ExperimentalSerializationApi::class)
    @SerialName("type2")
    @Serializable(Type1Serializer::class)
    @KeepGeneratedSerializer
    data class Type2(val type2Prop: String, override val additionalProps: MutableMap<String, JsonElement> = mutableMapOf()): PolyObject()
}