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

        val basePath = File(outputDir).resolve("src/main/kotlin")
        val (fileSpecs, templates) = EntryPoint(openapiFile).run()
        fileSpecs.forEach {
            it.writeTo(basePath)
        }
        templates.forEach {
            val path = basePath.resolve(it.packageName.split(".").joinToString("/"))
                .resolve(it.name + ".kt")
            path.writeText(it.contents)
        }
    }
}