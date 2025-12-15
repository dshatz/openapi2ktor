import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import tvdb.client.Client
import tvdb.client.Servers
import tvdb.models.paths.login.post.requestBody.PostLoginRequest
import tvdb.models.paths.login.post.response.PostLoginResponse401
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.fail

class TestTvDb {


    @Test
    fun `try to make request`() = runTest {
        val client = Client(CIO)
        val response = client.postLogin(PostLoginRequest("weewewfweewf"))

        try {
            response.dataOrThrow()
            fail("Request succeeded without apikey. Something is wrong.")
        } catch (e: PostLoginResponse401) {

        }
    }
}