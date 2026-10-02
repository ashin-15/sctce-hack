plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    explicitApi()
}

dependencies {
    api(project(":core:model"))
    testFixturesApi(project(":core:model"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
