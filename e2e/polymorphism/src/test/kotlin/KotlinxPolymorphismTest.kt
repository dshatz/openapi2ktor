import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.*
import sample.models.components.schemas.AdminUser.AdminUser
import sample.models.components.schemas.User.User
import sample.models.paths.orders.get.response.GetOrdersResponse400
import sample.models.paths.orders.get.response.GetOrdersResponse403
import sample.models.paths.orders.get.response.items.GetOrdersResponseItem
import sample.models.paths.users.get.response.GetUsersResponse200
import sample.models.paths.users.get.response.GetUsersResponse201
import sample.models.paths.users.post.response.PostUsersResponse200
import sample.models.paths.users.post.response.PostUsersResponse400
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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
        assertEquals(2, response.get().size)
        assertIs<User>(response.get().first())
        assertIs<AdminUser>(response.get().last())
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
        assertEquals(2, response.get().size)
        assertIs<User>(response.get().first())
        assertIs<AdminUser>(response.get().last())
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
        val response = Json.decodeFromString<List<GetOrdersResponseItem>>(json)
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
        assertEquals(11, response.data["a"]?.jsonPrimitive?.int)
    }


    @Test
    fun `missing contentMediaType and schema`() {
        val json = """
            {
                "a": 2
            }
        """.trimIndent()
        val d = Json.decodeFromString<GetOrdersResponse403>(json)
        assertIs<JsonObject>(d.data)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test(MissingFieldException::class)
    fun `test missing required prop`() {
        val json = """
            {
                "id": 0,
                "name": "Bob",
                "user_type": "User"
            }
        """.trimIndent()
        Json.decodeFromString<PostUsersResponse400>(json)
    }

    @Test
    fun `test optional fields default to type default`() {
        val json = """
            {
              "error": "very bad error"
            }
        """.trimIndent()
        val result = Json.decodeFromString<PostUsersResponse400>(json)
        assertNull(result.name)
        assertEquals(0, result.id)
        assertNull(result.userType)
        assertEquals("very bad error", result.error)
    }
}
