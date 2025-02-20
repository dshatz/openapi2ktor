package com.dshatz.openapi2ktor

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.io.File

object Cli {
    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("openapi2ktor")
        val openapiFile by parser.option(ArgType.String, shortName = "i", description = "Input OpenAPI3 spec file").required()
        val outputDir by parser.option(ArgType.String, shortName = "o", description = "Output directory").default("build/generated")
        parser.parse(args)

        val fileSpecs = EntryPoint(openapiFile).run()
        fileSpecs.forEach {
            it.writeTo(File(outputDir).resolve("src/main/kotlin"))
        }
    }
}