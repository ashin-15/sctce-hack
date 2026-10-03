package org.sakshi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

internal const val MARK_PATTERNS_STALE_FOR_CASE: String =
    "UPDATE pattern SET assessment_status = 'stale' WHERE case_id = :caseId AND assessment_status != 'stale'"

/** Access to patterns and the event revisions that support them. */
@Dao
public abstract class PatternDao {
    @Insert
    public abstract suspend fun insertPattern(pattern: PatternEntity)

    @Insert
    public abstract suspend fun insertSupport(support: List<PatternSupportEntity>)

    /** Inserts a pattern with its supporting events atomically. */
    @Transaction
    public open suspend fun insertWithSupport(pattern: PatternEntity, support: List<PatternSupportEntity>) {
        insertPattern(pattern)
        if (support.isNotEmpty()) insertSupport(support)
    }

    @Query("SELECT * FROM pattern WHERE id = :id")
    public abstract suspend fun get(id: String): PatternEntity?

    @Query("SELECT * FROM pattern WHERE case_id = :caseId ORDER BY type, generated_at DESC, id")
    public abstract fun observeForCase(caseId: String): Flow<List<PatternEntity>>

    @Query("SELECT * FROM pattern_support WHERE pattern_id = :patternId ORDER BY event_id, role")
    public abstract suspend fun getSupport(patternId: String): List<PatternSupportEntity>

    /** Changes the only mutable column. Returns the number of rows changed. */
    @Query("UPDATE pattern SET assessment_status = :status WHERE id = :id")
    public abstract suspend fun updateAssessment(id: String, status: String): Int

    /** Marks every non-stale pattern that uses the event as stale. Returns how many changed. */
    @Query(
        "UPDATE pattern SET assessment_status = 'stale' WHERE assessment_status != 'stale' " +
            "AND id IN (SELECT pattern_id FROM pattern_support WHERE event_id = :eventId)",
    )
    public abstract suspend fun markStaleForEvent(eventId: String): Int

    /** Every pattern of the case computed for one evidence view, in a fixed order. */
    @Query(
        "SELECT * FROM pattern WHERE case_id = :caseId AND evidence_view = :view " +
            "ORDER BY type, window_start, id",
    )
    public abstract suspend fun getForCaseView(caseId: String, view: String): List<PatternEntity>

    /**
     * The support rows of every pattern of the case and view, in the order they were inserted, which is the order
     * the engine listed the events (`pattern_support` has no order column; rows are insert-only).
     */
    @Query(
        "SELECT s.* FROM pattern_support s JOIN pattern p ON p.id = s.pattern_id " +
            "WHERE p.case_id = :caseId AND p.evidence_view = :view ORDER BY s.rowid",
    )
    public abstract suspend fun getSupportForCaseView(caseId: String, view: String): List<PatternSupportEntity>

    /** Marks every non-stale pattern of the case, whatever its view, as stale. Returns how many changed. */
    @Query(MARK_PATTERNS_STALE_FOR_CASE)
    public abstract suspend fun markStaleForCase(caseId: String): Int

    /** Deletes the patterns with these ids and, by cascade, their support rows. Keep the list below about 500 ids. */
    @Query("DELETE FROM pattern WHERE id IN (:ids)")
    public abstract suspend fun deleteByIds(ids: List<String>): Int

    /** Removes stale patterns of one type once a replacement has been stored. */
    @Query("DELETE FROM pattern WHERE case_id = :caseId AND type = :type AND assessment_status = 'stale'")
    public abstract suspend fun deleteStale(caseId: String, type: String): Int
}
