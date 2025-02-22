package com.dshatz.openapi2ktor.generators

import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

class TypeStore {

    // Schema string representation to model class name.
    private val types: MutableMap<String, Type> = mutableMapOf()
    fun getTypes(): Map<String, Type> = types.toMap()

    private val checkForDuplicates = true

    val addedTypes = mutableMapOf<TypeName, String>()

    fun registerType(jsonReference: String, type: Type) {
        println("Registering type! ${type.typeName}; $jsonReference")
        if (checkForDuplicates) {
            if (addedTypes[type.typeName] != null && (type.typeName as ClassName).simpleName.contains("labels")) {
                error("Duplicate type! ${type.typeName} ${jsonReference.stripFilePathFromRef()} ${addedTypes[type.typeName]?.stripFilePathFromRef()}")
            }
        }
        types[jsonReference] = type
        addedTypes[type.typeName] = jsonReference
    }

    fun registerComponentSchema(referenceId: String, type: Type) {
        println("Registering component schema! $referenceId")
        types[referenceId] = type
    }

    fun getClassName(schema: Schema): Type? {
        return types[schema.toString()]
    }

    fun printTypes() {
        val printAll = true
        val printComponents = false
        val printSuperInterfaces = true
        if (printAll) {
            println()
            println("Printing TypeStore contents...")
            println(types.entries.joinToString("\n") { "${it.key.stripFilePathFromRef()} -> ${it.value.simpleName()}" })
        }
        if (printSuperInterfaces) {
            println()
            println("Printing TypeStore oneOf mappings...")
            println(types.values.filterIsInstance<Type.OneOf>().joinToString("\n") { it.typeName.toString() + " -> " + it.childrenMapping.map { child -> child.key.typeName.toString() }.toString() })
        }
        if (printComponents) {
            println()
            println("Printing TypeStore components...")
            print(types.filterKeys { it.startsWith("#") }.entries.joinToString("\n") { "${it.key} -> ${it.value.simpleName()}" })
        }
    }

    private fun String.stripFilePathFromRef(): String {
        return substringAfter("#")
    }

    private fun Type.simpleName(): String {
        return (typeName as ClassName).simpleName
    }

}