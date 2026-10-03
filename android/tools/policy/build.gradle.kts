plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}

// The checks read the whole source tree, which Gradle does not see as test inputs; never skip them as up to date.
tasks.test {
    outputs.upToDateWhen { false }
}
