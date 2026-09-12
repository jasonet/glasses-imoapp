package com.jacb.inmocards

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardDatabaseTest {
    private lateinit var context: Context
    private val testName = "inmo_cards_migration_test.db"
    private var helper: CardDatabase? = null

    @Before
    fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testName)
    }

    @After
    fun cleanUp() {
        helper?.close()
        context.deleteDatabase(testName)
    }

    private fun open(): CardDatabase = CardDatabase(context, testName).also { helper = it }

    @Test
    fun freshInstallDefaultsToTwoDeckBlackjack() {
        assertEquals(ShoeConfig(), open().activeSession().config)
    }

    @Test
    fun migratesV1ThenRestoresEveryPresetAndItsObservations() {
        context.openOrCreateDatabase(testName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "started_at INTEGER NOT NULL, deck_count INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1)")
            db.execSQL("CREATE TABLE observations (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "session_id INTEGER NOT NULL, rank TEXT NOT NULL, detected_at INTEGER NOT NULL, " +
                "undone INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("INSERT INTO sessions VALUES (1, 12345, 2, 1)")
            db.execSQL("INSERT INTO observations VALUES (1, 1, 'A', 12346, 0)")
            db.execSQL("INSERT INTO observations VALUES (2, 1, 'K', 12347, 1)")
            db.version = 1
        }
        var db = open()
        assertEquals(2, db.readableDatabase.version)
        assertEquals(ShoeSession(1, ShoeConfig()), db.activeSession())
        assertEquals(listOf(CardRank.ACE), db.loadRanks(1))
        db.readableDatabase.rawQuery("SELECT detected_at, undone FROM observations WHERE id=2", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(12347L, it.getLong(0))
            assertEquals(1, it.getInt(1))
        }
        for (config in ShoeConfig.presets) {
            val session = db.resetSession(config)
            db.record(session.id, CardRank.QUEEN)
            db.record(session.id, CardRank.THREE)
            assertEquals(CardRank.THREE, db.undoLast(session.id))
            db.close()
            db = open()
            assertEquals(session, db.activeSession())
            val ranks = db.loadRanks(session.id)
            assertEquals(listOf(CardRank.QUEEN), ranks)
            assertEquals(config.totalCards - 1, ProbabilityEngine(ranks, config.decks).totalRemaining)
            assertEquals(listOf(CardRank.ACE), db.loadRanks(1))
            db.readableDatabase.rawQuery("SELECT COUNT(*) FROM sessions WHERE active=1", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun failedResetKeepsPreviousSessionActive() {
        val db = open()
        val original = db.activeSession()
        db.record(original.id, CardRank.TWO)
        db.writableDatabase.execSQL("CREATE TRIGGER fail_test_insert BEFORE INSERT ON sessions " +
            "BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END")
        try {
            db.resetSession(ShoeConfig(GameMode.BACCARAT, 8))
            fail("Expected write failure")
        } catch (expected: SQLiteException) {
            assertEquals(original, db.activeSession())
            assertEquals(listOf(CardRank.TWO), db.loadRanks(original.id))
        }
    }
}
