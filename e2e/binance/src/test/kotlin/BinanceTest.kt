import com.example.client.BaseClient
import com.example.client.HttpResult
import com.example.client.Servers
import com.example.client.api.ApiClient
import com.example.models.paths.api.v3.avgPrice.get.response.GetApiV3AvgPriceResponse400
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import kotlin.coroutines.coroutineContext
import kotlin.test.*

class BinanceTest {

    private lateinit var requests: MutableStateFlow<HttpRequestData?>

    @BeforeTest
    fun init() {
        requests = MutableStateFlow<HttpRequestData?>(null)
    }


    private val binance = ApiClient(CIO) {
        install(Logging) {
            level = LogLevel.ALL
            logger = Logger.SIMPLE
        }
    }
    @Test
    fun `get with invalid param`() = runTest {
        val r = binance.getV3AvgPrice("")
        var errorReturned = false
        val a = r.dataOrNull {
            errorReturned = true
            println(it.errorBody.data.msg)
        }
        assertNull(a)
        assertTrue(errorReturned)

        println(assertFailsWith(GetApiV3AvgPriceResponse400::class) {
            binance.getV3AvgPrice("").dataOrThrow()
        }.data.msg)
    }

    @Test
    fun `get with valid param`() = runTest {
        val r = binance.getV3AvgPrice("BTCEUR")
        var errorReturned = false
        val price = r.dataOrNull {
            errorReturned = true
            println(it.errorBody.data.msg)
        }!!.price
        assertNotNull(price)
        assertFalse(errorReturned)
        println("Price: $price")
        assertNotNull(price.toDoubleOrNull())
    }

    @Test
    fun `get with optional params`() = runTest {
        val mock = Mocker(binance)
        val response = mock.makeRequest(req = { getV3Trades("BTCEUR") }) {
            assertHasQueryParam("symbol", "BTCEUR")
            assertNoQueryParam("limit") // optional param, not passed
        }
        assertIs<HttpResult.Ok<*,*>>(response)
    }

    @Test
    fun `handle unknown error response`() = runTest {  }

    data class Mocker(val normalClient: ApiClient, val mockEngine: MockEngine = MockEngine { respondBadRequest() }) {
        val mockClient = ApiClient(mockEngine)
    }

    private suspend inline fun <reified T> Mocker.makeRequest(
        crossinline req: suspend ApiClient.() -> T,
        overrideResponse: T? = null,
        overrideRequest: (HttpRequestData) -> HttpRequestData = { it },
        crossinline assertScope: RequestAssertScope.() -> Unit
    ): T = supervisorScope {

        launch {
            runCatching { req(mockClient) }
        }
        val config = this@makeRequest.mockEngine.config
        config.requestHandlers.clear()
        config.addHandler { request: HttpRequestData ->
            requests.emit(request)
            respondBadRequest()
        }

        assertScope.invoke(RequestAssertScope(requests.filterNotNull().first()))
        requests.emit(null)
        return@supervisorScope overrideResponse ?: req(normalClient)
    }

    data class RequestAssertScope(val req: HttpRequestData) {
        fun assertHasQueryParam(name: String, value: String) {
            assertContains(req.url.parameters.names(), name)
            assertEquals(value, req.url.parameters[name])
        }

        fun assertNoQueryParam(name: String) = assertFalse(req.url.parameters.names().contains(name))
    }
}