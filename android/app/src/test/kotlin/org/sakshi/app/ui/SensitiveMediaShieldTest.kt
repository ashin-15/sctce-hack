package org.sakshi.app.ui

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.sakshi.app.ui.components.SensitiveMediaShield
import org.sakshi.app.ui.theme.SakshiTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class SensitiveMediaShieldTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val application = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(application.packageManager)
                .addActivityIfNotPresent(ComponentName(application.packageName, ComponentActivity::class.java.name))
        }
    }).around(compose)

    @Test
    fun hiddenContentIsAbsentFromAccessibilityUntilExplicitlyRevealed() {
        compose.setContent {
            var hidden by remember { mutableStateOf(true) }
            SakshiTheme {
                SensitiveMediaShield(hidden, { hidden = !hidden }) { Text("Private source excerpt") }
            }
        }
        compose.onNodeWithText("Private source excerpt", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Reveal preview").performClick()
        compose.onNodeWithText("Private source excerpt", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Hide preview").performClick()
        compose.onNodeWithText("Private source excerpt", useUnmergedTree = true).assertDoesNotExist()
    }
}
