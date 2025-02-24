package com.dshatz.openapi2ktor

import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.generators.analyze.OpenApiAnalyzer
import com.dshatz.openapi2ktor.generators.clients.IClientGenerator
import com.dshatz.openapi2ktor.utils.Packages
import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.FileSpec
import java.io.File
import java.nio.file.Path

fun main() {
    val outputDir = "build/generated";
    val (fileSpecs, templates) = EntryPoint("e2e/src/test/resources/sample.yaml").run()
    val basePath = File(outputDir).resolve("src/main/kotlin")
    fileSpecs.forEach {
        it.writeTo(basePath)
    }
    templates.forEach {
        val path = basePath.resolve(it.packageName.split(".").joinToString("/"))
            .resolve(it.name + ".kt")
        path.writeText(it.contents)
    }
}


data class EntryPoint(
    val apiFile: String,
    val logger: KSPLogger? = null
) {
    fun run(): Pair<List<FileSpec>, List<IClientGenerator.Template>> {
        val parser = Parser()
        val api = parser.fromFile(Path.of(apiFile))
        val packages = Packages("com.example")
        if (api != null) {
            val typeStore = TypeStore()
            val modelGen = OpenApiAnalyzer(typeStore, packages)
            return modelGen.generate(api)
        } else error("Api is null")
    }
}


