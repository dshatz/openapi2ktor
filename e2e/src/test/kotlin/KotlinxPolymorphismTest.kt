import com.example.models.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KotlinxPolymorphismTest {


    @Test
    fun testOneOf() {
        val json = """
            [
              {
                "id": 0,
                "name": "string",
                "userType": "User"
              },
              {
                "id": 0,
                "name": "string",
                "userType": "AdminUser",
                "beard": true
              }
            ]
        """.trimIndent()
        val response = Json.decodeFromString<List<IGetUsersResponse200>>(json)
        assertEquals(2, response.size)
        assertIs<User>(response.first())
        assertIs<AdminUser>(response.last())
    }

    @Test
    fun testOneOfCustomMapping() {
        val json = """
            [
              {
                "id": 0,
                "name": "string",
                "userType": "normal"
              },
              {
                "id": 0,
                "name": "string",
                "userType": "admin",
                "beard": true
              }
            ]
        """.trimIndent()
        val response = Json.decodeFromString<List<IGetUsersResponse201>>(json)
        assertEquals(2, response.size)
        assertIs<User>(response.first())
        assertIs<AdminUser>(response.last())
    }

    @Test
    fun testAllOf() {
        val json = """
            {
                "id": 0,
                "name": "string",
                "userType": "AdminUser",
                "beard": true
              }
        """.trimIndent()

        val response = Json.decodeFromString<PostUsersResponse>(json)
        assertEquals("string", response.name)
    }

    class IGetUsersResponse201Serializer: JsonContentPolymorphicSerializer<IGetUsersResponse201>(IGetUsersResponse201::class) {
        val discriminator = "type"
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<IGetUsersResponse201> {
            return when (element.jsonObject[discriminator]?.jsonPrimitive?.content) {
                "normal" -> User.serializer()
                else -> AdminUser.serializer()
            }
        }

    }
}