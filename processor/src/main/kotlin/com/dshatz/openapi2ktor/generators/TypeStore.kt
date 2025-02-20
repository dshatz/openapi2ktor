package com.dshatz.openapi2ktor.generators

import com.reprezen.kaizen.oasparser.model3.Schema

class TypeStore {

    // Schema string representation to model class name.
    private val types: MutableMap<String, Type> = mutableMapOf()
    fun getTypes(): Map<String, Type> = types.toMap()

    fun registerType(schema: Schema, type: Type) {
        types[schema.toString()] = type
    }

    fun registerReference(referenceId: String, type: Type) {
        types[referenceId] = type
    }

    fun getClassName(schema: Schema): Type? {
        return types[schema.toString()]
    }

    fun printTypes() {
        println("Printing TypeStore contents...")
        println(types)
    }

}