package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.utils.packageName
import com.dshatz.openapi2ktor.utils.simpleName
import com.dshatz.openapi2ktor.utils.stripFilePathFromRef
import com.reprezen.kaizen.oasparser.model3.Schema
import java.util.concurrent.ConcurrentHashMap

class TypeStore {

    // Schema string representation to model class name.
    private val types: ConcurrentHashMap<String, Type.WithTypeName> = ConcurrentHashMap()
    fun getTypes(): Map<String, Type.WithTypeName> = types.toMap()


    fun registerType(jsonReference: String, type: Type.WithTypeName) {
        println("Registering type! ${type.typeName}; $jsonReference")
        types[jsonReference] = type
    }

    fun registerComponentSchema(referenceId: String, type: Type.WithTypeName) {
        println("Registering component schema! $referenceId")
        types[referenceId] = type
    }

    fun getClassName(schema: Schema): Type.WithTypeName? {
        return types[schema.toString()]
    }

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
                    sb.appendLine(printType(it.value, parentObject = type, propName = it.key, depth = 3))
                }
            }
            is Type.WithTypeName.SimpleType -> {
                sb.append(("type: " + type.simpleName() + ("?".takeIf { type.typeName.isNullable } ?: "")).addSpaces(1))
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
                sb.appendLine("Enum".addSpaces(1))
                type.elements.forEach {
                    sb.appendLine("-$it".addSpaces(2))
                }
            }
            else -> {}
        }
        return sb.toString().addSpaces(depth)
    }

    fun printTypes() {
        val printAll = false
        val printComponents = false
        val printStructured = true
        if (printAll) {
            println()
            println("Printing TypeStore contents...")
            println(types.entries.joinToString("\n") { "${it.key.stripFilePathFromRef()} -> ${it.value.simpleName()}" })
        }
        if (printComponents) {
            println()
            println("Printing TypeStore components...")
            print(types.filterKeys { it.startsWith("#") }.entries.joinToString("\n") { "${it.key} -> ${it.value.simpleName()}" })
        }
        if (printStructured) {
            types.forEach {
                println(printType(it.value))
            }
        }
    }

}