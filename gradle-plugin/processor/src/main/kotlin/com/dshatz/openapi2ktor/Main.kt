package com.dshatz.openapi2ktor

import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.generators.analyze.OpenApiAnalyzer
import com.dshatz.openapi2ktor.generators.clients.IClientGenerator
import com.dshatz.openapi2ktor.utils.Packages
import com.squareup.kotlinpoet.FileSpec
import java.io.Serializable
import java.lang.RuntimeException
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
        val api = kotlin.runCatching { parser.fromFile(Path.of(apiFile)) }.getOrElse { throw RuntimeException("Could not read spec file at $apiFile") }
        val packages = Packages(config.basePackage)
        if (api != null) {
            val typeStore = TypeStore()
            val modelGen = OpenApiAnalyzer(typeStore, packages, config)
            return modelGen.generate(api)
        } else error("Api is null")
    }
}

interface GeneratorConfig: Serializable {
    val additionalPropsConfig: AdditionalPropsConfig
    val basePackage: String
    val generateClients: Boolean
    val dateLibrary: DateLibrary

    companion object {
        fun default(): GeneratorConfig = DefaultGeneratorConfig
    }
}

enum class DateLibrary {
    JavaTime,
    KotlinxDatetime,
    String
}

object DefaultGeneratorConfig: GeneratorConfig {
    override val additionalPropsConfig: AdditionalPropsConfig = object: AdditionalPropsConfig {
        override val additionalPropPatterns: List<String> = emptyList()
    }
    override val basePackage: String = ""
    override val generateClients: Boolean = true
    override val dateLibrary: DateLibrary = DateLibrary.String
}

interface AdditionalPropsConfig: Serializable {
    val additionalPropPatterns: List<String>
}
