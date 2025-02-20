package com.dshatz.openapi2ktor

import com.reprezen.kaizen.oasparser.OpenApiParser
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import java.nio.file.Path

class Parser {

    private val parser = OpenApiParser()

    fun fromFile(path: Path): OpenApi3? {
        return parser.parse(path.toFile()) as OpenApi3?
    }

}