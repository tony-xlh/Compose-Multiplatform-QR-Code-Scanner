import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinCocoapods)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }


    cocoapods {
        // Required fields
        version = "1.0"
        summary = "CocoaPods test library"
        homepage = "https://github.com/JetBrains/kotlin"
        ios.deploymentTarget = "15.0"
        // Specify path to Podfile
        podfile = project.file("../iosApp/Podfile")

        framework {
            baseName = "ComposeApp"
            isStatic = true
        }

        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
    }

    // Dynamsoft Barcode Reader v11 is installed via CocoaPods (see the Podfile) and its
    // XCFrameworks are consumed through Kotlin/Native cinterop.
    // DynamsoftBarcodeReaderBundle.h re-exports the Capture Vision Bundle headers,
    // so both frameworks must be on the compiler/linker search paths.
    val dynamsoftPodRoot = rootProject.projectDir.resolve(
        "iosApp/Pods/DynamsoftBarcodeReaderBundle/DynamsoftBarcodeReaderBundle.xcframework"
    )
    val dynamsoftCaptureVisionPodRoot = rootProject.projectDir.resolve(
        "iosApp/Pods/DynamsoftCaptureVisionBundle/DynamsoftCaptureVisionBundle.xcframework"
    )

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }

        // Pick the XCFramework slice matching this target.
        val xcframeworkSlice = when (iosTarget.name) {
            "iosArm64" -> "ios-arm64"
            else -> "ios-arm64_x86_64-simulator"
        }
        val frameworkDir = dynamsoftPodRoot.resolve(xcframeworkSlice)
        val captureVisionDir = dynamsoftCaptureVisionPodRoot.resolve(xcframeworkSlice)

        iosTarget.compilations.getByName("main") {
            cinterops {
                val dynamsoftBarcodeReader by creating {
                    defFile(rootProject.projectDir.resolve("iosApp/SDK/DynamsoftBarcodeReaderBundle.def"))
                    // The Dynamsoft framework headers use clang modules (@import), so -fmodules is required.
                    compilerOpts(
                        "-fmodules",
                        "-framework", "DynamsoftBarcodeReaderBundle",
                        "-framework", "DynamsoftCaptureVisionBundle",
                        "-F", frameworkDir.absolutePath,
                        "-F", captureVisionDir.absolutePath
                    )
                }
            }
        }
        iosTarget.binaries.all {
            linkerOpts(
                "-framework", "DynamsoftBarcodeReaderBundle",
                "-framework", "DynamsoftCaptureVisionBundle",
                "-F", frameworkDir.absolutePath,
                "-F", captureVisionDir.absolutePath
            )
        }
    }
    
    sourceSets {

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.accompanist.permissions)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.dynamsoft.barcode.reader)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
    }
}

android {
    namespace = "org.example.project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.example.project"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.material3.android)
    debugImplementation(compose.uiTooling)
}

