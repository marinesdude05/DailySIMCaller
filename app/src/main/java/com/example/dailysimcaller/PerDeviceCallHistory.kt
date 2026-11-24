package com.example.dailysimcaller

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Persistent, app-private outgoing call history ON EACH PHONE.
 * Does not request READ_CALL_LOG or upload numbers to a third party.
 * A later authenticated sync can append entries into a separate tab per device
 * in ONE shared Google Sheets spreadsheet.
 */
internal object PerDeviceCallHistory {
    private const val PREFS = "per_device_call_history"
    private const val DEVICE_KEY = "device_id"
    private const val TABLE = "call_history"

    internal data class Entry(
        val id: Long,
        val deviceId: String,
        val phoneNumber: String,
        val dialedAtMs: Long,
        val timeZoneId: String,
        val connectedSeconds: Long,
        val outcome: String
    ) {
        fun localDate(): String = format(dialedAtMs, "yyyy-MM-dd", timeZoneId)
        fun localTime(): String = format(dialedAtMs, "HH:mm:ss", timeZoneId)
    }

    private data class Tracked(val rowId: Long, var connectedAtElapsedMs: Long? = null)

    private class HistoryDb(context: Context) :
        SQLiteOpenHelper(context, "per_device_call_history.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE " + TABLE + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "device_id TEXT NOT NULL," +
                    "phone_number TEXT NOT NULL," +
                    "dialed_at_ms INTEGER NOT NULL," +
                    "time_zone_id TEXT NOT NULL," +
                    "connected_at_ms INTEGER," +
                    "connected_seconds INTEGER NOT NULL DEFAULT 0," +
                    "outcome TEXT NOT NULL DEFAULT 'Dialing'," +
                    "synced INTEGER NOT NULL DEFAULT 0)"
            )
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Preserve locally recorded calls when enabling cloud sync.
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    private val tracked = IdentityHashMap<Call, Tracked>()
    private var helper: HistoryDb? = null

    var version by mutableIntStateOf(0)
        private set

    @Synchronized
    private fun database(context: Context): SQLiteDatabase {
        if (helper == null) helper = HistoryDb(context.applicationContext)
        return helper!!.writableDatabase
    }

