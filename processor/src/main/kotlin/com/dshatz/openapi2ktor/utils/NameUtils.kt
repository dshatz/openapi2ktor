package com.dshatz.openapi2ktor.utils

import com.dshatz.openapi2ktor.generators.Type
import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName
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

fun String.capitalize(): String = this.replaceFirstChar { it.uppercase() }

private fun String.safeName(): String =
    replace("/", "")


fun Schema.isComponentSchemaRoot(): Boolean {
    val pathElements = Overlay.of(this).jsonReference.split("/").dropWhile { !it.contains("#") }.drop(1)
    return pathElements.first() == "components" && pathElements[1] in listOf("schemas") && pathElements.size == 3
}

fun Schema.isPartOfComponentSchema(): Boolean {
    val pathElements = Overlay.of(this).jsonReference.split("/").dropWhile { !it.contains("#") }.drop(1)
    return pathElements.first() == "components"
}

fun Schema.getComponentRef(): String? {
    return if (isPartOfComponentSchema()) {
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
    try {

        val parts = jsonReference
            .split("/")
            .dropWhile { !it.contains("#") }
            .drop(1)
            .filterNot { it.isBlank() }


        val typeIndex = if (parts[0] == "components") 1 else 0
        val isResponses = parts[typeIndex] == "responses"
        val isSchemas = parts[typeIndex] == "schemas"
        val isPaths = parts[typeIndex] == "paths"

        val cleanParts = if (isPaths) {
            if ("responses" in parts) {
                val responsesIndex = parts.indexOf("responses")
                val statusIndex = responsesIndex + 1
                val path = parts.subList(0, responsesIndex)
                val responseSegment = "response" + parts[statusIndex]
                val remaining = parts.subList(responsesIndex, parts.size).dropWhile { it != "schema" }.drop(1)
                buildList {
                    addAll(path)
                    add(responseSegment)
                    addAll(remaining)
                }
            } else if ("requestBody" in parts) {
                val requestBodyIndex = parts.indexOf("requestBody")
                val schemaIndex = parts.indexOf("schema")

                buildList<String> {
                    addAll(parts.subList(0, requestBodyIndex + 1))
                    addAll(parts.subList(schemaIndex + 1, parts.size))
                }
            } else {
                error("Unknown type of path $jsonReference")
            }
        } else if (isSchemas) {
            parts
        } else if (isResponses) {
            val schemaIndex = parts.indexOf("schema")
            val pathInSchema = parts.subList(schemaIndex + 1, parts.size)
            buildList {
                addAll(parts.subList(0, 3)) // responses/<name>
                addAll(pathInSchema)
            }
        } else {
            emptyList()
        }
        return "$basePackage." + cleanParts.joinToString(".") { it.safePathSegment() }
    } catch (e: Exception) {
        e.printStackTrace()
        error("Failed to generate package name for $jsonReference")
    }
}

private fun String.safePathSegment(): String {
    return if (startsWith("{") && endsWith("}")) {
        "_" + drop(1).dropLast(1).safePropName() + "_"
    } else {
        safePropName()
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

fun Schema.isArrayItemAReference(): Boolean {
    return Overlay.of(this).toJson()?.get("items")?.get("\$ref") != null
}

fun Schema.isPropAReference(prop: String): Boolean {
    return Overlay.of(this).toJson().get("properties")?.get(prop)?.get("\$ref") != null
}

fun String.safePropName(): String {
    val parts = split(".", "_", "-")
        .run {
            if (this@safePropName.contains("/")) {
                split('/').mapIndexed { index, it -> if (index != 0) it.capitalize() else it }
                    .flatMapIndexed { index: Int, s: String -> listOfNotNull("slash".takeUnless { index == 0 }, s) }
            } else this
        }
    return parts.first() + parts.drop(1).joinToString("") { it.capitalize() }
}

fun JsonPrimitive.makeCodeBlock(): CodeBlock {
    val template = if (isString) "%T(%S)" else "%T(%L)"
    return CodeBlock.of(template, JsonPrimitive::class, contentOrNull)
}

fun Type.WithTypeName.simpleName(): String {
    return (typeName as? ClassName)?.simpleName ?: (typeName as? ParameterizedTypeName).toString()
}

fun Type.WithTypeName.packageName(): String {
    return (typeName as? ClassName)?.packageName ?: (typeName as? ParameterizedTypeName).toString()
}

fun String.stripFilePathFromRef(): String {
    return "#" + substringAfter("#")
}