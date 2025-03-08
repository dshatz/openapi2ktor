import com.vanniktech.maven.publish.SonatypeHost

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    alias(libs.plugins.vanniktech.mavenPublish)
    `java-gradle-plugin`
}

group = "com.dshatz"
version = "1.0.0"

dependencies {
    implementation("com.reprezen.kaizen:openapi-parser:4.0.4") { exclude(group = "junit") }
    implementation("com.reprezen.jsonoverlay:jsonoverlay:4.0.4")
    implementation("com.squareup:kotlinpoet:1.14.1")
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.10-1.0.30")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
    implementation("net.pwall.mustache:kotlin-mustache:0.12")
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")


    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.0")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    // Define the plugin
    val openapi2ktor by plugins.creating {
        id = "com.dshatz.openapi2ktor.plugin"
        implementationClass = "com.dshatz.openapi2ktor.plugin.Plugin"
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

//    signAllPublications()

    coordinates(group.toString(), "openapi2ktor", version.toString())

    pom {
        name = "OpenApi3 to Ktor"
        description = "Ktor client and kotlin model generator."
        inceptionYear = "2025"
        url = "https://github.com/dshatz/openapi2ktor/"
        licenses {
            license {
                name = "GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007"
                url = "https://github.com/dshatz/openapi2ktor/blob/main/LICENSE"
            }
        }
        developers {
            developer {
                id = "dshatz"
                name = "Daniels Šatcs"
                email = "dev@dshatz.com"
            }
        }
        scm {
            url = "https://github.com/dshatz/openapi2ktor"
            connection = "git@github.com:dshatz/openapi2ktor.git"
        }
    }
}