package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.utils.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import java.util.concurrent.ConcurrentHashMap

class TypeStore {

    // Schema string representation to model class name.
    private val types: ConcurrentHashMap<String, Type> = ConcurrentHashMap()
    fun getTypes(): Map<String, Type> = types.toMap()

    private val responseMapping: MutableMap<PathId, MutableMap<Int, ResponseTypeInfo>> = mutableMapOf()
    private val responseInterfaces: MutableMap<PathId, Pair<ClassName?, ClassName?>> = mutableMapOf()
    private val requestBodies: MutableMap<PathId, RequestBody> = mutableMapOf()
    private val operationParameters: MutableMap<PathId, List<OperationParam>> = mutableMapOf()
    private val exceptionTypes: MutableSet<Type> = mutableSetOf()

    data class RequestBody(
        val type: Type,
        val mediaType: String
    )

    private val typesUsedInResponses by lazy { responseMapping.asSequence()
        .flatMap { it.value.values.map { type -> type to it.key } }
        .groupBy { findConcreteType(it.first.type) }
        .mapValues { it.value.map { it.second } } }



    fun registerType(jsonReference: String, type: Type) {
        types[jsonReference.cleanJsonReference()] = type
    }

    fun registerComponentSchema(referenceId: String, type: Type) {
        if (type is Type.Reference) error("Should not be a reference for component schema $referenceId")
        types[referenceId.cleanJsonReference()] = type
    }

    fun registerResponseMapping(path: PathId, status: Int, jsonReference: String, type: Type) = synchronized(responseMapping) {
        val map = responseMapping.getOrDefault(path, mutableMapOf()).also {
            it[status] = ResponseTypeInfo(type, jsonReference.cleanJsonReference())
        }
        responseMapping[path] = map
    }

    fun isUsedInResponse(type: Type): List<PathId>? {
        return typesUsedInResponses[findConcreteType(type)]
    }

    fun extendException(type: Type) {
        exceptionTypes.add(type)
    }

    fun shouldExtendException(type: Type): Boolean {
        return type in exceptionTypes
    }

    data class PathId(val pathString: String, val verb: String) {
        override fun toString(): String {
            return "${verb.uppercase()} $pathString"
        }
    }

    fun resolveReference(jsonReference: String): Type {
        return getTypes()[jsonReference] ?: run {
            printTypes()
            error("Could not resolve reference $jsonReference")
        }
    }

    fun <T: Type> findConcreteType(type: T): Type.WithTypeName? {
        return when (type) {
            is Type.WithTypeName -> type
            is Type.Reference -> findConcreteType(resolveReference(type.jsonReference))
            is Type.List -> null
            is Type.SimpleType -> null
        }
    }

    fun registerOperationParams(pathId: PathId, params: List<OperationParam>) {
        operationParameters[pathId] = params
    }

    fun getParamsForOperation(pathId: PathId): List<OperationParam> {
        return operationParameters[pathId] ?: emptyList()
    }

    data class ResponseTypeInfo(val type: Type, val jsonReference: String)

    data class OperationParam(
        val name: String,
        val type: Type,
        val isRequired: Boolean,
        val where: ParamLocation
    ) {
        enum class ParamLocation {
            QUERY,
            PATH,
            HEADER
        }
    }

    fun <T: Type> T.makeTypeName(): TypeName = with (ReservedKeywords) {
        escapeKeywords().run {
            return when (this) {
                is Type.WithTypeName -> typeName as ClassName
                is Type.Reference -> resolveReference(jsonReference).makeTypeName()
                is Type.List -> List::class.asTypeName().parameterizedBy(itemsType.makeTypeName())
                is Type.SimpleType -> this.kotlinType
            }
        }
    }

    fun registerResponseInterface(path: PathId, successInterface: ClassName?, errorInterface: ClassName?) {
        responseInterfaces[path] = successInterface to errorInterface
    }


    fun registerRequestBody(path: PathId, requestBody: Type, mediaType: String) {
        requestBodies[path] = RequestBody(requestBody, mediaType)
    }

    fun getRequestBody(pathId: PathId): RequestBody? {
        return requestBodies[pathId]
    }

    fun getResponseErrorInterface(path: PathId) = responseInterfaces[path]?.second
    fun getResponseSuccessInterface(path: PathId) = responseInterfaces[path]?.first

