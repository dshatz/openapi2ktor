plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.serial) apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}

nexusPublishing {
    repositories {
        sonatype {
            username.set(System.getenv("mavenUsername"))
            password.set(System.getenv("mavenPassword"))
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}


