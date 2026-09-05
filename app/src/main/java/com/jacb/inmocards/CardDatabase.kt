package com.jacb.inmocards

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CardDatabase(context: Context) : SQLiteOpenHelper(context, "inmo_cards.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at INTEGER NOT NULL,
                deck_count INTEGER NOT NULL,
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun activeSessionId(): Long {
        readableDatabase.rawQuery(
            "SELECT id FROM sessions WHERE active = 1 ORDER BY id DESC LIMIT 1",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return createSession()
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

    fun resetSession(): Long {
        writableDatabase.update("sessions", ContentValues().apply { put("active", 0) }, "active = 1", null)
        return createSession()
    }

    private fun createSession(): Long = writableDatabase.insertOrThrow(
        "sessions",
        null,
        ContentValues().apply {
            put("started_at", System.currentTimeMillis())
            put("deck_count", 2)
            put("active", 1)
        }
    )
}
