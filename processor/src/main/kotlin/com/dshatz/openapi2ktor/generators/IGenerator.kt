package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.utils.makeCodeBlock
import com.dshatz.openapi2ktor.utils.makeDefaultPrimitive
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.serialization.json.JsonPrimitive

interface IGenerator {

    val typeStore: TypeStore
    fun <T: Type> T.makeTypeName(): TypeName {
        return when (this) {
            is Type.WithTypeName -> typeName as ClassName
            is Type.Reference -> typeStore.resolveReference(jsonReference).makeTypeName()
            is Type.List -> List::class.asTypeName().parameterizedBy(itemsType.makeTypeName())
            is Type.SimpleType -> this.kotlinType
        }
    }

    fun <T: Type> findConcreteType(type: T): Type.WithTypeName? {
        return when (type) {
            is Type.WithTypeName -> type
            is Type.Reference -> findConcreteType(typeStore.resolveReference(type.jsonReference))
            is Type.List -> null
            is Type.SimpleType -> null
        }
    }

    fun Type.addNullabilityIfOptional(isRequired: Boolean): TypeName {
        val original = this.makeTypeName()
        return if (isRequired) {
            makeTypeName()
        } else {
            if (!original.isNullable) {
                original.copy(nullable = true)
            } else original
        }
    }

    fun Type.defaultValue(default: Any): CodeBlock {
        return when (this.makeTypeName().copy(nullable = false)) {
            String::class.asTypeName() -> CodeBlock.of("%S", default)
            Type.WithTypeName.Enum::class.asTypeName() -> CodeBlock.of("enumval")
            JsonPrimitive::class.asTypeName() -> default.makeDefaultPrimitive()!!.makeCodeBlock()
            List::class.asTypeName().parameterizedBy(String::class.asTypeName()) -> {
                (default as? List<String>)?.let {
                    CodeBlock.of(
                        "listOf(" + it.joinToString(", ") { "%S" } + ")",
                        args = it.toTypedArray()
                    )
                } ?: (default as List<*>).let {
                    CodeBlock.of(
                        "listOf(" + it.joinToString(", ") { "%L" } + ")",
                        args = it.toTypedArray()
                    )
                }
            }
            else -> CodeBlock.of("%L", default)
        }
    }

    fun Type.makeDefaultValueCodeBlock(isRequired: Boolean, defaultValue: Any?): CodeBlock? {
        return if (!isRequired) {
            if (defaultValue != null) {
                // Optional and non-null default is provided. Set that default.
                defaultValue(defaultValue)
            } else {
                // Optional and no default value provided, or default of null.
                CodeBlock.of("null")
            }
        } else defaultValue?.let { defaultValue(it) }
    }

}