package com.dshatz.openapi2ktor.utils

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
        api = OpenApiParser().parse(File("../../e2e/polymorphism/src/test/resources/sample.yaml")) as OpenApi3
    }

    @Test
    fun testMakePackageName() {

        val jsonReference = "file:/sample.yaml#/paths//orders//get/responses/200/content/application/json/schema/items"

        assertEquals(
            "com.example.models.paths.orders.get.response.items",
            makePackageName(jsonReference, "com.example.models")
        )
    }

    @Test
    fun `package name for response component`() {
        val jsonReference = "file:/sample.yaml#/components/responses/bad-request"
        assertEquals("com.example.models.components.responses.badRequest", makePackageName(jsonReference, "com.example.models"))
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
        assertFalse(orderList.arrayItemRefData().isReference)

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
        assertEquals("#/components/schemas/OrderList", getOrders405Response.arrayItemRefData()?.target)
    }

    @Test
    fun `is property a reference`() {
        val notAReference = api.schemas["User"]!!
        assertFalse(notAReference.propRefData("id").isReference)

        val isAReference = api.paths["/users"]!!.post.responses["204"]!!.defaultSchema()
        assertEquals("#/components/schemas/UserOrAdmin", isAReference.propRefData("existing")?.target)
    }

    @Test
    fun `safe prop name`() {
        assertEquals("comGithubBaseImageId", "com.github.baseImage.id".safePropName())
        assertEquals("mySnakeProp", "my_snake_prop".safePropName())
    }

    @Test
    fun `model package name`() {
        val base = "com.example.models"
        val jsonReference = "file:/github.yaml#/components/paths//projects/columns/{column_id}/moves//post/responses/201/content/application/json/schema"
        val packageName = makePackageName(jsonReference, base)
        assertEquals("com.example.models.components.paths.projects.columns.byColumnId.moves.post.response", packageName)


        val ref3 = "file:/github.yaml#/components/responses/actions_runner_labels_readonly/content/application/json/schema/properties/labels"
        val ref4 = "file:/github.yaml#/components/responses/actions_runner_labels/content/application/json/schema/properties/labels"
        val ref5 = "file:/github.yaml#/components/schemas/runner/properties/labels"
        assertNotEquals(makePackageName(ref3, base), makePackageName(ref4, base))
        assertEquals("$base.components.responses.actionsRunnerLabelsReadonly.properties.labels", makePackageName(ref3, base))
        assertEquals(
            "$base.components.schemas.runner.properties.labels",
            makePackageName(ref5, base)
        )

        val refArray = "file:/spot_api.yaml#/components/schemas/ocoOrder/properties/orders/items"
        assertEquals(
            "$base.components.schemas.ocoOrder.properties.orders.items",
            makePackageName(refArray, base)
        )

        val refLong = "file:/spot_api.yaml#/paths//sapi/v1/margin/allOrderList//get/responses/200/content/application/json/schema/items/properties/orders/items"
        assertEquals(
            "$base.paths.sapi.v1.margin.allOrderList.get.response.items.properties.orders.items",
            makePackageName(refLong, base)
        )

        val refRequest = "file:/github.yaml#/paths//applications/{client_id}/token/scoped//post/requestBody/content/application/json/schema"
        assertEquals(
            "$base.paths.applications.byClientId.token.scoped.post.requestBody",
            makePackageName(refRequest, base)

        )
    }

    @Test
    fun `parameter package name`() {
        val ref = "file:/sample.yaml#/components/parameters/user_type"
        val packageName = makePackageName(ref, "com.example.models")
        assertEquals("com.example.models.components.parameters", packageName)
    }

    private fun Response.defaultSchema(): Schema = contentMediaTypes.values.first().schema!!

}