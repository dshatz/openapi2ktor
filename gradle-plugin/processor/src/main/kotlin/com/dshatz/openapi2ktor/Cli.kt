package com.dshatz.openapi2ktor

import java.io.File

object Cli {
    /*@JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("openapi2ktor")
        val openapiFile by parser.option(ArgType.String, shortName = "i", description = "Input OpenAPI3 spec file").required()
        val outputDir by parser.option(ArgType.String, shortName = "o", description = "Output directory").default("build/generated")
        val basePackage by parser.option(ArgType.String, shortName = "b", description = "Base package").default("com.example")
        parser.parse(args)

        val basePath = File(outputDir).resolve("src/main/kotlin")
        val (fileSpecs, templates) = EntryPoint(openapiFile, basePackage = basePackage).run()
        fileSpecs.forEach {
            it.writeTo(basePath)
        }
        templates.forEach {
            val path = basePath.resolve(it.packageName.split(".").joinToString("/"))
                .resolve(it.name + ".kt")
            path.writeText(it.contents)
        }
    }*/

    fun runWithParams(
        openapiFile: String,
        outputDir: String,
        config: GeneratorConfig
    ) {
        println("Outputdir: $outputDir")
        val basePath = File(outputDir).resolve("src/main/kotlin")
        val (fileSpecs, templates) = EntryPoint(openapiFile, outputDir, config = config).run()
        fileSpecs.forEach {
            it.writeTo(basePath)
        }
        templates.forEach {
            val path = basePath.resolve(it.packageName.split(".").joinToString("/"))
                .resolve(it.name + ".kt")
            println("Writing template ${it.name} to ${path.absolutePath}")
            path.writeText(it.contents)
        }
    }
}