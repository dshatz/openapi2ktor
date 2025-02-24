package com.dshatz.openapi2ktor.generators

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

sealed class Type {

    abstract fun simpleName(): String
    open fun packageName(): String = "<no_package>"

    data class SimpleType(val kotlinType: TypeName) : Type() {
        override fun toString(): String = "SimpleType(${(kotlinType as ClassName).simpleName})"
        override fun simpleName(): String = toString()
    }

    sealed class WithTypeName: Type() {
        abstract val typeName: TypeName

        override fun simpleName(): String = (typeName as ClassName).simpleName
        override fun packageName(): String = (typeName as ClassName).packageName

        data class Object(
            override val typeName: TypeName,
            val props: Map<String, Type>,
            val requiredProps: kotlin.collections.List<String>,
            val defaultValues: Map<String, Any>,
        ) : WithTypeName() {
            override fun toString(): String {
                return "${simpleName()} - object"
            }
        }

        data class PrimitiveWrapper(
            override val typeName: TypeName,
            val wrappedType: Type,
        ): WithTypeName() {
            override fun toString(): String {
                return "PrimitiveWrapper of $wrappedType"
            }
        }

        data class Alias(
            override val typeName: TypeName,
            val aliasTarget: Type
        ): WithTypeName() {
            override fun toString(): String {
                return "${simpleName()} alias -> $aliasTarget"
            }
        }

        data class Enum<T>(
            override val typeName: TypeName,
            val elements: kotlin.collections.List<T>
        ): WithTypeName() {
            override fun toString(): String {
                return "${simpleName()} enum(${elements.joinToString()})"
            }
        }

        data class OneOf(override val typeName: TypeName, val childrenMapping: Map<Type, String>, val discriminator: String): WithTypeName() {
            override fun toString(): String {
                return "${simpleName()} - one of ${childrenMapping.keys.joinToString()}"
            }
        }
    }

    data class Reference(val jsonReference: String): Type() {
        override fun simpleName(): String = "Ref($jsonReference)"
    }

    data class List(
        val itemsType: Type
    ): Type() {
        override fun simpleName(): String = "List<${itemsType.simpleName()}>"
    }

    companion object {
        fun <T: TypeName> T.simpleType(
            nullable: Boolean = false,
        ): SimpleType = SimpleType(
            this.copy(nullable = nullable)
        )
    }
}