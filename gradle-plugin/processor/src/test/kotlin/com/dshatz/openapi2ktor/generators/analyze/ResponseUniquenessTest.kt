package com.dshatz.openapi2ktor.generators.analyze

import com.dshatz.openapi2ktor.GeneratorConfig
import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.utils.Packages
import com.dshatz.openapi2ktor.utils.isSuccessCode
import com.reprezen.kaizen.oasparser.OpenApiParser
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.reprezen.kaizen.oasparser.model3.Operation
import com.reprezen.kaizen.oasparser.model3.Response
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.*

class ResponseUniquenessTest {

    private lateinit var api: OpenApi3
    private lateinit var typeStore: TypeStore
    private lateinit var analyzer: TestAnalyzer
    private val packages = Packages("com.example")

    @BeforeTest
    fun init() {
        api = OpenApiParser().parse(File("../../e2e/polymorphism/src/test/resources/sample.yaml")) as OpenApi3
        typeStore = TypeStore()
        analyzer = TestAnalyzer(typeStore, packages)
    }

    private class TestAnalyzer(typeStore: TypeStore, packages: Packages): OpenApiAnalyzer(
        typeStore,
        packages,
        GeneratorConfig.default()
    ) {
        var onCalled: ((pathString: String, verb: String, success: Boolean, willWrap: Boolean) -> Unit)? = null

        override suspend fun processPathResponse(
            operation: Operation,
            response: Response,
            pathString: String,
            statusCode: Int,
            verb: String,
            wrapPrimitives: Boolean
        ): Job = withContext(Dispatchers.Default) {
            launch { onCalled?.invoke(pathString, verb, statusCode.isSuccessCode(), wrapPrimitives) }
        }
    }


    @Test
    fun `unique success responses`() = runTest {
        with (analyzer) {
            analyzer.onCalled = { pathString, verb, isSuccess, willWrap ->
                if (pathString == "/orders" && verb == "get") {
                    if (isSuccess) {
                        assertFalse(willWrap)
                    } else {
                        assertTrue(willWrap)
                    }
                }
            }
            api.gatherResponseModels().joinAll()
        }

    }

}