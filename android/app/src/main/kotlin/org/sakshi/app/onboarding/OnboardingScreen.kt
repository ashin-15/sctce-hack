package org.sakshi.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.sakshi.app.R

@Composable
fun OnboardingScreen(onAcknowledge: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
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
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.onboarding_acknowledge))
        }
    }
}

@Composable
private fun Section(heading: Int, paragraphs: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(heading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        paragraphs.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
    }
}
