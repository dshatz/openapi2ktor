package {{ client }}

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.statement.*

class BaseClient(engine: HttpClientEngine, config: HttpClientConfig<*>.() -> Unit = {}) {

    internal val httpClient = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json()
        }
        config()
    }

    constructor(engine: HttpClientEngineFactory<*>, config: HttpClientConfig<*>.() -> Unit = {}): this(engine.create(), config)

}

sealed class HttpResult<D, E> {
    abstract val raw: HttpResponse

    data class Ok<D, E>(val data: D, override val raw: HttpResponse): HttpResult<D, E>()
    data class Failure<D, E>(val errorBody: E?, override val raw: HttpResponse, val cause: Throwable): HttpResult<D, E>()

    fun getOrNull(onError: (error: E) -> Unit = {}): D? {
        return when (this) {
            is Ok -> data
            is Failure -> {
                onError(this.errorBody!!)
                null
            }
        }
    }
}

interface Wrapper<T> {
    abstract val d: T
    fun get(): T = d
}

fun <T> HttpRequestBuilder.addOptionalParam(name: String, value: T?, isNullable: Boolean) {
    if (value != null || isNullable) parameter(name, value)
}

fun <T> HttpRequestBuilder.addRequiredParam(name: String, value: T?) {
    parameter(name, value)
}

fun String.replacePathParams(name: String, value: Any?, nullable: Boolean): String {
    return if (value == null && !nullable) this
    else this.replace("{${name}}", value.toString().encodeURLPathPart())
}