import com.example.client.BaseClient
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.cio.*
import io.ktor.util.reflect.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import javax.swing.Box

class OrdersApiClient(
    private val apiClient: BaseClient
) {

    /**
     * @param limit - required (query)
     * @param optionalParam - optional but not originally nullable (query)
     *
     */
    suspend fun getOrders(limit: Int, optionalParam: Int? = null): HttpResult<IResponse, IErrorResponse> {
        val pathParams = mapOf(
            "a" to 1
        )
        try {
            val response = apiClient.httpClient.get("/orders") {
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
            val responseData: IErrorResponse? = when (e.response.status.value) {
                401 -> e.response.body<Response401>()
                else -> null
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
    data class Response401(val error: String): IErrorResponse

    @Serializable
    sealed interface IResponse

    @Serializable
    sealed interface IErrorResponse


    sealed class HttpResult<D, E> {
        abstract val raw: HttpResponse

        data class Ok<D, E>(val data: D, override val raw: HttpResponse): HttpResult<D, E>()
        data class Failure<D, E>(val errorBody: E?, override val raw: HttpResponse, val cause: Throwable): HttpResult<D, E>()
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
