package com.dshatz.openapi2ktor.ksp

import com.dshatz.openapi2ktor.EntryPoint
import com.dshatz.openapi2ktor.GeneratorConfig
import com.dshatz.openapi2ktor.AdditionalPropsConfig
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated

class KspProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        val inputFile = options["openApiFile"] ?: error("Please set openApiFile ksp option.")
        logger.warn("openApiFile = $inputFile")
        val (fileSpecs, templates) = EntryPoint(
            inputFile, "", config = object : GeneratorConfig {
                override val additionalPropsConfig: AdditionalPropsConfig = object : AdditionalPropsConfig {
                    override val additionalPropPatterns: MutableList<String> = mutableListOf()
                }
                override val basePackage: String = "com.example"

            }
        ).run()
        logger.warn(resolver.getAllFiles().toList().toString())
        fileSpecs.forEach {
            logger.warn("Writing file ${it.name} (${it.packageName})")
            codeGenerator.createNewFile(Dependencies(false), it.packageName, it.name).bufferedWriter().use { stream ->
                it.writeTo(stream)
            }
        }
        templates.forEach {
            codeGenerator.createNewFile(Dependencies(false), it.packageName, it.name).bufferedWriter().use { stream ->
                stream.write(it.contents)
            }
        }
        invoked = true
        return emptyList()
    }

}