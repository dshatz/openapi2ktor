
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    id("com.gradleup.shadow") version "9.0.0-beta10"
//    alias(libs.plugins.vanniktech.mavenPublish)
    `maven-publish`
}

gradlePlugin {
    // Define the plugin
    val openapi2ktor by plugins.creating {
        id = "com.dshatz.openapi2ktor"
        implementationClass = "com.dshatz.openapi2ktor.plugin.Plugin"
    }
}

group = "com.dshatz.openapi2ktor"
version = project.findProperty("version") as String? ?: "0.1.0-SNAPSHOT1"

kotlin {
    jvmToolchain(17)
}

tasks.shadowJar {
    archiveClassifier.set("") // Remove "-all" from the JAR name
//    exclude("processor")
}


publishing {
    publications {
        create<MavenPublication>("shadow") {
            from(components["shadow"])
        }
    }
    repositories {
        mavenLocal()
        maven {
            name = "MavenCentral"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

            credentials {
                username = System.getenv("mavenUsername") ?: ""
                password = System.getenv("mavenPassword") ?: ""
            }
        }
    }
}

tasks.getByName("generateMetadataFileForShadowPublication").dependsOn(tasks.jar)

dependencies {
    implementation(project(":processor"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
}