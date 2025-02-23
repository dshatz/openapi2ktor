package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.generators.Type.Companion.simpleType
import com.dshatz.openapi2ktor.generators.models.KotlinxCodeGenerator
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinxCodeGeneratorTest {

    private lateinit var generator: KotlinxCodeGenerator
    @BeforeTest
    fun init() {
        generator = KotlinxCodeGenerator(TypeStore())
    }

    @Test
    fun `default value for string`() {
        with (generator) {
            assertEquals(CodeBlock.of("%S", ""), String::class.type().defaultValue(""))
            assertEquals(
                CodeBlock.of("listOf(%S, %S)", "string1", "string2"),
                List::class.parameterizedBy(String::class).simpleType().defaultValue(listOf("string1", "string2")))
            assertEquals(
                CodeBlock.of("false"),
                Boolean::class.type().defaultValue(false)
            )
            assertEquals(
                CodeBlock.of("1"),
                Int::class.type().defaultValue(1)
            )
            assertEquals(
                CodeBlock.of("1.0"),
                Double::class.type().defaultValue(1.0)
            )
        }
    }

}