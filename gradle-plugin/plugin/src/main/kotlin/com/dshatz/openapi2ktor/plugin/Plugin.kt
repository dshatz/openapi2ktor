package com.dshatz.openapi2ktor.plugin

import com.dshatz.openapi2ktor.Cli
import com.dshatz.openapi2ktor.utils.capitalize
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import javax.inject.Inject

class Plugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("openapi3", OpenApiPluginExtension::class.java)

        project.afterEvaluate {
            extension.generators.get().forEach { config ->
                config.setDefaultOutputDir(project.layout.buildDirectory.dir("openapi").get())
                val task = project.tasks.register("generate${config.name.get().capitalize()}Clients", GenerateTask::class.java) { task ->
                    task.group = "openapi3"
                    task.packageName.convention(config.name)
                    task.inputSpec.set(config.inputSpec)
                    task.outputDir.set(config.outputDir)
                    task.packageName.set(config.packageName)
                }

                project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
                    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                    if (kotlinExtension != null) {
                        project.afterEvaluate {
                            kotlinExtension.sourceSets.getByName("commonMain").kotlin.srcDir(config.outputDir)
                            kotlinExtension.sourceSets.getByName("commonTest").kotlin.srcDir(config.outputDir)
                        }
                    }
                }
                project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                    project.extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.named("main") {
                        it.kotlin.srcDir(config.outputDir)
                    }
                }

                project.tasks.named("compileKotlin").configure {
                    it.dependsOn(task.get())
                }
            }
        }
    }
}

abstract class GenerateTask: DefaultTask() {
    /**
     * OpenAPI3 specification file (json).
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputSpec: RegularFileProperty

    /**
     * Where generated code will be written.
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    fun setDefaultOutputDir(buildDir: Directory) {
        outputDir.convention(buildDir)
    }

    @TaskAction
    fun generate() {
        val outputDir = outputDir.get().asFile
        val spec = inputSpec.get().asFile
        Cli.main(arrayOf("-i", spec.absolutePath, "-o", outputDir.absolutePath, "-b", packageName.get()))
    }
}

abstract class OpenApiPluginExtension @Inject constructor(private val objects: ObjectFactory) {
    val generators: ListProperty<Generator> = objects.listProperty(Generator::class.java)


    abstract class Generator @Inject constructor(objects: ObjectFactory, name: String) {
        /**
         * OpenAPI3 specification file (json).
         */
        val inputSpec: RegularFileProperty = objects.fileProperty()

        /**
         * Where generated code will be written.
         */
        val outputDir: DirectoryProperty = objects.directoryProperty()

        val packageName: Property<String> = objects.property(String::class.java)
        val name: Property<String> = objects.property(String::class.java)

        init {
            this.name.set(name)
            this.packageName.convention(name)
        }

        fun setDefaultOutputDir(buildDir: Directory) {
            outputDir.convention(buildDir)
        }
    }

    fun addGenerator(name: String, configure: Generator.() -> Unit) {
        val generator = objects.newInstance(Generator::class.java, name)
        generator.configure()
        generators.add(generator)
    }
}
