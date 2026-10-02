package org.sakshi.core.temporal

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.Event

private const val DEFAULT_EPISODE_GAP_MINUTES: Long = 30
private const val DEFAULT_WINDOW_MINUTES: Long = 10
private const val DEFAULT_REPEATED_CONTACT_MINIMUM: Int = 2
private const val DEFAULT_AFTER_BOUNDARY_MINIMUM: Int = 3
private const val DEFAULT_DENSITY_MINIMUM_COUNT: Int = 6
private const val DEFAULT_DENSITY_MINIMUM_RATIO: Int = 3
private const val SEVEN_DAYS: Long = 7

/** Which evidence a run may use. */
public enum class EvidenceView {
    /** Only events the user had confirmed at the knowledge cutoff. */
    CONFIRMED_ONLY,

    /** Confirmed events plus pending ones; results using pending events are at most candidates. */
    CANDIDATE_PREVIEW,
}

/**
 * Demonstration settings, not validated thresholds. Calendar days and hour bins use [zone].
 */
public data class PatternConfig(
    val ruleVersion: String = "temporal-rules-0.1-demo",
    val zone: ZoneId = ZoneOffset.UTC,
    val countWindows: List<Duration> =
        listOf(
            Duration.ofMinutes(DEFAULT_WINDOW_MINUTES),
            Duration.ofHours(1),
            Duration.ofDays(1),
            Duration.ofDays(SEVEN_DAYS),
        ),
    val episodeGap: Duration = Duration.ofMinutes(DEFAULT_EPISODE_GAP_MINUTES),
    val repeatedContactMinimum: Int = DEFAULT_REPEATED_CONTACT_MINIMUM,
    val afterBoundaryMinimum: Int = DEFAULT_AFTER_BOUNDARY_MINIMUM,
    val afterBoundaryWindow: Duration = Duration.ofDays(1),
    val transitionWindow: Duration = Duration.ofDays(SEVEN_DAYS),
    val transitions: List<Pair<CategoryLabel, CategoryLabel>> =
        listOf(CategoryLabel.VERBAL_ABUSE to CategoryLabel.EXPLICIT_THREAT),
    val densityBin: Duration = Duration.ofHours(1),
    val densityMinimumCount: Int = DEFAULT_DENSITY_MINIMUM_COUNT,
    val densityMinimumRatio: Int = DEFAULT_DENSITY_MINIMUM_RATIO,
) {
    init {
        require(countWindows.all { it > Duration.ZERO }) { "count windows must be positive" }
        require(densityBin.seconds > 0) { "densityBin must be at least one second" }
        require(!episodeGap.isNegative && !afterBoundaryWindow.isNegative && !transitionWindow.isNegative) {
            "durations must not be negative"
        }
    }
}

/** Everything one run depends on. [knowledgeCutoff] bounds which revisions and reviews are visible. */
public data class TemporalInput(
    val caseId: CaseId,
    val events: List<Event>,
    val gaps: List<CoverageGap>,
    val knowledgeCutoff: Instant,
    val view: EvidenceView,
    val config: PatternConfig = PatternConfig(),
)
