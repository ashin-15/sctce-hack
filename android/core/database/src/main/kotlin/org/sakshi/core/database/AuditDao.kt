package org.sakshi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Append-only access to the audit chain. */
@Dao
public abstract class AuditDao {
    /** Appends a record and returns its assigned sequence number. */
    @Insert
    public abstract suspend fun append(record: AuditRecordEntity): Long

    @Query("SELECT * FROM audit_record ORDER BY seq DESC LIMIT 1")
    public abstract suspend fun last(): AuditRecordEntity?

    @Query("SELECT * FROM audit_record ORDER BY seq")
    public abstract suspend fun all(): List<AuditRecordEntity>

    @Query("SELECT COUNT(*) FROM audit_record")
    public abstract suspend fun count(): Int

    /**
     * Reads the current chain head and appends the record built from it in one transaction, so
     * concurrent appenders cannot fork the chain. [next] receives null for the first record.
     */
    @Transaction
    public open suspend fun appendNext(next: (previous: AuditRecordEntity?) -> AuditRecordEntity): Long =
        append(next(last()))
}
