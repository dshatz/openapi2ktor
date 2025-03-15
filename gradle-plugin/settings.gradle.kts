pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        kotlin("jvm").version("2.1.10") apply false
        kotlin("multiplatform").version("2.1.10") apply false
        id("com.google.devtools.ksp") version "2.1.10-1.0.30" apply false
        kotlin("plugin.serialization") version "2.1.10" apply false
        id("com.android.library") version "8.2.2" apply false
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include(":processor")
include(":plugin")
include(":runtime")
