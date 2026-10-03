package org.sakshi.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.sakshi.app.R
import org.sakshi.app.ui.components.IconTile
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiBrandLogo
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.ScreenTitle
import org.sakshi.app.ui.components.Tone
import org.sakshi.app.ui.theme.Spacing

private val LOGO_SIZE = 64.dp

/** The facts the user reads once, as icon rows. The button stays at the bottom; the rows scroll above it. */
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SakshiBrandLogo(size = LOGO_SIZE)
            ScreenTitle(stringResource(R.string.onboarding_title), textAlign = TextAlign.Center)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                FactRow(Icons.Default.Lock, Tone.Brand, R.string.onboarding_device_heading, R.string.onboarding_device_body)
                FactRow(Icons.Default.Person, Tone.Neutral, R.string.onboarding_senders_heading, R.string.onboarding_senders_body)
                FactRow(Icons.Default.CheckCircle, Tone.Neutral, R.string.onboarding_hash_heading, R.string.onboarding_hash_body)
                FactRow(Icons.Default.Warning, Tone.Warning, R.string.onboarding_keys_heading, R.string.onboarding_keys_body)
                FactRow(Icons.Default.Share, Tone.Neutral, R.string.onboarding_export_heading, R.string.onboarding_export_body)
            }
        }
    }
}

@Composable
private fun FactRow(icon: ImageVector, tone: Tone, heading: Int, body: Int) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(icon, tone = tone)
        Column(Modifier.weight(1f)) {
            Text(stringResource(heading), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
