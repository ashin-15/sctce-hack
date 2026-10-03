import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Fails the build when the merged manifest requests a permission that is not on the allowlist, or when a component
 * other than the allowlisted ones is exported.
 */
abstract class VerifyManifestPermissions : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val allowed: ListProperty<String>

    @get:Input
    abstract val allowedExported: ListProperty<String>

    @get:Input
    abstract val systemBoundServices: MapProperty<String, String>

    @TaskAction
    fun verify() {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(mergedManifest.get().asFile)
        val namespace = "http://schemas.android.com/apk/res/android"
        val requested = document.getElementsByTagName("uses-permission").let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as org.w3c.dom.Element).getAttributeNS(namespace, "name") }
        }
        val unexpected = requested.filterNot { it in allowed.get() }
        check(unexpected.isEmpty()) { "Merged manifest requests permissions outside the allowlist: $unexpected" }
        requested.sorted().forEach { logger.lifecycle("uses-permission: $it") }
        val exported = listOf("activity", "activity-alias", "service", "receiver", "provider").flatMap { tag ->
            document.getElementsByTagName(tag).let { nodes ->
                (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            }
        }.filter { it.getAttributeNS(namespace, "exported") == "true" }.map { it.getAttributeNS(namespace, "name") }
        val unexported = exported.filterNot { it in allowedExported.get() }
        check(unexported.isEmpty()) { "Merged manifest exports components outside the allowlist: $unexported" }
        exported.sorted().forEach { logger.lifecycle("exported component: $it") }
        val services = document.getElementsByTagName("service").let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
        }
        systemBoundServices.get().forEach { (name, bindingPermission) ->
            val service = services.single { it.getAttributeNS(namespace, "name") == name }
            check(service.getAttributeNS(namespace, "permission") == bindingPermission) { "Service $name lost its system binding permission" }
            check(service.getAttributeNS(namespace, "enabled") == "false") { "Service $name must be disabled before explicit consent" }
        }
    }
}

android {
    namespace = "org.sakshi.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.sakshi.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
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
                it.systemProperty("sakshi.stringsXml", layout.projectDirectory.file("src/main/res/values/strings.xml").asFile.path)
            }
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:integrity"))
    implementation(project(":core:vault"))
    implementation(project(":acquisition:importer"))
    implementation(project(":acquisition:notifications"))
    implementation(project(":acquisition:accessibility"))
    implementation(project(":acquisition:projection"))
    implementation(project(":processing:analysis"))
    implementation(project(":processing:ocr"))
    implementation(project(":processing:stt"))
    implementation(project(":processing:llm"))
    implementation(project(":export:report"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

val verifyManifestPermissions = tasks.register<VerifyManifestPermissions>("verifyManifestPermissions") {
    description = "Fails if the merged debug manifest requests a permission or exports a component outside the allowlist."
    group = "verification"
    mergedManifest.set(layout.buildDirectory.file("intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"))
    allowed.set(
        listOf(
            "android.permission.USE_BIOMETRIC",
            "android.permission.USE_FINGERPRINT",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "org.sakshi.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        ),
    )
    allowedExported.set(
        listOf(
            "org.sakshi.app.MainActivity",
            "org.sakshi.app.ShareTargetActivity",
            "androidx.profileinstaller.ProfileInstallReceiver",
            "org.sakshi.acquisition.notifications.SakshiNotificationListener",
            "org.sakshi.acquisition.accessibility.SakshiVisibleTextService",
        ),
    )
    systemBoundServices.set(
        mapOf(
            "org.sakshi.acquisition.notifications.SakshiNotificationListener" to "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            "org.sakshi.acquisition.accessibility.SakshiVisibleTextService" to "android.permission.BIND_ACCESSIBILITY_SERVICE",
        ),
    )
    dependsOn("processDebugMainManifest")
}

tasks.named("check") {
    dependsOn(verifyManifestPermissions)
}
