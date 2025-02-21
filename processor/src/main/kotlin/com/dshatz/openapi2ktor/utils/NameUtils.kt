package com.dshatz.openapi2ktor.utils

import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.CodeBlock
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

private fun String.capitalize(): String = this.replaceFirstChar { it.uppercase() }

private fun String.safeName(): String =
    replace("/", "")


/**
 * Check if this schema is actually a reference.
 */
fun Schema.isComponentSchema(): Boolean {
    val pathElements = Overlay.of(this).jsonReference.split("/").dropWhile { !it.contains("#") }.drop(1)
    return pathElements.first() == "components" && pathElements[1] in listOf("schemas") && pathElements.size == 3
}

fun Schema.getComponentRef(): String? {
    return if (isComponentSchema()) {
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

fun Schema.modelPackageName(packages: Packages): String {
    return makePackageName(Overlay.of(this).jsonReference, packages.models)
}

val Schema.jsonReference: String get() = Overlay.of(this).jsonReference

internal fun makePackageName(jsonReference: String, basePackage: String): String {
    val parts = basePackage.split(".") + jsonReference
        .split("/")
        .dropWhile { !it.contains("#") }
        .drop(1)

    var isResponse = false
    return parts.filterNot { it.isEmpty() }.mapIndexed { index, a ->
        if (a == "responses") {
            isResponse = true
            a
        }
        else if (a == "schema") {
            isResponse = false
            null
        }
        else a.takeUnless { isResponse }
    }.filterNotNull().joinToString(".")
}

fun Any.makeDefaultPrimitive(): JsonPrimitive? {
    return when (this) {
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        else -> null
    }
}

fun Schema.isArrayItemAReference(): Boolean {
    return Overlay.of(this).toJson()?.get("items")?.get("\$ref") != null
}

fun Schema.isPropAReference(prop: String): Boolean {
    return Overlay.of(this).toJson().get("properties")?.get(prop)?.get("\$ref") != null
}

fun String.safePropName(): String {
    val parts = split(".", "_")
    return parts.first() + parts.drop(1).joinToString("") { it.capitalize() }
}

fun JsonPrimitive.makeCodeBlock(): CodeBlock {
    val template = if (isString) "%T(%S)" else "%T(%L)"
    return CodeBlock.of(template, JsonPrimitive::class, contentOrNull)
}