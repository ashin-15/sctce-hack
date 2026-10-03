package org.sakshi.acquisition.notifications

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lane is read-only: it never acts on, dismisses, snoozes or opens anything it observes, and it reads text fields
 * only. This scan fails the build if main sources or the manifest mention such an API. Comments are not exempt, so
 * the main sources avoid the words altogether.
 */
class ReadOnlySourceScanTest {
    private val forbidden: List<Pair<String, Regex>> = listOf(
        "cancelNotification" to Regex("""\bcancelNotifications?\b"""),
        "cancelAllNotifications" to Regex("""\bcancelAllNotifications\b"""),
        "snoozeNotification" to Regex("""\bsnoozeNotification\b"""),
        "setNotificationsShown" to Regex("""\bsetNotificationsShown\b"""),
        "PendingIntent" to Regex("""\bPendingIntent\b"""),
        "RemoteInput" to Regex("""\bRemoteInput\b"""),
        "RemoteViews" to Regex("""\bRemoteViews\b"""),
        "Notification.Action" to Regex("""\bNotification\.Action\b|\.actions\b|\bcontentIntent\b|\bdeleteIntent\b"""),
        "content URI" to Regex("""content://|\bContentResolver\b|\bgetDataUri\b|\bdataUri\b"""),
        "Uri" to Regex("""\bandroid\.net\.Uri\b"""),
        "icons and pictures" to Regex(
            """\bIcon\b|\bBitmap\b|\bDrawable\b|\bEXTRA_PICTURE|\bEXTRA_LARGE_ICON|\bEXTRA_SMALL_ICON|\bEXTRA_BACKGROUND_IMAGE_URI|\bgetLargeIcon\b|\bgetSmallIcon\b""",
        ),
        "logging" to Regex("""\bandroid\.util\.Log\b|\bLog\.[dviwe]\(|\bprintln\(|\bprintStackTrace\b"""),
        "network" to Regex("""java\.net\.|javax\.net\.|okhttp3|retrofit2|io\.ktor|android\.net\.http|\bWebView\b"""),
        "boot, foreground service, own notifications" to Regex(
            """RECEIVE_BOOT_COMPLETED|BOOT_COMPLETED|FOREGROUND_SERVICE|foregroundServiceType|\bstartForeground\b|POST_NOTIFICATIONS|\bNotificationManager\.notify\b|\.notify\(""",
        ),
        "permission INTERNET" to Regex("""android\.permission\.INTERNET"""),
    )

    private fun mainFiles(): List<File> {
        val root = File("src/main")
        assertTrue(root.isDirectory, "src/main not found from ${File(".").absolutePath}")
        return root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml", "java") }.toList()
    }

    private fun scan(files: List<Pair<String, String>>): List<String> =
        files.flatMap { (name, text) ->
            text.lines().flatMapIndexed { index, line ->
                forbidden.filter { (_, regex) -> regex.containsMatchIn(line) }.map { (label, _) -> "$name:${index + 1}: $label" }
            }
        }

    @Test
    fun `main sources and manifest use no action, dismissal, uri, view, picture, logging or network api`() {
        val files = mainFiles()
        assertTrue(files.any { it.name == "AndroidManifest.xml" })
        assertTrue(files.count { it.extension == "kt" } >= 10)
        val findings = scan(files.map { it.path to it.readText() })
        assertEquals(emptyList(), findings)
    }

    @Test
    fun `the scanner catches each forbidden token so a clean result is not vacuous`() {
        val samples = listOf(
            "cancelNotification(key)", "cancelAllNotifications()", "snoozeNotification(key, 5)", "setNotificationsShown(arrayOf())",
            "pendingIntent: PendingIntent", "RemoteInput.getResultsFromIntent", "RemoteViews(pkg, 1)", "notification.actions",
            "Notification.Action", "val u = \"content://x\"", "sbn.notification.getLargeIcon()", "android.graphics.drawable.Icon",
            "Log.d(tag, m)", "println(x)", "import java.net.URL", "android.permission.RECEIVE_BOOT_COMPLETED",
            "FOREGROUND_SERVICE", "manager.notify(1, n)",
        )
        samples.forEach { sample -> assertTrue(scan(listOf("sample" to sample)).isNotEmpty(), "not caught: $sample") }
    }

    @Test
    fun `the manifest declares the bound listener as required`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE""""))
        assertTrue(manifest.contains("""android:exported="true""""))
        assertTrue(manifest.contains("""android:enabled="false""""))
        assertTrue(manifest.contains("android.service.notification.NotificationListenerService"))
        assertEquals(1, Regex("<service").findAll(manifest).count())
        assertTrue(!manifest.contains("<uses-permission"))
    }
}
