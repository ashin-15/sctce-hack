package org.sakshi.tools.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rule 1: Sakshi must not use APIs or components that read other apps' data, watch the screen, reach the network
 * or load web content (AGENTS.md "Do Not Build", megaplan 224-227 and 895-899).
 * Comments are NOT exempt: a forbidden token in KDoc or an XML comment is reported too, so the rule stays simple
 * and a comment cannot hide a real usage on the same line.
 */
internal object ForbiddenApiScanner {
    private fun word(label: String) = Pattern(label, Regex("""\b${Regex.escape(label)}\b"""))
    private fun text(label: String) = Pattern(label, Regex(Regex.escape(label)))

    /** Patterns checked in Kotlin, Java and XML main sources. */
    val COMMON: List<Pattern> = listOf(
        word("AccessibilityService"), word("MediaProjection"), word("READ_SMS"), word("RECEIVE_SMS"),
        word("READ_CALL_LOG"), word("READ_CONTACTS"), word("MANAGE_EXTERNAL_STORAGE"),
        word("READ_EXTERNAL_STORAGE"), text("READ_MEDIA_"), word("QUERY_ALL_PACKAGES"),
        word("SYSTEM_ALERT_WINDOW"), text("java.net."), text("javax.net."), text("okhttp3"), text("retrofit2"),
        text("io.ktor"), text("android.net.http"), word("WebView"), word("DownloadManager"),
        word("NotificationListenerService"),
    )

    /** Patterns for code only. In XML the network permissions are judged per tag by [permissionTagFindings]. */
    val CODE_ONLY: List<Pattern> = listOf(
        Pattern("android.permission.INTERNET", Regex("""android\.permission\.INTERNET\b""")),
        Pattern("android.permission.ACCESS_NETWORK_STATE", Regex("""android\.permission\.ACCESS_NETWORK_STATE\b""")),
    )

    /**
     * Per-module exceptions, module directory to the pattern labels allowed there. Empty on purpose. When the optional
     * notification module is added, narrow the rule by adding for example `"acquisition/notification" to
     * setOf("NotificationListenerService")` here instead of loosening the pattern for every module.
     */
    val MODULE_EXCEPTIONS: Map<String, Set<String>> = mapOf(
        "acquisition/notifications" to setOf("NotificationListenerService"),
    )

    private val NETWORK_PERMISSIONS = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
    private val PERMISSION_TAG = Regex("""<uses-permission(?:-sdk-\d+)?\b[^>]*>""")
    private val REMOVE_NODE = Regex("""tools:node\s*=\s*"remove"""")

    /** Known real findings in code owned by other modules, awaiting a fix. */
    val KNOWN: List<KnownFinding> = emptyList()

    fun scan(file: RepoFile): List<Finding> {
        val patterns = if (file.extension == "xml") COMMON else COMMON + CODE_ONLY
        val excepted = MODULE_EXCEPTIONS[file.module].orEmpty()
        val lineFindings = scanLines(file.path, file.text, patterns).filter { it.pattern !in excepted }
        return lineFindings + if (file.extension == "xml") permissionTagFindings(file.path, file.text) else emptyList()
    }

    /** A network permission requested with `<uses-permission>`; a `tools:node="remove"` entry strips it and is fine. */
    fun permissionTagFindings(path: String, xml: String): List<Finding> =
        PERMISSION_TAG.findAll(xml).mapNotNull { tag ->
            val permission = NETWORK_PERMISSIONS.firstOrNull { it in tag.value }
            if (permission == null || REMOVE_NODE.containsMatchIn(tag.value)) {
                null
            } else {
                Finding(path, lineAt(xml, tag.range.first), permission, tag.value)
            }
        }.toList()
}

class ForbiddenApiCheck {
    @Test
    fun `main sources and manifests use no forbidden API or component`() {
        val sources = Repo.mainSources
        assertTrue(sources.size > 50, "too few main sources found (${sources.size}); is the scan root wrong?")
        assertTrue(sources.any { it.path == "processing/ocr/src/main/AndroidManifest.xml" }, "OCR manifest not scanned")
        val findings = sources.flatMap { ForbiddenApiScanner.scan(it) }
        assertNoProblems(reconcile("forbidden API or component", findings, ForbiddenApiScanner.KNOWN))
    }
}

class ForbiddenApiScannerTest {
    private fun kotlin(text: String) = ForbiddenApiScanner.scan(RepoFile("m/src/main/kotlin/A.kt", text))

