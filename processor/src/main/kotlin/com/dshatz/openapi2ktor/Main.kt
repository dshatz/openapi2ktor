package com.dshatz.openapi2ktor

import com.dshatz.openapi2ktor.generators.OpenApiAnalyzer
import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.utils.Packages
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.FileSpec
import java.io.File
import java.nio.file.Path

fun main() {
    val outputDir = "build/generated";
    val fileSpecs = EntryPoint("e2e/src/test/resources/sample.yaml").run()
    fileSpecs.forEach {
        it.writeTo(File(outputDir).resolve("src/main/kotlin"))
    }
}

data class EntryPoint(
    val apiFile: String,
    val logger: KSPLogger? = null
) {
    fun run(): List<FileSpec> {
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


