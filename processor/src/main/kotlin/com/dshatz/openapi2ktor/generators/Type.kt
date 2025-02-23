package com.dshatz.openapi2ktor.generators

import com.squareup.kotlinpoet.TypeName

sealed class Type {

    sealed class WithTypeName: Type() {
        abstract val typeName: TypeName
        data class SimpleType(override val typeName: TypeName) : WithTypeName()

        data class Object(
            override val typeName: TypeName,
            val props: Map<String, Type>,
            val requiredProps: kotlin.collections.List<String>,
            val defaultValues: Map<String, Any>
        ) : WithTypeName()

        data class Alias(
            override val typeName: TypeName,
            val aliasTarget: Type
        ): WithTypeName()

        data class Enum<T>(
            override val typeName: TypeName,
            val elements: kotlin.collections.List<T>
        ): WithTypeName()

        data class OneOf(override val typeName: TypeName, val childrenMapping: Map<Type, String>, val discriminator: String): WithTypeName()
    }

    data class Reference(val jsonReference: String): Type()

    data class List(
        val itemsType: Type
    ): Type()

    companion object {
        fun <T: TypeName> T.simpleType(
            nullable: Boolean = false,
        ): WithTypeName.SimpleType = WithTypeName.SimpleType(
            this.copy(nullable = nullable)
        )
    }
}