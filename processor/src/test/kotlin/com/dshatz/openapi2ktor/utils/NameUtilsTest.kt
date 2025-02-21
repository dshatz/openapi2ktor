package com.dshatz.openapi2ktor.utils

import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.OpenApiParser
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.reprezen.kaizen.oasparser.model3.Response
import com.reprezen.kaizen.oasparser.model3.Schema
import java.io.File
import kotlin.test.*

class NameUtilsTest {

    private lateinit var api: OpenApi3
    @BeforeTest
    fun init() {
        api = OpenApiParser().parse(File("../e2e/polymorphism/src/test/resources/sample.yaml")) as OpenApi3
    }

    @Test
    fun testMakePackageName() {

        val jsonReference = "file:/sample.yaml#/paths//orders//get/responses/200/content/application/json/schema/items"

        assertEquals(
            "com.example.models.paths.orders.get.responses.items",
            makePackageName(jsonReference, "com.example.models")
        )
    }

    @Test
    fun `is array item a reference`() {
        /**
         *     OrderList:
         *       properties:
         *         orders:
         *           type: array
         *           items:
         *             type: object
         *             properties:
         *               id:
         *                 type: integer
         *               amount:
         *                 type: integer
         *               params:
         *                 type: array
         *                 items: {}
         */
        val orderList = api.schemas["OrderList"]!!
        assertFalse(orderList.isArrayItemAReference())

        /**
         *         "405":
         *           description: array of references
         *           content:
         *             application/json:
         *               schema:
         *                 type: array
         *                 items:
         *                   "$ref": '#/components/schemas/OrderList'
         */
        val getOrders405Response = api.paths["/orders"]!!.get.responses["405"]!!.defaultSchema()
        assertTrue(getOrders405Response.isArrayItemAReference())
    }

    @Test
    fun `is property a reference`() {
        val notAReference = api.schemas["User"]!!
        assertFalse(notAReference.isPropAReference("id"))

        val isAReference = api.paths["/users"]!!.post.responses["204"]!!.defaultSchema()
        assertTrue(isAReference.isPropAReference("existing"))
    }

    @Test
    fun `safe prop name`() {
        assertEquals("comGithubBaseImageId", "com.github.baseImage.id".safePropName())
        assertEquals("mySnakeProp", "my_snake_prop".safePropName())
    }

    private fun Response.defaultSchema(): Schema = contentMediaTypes.values.first().schema!!

}