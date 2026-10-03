package org.sakshi.app.screenshots

import android.app.Application
import android.app.Dialog
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assume
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDialog
import org.sakshi.app.ui.theme.SakshiTheme

/** Where the gallery writes its PNG files and the index page. */
internal val screenshotDirectory: File = File("build/screenshots")

/** One rendering variant: theme and font scale. */
internal data class Variant(val dark: Boolean, val fontScale: Float) {
    val suffix: String get() = "${if (dark) "dark" else "light"}-$fontScale"
}

internal val allVariants: List<Variant> = listOf(
    Variant(dark = false, fontScale = 1.0f),
    Variant(dark = true, fontScale = 1.0f),
    Variant(dark = false, fontScale = 1.5f),
    Variant(dark = true, fontScale = 1.5f),
)

/** True when the owner asked for the gallery with `SAKSHI_SCREENSHOTS=true`. */
internal fun screenshotsRequested(): Boolean = System.getenv("SAKSHI_SCREENSHOTS") == "true"

/** Skips the whole test, before any set-up of the test class runs, unless the gallery was asked for. */
private object OptIn : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            Assume.assumeTrue("Set SAKSHI_SCREENSHOTS=true to render the gallery", screenshotsRequested())
            base.evaluate()
        }
    }
}

/**
 * Renders composables to PNG files. [rules] must be the test class's rule: it skips the test unless the gallery was
 * requested, registers [ComponentActivity] for Robolectric (so the shipped manifest stays unchanged) and starts the
 * Compose rule.
 */
internal class GalleryHarness {
    private val compose: AndroidComposeTestRule<*, ComponentActivity> = createAndroidComposeRule<ComponentActivity>()

    val rules: RuleChain = RuleChain.outerRule(OptIn)
        .around(
            object : ExternalResource() {
                override fun before() {
                    val application = ApplicationProvider.getApplicationContext<Application>()
                    Shadows.shadowOf(application.packageManager)
                        .addActivityIfNotPresent(ComponentName(application.packageName, ComponentActivity::class.java.name))
                }
            },
        )
        .around(compose)

    /**
     * Renders [content] once per entry of [variants] and saves `<name>-<light|dark>-<scale>.png`. A screen that scrolls
     * is scrolled to its end and saved again as `<name>-<variant>-end.png`. [before] runs once after the first
     * composition, to open something the state alone cannot open (for example an expandable section).
     */
    fun shoot(
        name: String,
        variants: List<Variant> = allVariants,
        before: (AndroidComposeTestRule<*, ComponentActivity>) -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        screenshotDirectory.mkdirs()
        var current by mutableStateOf(variants.first())
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, current.fontScale)) {
                SakshiTheme(darkTheme = current.dark) { content() }
            }
        }
        compose.waitForIdle()
        before(compose)
        variants.forEach { variant ->
            current = variant
            compose.waitForIdle()
            save("$name-${variant.suffix}")
            if (scrollTo(SCROLL_FAR)) {
                save("$name-${variant.suffix}-end")
                scrollTo(-SCROLL_FAR)
            }
        }
        writeIndex()
    }

    /** Scrolls the first scrollable of the screen by [distance]. True when it moved. */
    private fun scrollTo(distance: Float): Boolean {
        val scrollables = compose.onAllNodes(hasScrollAction() and hasVerticalScroll())
        if (scrollables.fetchSemanticsNodes().isEmpty()) return false
        val node = scrollables.onFirst()
        val before = node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke()
        compose.runOnUiThread {
            node.fetchSemanticsNode().config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, distance)
        }
        compose.waitForIdle()
        val after = node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke()
        return before != null && after != null && before != after && distance > 0f
    }

    private fun save(fileName: String) {
        val activityView = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(activityView.width, activityView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        activityView.draw(canvas)
        val dialog: Dialog? = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
        if (dialog != null) {
            canvas.drawColor(SCRIM)
            val dialogView = dialog.window?.decorView
            if (dialogView != null && dialogView.width > 0 && dialogView.height > 0) {
                canvas.save()
                canvas.translate((bitmap.width - dialogView.width) / 2f, (bitmap.height - dialogView.height) / 2f)
                dialogView.draw(canvas)
                canvas.restore()
            }
        }
        File(screenshotDirectory, "$fileName.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encoding failed for $fileName" }
        }
    }

    private companion object {
        const val SCROLL_FAR = 100_000f
        val SCRIM: Int = Color.argb(110, 0, 0, 0)
    }
}

private fun hasVerticalScroll(): SemanticsMatcher =
    SemanticsMatcher("has a vertical scroll range") { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }

private val imageName = Regex("""^(.*)-(light|dark)-(1\.0|1\.5)(-end)?\.png$""")

/** Rewrites `index.html`: one row per screen, the four variants side by side, then the scrolled-to-end images. */
private fun writeIndex() {
    val files = screenshotDirectory.listFiles { file -> imageName.matches(file.name) }.orEmpty()
    val screens = files.groupBy { imageName.matchEntire(it.name)!!.groupValues[1] }.toSortedMap()
    val html = StringBuilder()
    html.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Sakshi screen gallery</title>")
    html.append("<style>body{font-family:sans-serif;margin:16px;background:#eee}h2{margin:28px 0 6px}")
    html.append(".row{display:flex;gap:10px;align-items:flex-start;overflow-x:auto}")
    html.append("figure{margin:0}figcaption{font-size:12px;color:#444}img{width:260px;border:1px solid #999}</style></head><body>")
    html.append("<h1>Sakshi screen gallery</h1><p>Synthetic state only. Variants: light or dark theme, font scale 1.0 or 1.5; end means scrolled to the bottom.</p>")
    screens.forEach { (screen, images) ->
        html.append("<h2>").append(screen).append("</h2><div class=\"row\">")
        images.sortedBy { it.name }.sortedBy { if (imageName.matchEntire(it.name)!!.groupValues[4].isEmpty()) 0 else 1 }.forEach {
            html.append("<figure><a href=\"").append(it.name).append("\"><img loading=\"lazy\" src=\"").append(it.name).append("\"></a>")
            html.append("<figcaption>").append(it.nameWithoutExtension.removePrefix("$screen-")).append("</figcaption></figure>")
        }
        html.append("</div>")
    }
    html.append("</body></html>")
    File(screenshotDirectory, "index.html").writeText(html.toString())
}
internal const val PHONE_QUALIFIERS: String = "w411dp-h891dp-420dpi"
