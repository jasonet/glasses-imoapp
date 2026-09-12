package com.jacb.inmocards

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CardDatabase(context: Context, name: String = "inmo_cards.db") :
    SQLiteOpenHelper(context, name, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at INTEGER NOT NULL,
                deck_count INTEGER NOT NULL,
                game_mode TEXT NOT NULL DEFAULT 'BLACKJACK',
                active INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE observations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                rank TEXT NOT NULL,
                detected_at INTEGER NOT NULL,
                undone INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN game_mode TEXT NOT NULL DEFAULT 'BLACKJACK'")
        }
    }

    fun activeSession(): ShoeSession {
        readableDatabase.rawQuery(
            "SELECT id, deck_count, game_mode FROM sessions WHERE active = 1 ORDER BY id DESC LIMIT 1",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) return ShoeSession(
                cursor.getLong(0),
                ShoeConfig(GameMode.valueOf(cursor.getString(2)), cursor.getInt(1))
            )
        }
        return resetSession(ShoeConfig())
    }

    fun loadRanks(sessionId: Long): List<CardRank> {
        val ranks = mutableListOf<CardRank>()
        readableDatabase.rawQuery(
            "SELECT rank FROM observations WHERE session_id = ? AND undone = 0 ORDER BY id",
            arrayOf(sessionId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) CardRank.fromLabel(cursor.getString(0))?.let(ranks::add)
        }
        return ranks
    }

    fun record(sessionId: Long, rank: CardRank) {
        writableDatabase.insertOrThrow("observations", null, ContentValues().apply {
            put("session_id", sessionId)
            put("rank", rank.label)
            put("detected_at", System.currentTimeMillis())
        })
    }

    fun undoLast(sessionId: Long): CardRank? {
        val db = writableDatabase
        db.rawQuery(
            "SELECT id, rank FROM observations WHERE session_id = ? AND undone = 0 ORDER BY id DESC LIMIT 1",
            arrayOf(sessionId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(0)
            val rank = CardRank.fromLabel(cursor.getString(1)) ?: return null
            db.update("observations", ContentValues().apply { put("undone", 1) }, "id = ?", arrayOf(id.toString()))
            return rank
        }
    }

    fun resetSession(config: ShoeConfig): ShoeSession {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update("sessions", ContentValues().apply { put("active", 0) }, "active = 1", null)
            val id = createSession(db, config)
            db.setTransactionSuccessful()
            return ShoeSession(id, config)
        } finally {
            db.endTransaction()
        }
    }

    private fun createSession(db: SQLiteDatabase, config: ShoeConfig): Long = db.insertOrThrow(
        "sessions",
        null,
        ContentValues().apply {
            put("started_at", System.currentTimeMillis())
            put("deck_count", config.decks)
            put("game_mode", config.mode.name)
            put("active", 1)
        }
    )
}
