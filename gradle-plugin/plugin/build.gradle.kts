import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "com.dshatz.openapi2ktor"
version = project.findProperty("version") as? String ?: "0.1.0-SNAPSHOT1"

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    // Define the plugin
    val openapi2ktor by plugins.creating {
        id = "com.dshatz.openapi2ktor"
        implementationClass = "com.dshatz.openapi2ktor.plugin.Plugin"
    }
}

dependencies {
    implementation(project(":processor"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
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