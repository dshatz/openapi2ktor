plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.serial)
}

val input = "${project.projectDir}/src/test/resources/github.yaml"

dependencies {
    implementation(libs.serial)
//    kspTest(project(":processor"))
    implementation(libs.bundles.ktor)
    testImplementation(kotlin("test"))
}


/*
ksp {
    arg("openApiFile", input)
}

tasks {
fun createGenerateCodeTask(name: String, apiFilePath: String) =
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
    }


    withType<KspTask> {
//        dependsOn(processTestResources)
        inputs.file(input)
    }

}
*/
