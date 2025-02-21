pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        kotlin("jvm").version(extra["kotlin.version"] as String) apply false
        id("com.google.devtools.ksp") version "2.1.10-1.0.30" apply false
        kotlin("plugin.serialization") version "2.1.10" apply false
        id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }
}

rootProject.name = "openapi2ktor"
include(":e2e:polymorphism")
include(":e2e:binance")
include(":e2e:github")
include(":processor")