    fun getResponseMapping(response: PathId): Map<Int, ResponseTypeInfo> = synchronized(responseMapping) {
        return responseMapping[response] ?: error("No response mapping for $response\n ${responseMappingToString()}")
    }

    private fun responseMappingToString(): String {
        return responseMapping.entries.joinToString("\n") {
            "${it.key} -> ${it.value.entries.joinToString("\n") { 
                "${it.key} -> ${it.value}"
            }.prependIndent("  ")}"
        }
    }

    fun getAllResponseTypes() = responseMapping.keys

    private fun printType(type: Type, parentObject: Type.WithTypeName.Object? = null, propName: String? = null, depth: Int = 0): String? {

        fun String.addSpaces(depth: Int): String {
            return this.prependIndent("  ".repeat(depth))
        }

        val sb = StringBuilder()
        when (type) {
            is Type.WithTypeName.Object -> {
                sb.append("${type.simpleName()} (${type.packageName()})")
                sb.appendLine("Properties: ".addSpaces(1))
                type.props.entries.forEach {
                    sb.appendLine("-${it.key}".addSpaces(2))
                    sb.appendLine(printType(it.value.type, parentObject = type, propName = it.key, depth = 3))
                }
            }
            is Type.SimpleType -> {
                sb.append(("type: " + type.kotlinTypeName() + ("?".takeIf { type.kotlinType.isNullable } ?: "")).addSpaces(1))
                if (parentObject != null && propName != null) {
                    sb.appendLine()
                    sb.appendLine("required: ${propName in parentObject.requiredProps}".addSpaces(1))
                    sb.appendLine("default: ${parentObject.defaultValues[propName]}".addSpaces(1))
                }
            }
            is Type.Reference -> {
                sb.append("Ref => ${type.jsonReference} (exists: ${types.contains(type.jsonReference)})".addSpaces(1))
            }
            is Type.WithTypeName.OneOf -> {
                sb.appendLine("${type.simpleName()} (${type.packageName()})")
                sb.appendLine("OneOf".addSpaces(1))
                type.childrenMapping.keys.forEach {
                    sb.appendLine(("-" + printType(it)).addSpaces(2))
                }
            }
            is Type.WithTypeName.Enum<*> -> {
                sb.appendLine("Enum ${type.simpleName()} (${type.packageName()})".addSpaces(1))
                type.elements.forEach {
                    sb.appendLine("-$it".addSpaces(2))
                }
            }
            is Type.WithTypeName.PrimitiveWrapper -> {
                sb.appendLine("${type.simpleName()} (${type.packageName()})")
                sb.appendLine("Wrapper of ${type.wrappedType}")
            }
            else -> {}
        }
        return sb.toString().addSpaces(depth)
    }

    fun disambiguateTypeNames() {
        val duplicates = types.entries
            .asSequence()
            .filterValuesIsInstance<String, Type, Type.WithTypeName>()
            .findDuplicates { it.value.typeName }
        for ((type, duplicatesForType) in duplicates) {
            duplicatesForType.mapIndexed { idx, (jsonRef, currentType) ->
                jsonRef to currentType.withTypeName(currentType.typeName.updateSimpleName { it + idx })
            }.forEach {
                registerType(it.first, it.second)
            }
        }
    }

    fun printTypes() {
        val printAll = false
        val printComponents = false
        val printStructured = true
        if (printAll) {
            println()
            println("Printing TypeStore contents...")
            println(types.entries.joinToString("\n") { "${it.key.cleanJsonReference()} -> ${it.value}" })
        }
        if (printComponents) {
            println()
            println("Printing TypeStore components...")
            print(types.filterKeys { it.startsWith("#") }.entries.joinToString("\n") { "${it.key} -> ${it.value}" })
        }
        if (printStructured) {
            types.forEach {
                println(printType(it.value))
            }
        }
    }

    fun printableSummary(): String {
        val sb = StringBuilder()
        types.forEach {
            sb.append(printType(it.value))
        }
        return sb.toString()
    }

}

object ReservedKeywords {
    private val reservedKeywords = setOf("Companion")
    fun Type.escapeKeywords(): Type {
        return if (this is Type.WithTypeName && this.simpleName() in reservedKeywords) {
            this.withTypeName(this.typeName.updateSimpleName { it + "_" })
        } else this
    }
}
