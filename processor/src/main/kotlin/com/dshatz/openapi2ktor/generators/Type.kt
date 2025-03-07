package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.kdoc.DocTemplate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

sealed class Type {

    abstract fun simpleName(): String
    open fun packageName(): String = "<no_package>"
    fun qualifiedName(): String = packageName() + "." + simpleName()

    data class SimpleType(val kotlinType: TypeName) : Type() {
        override fun toString(): String = "SimpleType(${(kotlinType as ClassName).simpleName})"
        override fun simpleName(): String = toString()
    }

    sealed class WithTypeName: Type() {
        abstract val typeName: TypeName
        abstract val description: DocTemplate?

        override fun simpleName(): String = (typeName as ClassName).simpleName
        override fun packageName(): String = (typeName as ClassName).packageName

        abstract fun withTypeName(newTypeName: TypeName): Type.WithTypeName

        data class Object(
            override val typeName: TypeName,
            val props: Map<String, PropInfo>,
            val requiredProps: kotlin.collections.List<String>,
            val defaultValues: Map<String, Any>,
            override val description: DocTemplate?,
        ) : WithTypeName() {
            override fun toString(): String {
                return "${simpleName()} - object"
            }

            override fun withTypeName(newTypeName: TypeName): WithTypeName = copy(typeName = newTypeName)
            data class PropInfo(val type: Type, val doc: DocTemplate?)
        }

        data class PrimitiveWrapper(
            override val typeName: TypeName,
            val wrappedType: Type,
        ): WithTypeName() {
            override fun toString(): String {
                return "PrimitiveWrapper of $wrappedType"
            }
            override fun withTypeName(newTypeName: TypeName): WithTypeName = copy(typeName = newTypeName)
            override val description: DocTemplate? = DocTemplate.Builder()
                .add("Wrapper of ")
                .addTypeLink(wrappedType)
                .addDocFor(wrappedType)
                .build()
        }

        data class Alias(
            override val typeName: TypeName,
            val aliasTarget: Type
        ): WithTypeName() {
            override fun toString(): String {
                return "${simpleName()} alias -> $aliasTarget"
            }

            override val description: DocTemplate? = (aliasTarget as? WithTypeName)?.description

            override fun withTypeName(newTypeName: TypeName): WithTypeName = copy(typeName = newTypeName)
        }

        data class Enum<T>(
            override val typeName: TypeName,
            val elements: Map<T, String>,
            override val description: DocTemplate?
        ): WithTypeName() {
            override fun toString(): String {
                return "${simpleName()} enum(${elements.entries.joinToString()})"
            }
            override fun withTypeName(newTypeName: TypeName): WithTypeName = copy(typeName = newTypeName)
        }

        data class OneOf(override val typeName: TypeName, val childrenMapping: Map<Type, String>, val discriminator: String): WithTypeName() {
            override fun toString(): String {
                return "${simpleName()} - one of ${childrenMapping.keys.joinToString()}"
            }
            override fun withTypeName(newTypeName: TypeName): WithTypeName = copy(typeName = newTypeName)
            override val description: DocTemplate? = DocTemplate.Builder()
                .add("OneOf ")
                .addMany(childrenMapping.keys) { idx, type ->
                    addTypeLink(type)
                    if (idx != childrenMapping.keys.indices.last) add(", ")
                }.build()
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