    private fun manifest(text: String) = ForbiddenApiScanner.scan(RepoFile("m/src/main/AndroidManifest.xml", text))

    @Test
    fun `every forbidden token is caught in code`() {
        val snippets = listOf(
            "class S : AccessibilityService()", "val p = MediaProjection", "Manifest.permission.READ_SMS",
            "Manifest.permission.RECEIVE_SMS", "Manifest.permission.READ_CALL_LOG", "Manifest.permission.READ_CONTACTS",
            "Manifest.permission.MANAGE_EXTERNAL_STORAGE", "Manifest.permission.READ_EXTERNAL_STORAGE",
            "Manifest.permission.READ_MEDIA_IMAGES", "Manifest.permission.QUERY_ALL_PACKAGES",
            "Manifest.permission.SYSTEM_ALERT_WINDOW", "import java.net.URL", "import java.net.HttpURLConnection",
            "import java.net.Socket", "import javax.net.ssl.SSLContext", "import okhttp3.OkHttpClient",
            "import retrofit2.Retrofit", "import io.ktor.client.HttpClient", "import android.net.http.HttpEngine",
            "val v = WebView(context)", "val d: DownloadManager", "class L : NotificationListenerService()",
            "\"android.permission.INTERNET\"", "\"android.permission.ACCESS_NETWORK_STATE\"",
        )
        snippets.forEach { assertTrue(kotlin(it).isNotEmpty(), "not caught: $it") }
    }

    @Test
    fun `clean code and similar names pass`() {
        val clean = """
            import android.content.Intent
            val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
            class MyWebViewModelLike
            val net = java_net_value
        """.trimIndent()
        assertEquals(emptyList(), kotlin(clean))
    }

    @Test
    fun `a comment mentioning a forbidden API is flagged because comments are not exempt`() {
        val finding = kotlin("/** Never use MediaProjection here. */").single()
        assertEquals(1, finding.line)
        assertEquals("MediaProjection", finding.pattern)
    }

    @Test
    fun `network permission requests are flagged in a manifest`() {
        val xml = "<manifest>\n<uses-permission android:name=\"android.permission.INTERNET\" />\n</manifest>"
        val finding = manifest(xml).single()
        assertEquals(2, finding.line)
        assertEquals("android.permission.INTERNET", finding.pattern)
    }

    @Test
    fun `a tools node remove entry that strips a network permission is allowed`() {
        val xml = """
            <manifest xmlns:android="a" xmlns:tools="t">
                <uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
                <uses-permission
                    android:name="android.permission.ACCESS_NETWORK_STATE"
                    tools:node="remove" />
            </manifest>
        """.trimIndent()
        assertEquals(emptyList(), manifest(xml))
    }

    @Test
    fun `a manifest with other permissions passes and a forbidden one is caught`() {
        assertEquals(emptyList(), manifest("<uses-permission android:name=\"android.permission.USE_BIOMETRIC\" />"))
        assertTrue(manifest("<uses-permission android:name=\"android.permission.READ_SMS\" />").isNotEmpty())
    }

    @Test
    fun `a module exception can be added per module for the notification listener`() {
        val file = RepoFile("m/src/main/kotlin/A.kt", "class L : NotificationListenerService()")
        assertTrue(ForbiddenApiScanner.MODULE_EXCEPTIONS[file.module].orEmpty().isEmpty())
        assertEquals("m", file.module)
    }
}
