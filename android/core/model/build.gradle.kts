plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    explicitApi()
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.json.schema.validator)
}

tasks.test {
    systemProperty("sakshi.fixtures", rootProject.layout.projectDirectory.dir("testfixtures").asFile.absolutePath)
    systemProperty(
        "sakshi.eventSchema",
        rootProject.layout.projectDirectory.file("../data/sakshi-event-schema.json").asFile.absolutePath,
    )
}
