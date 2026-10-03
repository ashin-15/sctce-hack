package org.sakshi.app.search

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.util.Locale
import org.sakshi.app.R
import org.sakshi.app.ui.components.CheckRow
import org.sakshi.app.ui.components.EmptyState
import org.sakshi.app.ui.components.EpistemicBlock
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.RadioRow
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SakshiTextField
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.formatCreatedDate
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.core.vault.SearchHit
import org.sakshi.core.vault.SearchScope

class SearchActions(
    val back: () -> Unit,
    val openEvent: (String) -> Unit,
    val onQueryChanged: (String) -> Unit,
    val filters: SearchFilterActions,
)

class SearchFilterActions(
    val setOpen: (Boolean) -> Unit,
    val setScope: (SearchScope) -> Unit,
    val setPerson: (ActorId?) -> Unit,
    val toggleSource: (SourceChoice) -> Unit,
    val setFromText: (String) -> Unit,
    val setUntilText: (String) -> Unit,
    val clear: () -> Unit,
)

@Composable
fun SearchScreen(
    state: SearchUiState,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        if (state.filtersOpen) {
            actions.filters.setOpen(false)
        } else {
            actions.back()
        }
    }

    SakshiScaffold(
        title = stringResource(R.string.search_title),
        modifier = modifier,
        onBack = actions.back,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQueryChanged,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { actions.onQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.search_clear),
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )

            FiltersBar(state, actions.filters)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    state.query.isBlank() -> {
                        Box(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
                            EmptyState(
                                title = stringResource(R.string.search_empty_initial_title),
                                body = stringResource(R.string.search_empty_initial_body),
                            )
                        }
                    }
                    state.dateProblem != null -> {
                        Box(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
                            EmptyState(
                                title = stringResource(R.string.search_dates_problem_title),
                                body = stringResource(R.string.search_dates_problem_body),
                            )
                        }
                    }
                    state.failed -> {
                        Box(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
                            EmptyState(
                                title = stringResource(R.string.search_failed_title),
                                body = stringResource(R.string.search_failed_body),
                            )
                        }
                    }
                    state.isSearching && state.hits.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.xxl),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    state.hits.isEmpty() -> {
                        Box(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
                            if (state.filters.isActive) {
                                EmptyState(
                                    title = stringResource(R.string.search_filtered_empty_title),
                                    body = stringResource(R.string.search_filtered_empty_body, state.query),
                                )
                            } else {
                                EmptyState(
                                    title = stringResource(R.string.search_empty_results_title),
                                    body = stringResource(R.string.search_empty_results_body, state.query),
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            if (state.limitReached) {
                                item(key = "limit") {
                                    StatusNote(NoteKind.Info, stringResource(R.string.search_limit_reached))
                                }
                            }
                            items(state.hits, key = { it.key }) { hit ->
                                SearchHitCard(hit = hit, onOpenEvent = actions.openEvent)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The Filters button, the one-line summary that is always shown, and the panel when it is open. */
@Composable
private fun FiltersBar(state: SearchUiState, actions: SearchFilterActions) {
    val resources = LocalResources.current
    val summary = filterSummary(state.filters, state.people, resources)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton(
                text = stringResource(if (state.filtersOpen) R.string.search_filters_hide else R.string.search_filters_open),
                onClick = { actions.setOpen(!state.filtersOpen) },
            )
            if (state.filters.isActive) QuietTextButton(stringResource(R.string.search_filters_clear), actions.clear)
        }
        if (state.filtersOpen) FiltersPanel(state, actions)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.filters.isActive) {
            StatusNote(
                NoteKind.Info,
                stringResource(if (state.filters.hasDates) R.string.search_filters_note_dates else R.string.search_filters_note),
            )
        }
    }
}

@Composable
private fun FiltersPanel(state: SearchUiState, actions: SearchFilterActions) {
    val filters = state.filters
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = PANEL_MAX_HEIGHT).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SectionHeader(stringResource(R.string.search_filters_scope_heading))
        RadioRow(
            stringResource(R.string.search_scope_all),
            filters.scope == SearchScope.ALL_PRESERVED_TEXT,
            { actions.setScope(SearchScope.ALL_PRESERVED_TEXT) },
        )
        RadioRow(
            stringResource(R.string.search_scope_accepted),
            filters.scope == SearchScope.ACCEPTED_FINDINGS_ONLY,
            { actions.setScope(SearchScope.ACCEPTED_FINDINGS_ONLY) },
        )

        SectionHeader(stringResource(R.string.search_filters_person_heading))
        RadioRow(stringResource(R.string.search_person_anyone), filters.personId == null, { actions.setPerson(null) })
        state.people.forEach { person ->
            RadioRow(person.label, filters.personId == person.id, { actions.setPerson(person.id) })
        }

        SectionHeader(stringResource(R.string.search_filters_source_heading))
        SourceChoice.entries.forEach { source ->
            CheckRow(stringResource(source.label), source in filters.sources, { actions.toggleSource(source) })
        }

        SectionHeader(stringResource(R.string.search_filters_dates_heading))
        SakshiTextField(
            value = filters.fromText,
            onValueChange = actions.setFromText,
            label = stringResource(R.string.search_date_from_label),
            supportingText = stringResource(R.string.search_date_hint),
        )
        SakshiTextField(
            value = filters.untilText,
            onValueChange = actions.setUntilText,
            label = stringResource(R.string.search_date_until_label),
        )
        when (filters.dateProblem) {
            DateProblem.FROM_INVALID, DateProblem.UNTIL_INVALID ->
                StatusNote(NoteKind.Problem, stringResource(R.string.search_date_error_invalid))
            DateProblem.FROM_AFTER_UNTIL -> StatusNote(NoteKind.Problem, stringResource(R.string.search_date_error_order))
            null -> Unit
        }
    }
}

