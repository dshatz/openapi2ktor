package com.dshatz.openapi2ktor.generators.clients

import com.dshatz.openapi2ktor.utils.Packages
import com.dshatz.openapi2ktor.utils.capitalize
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.asTypeName
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SecuritySchemeSerializer: JsonContentPolymorphicSerializer<SecurityScheme>(SecurityScheme::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SecurityScheme> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.content ?: error("attribute type not found in securitySchema")
        return when (type) {
            "http" -> Http.serializer()
            "apiKey" -> ApiKey.serializer()
            "oauth2" -> OAuth.serializer()
            else -> error("Unknown type of securitySchema: $type")
        }
    }
}

@Serializable(SecuritySchemeSerializer::class)
sealed class SecurityScheme() {
    abstract val description: String
    abstract fun generateSetter(name: String): FunSpec
    fun generateAccessor(name: String): CodeBlock {
        return CodeBlock.of("(authSchemes[%S] as ${generatedContainerName()})", name)
    }
    abstract fun generateApplicator(name: String): CodeBlock
    companion object {
        val headerMethod = MemberName("io.ktor.client.request", "header")
        val cookieMethod = MemberName("io.ktor.client.request", "cookie")
        val parameterMethod = MemberName("io.ktor.client.request", "parameter")
        val bearerMethod =  MemberName("io.ktor.client.request", "bearerAuth")
    }
}

@Serializable
@JsonClassDiscriminator("scheme")
sealed class Http(): SecurityScheme() {
    @Serializable
    @SerialName("bearer")
    data class Bearer(val bearerFormat: String, override val description: String = ""): Http() {
        override fun generateSetter(name: String): FunSpec {
            return FunSpec.builder("set${name.capitalize()}")
                .addParameter(ParameterSpec.builder("bearer", String::class.asTypeName()).build())
                .addCode(CodeBlock.of("authSchemes[%S] = WithBearer(bearer)", name)).build()
        }

        override fun generateApplicator(name: String): CodeBlock {
            return CodeBlock.builder().addStatement("%L?.bearer?.let { %M(it) }", generateAccessor(name), bearerMethod).build()
        }
    }

    @Serializable
    @SerialName("basic")
    class Basic(override val description: String = "") : Http() {
        override fun generateSetter(name: String): FunSpec {
            return FunSpec.builder("set${name.capitalize()}")
                .addParameter(ParameterSpec.builder("username", String::class.asTypeName()).build())
                .addParameter(ParameterSpec.builder("password", String::class.asTypeName()).build())
                .addCode(CodeBlock.of("authSchemes[%S] = WithBasic(username, password)", name)).build()
        }

        override fun generateApplicator(name: String): CodeBlock {
            return CodeBlock.of("%L.let { basicAuth(it.username, it.password) }", generateAccessor(name))
        }
    }
}

@Serializable
data class OAuth(override val description: String = "", val flows: Map<String, OAuthFlow>): SecurityScheme() {
    override fun generateSetter(name: String): FunSpec {
        return FunSpec.builder("set${name.capitalize()}").build()
        // TODO
    }

    override fun generateApplicator(name: String): CodeBlock {
        return CodeBlock.of("")
        // TODO
    }

    @Serializable
    data class OAuthFlow(val authorizationUrl: String? = null,
                         val tokenUrl: String? = null,
                         val refreshUrl: String,
                         val scopes: Map<String, String>)
}

@Serializable
data class ApiKey(
    @SerialName("in") val where: In,
    val name: String,
    override val description: String = "",
    @SerialName("x-bearer-format") val bearerFormat: String? = null
): SecurityScheme() {
    override fun generateSetter(name: String): FunSpec {
        return FunSpec.builder("set${name.capitalize()}")
            .addParameter(ParameterSpec.builder("apiKey", String::class.asTypeName()).build())
            .addCode(CodeBlock.of("authSchemes[%S] = WithApiKey(apiKey)", name)).build()
    }

    override fun generateApplicator(name: String): CodeBlock {
        val accessor = generateAccessor(name)
        val method = when (where) {
            In.Header -> headerMethod
            In.Query -> parameterMethod
            In.Cookie -> cookieMethod
        }
        return CodeBlock.builder().addStatement("%M(%S, %L.apiKey)", method, this.name, accessor).build()
    }

    @Serializable
    enum class In {
        @SerialName("header") Header,
        @SerialName("query") Query,
        @SerialName("cookie") Cookie
    }
}

fun authSchemeType(packages: Packages) = ClassName(packages.client, "AuthScheme")

fun SecurityScheme.generatedContainerName(): String {
    return when (this) {
        is ApiKey -> "AuthScheme.WithApiKey"
        is Http.Basic -> "AuthScheme.WithBasic"
        is Http.Bearer -> "AuthScheme.WithBearer"
        is OAuth -> error("Oauth not supported")
    }
}
fun SecurityScheme.generatedContainer(packages: Packages): ClassName {
    return ClassName(packages.client, generatedContainerName())
}
