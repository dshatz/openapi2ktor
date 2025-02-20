package com.dshatz.openapi2ktor.utils

import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.model3.Schema

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