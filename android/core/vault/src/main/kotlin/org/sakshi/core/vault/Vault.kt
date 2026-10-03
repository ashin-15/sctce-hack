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

/** An open vault session: cases, evidence, events, actors, stored patterns, audit log and job queue over one database and blob store. */
public class Vault private constructor(
    private val database: SakshiDatabase,
    private val sweeper: OrphanSweeper,
    public val cases: CaseRepository,
    public val evidence: EvidenceRepository,
    public val audit: AuditLog,
    public val jobs: JobQueue,
    public val events: EventStore,
    public val actors: ActorRegistry,
    public val derivatives: DerivativeStore,
    public val review: ReviewCoordinator,
    public val search: EvidenceSearch,
    public val reports: ReportHistory,
    public val patterns: PatternStore,
    public val threatAnalysisRuns: ThreatAnalysisRunStore,
    private val destruction: () -> VaultDestroyResult,
) : Closeable {

    /**
     * Makes the text of manual notes searchable through [source]; see [VaultEvidenceSearch.useNoteTextSource].
     * Call it once after opening. Without it, search reads derivatives only.
     */
    public fun useNoteTextSource(source: NoteTextSource?) {
        (search as? VaultEvidenceSearch)?.useNoteTextSource(source)
    }

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

    /**
     * Closes this vault and then destroys it on this device, as [Companion.destroy] does. Do not use the vault
     * afterwards; open a new one with [Companion.open].
     */
    public fun destroy(): VaultDestroyResult {
        close()
        return destruction()
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
                return assemble(database, File(root, BLOB_DIRECTORY), wrapper, clock, { UUID.randomUUID().toString() }, dispatcher) {
                    destroy(context, wrapper)
                }
            } finally {
                passphrase.fill(0)
            }
        }

        /**
         * Destroys the whole vault on this device while it is closed: the wrapped database passphrase file, the
         * master key when [wrapper] is a [DestroyableKeyWrapper] (the Keystore alias for [KeystoreKeyWrapper]), the
         * database and its side files, every blob and temporary file, and the vault directory. Blocks; call it off
         * the main thread. Nothing outside the vault directory is touched. Open vaults must be closed first (or use
         * the instance [destroy]).
         *
         * It never throws for a file that cannot be removed: it returns [VaultDestroyResult.Incomplete] listing
         * what remains, and only [VaultDestroyResult.Destroyed] means nothing remains. It is idempotent: on a
         * vault that is already destroyed or was never created it returns [VaultDestroyResult.Destroyed], and
         * calling it again after an incomplete result retries. A later [open] creates a fresh empty vault with a
         * new key.
         *
         * Honest limit: files are deleted and the key is destroyed, which makes remaining ciphertext unreadable,
         * but physical erasure from flash storage cannot be promised. Backups or copies made outside the app are
         * not reached.
         */
        public fun destroy(
            context: Context,
            wrapper: KeyWrapper,
            deleter: VaultFileDeleter = VaultFileDeleter.DEFAULT,
        ): VaultDestroyResult = VaultDestruction(File(context.noBackupFilesDir, VAULT_DIRECTORY), wrapper, deleter).run()

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
        ) { destroy(context, wrapper) }

        private fun assemble(
            database: SakshiDatabase,
            blobDirectory: File,
            wrapper: KeyWrapper,
            clock: () -> Instant,
            ids: () -> String,
            dispatcher: CoroutineDispatcher,
            destruction: () -> VaultDestroyResult,
        ): Vault {
            val blobs = BlobStore(blobDirectory, wrapper)
            val audit = AuditLog(database, clock)
            val events = EventStore(database, audit, clock, ids, dispatcher)
            val actors = ActorRegistry(database, audit, ids, dispatcher)
            return Vault(
                database = database,
                sweeper = OrphanSweeper(database, blobs, dispatcher),
                cases = CaseRepository(database, blobs, audit, clock, ids, dispatcher),
                evidence = EvidenceRepository(database, blobs, audit, clock, ids, dispatcher),
                audit = audit,
                jobs = JobQueue(database, clock),
                events = events,
                actors = actors,
                derivatives = DerivativeStore(database, audit, clock, ids, dispatcher),
                review = ReviewCoordinator(database, events, audit, clock, ids, dispatcher),
                search = VaultEvidenceSearch(database, dispatcher),
                reports = ReportHistory(database, events, audit, clock, ids, dispatcher),
                patterns = PatternStore(database, events, actors, audit, clock, ids, dispatcher),
                threatAnalysisRuns = ThreatAnalysisRunStore(database, dispatcher),
                destruction = destruction,
            )
        }
    }
}
