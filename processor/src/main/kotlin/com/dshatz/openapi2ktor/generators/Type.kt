package com.dshatz.openapi2ktor.generators

import com.squareup.kotlinpoet.TypeName
import kotlinx.cli.Option
import java.util.Optional
import kotlin.reflect.KClass

sealed class Type {

    abstract val typeName: TypeName

    data class SimpleType(override val typeName: TypeName, val required: Boolean, val default: Any? = null) : Type()

    sealed class WithProps: Type() {
        abstract val props: Map<String, Type>
        data class Object(override val typeName: TypeName, override val props: Map<String, Type>) : WithProps()
    }

    data class Alias(
        override val typeName: TypeName,
        val aliasTarget: Type
    ): Type()

    data class OneOf(override val typeName: TypeName, val childrenMapping: Map<Type, String>, val discriminator: String): Type()

    companion object {
        fun <T: TypeName> T.simpleType(
            required: Boolean = false,
            nullable: Boolean = false,
            default: Any? = null
        ): SimpleType = SimpleType(
            this.copy(nullable = nullable), required = required, default = default)
    }
}