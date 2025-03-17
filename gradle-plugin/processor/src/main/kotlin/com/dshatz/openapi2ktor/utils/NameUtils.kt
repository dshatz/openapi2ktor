package com.dshatz.openapi2ktor.utils

import com.dshatz.openapi2ktor.generators.Type
import com.dshatz.openapi2ktor.generators.TypeStore
import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull


fun makeResponseModelName(verb: String, path: String, statusCode: Int, includeStatus: Boolean): String {
    return makeCamelCase(verb, path.makeResponseModelName(), "Response", statusCode.takeIf { includeStatus }?.toString())
}

fun makeResponseModelName(pathId: TypeStore.PathId, statusCode: Int, includeStatus: Boolean): String {
    return makeResponseModelName(pathId.verb, pathId.pathString, statusCode, includeStatus)
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

@Deprecated("Please use ReferenceMetadata")
fun Schema.getComponentRef(): String? {
    return if (isPartOfComponentSchema()) {
        "#/" + Overlay.of(this)
            .jsonReference
            .split("/")
            .dropWhile { !it.contains("#") }
            .drop(1) // drop empty part
            .take(3) // take /components/<type>/<name>
            .joinToString("/")
    } else {
        null
    }
}

fun Schema.modelPackageName(packages: Packages): String {
    return makePackageName(Overlay.of(this).jsonReference, packages.models)
}

val Schema.jsonReference: String get() = Overlay.of(this).jsonReference

fun makePackageName(jsonReference: String, basePackage: String): String {
    try {
        val parts = jsonReference
            .split("/")
            .dropWhile { !it.contains("#") }
            .filterNot { it.isBlank() }
            .drop(1)
            .replaceIntegers()
            .replaceNamesStartingWithInt()
            .replaceCurlyWithBy()


        val typeIndex = if (parts[0] == "components") 1 else 0
        val isResponseComponent = parts[typeIndex] == "responses"
        val isSchemaComponent = parts[typeIndex] == "schemas"
        val isPathsComponent = parts[typeIndex] == "paths"
        val isParametersComponent = parts[typeIndex] == "parameters"

        val cleanParts = if (isPathsComponent) {
            if ("responses" in parts) {
                val responsesIndex = parts.indexOf("responses")
                val statusIndex = responsesIndex + 1
                val path = parts.subList(0, responsesIndex)
                val responseSegment = "response" /*+ parts[statusIndex]*/
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
                // Some other /paths reference
                parts
            }
        } else if (isSchemaComponent) {
            parts
        } else if (isResponseComponent) {
            val schemaIndex = parts.indexOf("schema")
            val pathInSchema = if (schemaIndex != -1) parts.subList(schemaIndex + 1, parts.size) else emptyList()
            buildList {
                addAll(parts.subList(0, 3)) // responses/<name>
                addAll(pathInSchema)
            }
        } else if (isParametersComponent) {
            parts.subList(0, parts.size - 1)
        } else {
            emptyList()
        }
        return "$basePackage." + cleanParts.joinToString(".") { it.safePathSegment() }
    } catch (e: Exception) {
        e.printStackTrace()
        error("Failed to generate package name for $jsonReference")
    }
}

fun Int.isSuccessCode(): Boolean = this in 200..299

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

fun Schema.arrayItemRefData(): ReferenceMetadata? {
    return Overlay.of(this).getReference("itemsSchema")?.refString.reference()
//    return Overlay.of(this).toJson()?.get("items")?.get("\$ref")?.toString().reference()
}

fun Schema.propRefData(prop: String): ReferenceMetadata? {
    return Overlay.of(this.properties)?.getReference(prop)?.refString.reference()
//    return Overlay.of(this).toJson().get("properties")?.get(prop)?.get("\$ref")?.toString().reference()
}

fun Schema.oneOfRefData(index: Int): ReferenceMetadata? {
    return Overlay.of(oneOfSchemas).getReference(index)?.refString.reference()
}

fun TypeStore.PathId.makeRequestFunName(dropPrefix: String): String {
    val endpointName = pathString.split("/").toMutableList()
        .replaceCurlyWithBy()
        .joinToString("") {it.safePropName().capitalize()}
        .replaceFirst(dropPrefix.capitalize(), "", ignoreCase = true)
    return "$verb$endpointName"
}

fun List<String>.replaceCurlyWithBy(): List<String> {
    return map {
        if (it.startsWith("{") && it.endsWith("}"))
            "by" + it.drop(1).dropLast(1).safePropName().capitalize()
        else it
    }
}

private fun List<String>.replaceIntegers(): List<String> {
    return map {
        if (it.toIntOrNull() != null) "i$it" else it
    }
}

private fun List<String>.replaceNamesStartingWithInt(): List<String> {
    return map {
        if (it.first().isDigit()) it.replace(Regex("^([0-9]+)(.*)$")) {
            it.groups[2]?.value + it.groups[1]?.value
        } else it
    }
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

fun String.makeResponseModelName(): String {
    return split("/").replaceCurlyWithBy().joinToString("") { it.safePropName().capitalize() }
}

fun String.safeEnumEntryName(): String {
    return (safePropName().takeUnless { it.isBlank() } ?: "Empty").uppercase()
}

fun JsonPrimitive.makeCodeBlock(): CodeBlock {
    val template = if (isString) "%T(%S)" else  "%T(%L)"
    return CodeBlock.of(template, JsonPrimitive::class, contentOrNull)
}

fun Type.SimpleType.kotlinTypeName(): String = (this.kotlinType as ClassName).simpleName

fun Type.WithTypeName.packageName(): String {
    return (typeName as? ClassName)?.packageName ?: (typeName as? ParameterizedTypeName).toString()
}

fun String.cleanJsonReference(): String {
    return ("#" + substringAfter("#")).replace("//", "/")
}

fun TypeName.updateSimpleName(update: (String) -> String): ClassName {
    val cls = this as ClassName
    return ClassName(cls.packageName, update(cls.simpleName))
}
