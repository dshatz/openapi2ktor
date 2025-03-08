package com.dshatz.openapi2ktor.plugin

import com.dshatz.openapi2ktor.Cli
import com.dshatz.openapi2ktor.utils.capitalize
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

class Plugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("openapi3", OpenApiPluginExtension::class.java)

        project.afterEvaluate {
            extension.generators.forEach {
                val output = project.layout.buildDirectory.dir("openapi").get().dir(it.name).asFile.path
                project.tasks.register("generate${it.name.capitalize()}Clients", GenerateTask::class.java) { task ->
                    task.group = "openapi3"
                    task.currentProjectPath = project.projectDir.absolutePath
                    task.generator = it
                    task.outputPath = output
                    task.inputs.file(it.apiFile)
                }

                project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
                    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                    if (kotlinExtension != null) {
                        project.afterEvaluate {
                            kotlinExtension.sourceSets.getByName("commonMain").kotlin.srcDir(output)
                            kotlinExtension.sourceSets.getByName("commonTest").kotlin.srcDir(output)
                        }
                    }
                }
            }
        }
    }
}

abstract class GenerateTask: DefaultTask() {
    @Input
    lateinit var generator: OpenApiPluginExtension.Generator

    @Input
    lateinit var outputPath: String

    @Input
    lateinit var currentProjectPath: String

    @TaskAction
    fun generate() {
        Cli.main(arrayOf("-i", File(currentProjectPath, generator.apiFile).absolutePath, "-o", outputPath, "-b", generator.basePackage))
    }
}

open class OpenApiPluginExtension {
    var generators: List<Generator> = emptyList()

    class GeneratorBuilder() {
        var openApiFilePath: String? = null
        var basePackageName: String? = null
        fun build(name: String): Generator {
            return Generator(
                name,
                openApiFilePath ?: error("Please set openApiFilePath"),
                basePackageName ?: error("Please set basePackageName")
            )
        }
    }

    data class Generator(val name: String, val apiFile: String, val basePackage: String)

    fun addGenerator(name: String, configure: GeneratorBuilder.() -> Unit) {
        val builder = GeneratorBuilder()
        configure(builder)
        generators += builder.build(name)
    }

    fun advancedConfig(action: AdvancedConfig.() -> Unit) {
        advancedConfig.apply(action)
    }

    var advancedConfig = AdvancedConfig()
}

open class AdvancedConfig {
    var level: Int = 1
}
