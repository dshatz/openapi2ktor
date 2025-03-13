
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.local.plugin)
}

val input = "${project.projectDir}/src/test/resources/spot_api.yaml"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.10-1.0.30")
    implementation(libs.bundles.ktor)
    testImplementation(libs.ktor.mock)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.0")
    testImplementation(kotlin("test"))
}

openapi3 {
    addGenerator("binance") {
        inputSpec.set(layout.projectDirectory.file("src/test/resources/spot_api.yaml"))
    }
}
