import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

class ResponseWrapperTest {

    @Test
    fun `test serialize wrapper`() {
        val resp = OrdersApiClient.GetUsersListResponse205(listOf("aa"))
        val json = Json.encodeToJsonElement(resp)
        assertIs<JsonArray>(json)
        assertContains(json.jsonArray, JsonPrimitive("aa"))
    }

    @Test
    fun `test deserialize wrapper`() {
        val json = """
            [
                "aa"
            ]
        """.trimIndent()
        val resp = Json.decodeFromString<OrdersApiClient.GetUsersListResponse205>(json)
        assertContains(resp.data, "aa")
    }

}