    @Synchronized
    fun deviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(DEVICE_KEY, null)
        if (!existing.isNullOrBlank()) return existing
        // App-generated identifier: not a hardware serial, phone number, or IMEI.
        val created = "C9-" + UUID.randomUUID().toString().substring(0, 8).uppercase(Locale.US)
        prefs.edit().putString(DEVICE_KEY, created).commit()
        return created
    }

    @Synchronized
    fun onCallAdded(context: Context, call: Call) {
        if (tracked.containsKey(call)) return
        if (Build.VERSION.SDK_INT >= 29) {
            if (call.details.callDirection != Call.Details.DIRECTION_OUTGOING) return
        } else if (call.state == Call.STATE_RINGING) {
            return
        }
        val handle = call.details.handle ?: return
        if (handle.scheme != "tel") return
        val phoneNumber = PhoneCsv.cleanPhoneNumber(handle.schemeSpecificPart) ?: return
        runCatching {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("device_id", deviceId(context))
                put("phone_number", phoneNumber)
                put("dialed_at_ms", now)
                put("time_zone_id", TimeZone.getDefault().id)
                put("outcome", "Dialing")
            }
            val id = database(context).insertOrThrow(TABLE, null, values)
            val record = Tracked(id)
            tracked[call] = record
            version++
            if (call.state == Call.STATE_ACTIVE) onCallState(context, call, Call.STATE_ACTIVE)
        }
    }

    @Synchronized
    fun onCallState(context: Context, call: Call, state: Int) {
        val record = tracked[call] ?: return
        if (state == Call.STATE_ACTIVE && record.connectedAtElapsedMs == null) {
            record.connectedAtElapsedMs = SystemClock.elapsedRealtime()
            runCatching {
                val values = ContentValues().apply {
                    put("connected_at_ms", System.currentTimeMillis())
                    put("outcome", "Connected")
                }
                database(context).update(TABLE, values, "id=?", arrayOf(record.rowId.toString()))
                version++
            }
        }
    }

    @Synchronized
    fun onCallRemoved(context: Context, call: Call) {
        val record = tracked.remove(call) ?: return
        val elapsed = record.connectedAtElapsedMs
        val seconds = if (elapsed == null) 0L
                      else ((SystemClock.elapsedRealtime() - elapsed) / 1000L).coerceAtLeast(0L)
        runCatching {
            val values = ContentValues().apply {
                put("connected_seconds", seconds)
                put("outcome", if (elapsed == null) "Not connected" else "Ended")
            }
            database(context).update(TABLE, values, "id=?", arrayOf(record.rowId.toString()))
            version++
        }
    }


    /** Only completed calls are eligible. Unfinished dial attempts never go to cloud. */
    @Synchronized
    fun pending(context: Context, limit: Int = 60): List<Entry> {
        val result = mutableListOf<Entry>()
        database(context).query(
            TABLE,
            arrayOf("id", "device_id", "phone_number", "dialed_at_ms",
                "time_zone_id", "connected_seconds", "outcome"),
            "synced=0 AND outcome IN ('Ended', 'Not connected')",
            null, null, null, "id ASC", limit.coerceIn(1, 100).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Entry(
                    id = cursor.getLong(0),
                    deviceId = cursor.getString(1),
                    phoneNumber = cursor.getString(2),
                    dialedAtMs = cursor.getLong(3),
                    timeZoneId = cursor.getString(4),
                    connectedSeconds = cursor.getLong(5),
                    outcome = cursor.getString(6)
                )
            }
        }
        return result
    }

    @Synchronized
    fun pendingCount(context: Context): Int {
        database(context).rawQuery(
            "SELECT COUNT(*) FROM " + TABLE +
                " WHERE synced=0 AND outcome IN ('Ended', 'Not connected')", null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    @Synchronized
    fun markSynced(context: Context, callId: Long) {
        val values = ContentValues().apply { put("synced", 1) }
        val changed = database(context).update(
            TABLE, values,
            "id=? AND synced=0 AND outcome IN ('Ended', 'Not connected')",
            arrayOf(callId.toString())
        )
        if (changed > 0) version++
    }

    @Synchronized
    fun recent(context: Context, limit: Int = 30): List<Entry> {
        val output = mutableListOf<Entry>()
        runCatching {
            database(context).query(
                TABLE,
                arrayOf("id", "device_id", "phone_number", "dialed_at_ms",
                        "time_zone_id", "connected_seconds", "outcome"),
                null, null, null, null, "id DESC", limit.coerceIn(1, 1000).toString()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    output += Entry(
                        id = cursor.getLong(0),
                        deviceId = cursor.getString(1),
                        phoneNumber = cursor.getString(2),
                        dialedAtMs = cursor.getLong(3),
                        timeZoneId = cursor.getString(4),
                        connectedSeconds = cursor.getLong(5),
                        outcome = cursor.getString(6)
                    )
                }
            }
        }
        return output
    }

    /** One row = one call. The destination sheet determines per-device separation. */
    fun csvHeader(): String = "call_id,device_id,phone_number,date,time,time_zone,connected_seconds,result"

    fun rowAsCsv(row: Entry): String = listOf(
        row.id.toString(), row.deviceId, row.phoneNumber,
        row.localDate(), row.localTime(), row.timeZoneId,
        row.connectedSeconds.toString(), row.outcome
    ).joinToString(",") { value -> "\"" + value.replace("\"", "\"\"") + "\"" }

    private fun format(epochMs: Long, pattern: String, tzId: String): String =
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone(tzId)
        }.format(Date(epochMs))
}
