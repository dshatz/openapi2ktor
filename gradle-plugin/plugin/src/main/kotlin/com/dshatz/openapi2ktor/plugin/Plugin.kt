package com.dshatz.openapi2ktor.plugin

import com.dshatz.openapi2ktor.Cli
import com.dshatz.openapi2ktor.GeneratorConfig
import com.dshatz.openapi2ktor.AdditionalPropsConfig
import com.dshatz.openapi2ktor.utils.capitalize
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectList
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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject

class Plugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("openapi3", OpenApiPluginExtension::class.java)

        project.afterEvaluate {
            extension.generators.names.all { generatorName ->
                val generatorExtension = extension.generators.getByName(generatorName)
                generatorExtension.setDefaultOutputDir(project.layout.buildDirectory.dir("openapi").get())
                /*generatorExtension.config.convention(project.objects.newInstance(Generator.GeneratorConfigExtension::class.java, generatorExtension.name))*/
                val task = project.tasks.register("generate${generatorExtension.name.capitalize()}Clients", GenerateTask::class.java) { task ->
                    task.group = "openapi3"
                    task.generatorConfig.set(generatorExtension.config.get().makeConfig())
                    task.inputSpec.set(generatorExtension.inputSpec)
                    task.outputDir.set(generatorExtension.outputDir)
                }

                project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
                    val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                    if (kotlinExtension != null) {
                        project.afterEvaluate {
                            kotlinExtension.sourceSets.getByName("commonMain").kotlin.srcDir(generatorExtension.outputDir.dir("src/main/kotlin"))
                            kotlinExtension.sourceSets.getByName("commonTest").kotlin.srcDir(generatorExtension.outputDir.dir("src/main/kotlin"))
                        }
                    }
                }
                project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                    project.extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.named("main") {
                        it.kotlin.srcDir(generatorExtension.outputDir)
                    }
                }
                project.tasks.withType(KotlinCompile::class.java).configureEach {
                    it.dependsOn(task.get())
                }
                true
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
    abstract val generatorConfig: Property<GeneratorConfig>

    @TaskAction
    fun generate() {
        val outputDir = outputDir.get().asFile
        val spec = inputSpec.get().asFile
        val config = generatorConfig.get()
        Cli.runWithParams(spec.absolutePath, outputDir.absolutePath, config)
    }
}

abstract class OpenApiPluginExtension @Inject constructor(project: Project, objects: ObjectFactory) {
    val generators: NamedDomainObjectContainer<Generator> = project.container(Generator::class.java) {
        name -> objects.newInstance(Generator::class.java, objects, name)
    }
}

abstract class Generator @Inject constructor(
    private val objects: ObjectFactory,
    private val name: String
): Named {

    override fun getName(): String = name

    /**
     * OpenAPI3 specification file (json).
     */
    val inputSpec: RegularFileProperty = objects.fileProperty()

    /**
     * Where generated code will be written.
     */
    val outputDir: DirectoryProperty = objects.directoryProperty()
    @Nested
    val config: Property<GeneratorConfigExtension> = objects.property(GeneratorConfigExtension::class.java)

    init {
        config.convention(objects.newInstance(GeneratorConfigExtension::class.java, name))
    }

    fun setDefaultOutputDir(buildDir: Directory) {
        outputDir.convention(buildDir)
    }

    fun config(action: Action<GeneratorConfigExtension>) {
        action.execute(config.get())
    }

    abstract class GeneratorConfigExtension @Inject constructor(objects: ObjectFactory, name: String) {
        @get:Nested
        internal val additionalPropsConfig: Property<AdditionalPropsConfigExtension> = objects.property(AdditionalPropsConfigExtension::class.java)
        internal val basePackage: Property<String> = objects.property(String::class.java)

        init {
            additionalPropsConfig.convention(objects.newInstance(AdditionalPropsConfigExtension::class.java))
            basePackage.convention(name)
        }

        fun packageName(pkg: String) {
            basePackage.set(pkg)
        }

        fun parseUnknownProps(action: Action<AdditionalPropsConfigExtension>) {
            action.execute(additionalPropsConfig.get())
        }
    }
}

abstract class AdditionalPropsConfigExtension @Inject constructor(objects: ObjectFactory) {
    internal val additionalPropPatterns: ListProperty<String> = objects.listProperty(String::class.java)

    init {
        additionalPropPatterns.convention(emptyList())
    }

    fun urlStartsWith(prefix: String) {
        regex("$prefix.*")
    }
    fun urlEndsWith(suffix: String) {
        regex(".*$suffix")
    }
    fun urlIs(url: String) {
        regex("^$url\$")
    }
    fun regex(url: String) {
        additionalPropPatterns.add(url)
    }
    fun all() {
        regex(".*")
    }
}

internal fun AdditionalPropsConfigExtension.makePropConfigExtension(): AdditionalPropsConfig {
    return object: AdditionalPropsConfig {
        override val additionalPropPatterns: List<String> =
            this@makePropConfigExtension.additionalPropPatterns.get()
    }
}

internal fun Generator.GeneratorConfigExtension.makeConfig(): GeneratorConfig {
    return object: GeneratorConfig {
        override val additionalPropsConfig: AdditionalPropsConfig = this@makeConfig.additionalPropsConfig.get().makePropConfigExtension()
        override val basePackage: String = this@makeConfig.basePackage.get()
    }
}
