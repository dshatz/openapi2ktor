import com.example.client.BaseClient
import com.example.client.Servers
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import io.ktor.client.engine.*
import io.ktor.client.*
import kotlinx.serialization.serializer

class OrdersApiClient(
    engine: HttpClientEngine, baseUrl: String = Servers.API_EXAMPLE_COM.url, config: HttpClientConfig<*>.() -> Unit = {}
): BaseClient(engine, config = {
    defaultRequest {
        url(baseUrl)
    }
    config()
}) {

    /**
     * @param limit - required (query)
     * @param optionalParam - optional but not originally nullable (query)
     *
     */
    suspend fun getOrders(limit: Int, optionalParam: Int? = null): HttpResult<IResponse, Response401> {
        val pathParams = mapOf(
            "a" to 1
        )
        try {
            val response = httpClient.get("/orders") {
                url {
                    encodedPathSegments = encodedPathSegments.map {
                        pathParams[it]?.toString() ?: it
                    }
                }
//                this.addRequiredParam("limit", limit)
//                this.addOptionalParam("optional_param", optionalParam, false)
            }
            val result: IResponse = when (response.status.value) {
                200 -> response.body<Response200>()
                201 -> response.body<Response201>()
                else -> error("Unknown success status code! ${response.status.value}")
            }
            return HttpResult.Ok(result, response)
        } catch (e: ClientRequestException) {
            // 4xx
            val responseData: Response401 = when (e.response.status.value) {
                401 -> e.response.body<Response401>()
                else -> throw e
            }
            return HttpResult.Failure(responseData, e.response, e)
        }
    }

    private suspend fun test() {
        val response = getOrders(10)
        when (response) {
            is HttpResult.Ok -> {
                when (response.data) {
                    is Response200 -> response.data.ok
                    is Response201 -> response.data.error
                    else -> {}
                }
            }
            is HttpResult.Failure -> {
                when (response.errorBody) {
                    is Response401 -> { response.errorBody.error }
                    null -> error("Unknown error response body")
                }
            }
        }
    }

    @Serializable
    data class Response200(val ok: Boolean): IResponse

    @Serializable
    data class Response201(val error: String): IResponse

    @Serializable
    data class Response401(val error: String): IErrorResponse, Exception()

    @Serializable
    sealed interface IResponse

    @Serializable
    sealed interface IErrorResponse


    sealed class HttpResult<D, E: Exception> {
        abstract val raw: HttpResponse

        data class Ok<D, E: Exception>(val data: D, override val raw: HttpResponse): HttpResult<D, E>()
        data class Failure<D, E: Exception>(val errorBody: E, override val raw: HttpResponse, val cause: Throwable): HttpResult<D, E>()

        public fun dataOrThrow(): D =
            when (this) {
                is HttpResult.Ok -> data
                is Failure -> throw this.errorBody
            }
    }

    interface Wrapper<T> {
        val data: T
    }

    @Serializable(GetUsersListResponse205Serializer::class)
    data class GetUsersListResponse205(override val data: List<String>): Wrapper<List<String>>, IResponse

    class GetUsersListResponse205Serializer: KSerializer<GetUsersListResponse205> {
        override val descriptor: SerialDescriptor = serializer<List<String>>().descriptor
        override fun deserialize(decoder: Decoder): GetUsersListResponse205 {
            return GetUsersListResponse205(decoder.decodeSerializableValue(serializer()))
        }
        override fun serialize(encoder: Encoder, value: GetUsersListResponse205) {
            encoder.encodeSerializableValue(serializer(), value.data)
        }
    }

}
