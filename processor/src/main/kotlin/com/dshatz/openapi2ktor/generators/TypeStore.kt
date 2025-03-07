package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.utils.*
import com.squareup.kotlinpoet.ClassName
import java.util.concurrent.ConcurrentHashMap

class TypeStore {

    // Schema string representation to model class name.
    private val types: ConcurrentHashMap<String, Type> = ConcurrentHashMap()
    fun getTypes(): Map<String, Type> = types.toMap()

    private val responseMapping: MutableMap<PathId, MutableMap<Int, ResponseTypeInfo>> = mutableMapOf()
    private val responseInterfaces: MutableMap<PathId, Pair<ClassName?, ClassName?>> = mutableMapOf()
    private val operationParameters: MutableMap<PathId, List<OperationParam>> = mutableMapOf()

    fun registerType(jsonReference: String, type: Type) {
        println("Registering type! ${type.simpleName()}; ${jsonReference.cleanJsonReference()}")
        types[jsonReference.cleanJsonReference()] = type
    }

    fun registerComponentSchema(referenceId: String, type: Type) {
        if (type is Type.Reference) error("Should not be a reference for component schema $referenceId")
        println("Registering component schema! $referenceId")
        types[referenceId.cleanJsonReference()] = type
    }

    fun registerResponseMapping(path: PathId, status: Int, jsonReference: String, type: Type) = synchronized(responseMapping) {
        val map = responseMapping.getOrDefault(path, mutableMapOf()).also {
            it[status] = ResponseTypeInfo(type, jsonReference.cleanJsonReference())
        }
        responseMapping[path] = map
    }

    data class PathId(val pathString: String, val verb: String) {
        val pathId = verb.capitalize() + pathString.split("/").joinToString("") { it.capitalize() }
    }

    fun resolveReference(jsonReference: String): Type {
        println("Resolving $jsonReference...")
        return getTypes()[jsonReference] ?: run {
            printTypes()
            error("Could not resolve reference $jsonReference")
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
            PATH
        }
    }

    fun registerResponseInterface(path: PathId, successInterface: ClassName?, errorInterface: ClassName?) {
        responseInterfaces[path] = successInterface to errorInterface
    }

    fun getResponseErrorInterface(path: PathId) = responseInterfaces[path]?.second
    fun getResponseSuccessInterface(path: PathId) = responseInterfaces[path]?.first

    fun getResponseMapping(response: PathId): Map<Int, ResponseTypeInfo> = synchronized(responseMapping) {
        return responseMapping[response]!!
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
            println("Found TypeName $type with ${duplicatesForType.size} jsonRefs. Deduplicating...")
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