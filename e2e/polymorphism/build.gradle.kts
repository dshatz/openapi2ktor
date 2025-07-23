import com.dshatz.openapi2ktor.DateLibrary

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.serial)
    alias(libs.plugins.local.plugin)
}


dependencies {
    implementation(libs.serial)
    implementation(libs.bundles.ktor)
    testImplementation(libs.coroutines.test)
    testImplementation("io.github.denisbronx.netmock:netmock-engine:0.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    testImplementation(kotlin("test"))
}

openapi3 {
    generators {
        create("sample") {
            inputSpec.set(layout.projectDirectory.file("src/test/resources/sample.yaml"))
            config {
                parseUnknownProps {
                    urlStartsWith("/users")
                }
                dateLibrary(DateLibrary.JavaTime)
            }
        }

        create("sampleKotlinxDatetime") {
            inputSpec.set(layout.projectDirectory.file("src/test/resources/sample.yaml"))
            config {
                parseUnknownProps {
                    urlStartsWith("/users")
                }
                dateLibrary(DateLibrary.KotlinxDatetime)
            }
        }
    }
}
