package org.sakshi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT COUNT(*) FROM case_file")
    public abstract suspend fun count(): Int

    /** Deletes the case and, by cascade, every row it owns. Returns the number of cases removed. */
    @Query("DELETE FROM case_file WHERE id = :id")
    public abstract suspend fun delete(id: String): Int
}
