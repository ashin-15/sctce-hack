package org.sakshi.core.vault

import android.content.Context
import java.io.Closeable
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.sakshi.core.crypto.KeyWrapper
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.SakshiDatabaseFactory

/** What [Vault.startUp] cleaned up. */
public class StartUpReport(public val sweep: SweepReport, public val jobsRecovered: Int)

/** An open vault session: cases, evidence, audit log and job queue over one database and blob store. */
public class Vault private constructor(
    private val database: SakshiDatabase,
    private val sweeper: OrphanSweeper,
    public val cases: CaseRepository,
    public val evidence: EvidenceRepository,
    public val audit: AuditLog,
    public val jobs: JobQueue,
) : Closeable {

    /** Removes temporary and orphaned files, returns interrupted jobs to the queue and audits the opening. */
    public suspend fun startUp(): StartUpReport {
        val sweep = sweeper.sweep()
        val recovered = jobs.recover()
        audit.append(
            AuditActions.VAULT_OPENED,
            SUBJECT_TYPE,
            SUBJECT_TYPE,
            jsonObjectOf(
                "orphan_files_deleted" to sweep.orphanFilesDeleted,
                "temporary_files_deleted" to sweep.temporaryFilesDeleted,
                "missing_files" to sweep.missingFileEvidenceIds.size,
                "jobs_recovered" to recovered,
            ),
        )
        return StartUpReport(sweep, recovered)
    }

    /** Closes the database. Do not use the vault afterwards. */
    override fun close() {
        database.close()
    }

    public companion object {
        private const val SUBJECT_TYPE = "vault"
        private const val VAULT_DIRECTORY = "vault"
        private const val BLOB_DIRECTORY = "blobs"
        private const val DATABASE_FILE = "sakshi.db"

        /**
         * Device path: Keystore-backed wrapper and SQLCipher database, all under `noBackupFilesDir`.
         * The passphrase array is wiped once the database has been handed over.
         *
         * @throws VaultKeyException when the Keystore key cannot be used.
         * @throws java.security.GeneralSecurityException when the stored passphrase cannot be unwrapped.
         */
        public fun open(
            context: Context,
            wrapper: KeyWrapper,
            clock: () -> Instant = Instant::now,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): Vault {
            val root = File(context.noBackupFilesDir, VAULT_DIRECTORY)
            val passphrase = VaultKeyFile(root).loadOrCreate(wrapper)
            try {
                val database = SakshiDatabaseFactory.openEncrypted(
                    context,
                    passphrase,
                    File(root, DATABASE_FILE).absolutePath,
                )
                return assemble(database, File(root, BLOB_DIRECTORY), wrapper, clock, { UUID.randomUUID().toString() }, dispatcher)
            } finally {
                passphrase.fill(0)
            }
        }

        /** Tests only: in-memory unencrypted database, with blobs under the app's no-backup directory. */
        public fun openForTests(
            context: Context,
            wrapper: KeyWrapper,
            clock: () -> Instant,
            ids: () -> String,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): Vault = assemble(
            SakshiDatabaseFactory.openInMemoryForTests(context),
            File(File(context.noBackupFilesDir, VAULT_DIRECTORY), BLOB_DIRECTORY),
            wrapper,
            clock,
            ids,
            dispatcher,
        )

        private fun assemble(
            database: SakshiDatabase,
            blobDirectory: File,
            wrapper: KeyWrapper,
            clock: () -> Instant,
            ids: () -> String,
            dispatcher: CoroutineDispatcher,
        ): Vault {
            val blobs = BlobStore(blobDirectory, wrapper)
            val audit = AuditLog(database, clock)
            return Vault(
                database = database,
                sweeper = OrphanSweeper(database, blobs, dispatcher),
                cases = CaseRepository(database, blobs, audit, clock, ids, dispatcher),
                evidence = EvidenceRepository(database, blobs, audit, clock, ids, dispatcher),
                audit = audit,
                jobs = JobQueue(database, clock),
            )
        }
    }
}
