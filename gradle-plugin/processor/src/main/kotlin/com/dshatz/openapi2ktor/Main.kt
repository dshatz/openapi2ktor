package com.dshatz.openapi2ktor

import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.generators.analyze.OpenApiAnalyzer
import com.dshatz.openapi2ktor.generators.clients.IClientGenerator
import com.dshatz.openapi2ktor.utils.Packages
import com.squareup.kotlinpoet.FileSpec
import java.io.Serializable
import java.nio.file.Path

/*fun main() {
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
}*/


data class EntryPoint(
    val apiFile: String,
    val outputDir: String,
    val config: GeneratorConfig
) {
    fun run(): Pair<List<FileSpec>, List<IClientGenerator.Template>> {
        val parser = Parser()
        val api = parser.fromFile(Path.of(apiFile))
        val packages = Packages(config.basePackage)
        if (api != null) {
            val typeStore = TypeStore()
            val modelGen = OpenApiAnalyzer(typeStore, packages)
            return modelGen.generate(api)
        } else error("Api is null")
    }
}

interface GeneratorConfig: Serializable {
    val additionalPropsConfig: AdditionalPropsConfig
    val basePackage: String
}

interface AdditionalPropsConfig: Serializable {
    val additionalPropPatterns: List<String>
}
