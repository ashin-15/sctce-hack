package org.sakshi.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.sakshi.app.R
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.ScreenTitle
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.theme.Spacing

/** The three plain sections the user reads once. The button stays at the bottom; the text scrolls above it. */
@Composable
fun OnboardingScreen(onAcknowledge: () -> Unit, modifier: Modifier = Modifier) {
    SakshiScaffold(
        title = null,
        modifier = modifier,
        bottomBar = {
            PrimaryButton(
                stringResource(R.string.onboarding_acknowledge),
                onAcknowledge,
                Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            )
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            ScreenTitle(stringResource(R.string.onboarding_title))
            Section(R.string.onboarding_does_heading, listOf(R.string.onboarding_does_body))
            Section(R.string.onboarding_cannot_heading, listOf(R.string.onboarding_cannot_body))
            Section(
                R.string.onboarding_limits_heading,
                listOf(
                    R.string.onboarding_limit_hash,
                    R.string.onboarding_limit_loss,
                    R.string.onboarding_limit_unlock,
                    R.string.onboarding_limit_export,
                ),
            )
        }
    }
}

@Composable
private fun Section(heading: Int, paragraphs: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(heading))
        paragraphs.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
    }
}
