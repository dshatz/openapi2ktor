package com.dshatz.openapi2ktor.utils

import com.reprezen.kaizen.oasparser.OpenApiParser
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PathTest {

    private lateinit var api: OpenApi3
    @BeforeTest
    fun init() {
        api = OpenApiParser().parse(File("../../samples/tmdb.json")) as OpenApi3
    }

    @Test
    fun `build path tree`() {
        val tree = buildPathTree(api.paths)
        println(tree)
        assertEquals(1, tree.children.size)
        assertEquals(21, tree.children.first().children.size)
    }

}