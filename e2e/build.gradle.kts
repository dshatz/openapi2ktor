import com.google.devtools.ksp.gradle.KspTask

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

val input = "${project.rootProject.projectDir}/e2e/src/test/resources/sample.yaml"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.10-1.0.30")
    kspTest(project(":processor"))
    testImplementation(kotlin("test"))
}

ksp {
    arg("openApiFile", input)
}

tasks {
    /*fun createGenerateCodeTask(name: String, apiFilePath: String) =
        create(name, JavaExec::class) {
            inputs.files(file(apiFilePath))
            outputs.dir(generationDir)
            outputs.cacheIf { true }
            classpath = rootProject.files("./build/libs/openapi2ktor-${rootProject.version}.jar")
            mainClass.set("com.dshatz.openapi2ktor.Cli")
            args = listOf(
                "-o", generationDir,
                "-i", apiFilePath,
            )
            dependsOn(":jar")
        }

    val generateCodeTask = createGenerateCodeTask(
        "generateCode",
        "$projectDir/openapi/sample.yaml",
    )

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        dependsOn(generateCodeTask)
    }*/

    withType<KspTask> {
//        dependsOn(processTestResources)
        inputs.file(input)
    }

}