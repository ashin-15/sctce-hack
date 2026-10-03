package org.sakshi.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Installs the insert-only triggers on creation and enforces foreign keys on every open. */
private object SchemaCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        SakshiSchema.immutabilityTriggers.forEach(db::execSQL)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        SakshiSchema.immutabilityTriggers.forEach(db::execSQL)
    }
}

/** Builds [SakshiDatabase] instances. There is no destructive migration fallback. */
public object SakshiDatabaseFactory {
    /**
     * Opens the SQLCipher-encrypted vault database.
     *
     * The library receives its own copy of [passphrase] and wipes that copy once the database is
     * open, so the caller keeps ownership of the array it passed and must wipe it. The key is
     * never turned into a String. Do not reuse a [SakshiDatabase] after closing it; open a new
     * one with the key again.
     *
     * This path loads a native library, so it cannot run under Robolectric. It is unverified
     * until an instrumented test has run it on a device.
     */
    public fun openEncrypted(context: Context, passphrase: ByteArray, name: String = "sakshi.db"): SakshiDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context.applicationContext, SakshiDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .addMigrations(ThreatAnalysisMigrations.MIGRATION_1_2)
            .addCallback(SchemaCallback)
            .build()
    }

    /**
     * Opens an in-memory, plain SQLite database with the same schema and triggers.
     *
     * Not encrypted. For tests only; never use it for real data.
     */
    public fun openInMemoryForTests(context: Context): SakshiDatabase =
        Room.inMemoryDatabaseBuilder(context.applicationContext, SakshiDatabase::class.java)
            .addMigrations(ThreatAnalysisMigrations.MIGRATION_1_2)
            .addCallback(SchemaCallback)
            .build()
}
