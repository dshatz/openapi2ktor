package com.dshatz.openapi2ktor.utils

import com.dshatz.openapi2ktor.BaseTestClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenApi3UtilsTest: BaseTestClass() {

    @Test
    fun `is parameter a reference`() {
        val operation = api.paths["/users"]!!.get
        assertFalse(operation.isParameterAReference("limit").isReference)
        assertTrue(operation.isParameterAReference("config")?.target == "#/components/schemas/webhook-config-url")
    }

    @Test
    fun `get response reference`() {
        val operation = api.paths["/users/by-id/{id}"]!!.get
        val ref1 = operation.getReferenceForResponse(400)
        assertNotNull(ref1)

        val op2 = api.paths["/users"]!!.get
        val ref2 = operation.getReferenceForResponse(200)
        assertNull(ref2)
    }

    @Test
    fun `is response component a reference`() {
        val refResponse = api.responses["bad_request"]
        assertEquals("#/components/schemas/basic-error", refResponse!!.getResponseComponentRefInfo()?.target)

        val nonRefResponse = api.responses["error_response"]
        assertFalse(nonRefResponse!!.getResponseComponentRefInfo().isReference)
    }
}