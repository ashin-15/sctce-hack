package org.sakshi.core.database

import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
abstract class DatabaseTestBase {
    protected lateinit var db: SakshiDatabase

    @Before
    fun openDatabase() {
        db = newDatabase()
    }

    @After
    fun closeDatabase() {
        db.close()
    }
}
