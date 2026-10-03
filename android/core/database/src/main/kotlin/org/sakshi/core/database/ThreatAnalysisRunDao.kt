package org.sakshi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Persistence for append-only threat analysis attempt records. */
@Dao
public interface ThreatAnalysisRunDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertIfAbsent(run: ThreatAnalysisRunEntity): Long

    @Query(
        "SELECT * FROM threat_analysis_run WHERE event_id = :eventId AND event_revision = :revision " +
            "AND request_id = :requestId LIMIT 1",
    )
    public suspend fun getForRequest(eventId: String, revision: Int, requestId: String): ThreatAnalysisRunEntity?

    @Query("SELECT * FROM threat_analysis_run WHERE event_id = :eventId AND event_revision = :revision ORDER BY created_at, id")
    public suspend fun getForEventRevision(eventId: String, revision: Int): List<ThreatAnalysisRunEntity>

    @Query("SELECT * FROM threat_analysis_run WHERE event_id = :eventId ORDER BY created_at DESC, id DESC")
    public suspend fun getForEvent(eventId: String): List<ThreatAnalysisRunEntity>
}
