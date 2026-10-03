package org.sakshi.tools.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rule 2: no networking stack, analytics, crash reporting, ads, network-caching image loader or cloud AI SDK in
 * any build file or in the version catalog. ML Kit's bundled (on-device) text recognition is allowed.
 */
internal object ForbiddenDependencyScanner {
    private fun artifact(label: String) =
        Pattern(label, Regex("""(?<![a-z])${Regex.escape(label)}""", RegexOption.IGNORE_CASE))

    val PATTERNS: List<Pattern> = listOf(
        "okhttp", "retrofit", "ktor", "volley",
        "firebase", "crashlytics", "sentry", "play-services-ads", "play-services-measurement", "appcenter",
        "bugsnag", "mixpanel", "amplitude", "google-services",
        "coil", "glide", "picasso",
        "generativeai", "openai", "anthropic", "vertex",
    ).map(::artifact)

    val KNOWN: List<KnownFinding> = emptyList()

    fun scan(file: RepoFile): List<Finding> = scanLines(file.path, file.text, PATTERNS)
}

class ForbiddenDependencyCheck {
    @Test
    fun `build files and the version catalog declare no forbidden dependency`() {
        assertTrue(Repo.buildFiles.any { it.path == "gradle/libs.versions.toml" }, "version catalog not found")
        val findings = Repo.buildFiles.flatMap { ForbiddenDependencyScanner.scan(it) }
        assertNoProblems(reconcile("forbidden dependency", findings, ForbiddenDependencyScanner.KNOWN))
    }
}

class ForbiddenDependencyScannerTest {
    private fun scan(text: String) = ForbiddenDependencyScanner.scan(RepoFile("m/build.gradle.kts", text))

    @Test
    fun `each forbidden dependency family is caught`() {
        val lines = listOf(
            "implementation(\"com.squareup.okhttp3:okhttp:4.12.0\")", "implementation(libs.retrofit)",
            "implementation(\"io.ktor:ktor-client-core:2.0\")", "implementation(\"com.android.volley:volley:1\")",
            "implementation(\"com.google.firebase:firebase-analytics\")", "implementation(libs.crashlytics)",
            "implementation(\"io.sentry:sentry-android:7\")", "implementation(\"com.google.android.gms:play-services-ads:23\")",
            "implementation(\"com.google.android.gms:play-services-measurement:23\")",
            "implementation(\"com.microsoft.appcenter:appcenter:5\")", "implementation(\"com.bugsnag:bugsnag-android:6\")",
            "implementation(\"com.mixpanel.android:mixpanel-android:7\")", "implementation(\"com.amplitude:android-sdk:1\")",
            "implementation(\"io.coil-kt:coil-compose:2\")", "implementation(\"com.github.bumptech.glide:glide:4\")",
            "implementation(\"com.squareup.picasso:picasso:2\")", "implementation(\"com.google.ai.client.generativeai:generativeai:0.9\")",
            "implementation(\"com.aallam.openai:openai-client:3\")", "implementation(\"com.anthropic:anthropic-java:1\")",
            "implementation(\"com.google.cloud:vertexai:1\")",
        )
        lines.forEach { assertTrue(scan(it).isNotEmpty(), "not caught: $it") }
    }

    @Test
    fun `allowed dependencies pass including the bundled ML Kit recognizer`() {
        val clean = """
            implementation(libs.mlkit.text.recognition)
            implementation("com.google.mlkit:text-recognition:16.0.1")
            implementation(libs.androidx.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
        """.trimIndent()
        assertEquals(emptyList(), scan(clean))
    }

    @Test
    fun `the version catalog is scanned the same way`() {
        val finding = ForbiddenDependencyScanner.scan(RepoFile("gradle/libs.versions.toml", "okhttp = \"4.12.0\"")).single()
        assertEquals("okhttp", finding.pattern)
    }
}
