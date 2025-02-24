import com.example.client.BaseClient
import com.example.client.Servers
import com.example.client.api.ApiClient
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinanceTest {

    val apiClient = BaseClient(CIO) {
        defaultRequest {
            url(Servers.API_BINANCE_COM.url)
        }
    }
    private val binance = ApiClient(client = apiClient)
    @Test
    fun `get without params`() = runTest {
        val r = binance.getApiV3AvgPrice()
        var errorReturned = false
        val a = r.getOrNull {
            errorReturned = true
            println(it.msg)
        }
        assertNull(a)
        assertTrue(errorReturned)
    }
}