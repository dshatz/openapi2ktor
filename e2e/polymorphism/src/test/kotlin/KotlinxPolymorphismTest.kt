import com.example.models.components.schemas.AdminUser.AdminUser
import com.example.models.components.schemas.User.User
import com.example.models.paths.orders.get.responses.GetOrdersResponse
import com.example.models.paths.orders.get.responses.GetOrdersResponse400
import com.example.models.paths.orders.get.responses.GetOrdersResponse403
import com.example.models.paths.users.get.responses.GetUsersResponse200
import com.example.models.paths.users.get.responses.GetUsersResponse201
import com.example.models.paths.users.post.responses.PostUsersResponse200
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KotlinxPolymorphismTest {


    @Test
    fun oneOf() {
        val json = """
            [
              {
                "id": 0,
                "name": "string",
                "user_type": "User"
              },
              {
                "id": 0,
                "name": "string",
                "user_type": "AdminUser",
                "beard": true
              }
            ]
        """.trimIndent()
        val response = Json.decodeFromString<GetUsersResponse200>(json)
        assertEquals(2, response.size)
        assertIs<User>(response.first())
        assertIs<AdminUser>(response.last())
    }

    @Test
    fun `oneOf with custom type mapping`() {
        val json = """
            [
              {
                "id": 0,
                "name": "string",
                "user_type": "normal"
              },
              {
                "id": 0,
                "name": "string",
                "user_type": "admin",
                "beard": true
              }
            ]
        """.trimIndent()
        val response = Json.decodeFromString<GetUsersResponse201>(json)
        assertEquals(2, response.size)
        assertIs<User>(response.first())
        assertIs<AdminUser>(response.last())
    }

    @Test
    fun allOf() {
        val json = """
            {
                "id": 0,
                "name": "string",
                "user_type": "AdminUser",
                "beard": true
              }
        """.trimIndent()

        val response = Json.decodeFromString<PostUsersResponse200>(json)
        assertEquals("string", response.name)
    }

    @Test
    fun `model in a list`() {
        val json = """
            [{
                "id": 0,
                "amount": "2"
              }]
        """.trimIndent()
        val response = Json.decodeFromString<GetOrdersResponse>(json)
        assertEquals(0, response.first().id)
    }

    @Test
    fun `schema with no properties`() {
        val json = """
            {
                "a": 11,
                "b": "test"
            }
        """.trimIndent()
        val response = Json.decodeFromString<GetOrdersResponse400>(json)
        assertEquals(11, response["a"]?.jsonPrimitive?.int)
    }

    @Test
    fun `missing contentMediaType and schema`() {
        val json = """
            {
                "a": 2
            }
        """.trimIndent()
        assertIs<JsonObject>(Json.decodeFromString<GetOrdersResponse403>(json))
    }
}