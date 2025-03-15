import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AdditionalPropsTest {

    @Test
    fun testAdditionalProps() {
        val o: LargeObject = Json { ignoreUnknownKeys = true }.decodeFromString(testJson)
        assertEquals(JsonPrimitive(999), o.additionalProps["length"])
        assertEquals(1, o.additionalProps.size)
    }

    @Test
    fun polymorphic() {
        val json = """
        [
            {
                "type": "type1",
                "type1Prop": "value1"
            },
            {
              "type": "type2",
              "type2Prop": "value2",
              "unknownProp": "hello"
            }
        ]
        """.trimIndent()
        val j = Json { ignoreUnknownKeys = true }
        val objs: List<PolyObject> = j.decodeFromString(json)
        val obj1 = objs[0]
        val obj2 = objs[1]
        assertIs<List<PolyObject>>(objs)
        assertIs<PolyObject.Type1>(obj1)
        assertEquals("value1", obj1.type1Prop)
        assertEquals(1, obj1.additionalProps.size)

        assertIs<PolyObject.Type2>(obj2)
        assertEquals("value2", obj2.type2Prop)
        assertEquals(2, obj2.additionalProps.size)
        assertEquals(mapOf(
            "unknownProp" to JsonPrimitive("hello") as JsonElement,
            "type" to JsonPrimitive("type2")
        ), obj2.additionalProps)

    }

}