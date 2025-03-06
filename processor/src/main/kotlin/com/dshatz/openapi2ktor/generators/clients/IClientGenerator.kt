package com.dshatz.openapi2ktor.generators.clients

import com.dshatz.openapi2ktor.generators.IGenerator
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.squareup.kotlinpoet.FileSpec

interface IClientGenerator: IGenerator {

    abstract fun generate(schema: OpenApi3): List<FileSpec>

    abstract fun generateTemplates(): List<Template>

    data class Template(val packageName: String, val name: String, val contents: String)

}