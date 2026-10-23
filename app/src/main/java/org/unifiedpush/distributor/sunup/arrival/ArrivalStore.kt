package org.unifiedpush.distributor.sunup.arrival

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Separate from the library-owned registration schema. No process-local preference cache. */
class ArrivalStore(context: Context) : SQLiteOpenHelper(context, "push_arrival.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE settings (packageName TEXT PRIMARY KEY, enabled INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE seen (server TEXT, uaid TEXT, channel TEXT, version TEXT, received INTEGER, PRIMARY KEY(server, uaid, channel, version))"
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) = Unit

    fun isEnabled(packageName: String): Boolean = readableDatabase.rawQuery(
        "SELECT enabled FROM settings WHERE packageName = ?",
        arrayOf(packageName)
    ).use { it.moveToFirst() && it.getInt(0) == 1 }

    fun setEnabled(packageName: String, enabled: Boolean) {
        val result = writableDatabase.insertWithOnConflict(
            "settings",
            null,
            ContentValues().apply {
                put("packageName", packageName)
                put("enabled", if (enabled) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        check(result != -1L) { "Could not persist push arrival setting" }
    }

    /** Serialize the final post against cross-process disable/remove writes. */
    fun ifEnabled(packageName: String, post: () -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (isEnabled(packageName)) post()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun removePackage(packageName: String) {
        writableDatabase.delete("settings", "packageName = ?", arrayOf(packageName))
    }

    /** Deduplicate reminders only. Delivery and ACK must never depend on this result. */
    fun firstSeen(
        server: String,
        uaid: String,
        channel: String,
        version: String,
        now: Long
    ): Boolean {
        // An absent transport identity isn't evidence of a duplicate.
        if (version.isEmpty()) return true
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("seen", "received < ?", arrayOf((now - RETENTION_MS).toString()))
            val inserted = db.insertWithOnConflict(
                "seen",
                null,
                ContentValues().apply {
                    put("server", server)
                    put("uaid", uaid)
                    put("channel", channel)
                    put("version", version)
                    put("received", now)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            ) != -1L
            db.execSQL("DELETE FROM seen WHERE rowid NOT IN (SELECT rowid FROM seen ORDER BY received DESC, rowid DESC LIMIT 512)")
            db.setTransactionSuccessful()
            return inserted
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val RETENTION_MS = 24 * 60 * 60 * 1000L
    }
}
