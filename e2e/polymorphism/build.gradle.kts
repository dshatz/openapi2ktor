plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.local.plugin)
}


dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation(libs.bundles.ktor)
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.10-1.0.30")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.0")
    testImplementation("io.github.denisbronx.netmock:netmock-engine:0.7.0")
    testImplementation(kotlin("test"))
}

openapi3 {
    /*addGenerator("simple") {
        *//*openApiFilePath = "src/test/resources/sample.yaml"
        basePackageName = "com.example.sample"*//*
    }*/
}
