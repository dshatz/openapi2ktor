package com.dshatz.openapi2ktor.utils

import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.CodeBlock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun makeResponseModelName(verb: String, path: String, response: String, includeStatus: Boolean): String {
    return makeCamelCase(verb, path.safeName(), "Response", response.takeIf { includeStatus })
}

fun makeRequestBodyModelName(verb: String, path: String): String {
    return makeCamelCase(verb, path.safeName(), "Request")
}


private fun makeCamelCase(vararg parts: String?): String {
    return parts
        .filterNotNull()
        .joinToString(separator = "") {
            it.replaceFirstChar { char -> char.uppercase() }
        }
}

private fun String.safeName(): String =
    replace("/", "")

fun Schema.isReference(): Boolean {
    return Overlay.of(this).jsonReference.split("/").any { it.endsWith("#") }
}

fun Schema.getReferenceId(): String? {
    return if (isReference()) {
        "#/" + Overlay.of(this)
            .jsonReference
            .split("/")
            .dropWhile { !it.contains("#") }
            .drop(1)
            .joinToString("/")
    } else {
        null
    }
}

fun Any.makeDefaultPrimitive(): JsonPrimitive? {
    return when (this) {
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        else -> null
    }
}

fun JsonPrimitive.makeCodeBlock(): CodeBlock {
    val template = if (isString) "%T(%S)" else "%T(%L)"
    return CodeBlock.of(template, JsonPrimitive::class, contentOrNull)
}