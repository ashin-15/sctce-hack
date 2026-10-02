package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Derivative metadata without its text, for list screens. */
public data class DerivativeSummary(
    val id: String,
    val revision: Int,
    val kind: String,
    @ColumnInfo(name = "parent_derivative_id") val parentDerivativeId: String?,
    @ColumnInfo(name = "tool_id") val toolId: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
)

/** Access to derivatives and their regions. */
@Dao
public abstract class DerivativeDao {
    @Insert
    public abstract suspend fun insert(derivative: DerivativeEntity)

    @Insert
    public abstract suspend fun insertRegions(regions: List<RegionEntity>)

    /** Inserts a derivative and its regions atomically. */
    @Transaction
    public open suspend fun insertWithRegions(derivative: DerivativeEntity, regions: List<RegionEntity>) {
        insert(derivative)
        if (regions.isNotEmpty()) insertRegions(regions)
    }

    @Query("SELECT * FROM derivative WHERE id = :id")
    public abstract suspend fun get(id: String): DerivativeEntity?

    @Query("SELECT * FROM derivative WHERE evidence_id = :evidenceId AND kind = :kind ORDER BY revision DESC LIMIT 1")
    public abstract suspend fun getLatest(evidenceId: String, kind: String): DerivativeEntity?

    @Query(
        "SELECT id, revision, kind, parent_derivative_id, tool_id, created_at FROM derivative " +
            "WHERE evidence_id = :evidenceId ORDER BY kind, revision DESC, id",
    )
    public abstract fun observeSummaries(evidenceId: String): Flow<List<DerivativeSummary>>

    @Query("SELECT * FROM region WHERE derivative_id = :derivativeId ORDER BY page_index, id")
    public abstract suspend fun getRegions(derivativeId: String): List<RegionEntity>
}
