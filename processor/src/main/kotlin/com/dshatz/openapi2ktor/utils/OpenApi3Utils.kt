package com.dshatz.openapi2ktor.utils

import com.dshatz.openapi2ktor.generators.TypeStore
import com.reprezen.jsonoverlay.IJsonOverlay
import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.reprezen.kaizen.oasparser.model3.Operation
import com.reprezen.kaizen.oasparser.model3.Response

inline fun <T> OpenApi3.mapPaths(block: (pathID: TypeStore.PathId, operation: Operation) -> T): List<T> {
    return paths.flatMap { (pathString, path) ->
        path.operations.map { (verb, operation) ->
            block(TypeStore.PathId(pathString, verb), operation)
        }
    }
}

fun <T> OpenApi3.mapPathResponses(block: (pathID: TypeStore.PathId, responses: Map<Int, Response>) -> T): List<T> {
    return mapPaths { pathId, operation ->
        block(pathId, operation.responses.mapKeys { it.key.toInt() })
    }
}

val IJsonOverlay<*>.jsonReference: String get() = Overlay.of(this).jsonReference

fun Operation.isParameterAReference(name: String): ReferenceMetadata? {
    val paramsIndex = parameters.indexOfFirst { it.name == name }
    return Overlay.of(this.parameters[paramsIndex]).getReference("schema")?.refString.reference()
}

fun Operation.isResponseAReference(statusCode: Int): ReferenceMetadata? {
    return getReferenceForResponse(statusCode) ?: responses[statusCode.toString()].getResponseComponentRefInfo()
}

fun Operation.getReferenceForResponse(statusCode: Int): ReferenceMetadata? {
    return Overlay.of(responses).getReference(statusCode.toString())?.refString.reference()
}

fun Response?.getResponseComponentRefInfo(): ReferenceMetadata? {
    return Overlay.of(this?.contentMediaTypes?.values?.firstOrNull()).getReference("schema")?.refString?.let { ReferenceMetadata(it) }
//    return Overlay.of(this?.contentMediaTypes?.values?.firstOrNull()).isReference("schema")
}

data class ReferenceMetadata(val target: String)
val ReferenceMetadata?.isReference get() = this != null
fun String?.reference(): ReferenceMetadata? = this?.let { ReferenceMetadata(it) }