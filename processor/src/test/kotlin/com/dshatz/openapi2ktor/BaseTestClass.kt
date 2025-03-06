package com.dshatz.openapi2ktor

import com.reprezen.kaizen.oasparser.OpenApiParser
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import java.io.File
import kotlin.test.BeforeTest

open class BaseTestClass(private val apiFile: String = "../e2e/polymorphism/src/test/resources/sample.yaml") {


    protected lateinit var api: OpenApi3

    @BeforeTest
    open fun init() {
        api = OpenApiParser().parse(File(apiFile)) as OpenApi3
    }
}