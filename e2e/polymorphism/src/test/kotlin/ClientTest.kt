import com.denisbrandi.netmock.Method
import com.denisbrandi.netmock.NetMockResponseBuilder
import com.denisbrandi.netmock.engine.NetMockEngine
import com.example.client.BaseClient
import com.example.client.Servers
import com.example.client.users.UsersClient
import com.example.models.components.schemas.AdminUser.AdminUser
import com.example.models.paths.users.get.response.GetUsersResponse200
import com.example.models.paths.users.get.response.GetUsersResponse201
import com.example.models.paths.users.get.response.GetUsersResponse205
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class ClientTest {
    private val netMock = NetMockEngine()

    @Test
    fun `list of oneof where one type is nullable`() = runTest {
        val users = UsersClient(BaseClient(netMock))
        mockGet("/users/alice", """
            null
        """.trimIndent())

        users.getByName("alice").getOrNull().also {
            assertNull(it)
        }

        mockGet("/users?limit=10", """
            [
                {
                    "id": 0,
                    "name": "Alice",
                    "user_type": "User"
                },
                {
                    "id": 1,
                    "name": "Bob",
                    "user_type": "AdminUser"
                },
                null
            ]
        """.trimIndent())

        users.get(10).getOrNull().also {
            val data = assertIs<GetUsersResponse200>(it).get()
            assertEquals(3, data.size)
            assertIs<AdminUser>(data[1])
            assertNull(data[2])
            println(data)
        }
    }

    private fun mockGet(relPath: String, response: String, status: Int = 200) {
        netMock.addMock(
            request = {
                method = Method.Get
                requestUrl = "http://localhost$relPath"
            },
            response = {
                body(response, status)
            }
        )
    }

    private fun NetMockResponseBuilder.body(body: String, status: Int = 200) {
        code = status
        this.body = body
        mandatoryHeaders = mapOf("Content-Type" to "application/json")
    }
}