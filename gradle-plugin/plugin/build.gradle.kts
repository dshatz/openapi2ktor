plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

gradlePlugin {
    // Define the plugin
    val openapi2ktor by plugins.creating {
        id = "com.dshatz.openapi2ktor.plugin"
        implementationClass = "com.dshatz.openapi2ktor.plugin.Plugin"
    }
}

kotlin {
    jvmToolchain(17)
}


dependencies {
    implementation(project(":processor"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
}