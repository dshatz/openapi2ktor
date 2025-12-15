package com.dshatz.openapi2ktor.generators.clients

import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.generators.clients.KtorHelpers.contentTypeClass
import com.dshatz.openapi2ktor.generators.clients.KtorHelpers.contentTypeExtension
import com.reprezen.kaizen.oasparser.model3.Server
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec

internal object KtorHelpers {
    val setBodyExtension = MemberName("io.ktor.client.request", "setBody", isExtension = true)
    val contentTypeExtension = MemberName("io.ktor.http", "contentType", isExtension = true)
    val contentTypeClass = ClassName("io.ktor.http", "ContentType")
}

internal fun CodeBlock.Builder.setRequestBody(bodyParam: ParameterSpec?) = apply {
    bodyParam?.let {
        addStatement("%M(%N)", KtorHelpers.setBodyExtension, it.name)
    }
}

internal fun CodeBlock.Builder.setContentType(requestBodyInfo: TypeStore.RequestBody?) = apply {
    requestBodyInfo?.let {
        addStatement("%M(%L)", contentTypeExtension, CodeBlock.of(
            "%T.parse(%S)",
            contentTypeClass,
            it.mediaType
        ))
    }
}


internal fun String.ensureTrailingSlash(): String {
    return if (this.endsWith("/")) this else "$this/"
}

internal fun TypeSpec.Builder.addServerEnumConstants(values: Map<Server, String>) = apply {
    values.forEach { (server, name) ->
        addEnumConstant(name,
            TypeSpec.anonymousClassBuilder()
                .addSuperclassConstructorParameter("%S", server.url.ensureTrailingSlash())
                .build()
        )
    }
}