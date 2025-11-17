plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.local.plugin)
}

val input = "${project.projectDir}/src/test/resources/swagger.yml"

dependencies {
    implementation(libs.serial)
    implementation(libs.bundles.ktor)
    testImplementation(libs.ktor.mock)
    testImplementation(libs.coroutines.test)
    testImplementation(kotlin("test"))
}

openapi3 {
    generators {
        create("tvdb") {
            inputSpec.set(layout.projectDirectory.file("src/test/resources/swagger.yml"))
        }
    }
}
