plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.sakshi.processing.stt"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // arm64 only: whisper.cpp is built for real phones. The library does not load on x86_64 emulators.
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
                targets += "sakshi_stt"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric touches java.io.FileDescriptor and JDK access internals when it simulates API 36.
                it.jvmArgs(
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                )
            }
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    explicitApi()
}

// The build never downloads whisper.cpp; it fails clearly when the explicit preparation step has not been run.
val whisperSource = rootProject.layout.projectDirectory.dir("third_party/whisper.cpp")
val requireWhisperSource = tasks.register("requireWhisperSource") {
    val marker = whisperSource.file("CMakeLists.txt").asFile
    inputs.property("markerPath", marker.path)
    doLast {
        if (!marker.isFile) {
            throw GradleException(
                "whisper.cpp source is missing at ${whisperSource.asFile}. " +
                    "Run android/tools/prepare-whisper.sh once (it is the only network step); the build never downloads.",
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(requireWhisperSource) }

dependencies {
    api(project(":core:model"))
    api(project(":core:crypto"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
