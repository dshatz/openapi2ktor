import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

/*android {
    namespace = "com.dshatz.openapi2ktor.runtime"
}*/

kotlin {
    jvmToolchain(17)
    jvm()
//    androidTarget()

    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    mingwX64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()
    js()
    androidNativeX86()
    androidNativeX64()
    androidNativeArm32()
    androidNativeArm64()

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    watchosX64()
    watchosArm32()
    watchosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.serial)
        }
    }

}