private val PANEL_MAX_HEIGHT = 360.dp

@Composable
private fun SearchHitCard(
    hit: SearchHit,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A note has no page of its own to open, so a note hit is tappable only when a real event came from it.
    val eventId = hit.eventId
    val clickableModifier = if (eventId != null) {
        Modifier.clickable(role = Role.Button) { onOpenEvent(eventId) }
    } else {
        Modifier
    }

    SakshiCard(modifier = modifier.then(clickableModifier)) {
        if (hit.isNote) {
            EpistemicBlock(EpistemicStatus.USER_REPORTED, Modifier.padding(Spacing.md)) { HitContent(hit) }
        } else {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HitContent(hit)
            }
        }
    }
}

/**
 * The kind chip, the sender and time, and the matched snippets of one hit. For a note the chip says "Your note" and no
 * sender is shown, because the words are the person's own and never another person's message.
 */
@Composable
private fun HitContent(hit: SearchHit) {
    val actorLabel = hit.actorLabel.takeUnless { hit.isNote }
    val locale = LocalConfiguration.current.locales[0]
    val timestamp = hit.timestamp?.let { formatCreatedDate(it, locale, ZoneId.systemDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = derivativeKindLabel(hit.derivativeKind),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
            )
        }

        if (!actorLabel.isNullOrBlank()) {
            Text(
                text = actorLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        if (!timestamp.isNullOrBlank()) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }

    val highlightColor = MaterialTheme.colorScheme.primary
    hit.matches.forEach { match ->
        val annotatedSnippet = buildSnippetAnnotatedString(
            snippet = match.snippet,
            start = match.matchStartInSnippet,
            end = match.matchEndInSnippet,
            highlightColor = highlightColor,
        )
        Text(
            text = annotatedSnippet,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The words for the kind of text a hit was found in, or null for a kind this version has no words for. */
@StringRes
internal fun derivativeKindLabelRes(kind: String): Int? = when (kind) {
    DerivativeKind.PARSED_TEXT -> R.string.derivative_kind_parsed_text
    DerivativeKind.OCR -> R.string.derivative_kind_ocr
    DerivativeKind.TRANSCRIPT -> R.string.derivative_kind_transcript
    DerivativeKind.NORMALISED_VIEW -> R.string.derivative_kind_normalised_view
    DerivativeKind.USER_EDIT -> R.string.derivative_kind_user_edit
    SearchHit.NOTE_DERIVATIVE_KIND -> R.string.derivative_kind_note
    else -> null
}

@Composable
internal fun derivativeKindLabel(kind: String): String =
    derivativeKindLabelRes(kind)?.let { stringResource(it) }
        ?: kind.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

internal fun buildSnippetAnnotatedString(
    snippet: String,
    start: Int,
    end: Int,
    highlightColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(snippet)
    val safeStart = start.coerceIn(0, snippet.length)
    val safeEnd = end.coerceIn(safeStart, snippet.length)
    if (safeStart < safeEnd) {
        addStyle(
            style = SpanStyle(
                fontWeight = FontWeight.Bold,
                color = highlightColor,
            ),
            start = safeStart,
            end = safeEnd,
        )
    }
}
