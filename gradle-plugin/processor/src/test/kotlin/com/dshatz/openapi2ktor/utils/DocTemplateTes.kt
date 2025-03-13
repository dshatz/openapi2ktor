package com.dshatz.openapi2ktor.utils

import com.dshatz.openapi2ktor.generators.Type
import com.dshatz.openapi2ktor.kdoc.DocTemplate
import com.squareup.kotlinpoet.ClassName
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DocTemplateTest {

    val typeStore = mutableMapOf<String, Type>()

    @BeforeTest
    fun init() {

    }

    fun resolveType(type: Type): Type.WithTypeName? {
        return when (type) {
            is Type.WithTypeName -> type
            is Type.SimpleType -> null
            is Type.Reference -> resolveType(typeStore[type.jsonReference]!!)
            is Type.List -> null
        }
    }

    @Test
    fun `test simple`() {
        val doc = DocTemplate.Builder()
            .add("hello").build().toCodeBlock(::resolveType)

        assertEquals("hello", doc.toString())
    }

    private fun makeTestObject(description: DocTemplate? = null): Type.WithTypeName.Object {
        val cl = ClassName("com.example.models", "Object1")
        val obj1 = Type.WithTypeName.Object(cl, emptyMap(), emptyList(), emptyMap(), description)
        return obj1
    }

    private fun expectedLink(type: Type.WithTypeName): String {
        return "[${type.simpleName()}][${type.qualifiedName()}]"
    }

    @Test
    fun `test object`() {
        val obj1 = makeTestObject()
        val doc = DocTemplate.Builder()
            .add("Hello ")
            .addTypeLink(obj1)
            .addDocFor(obj1)
            .build()
        assertEquals("Hello ${expectedLink(obj1)}", doc.toCodeBlock(::resolveType).toString())
        println(doc.toCodeBlock(::resolveType))
    }

    @Test
    fun `test object and description`() {
        val obj1 = makeTestObject(description = DocTemplate.of("test description"))
        val doc = DocTemplate.Builder()
            .add("Hello ")
            .addTypeLink(obj1)
            .newLine()
            .addDocFor(obj1)
            .build()
        assertEquals("Hello ${expectedLink(obj1)}\ntest description", doc.toCodeBlock(::resolveType).toString())
        println(doc.toCodeBlock(::resolveType))
    }

    @Test
    fun `test reference`() {
        val obj1 = makeTestObject(DocTemplate.of("referenced object"))
        val ref = Type.Reference("obj1")
        typeStore[ref.jsonReference] = obj1
        val doc = DocTemplate.Builder()
            .addTypeLink(ref)
            .newLine()
            .addDocFor(ref)
            .build()
        assertEquals("${expectedLink(obj1)}\nreferenced object", doc.toCodeBlock(::resolveType).toString())
    }

}