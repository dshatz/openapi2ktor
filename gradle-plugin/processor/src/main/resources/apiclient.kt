package {{ client }}

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.statement.*

open class BaseClient(engine: HttpClientEngine, protected val json: Json, config: HttpClientConfig<*>.() -> Unit = {}) {
    internal val httpClient = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(json)
        }
        config()
    }

    constructor(engine: HttpClientEngineFactory<*>, json: Json, config: HttpClientConfig<*>.() -> Unit = {}): this(engine.create(), json, config)

}

sealed class HttpResult<D, E: Exception> {
    abstract val raw: HttpResponse

    data class Ok<D, E: Exception>(val data: D, override val raw: HttpResponse): HttpResult<D, E>()
    data class Failure<D, E: Exception>(val errorBody: E, override val raw: HttpResponse, val cause: Throwable): HttpResult<D, E>()

    fun dataOrNull(onError: (error: Failure<D, E>) -> Unit = {}): D? {
        return when (this) {
            is Ok -> data
            is Failure -> {
                onError(this)
                null
            }
        }
    }
    public fun dataOrThrow(): D =
        when (this) {
            is HttpResult.Ok -> data
            is Failure -> throw this.errorBody
        }
}

data class UnknownSuccessCodeError(val body: String, val statusCode: Int, val response: HttpResponse): Exception("Unknown success status code $statusCode")
data class OpenAPIResponseError(val body: String, val statusCode: Int, val response: HttpResponse): Exception("Status $statusCode from ${response.request.url}")


sealed class AuthScheme {
    data class WithBearer(var bearer: String? = null): AuthScheme()
    data class WithBasic(var basicUsername: String? = null, var basicPassword: String? = null): AuthScheme()
    data class WithApiKey(var apiKey: String? = null): AuthScheme()
}

interface Wrapper<T> {
    abstract val data: T
    fun get(): T = data
}

fun <T> HttpRequestBuilder.addOptionalParam(name: String, value: T?, isNullable: Boolean) {
    if (value != null || isNullable) parameter(name, value)
}

fun <T> HttpRequestBuilder.addOptionalHeaderParam(name: String, value: T?, isNullable: Boolean) {
    if (value != null || isNullable) header(name, value)
}

fun <T> HttpRequestBuilder.addRequiredParam(name: String, value: T?) {
    parameter(name, value)
}

fun <T> HttpRequestBuilder.addRequiredHeaderParam(name: String, value: T?) {
    header(name, value)
}

fun String.replacePathParams(name: String, value: Any?, nullable: Boolean): String {
    return if (value == null && !nullable) this
    else this.replace("{${name}}", value.toString().encodeURLPathPart())
}

open class PropsSerializer<T: WithAdditionalProps>(private val baseSerializer: KSerializer<T>): JsonTransformingSerializer<T>(baseSerializer) {
    override val descriptor: SerialDescriptor = baseSerializer.descriptor

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val additionalProps = mutableMapOf<String, JsonElement>()
        val modelNames = baseSerializer.descriptor.elementNames
        return if (element is JsonObject) {
            element.forEach { (key, value) ->
                if (key !in modelNames) {
                    additionalProps[key] = value
                }
            }
            JsonObject(element.toMutableMap().apply {
                put("additionalProps", JsonObject(additionalProps))
            })
        }
        else return element
    }
}

interface WithAdditionalProps {
    val additionalProps: Map<String, JsonElement>
}
