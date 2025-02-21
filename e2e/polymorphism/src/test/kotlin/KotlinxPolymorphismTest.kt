import com.example.models.components.schemas.AdminUser.AdminUser
import com.example.models.components.schemas.User.User
import com.example.models.paths.orders.get.responses.GetOrdersResponse
import com.example.models.paths.orders.get.responses.GetOrdersResponse400
import com.example.models.paths.users.get.responses.GetUsersResponse200
import com.example.models.paths.users.get.responses.GetUsersResponse201
import com.example.models.paths.users.post.responses.PostUsersResponse
import kotlinx.serialization.json.*
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
    fun testOneOfCustomMapping() {
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
    fun testAllOf() {
        val json = """
            {
                "id": 0,
                "name": "string",
                "user_type": "AdminUser",
                "beard": true
              }
        """.trimIndent()

        val response = Json.decodeFromString<PostUsersResponse>(json)
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
    fun `empty object`() {
        val json = """
            {
                "a": 11,
                "b": "test"
            }
        """.trimIndent()
        val response = Json.decodeFromString<GetOrdersResponse400>(json)
        assertEquals(11, response["a"]?.jsonPrimitive?.int)

    }
}