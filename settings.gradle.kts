pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        kotlin("jvm").version(extra["kotlin.version"] as String) apply false
        kotlin("plugin.serialization") version(extra["kotlin.version"] as String) apply false
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

includeBuild("gradle-plugin")


