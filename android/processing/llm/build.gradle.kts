plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.sakshi.processing.llm"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
                targets += "sakshi_llm"
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
                it.jvmArgs(
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                )
            }
        }
    }
}

val llamaSource = rootProject.layout.projectDirectory.dir("third_party/llama.cpp")
val requireLlamaSource = tasks.register("requireLlamaSource") {
    val lockFile = llamaSource.file(".sakshi-source-lock").asFile
    inputs.file(lockFile)
    doLast {
        val commitLine = "commit=a7a98e0fffed794396b3fbad4dcdbbc184963645"
        val treeLine = "tree=b611e5e7692c49a935fa09fc9c90a6512466db09"
        if (!lockFile.isFile || lockFile.readLines().none { it == commitLine } || lockFile.readLines().none { it == treeLine }) {
            throw GradleException(
                "Pinned llama.cpp source is missing or has the wrong identity at ${llamaSource.asFile}. " +
                    "Run android/tools/prepare-llama.sh; the build never downloads source or model weights.",
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(requireLlamaSource) }

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    explicitApi()
}

dependencies {
    api(project(":core:model"))
    api(project(":core:integrity"))
    implementation(project(":processing:analysis"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
