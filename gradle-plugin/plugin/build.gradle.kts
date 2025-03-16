
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
version = "1.0.0-SNAPSHOT1"

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
    }
}

tasks.getByName("generateMetadataFileForShadowPublication").dependsOn(tasks.jar)

dependencies {
    implementation(project(":processor"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
}

/*mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

//    signAllPublications()

    coordinates(group.toString(), "openapi2ktor", version.toString())

    configure(GradlePlugin(JavadocJar.None()))

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
        withXml {
            val dependenciesNode = asNode().get("dependencies") as? groovy.util.Node
            dependenciesNode?.children()?.removeIf { dep ->
                dep is groovy.util.Node &&
                        dep.children().find { it is groovy.util.Node && it.name() == "artifactId" }?.let {
                            (it as groovy.util.Node).text() == "processor"
                        } ?: false
            }
        }
    }
}*/
