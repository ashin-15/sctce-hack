package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A case row with the number of evidence items it holds. */
public data class CaseWithEvidenceCount(
    val id: String,
    val title: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "evidence_count") val evidenceCount: Int,
)

/** Access to cases. Deleting a case cascades to everything the case owns. */
@Dao
public abstract class CaseDao {
    @Insert
    public abstract suspend fun insert(case: CaseEntity)

    @Update
    public abstract suspend fun update(case: CaseEntity): Int

    @Query("SELECT * FROM case_file WHERE id = :id")
    public abstract suspend fun get(id: String): CaseEntity?

    @Query("SELECT * FROM case_file ORDER BY created_at DESC, id")
    public abstract fun observeAll(): Flow<List<CaseEntity>>

    /** Every case, newest first, re-emitting when cases or evidence change. */
    @Query(
        "SELECT c.id AS id, c.title AS title, c.status AS status, c.created_at AS created_at, " +
            "(SELECT COUNT(*) FROM evidence e WHERE e.case_id = c.id) AS evidence_count " +
            "FROM case_file c ORDER BY c.created_at DESC, c.id",
    )
    public abstract fun observeWithEvidenceCounts(): Flow<List<CaseWithEvidenceCount>>

    @Query("SELECT COUNT(*) FROM case_file")
    public abstract suspend fun count(): Int

    /** Deletes the case and, by cascade, every row it owns. Returns the number of cases removed. */
    @Query("DELETE FROM case_file WHERE id = :id")
    public abstract suspend fun delete(id: String): Int
}
