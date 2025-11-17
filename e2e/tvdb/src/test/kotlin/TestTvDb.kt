import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import tvdb.client.Client
import kotlin.test.Test

class TestTvDb {


    @Test
    fun `create`() = runTest {
        val client = Client(CIO)
        client.getUser()
    }
}