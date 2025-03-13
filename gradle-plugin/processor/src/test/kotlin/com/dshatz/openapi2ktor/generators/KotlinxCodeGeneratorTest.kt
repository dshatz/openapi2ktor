package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.generators.Type.Companion.simpleType
import com.dshatz.openapi2ktor.generators.models.KotlinxCodeGenerator
import com.dshatz.openapi2ktor.utils.Packages
import com.dshatz.openapi2ktor.utils.capitalize
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinxCodeGeneratorTest {

    private lateinit var generator: KotlinxCodeGenerator
    @BeforeTest
    fun init() {
        generator = KotlinxCodeGenerator(TypeStore(), Packages("com.example"))
        generator.responseMappings = KotlinxCodeGenerator.ResponseInterfaceResult(emptyList(), emptyMap())
    }

    @Test
    fun `default values`() {
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

    @Test
    fun `generate objects`() {
        val user = generator.typeStore.addObject("User", "name" to String::class.type(), "age" to Int::class.type())
        val fileSpecs = generator.generateObjects()
        assertEquals(1, fileSpecs.size)
        assertTrue(fileSpecs.first().hasType(user))
    }


    private fun TypeStore.addObject(name: String, vararg props: Pair<String, Type>): ClassName {
        val cl = makeObjectClassName(name)
        registerType("#/components/schema/$name",
            Type.WithTypeName.Object(
                cl,
                props.toMap().mapValues { Type.WithTypeName.Object.PropInfo(it.value, null) },
                props.map { it.first },
                emptyMap(),
                null
            )
        )
        return cl
    }

    private fun makeObjectClassName(name: String): ClassName {
        return ClassName("com.example.models", "components.schemas.${name.capitalize()}")
    }

    private fun FileSpec.hasType(cl: ClassName): Boolean {
        return this.packageName == cl.packageName && this.members.find { it is TypeSpec && it.name == cl.simpleName } != null
    }

}