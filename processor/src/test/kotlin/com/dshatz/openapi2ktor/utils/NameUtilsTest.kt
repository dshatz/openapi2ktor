package com.dshatz.openapi2ktor.utils

import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.OpenApiParser
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameUtilsTest {

    @Test
    fun testMakePackageName() {

        val jsonReference = "file:/sample.yaml#/paths//orders//get/responses/200/content/application/json/schema/items"

        assertEquals(
            "com.example.models.paths.orders.get.responses.items",
            makePackageName(jsonReference, "com.example.models")
        )
    }

    @Test
    fun testIsComponent() {
        val api = OpenApiParser().parse(File("../e2e/polymorphism/src/test/resources/sample.yaml")) as OpenApi3

        val schemaDefinition = api.schemas["User"]
        assertTrue(schemaDefinition!!.isComponentSchema())

        val response200 = api.paths["/users"]!!.get.responses["200"]!!
//        println(response200)
        val schemaReferencing = response200.contentMediaTypes.values.first().schema.itemsSchema.oneOfSchemas.first()
        println(Overlay.of(schemaReferencing).overlay._getCreatingRef().json)
        assertFalse(schemaReferencing.isComponentSchema())
//        val schemaReferencing = .contentMediaTypes.values.first().schema.oneOfSchemas.first()
//        println(Overlay.of(schemaReferencing).jsonReference)
    }

}