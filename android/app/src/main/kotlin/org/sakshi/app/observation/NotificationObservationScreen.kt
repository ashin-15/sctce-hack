package org.sakshi.app.observation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sakshi.acquisition.importer.ItemOutcome
import org.sakshi.acquisition.notifications.CoverageDetail
import org.sakshi.acquisition.notifications.CoverageState
import org.sakshi.acquisition.notifications.NotificationAvailability
import org.sakshi.acquisition.notifications.NotificationObservation
import org.sakshi.acquisition.notifications.ObservedCandidate
import org.sakshi.acquisition.notifications.ObservedTextStatus
import org.sakshi.acquisition.notifications.PackageAllowlist
import org.sakshi.acquisition.notifications.UnavailableReason
import org.sakshi.app.R
import org.sakshi.app.SessionServices
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.vault.CaseSummary
import org.sakshi.core.database.CaseStatus

@Composable
fun NotificationObservationScreen(
    services: SessionServices,
    onBack: () -> Unit,
    onAnalyse: (caseId: String, evidenceId: String) -> Unit,
    onOpenAccessSettings: (Intent) -> Unit,
) {
    val context = LocalContext.current
    val observation = remember(context) { NotificationObservation.from(context) }
    val settings by observation.settingsState.collectAsState()
    val coverage by observation.coverageDetail.collectAsState()
    val candidates by observation.inbox.candidates.collectAsState()
    val cases by remember(services) { services.vault.cases.observe() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var customPackage by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<Int?>(null) }
    var savingId by remember { mutableStateOf<String?>(null) }
    val supported = observation.availability() == NotificationAvailability.AVAILABLE
    fun settingsChange(change: () -> Unit) {
        try {
            change()
            notice = null
        } catch (_: Exception) {
            notice = R.string.observation_settings_failed
        }
    }
    val alertPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        settingsChange { observation.setCueAlertsOptIn(granted) }
        if (!granted) notice = R.string.observation_alert_permission_denied
    }
    BackHandler(enabled = savingId == null, onBack = onBack)
    SakshiScaffold(title = stringResource(R.string.observation_title), onBack = if (savingId == null) onBack else null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SupportingText(stringResource(R.string.observation_disclosure))
            if (!supported) SupportingText(stringResource(R.string.observation_unsupported))
            if (!settings.enabled) {
                PrimaryButton(stringResource(R.string.observation_enable), { settingsChange { observation.enable() } }, enabled = supported)
            } else {
                PrimaryButton(stringResource(R.string.observation_access), { onOpenAccessSettings(observation.accessSettingsIntent()) })
                QuietTextButton(
                    stringResource(if (settings.paused) R.string.observation_resume else R.string.observation_pause),
                    { settingsChange { if (settings.paused) observation.resume() else observation.pause() } },
                )
                QuietTextButton(stringResource(R.string.observation_disable), { settingsChange { observation.disable() } })
            }
            SectionHeader(stringResource(R.string.observation_apps))
            val names = POPULAR_APPS + settings.allowlist.names.filterNot { it in POPULAR_APPS.keys }.associateWith { it }
            names.forEach { (packageName, title) ->
                Choice(title, packageName in settings.allowlist, false, supported) { selected ->
                    settingsChange {
                        val current = observation.settingsState.value.allowlist
                        observation.setAllowlist(if (selected) current.with(packageName) else current.without(packageName))
                    }
                }
            }
            OutlinedTextField(
                value = customPackage,
                onValueChange = { customPackage = it.take(255) },
                label = { Text(stringResource(R.string.observation_custom_label)) },
                supportingText = { Text(stringResource(R.string.observation_custom_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            QuietTextButton(stringResource(R.string.observation_add), {
                val name = customPackage.trim()
                if (!PackageAllowlist.isValidName(name)) notice = R.string.observation_invalid_package
                else settingsChange {
                    observation.setAllowlist(observation.settingsState.value.allowlist.with(name))
                    customPackage = ""
                }
            }, enabled = supported)
            SupportingText(stringResource(R.string.observation_background_disclosure))
            Choice(stringResource(R.string.observation_background_opt_in), settings.backgroundObservationOptIn, true, supported) {
                settingsChange { observation.setBackgroundObservationOptIn(it) }
            }
            SupportingText(stringResource(R.string.observation_alert_disclosure))
            Choice(stringResource(R.string.observation_alert_opt_in), settings.cueAlertsOptIn, true, supported) { enabled ->
                if (!enabled) settingsChange { observation.setCueAlertsOptIn(false) }
                else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    alertPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else settingsChange { observation.setCueAlertsOptIn(true) }
            }
            Choice(stringResource(R.string.observation_active_opt_in), settings.includeActiveOnConnect, true, supported) {
                settingsChange { observation.setIncludeActiveOnConnect(it) }
            }
            SectionHeader(stringResource(R.string.observation_coverage))
            Text(coverageText(coverage), style = MaterialTheme.typography.bodyLarge)
            SupportingText(stringResource(R.string.observation_limits))
            SupportingText(stringResource(R.string.observation_counters, coverage.candidatesDropped, coverage.unreadableCount, coverage.lockedWithheldCount, coverage.queueOverflowCount))
            notice?.let { Text(stringResource(it), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            SectionHeader(stringResource(R.string.observation_inbox))
            if (candidates.isEmpty()) SupportingText(stringResource(R.string.observation_empty))
            candidates.forEach { candidate ->
                androidx.compose.runtime.key(candidate.id) {
                    CandidateCard(candidate, cases.filter { it.status == CaseStatus.ACTIVE }, savingId != null, savingId == candidate.id,
                        onDiscard = { observation.inbox.discard(setOf(candidate.id)) },
                        onSave = { caseId, analyse ->
                            if (savingId == null && observation.inbox.candidates.value.any { it.id == candidate.id }) {
                                savingId = candidate.id
                                scope.launch {
                                    try {
                                        val outcome = withContext(services.io) {
                                            services.importer.commitNotificationExcerpt(caseId, candidate.toImport())
                                        }
                                        currentCoroutineContext().ensureActive()
                                        if (outcome is ItemOutcome.Saved) {
                                            observation.inbox.discard(setOf(candidate.id))
                                            notice = R.string.observation_saved
                                            if (analyse) onAnalyse(caseId, outcome.evidenceId)
                                        } else notice = R.string.observation_save_failed
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        notice = R.string.observation_save_failed
                                    } finally {
                                        savingId = null
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: ObservedCandidate,
    cases: List<CaseSummary>,
    busy: Boolean,
    saving: Boolean,
    onDiscard: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    var selectedCase by remember { mutableStateOf<String?>(null) }
    val selected = selectedCase?.takeIf { id -> cases.any { it.id == id } }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SupportingText(stringResource(R.string.observation_source, candidate.message.sourceAppClaim))
            candidate.message.senderLabel?.let { SupportingText(stringResource(R.string.observation_sender, it)) }
            Text(candidate.message.text, style = MaterialTheme.typography.bodyLarge)
            SupportingText(stringResource(R.string.observation_source_claim))
            when (candidate.message.textStatus) {
                ObservedTextStatus.TRUNCATED -> SupportingText(stringResource(R.string.observation_truncated))
                ObservedTextStatus.SUMMARY_ONLY -> SupportingText(stringResource(R.string.observation_summary))
                ObservedTextStatus.COMPLETE -> Unit
            }
            if (cases.isEmpty()) SupportingText(stringResource(R.string.observation_no_cases))
            else {
                SupportingText(stringResource(R.string.observation_choose_case))
                Column(Modifier.selectableGroup()) {
                    cases.forEach { case ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget).selectable(
                                selected = selected == case.id, enabled = !busy, role = Role.RadioButton,
                                onClick = { selectedCase = case.id },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected == case.id, onClick = null, enabled = !busy)
                            Text(case.title, modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                }
            }
            if (saving) SupportingText(stringResource(R.string.observation_saving))
            PrimaryButton(stringResource(R.string.observation_save), { selected?.let { onSave(it, false) } }, enabled = selected != null && !busy)
            if (candidate.message.textStatus != ObservedTextStatus.SUMMARY_ONLY) {
                QuietTextButton(stringResource(R.string.observation_save_analyse), { selected?.let { onSave(it, true) } }, enabled = selected != null && !busy)
            }
            QuietTextButton(stringResource(R.string.observation_discard), onDiscard, enabled = !busy)
        }
    }
}

@Composable
private fun Choice(title: String, selected: Boolean, switch: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
            .toggleable(value = selected, enabled = enabled, role = if (switch) Role.Switch else Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f).padding(end = Spacing.sm))
        if (switch) Switch(selected, onCheckedChange = null, enabled = enabled)
        else Checkbox(selected, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun coverageText(detail: CoverageDetail): String = stringResource(
    when (detail.state) {
        CoverageState.CONNECTED -> R.string.observation_connected
        CoverageState.ACCESS_GRANTED_NOT_CONNECTED -> R.string.observation_waiting
        CoverageState.COVERAGE_UNKNOWN -> R.string.observation_unknown
        CoverageState.PAUSED -> R.string.observation_paused
        CoverageState.UNAVAILABLE -> when (detail.unavailableReason) {
            UnavailableReason.ANDROID_VERSION -> R.string.observation_unsupported
            UnavailableReason.ACCESS_NOT_GRANTED -> R.string.observation_access_missing
            UnavailableReason.SESSION_STOPPED -> R.string.observation_session_stopped
            else -> R.string.observation_disabled
        }
    },
)

private val POPULAR_APPS = linkedMapOf(
    "com.whatsapp" to "WhatsApp",
    "com.whatsapp.w4b" to "WhatsApp Business",
    "com.instagram.android" to "Instagram",
    "org.telegram.messenger" to "Telegram",
    "org.thoughtcrime.securesms" to "Signal",
)
