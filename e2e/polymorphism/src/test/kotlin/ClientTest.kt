import com.denisbrandi.netmock.Method
import com.denisbrandi.netmock.NetMockRequest
import com.denisbrandi.netmock.NetMockResponseBuilder
import com.denisbrandi.netmock.engine.NetMockEngine
import kotlinx.coroutines.test.runTest
import sample.client.Client
import sample.models.components.parameters.UserType
import sample.models.components.parameters.UserTypeParam
import sample.models.components.schemas.AdminUser.AdminUser
import sample.models.paths.users.get.response.GetUsersResponse200
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ClientTest {
    private val netMock = NetMockEngine()
    private val client = Client(netMock)

    @Test
    fun `list of oneof where one type is nullable`() = runTest {
        val users = Client(netMock, baseUrl = "http://localhost")
        mockGet("/users/alice", """
            null
        """.trimIndent())

        users.getUsersByName("alice").dataOrNull().also {
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

        users.getUsers(10).dataOrNull().also {
            val data = assertIs<GetUsersResponse200>(it).get()
            assertEquals(3, data.size)
            assertIs<AdminUser>(data[1])
            assertNull(data[2])
            println(data)
        }
    }

    @Test
    fun `test with api key`() = runTest {
        client.setApiKeyAuth("my_secret_key")
        interceptRequest("/orders") {
            assertEquals("my_secret_key", mandatoryHeaders["X-MBX-APIKEY"])
        }
        runCatching { client.getOrders(UserType.ADMIN) } // Will fail because it's mocked.
    }

    private fun mockGet(relPath: String, response: String, status: Int = 200) {
        netMock.addMockWithCustomMatcher(
            requestMatcher = { interceptedRequest ->
                interceptedRequest.requestUrl.startsWith("http://localhost$relPath") && interceptedRequest.method == Method.Get
            },
            response = {
                body(response, status)
            }
        )
    }

    private fun interceptRequest(relPath: String, request: NetMockRequest.() -> Unit) {
        netMock.addMockWithCustomMatcher(
            requestMatcher = { interceptedRequest ->
                request(interceptedRequest)
                interceptedRequest.requestUrl.startsWith("http://localhost$relPath") && interceptedRequest.method == Method.Get
            },
            response = {

            }
        )
    }

    private fun NetMockResponseBuilder.body(body: String, status: Int = 200) {
        code = status
        this.body = body
        mandatoryHeaders = mapOf("Content-Type" to "application/json")
    }
}