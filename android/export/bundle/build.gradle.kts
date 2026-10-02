plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("org.sakshi.export.bundle.VerifierCli")
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    explicitApi()
}

dependencies {
    api(project(":core:model"))
    api(project(":core:integrity"))
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    systemProperty("sakshi.fixtures", rootProject.layout.projectDirectory.dir("testfixtures").asFile.absolutePath)
    systemProperty("sakshi.sample", layout.buildDirectory.dir("sample-bundle").get().asFile.absolutePath)
}
