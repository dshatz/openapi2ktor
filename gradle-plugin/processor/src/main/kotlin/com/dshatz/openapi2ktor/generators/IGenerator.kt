package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.utils.makeCodeBlock
import com.dshatz.openapi2ktor.utils.makeDefaultPrimitive
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.serialization.json.JsonPrimitive

interface IGenerator {

    val typeStore: TypeStore
    fun <T: Type> T.makeTypeName(): TypeName {
        return with (typeStore) {
            makeTypeName()
        }
    }

    fun <T: Type> findConcreteType(type: T): Type.WithTypeName? {
        return typeStore.findConcreteType(type)
    }

    fun Type.nullableIfNoDefault(isRequired: Boolean, default: CodeBlock?): TypeName {
        val original = this.makeTypeName()
        return if (isRequired) {
            makeTypeName()
        } else {
            if (!original.isNullable && default.toString() == "null") {
                original.copy(nullable = true)
            } else original
        }
    }

    fun Type.defaultValue(default: Any): CodeBlock {
        val concreteType = this.makeTypeName().copy(nullable = false)
        return when (concreteType) {
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
            Float::class.asTypeName() -> CodeBlock.of("%Lf", default)
            Double::class.asTypeName() -> CodeBlock.of("%L", default as Double)
            else -> CodeBlock.of("%L", default)
        }
    }

    fun Type.makeDefaultValueCodeBlock(isRequired: Boolean, defaultValue: Any?, useKotlinDefaults: Boolean = true): CodeBlock? {
        return if (!isRequired) {
            if (defaultValue != null) {
                // Optional and non-null default is provided. Set that default.
                defaultValue(defaultValue)
            } else if (useKotlinDefaults) {
                // Optional and no default value provided, or default of null.
                typeDefaultValue()
            } else CodeBlock.of("null")
        } else defaultValue?.let { defaultValue(it) }
    }

    private fun Type.typeDefaultValue(): CodeBlock {
        return when (this.makeTypeName().run {
            if (this is ParameterizedTypeName) this.rawType else this
        }) {
            INT -> CodeBlock.of("0")
            FLOAT -> CodeBlock.of("0f")
            DOUBLE -> CodeBlock.of("0.0")
            LIST -> CodeBlock.of("emptyList()")
            else -> CodeBlock.of("null")
        }
    }

}