package com.matan.tasks

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val accountType: String,
    val color: Int,
) {
    val isGoogle: Boolean get() = accountType == "com.google"
}

data class CalEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val calendarName: String,
) {
    /** Does this event fall on the given local day? */
    fun isOn(day: LocalDate): Boolean {
        return if (allDay) {
            val s = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
            var e = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
            if (!e.isAfter(s)) e = s.plusDays(1)
            !day.isBefore(s) && day.isBefore(e)
        } else {
            val zone = ZoneId.systemDefault()
            val ds = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val de = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            begin < de && (if (end > begin) end else begin + 1) > ds
        }
    }

    fun startMinOfDay(): Int {
        if (allDay) return -1
        val z = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault())
        return z.hour * 60 + z.minute
    }
}

data class EventState(
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val title: String,
)

object CalendarSync {
    const val DONE_PREFIX = "✓ "

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun listCalendars(ctx: Context): List<CalendarInfo> {
        val out = mutableListOf<CalendarInfo>()
        val proj = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_ACCESS_LEVEL,
            Calendars.CALENDAR_COLOR,
        )
        try {
            ctx.contentResolver.query(Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val access = c.getInt(4)
                    if (access >= Calendars.CAL_ACCESS_CONTRIBUTOR) {
                        out.add(
                            CalendarInfo(
                                id = c.getLong(0),
                                name = c.getString(1) ?: "",
                                account = c.getString(2) ?: "",
                                accountType = c.getString(3) ?: "",
                                color = c.getInt(5),
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return out.sortedWith(compareByDescending<CalendarInfo> { it.isGoogle }.thenBy { it.account }.thenBy { it.name })
    }

    private data class Times(val start: Long, val end: Long, val allDay: Boolean)

    private fun times(t: Task): Times? {
        val d = t.date ?: return null
        val tm = t.timeMin
        return if (tm == null) {
            val start = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            Times(start, start + 86_400_000L, true)
        } else {
            val start = d.atStartOfDay(ZoneId.systemDefault()).plusMinutes(tm.toLong()).toInstant().toEpochMilli()
            val dur = if (t.durationMin < 5) 5 else t.durationMin
            Times(start, start + dur * 60_000L, false)
        }
    }

    private fun description(t: Task): String {
        val sb = StringBuilder()
        if (t.notes.isNotBlank()) sb.append(t.notes.trim()).append("\n\n")
        if (t.subtasks.isNotEmpty()) {
            for (s in t.subtasks) sb.append(if (s.done) "☑ " else "☐ ").append(s.text).append("\n")
            sb.append("\n")
        }
        sb.append("רשימה: ").append(t.list).append("\n")
        sb.append("נוצר באפליקציית המשימות")
        return sb.toString()
    }

    private fun values(t: Task, tm: Times): ContentValues {
        val v = ContentValues()
        v.put(Events.TITLE, (if (t.done) DONE_PREFIX else "") + t.title)
        if (t.ownsEvent) v.put(Events.DESCRIPTION, description(t))
        v.put(Events.DTSTART, tm.start)
        v.put(Events.DTEND, tm.end)
        v.put(Events.ALL_DAY, if (tm.allDay) 1 else 0)
        v.put(Events.EVENT_TIMEZONE, if (tm.allDay) "UTC" else ZoneId.systemDefault().id)
        return v
    }

    /** Creates or updates the calendar event of a task. Returns the event id, or null on failure. */
    fun upsert(ctx: Context, t: Task, calendarId: Long): Long? {
        val tm = times(t) ?: return null
        val cr = ctx.contentResolver
        return try {
            val v = values(t, tm)
            var id = t.eventId
            if (id != null) {
                val exists = readEvent(ctx, id) != null
                if (!exists || cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), v, null, null) == 0) {
                    id = null
                }
            }
            if (id == null) {
                if (calendarId < 0) return null
                v.put(Events.CALENDAR_ID, calendarId)
                if (!t.ownsEvent) v.put(Events.DESCRIPTION, description(t))
                val uri = cr.insert(Events.CONTENT_URI, v) ?: return null
                id = ContentUris.parseId(uri)
            }
            if (t.ownsEvent) setReminder(cr, id, t.reminderMin)
            id
        } catch (e: Exception) {
            null
        }
    }

    private fun setReminder(cr: ContentResolver, eventId: Long, minutes: Int?) {
        try {
            cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()))
            if (minutes != null) {
                val r = ContentValues()
                r.put(Reminders.EVENT_ID, eventId)
                r.put(Reminders.MINUTES, minutes)
                r.put(Reminders.METHOD, Reminders.METHOD_ALERT)
                cr.insert(Reminders.CONTENT_URI, r)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun delete(ctx: Context, eventId: Long) {
        try {
            ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Returns current event state, or null if the event no longer exists. */
    fun readEvent(ctx: Context, eventId: Long): EventState? {
        val proj = arrayOf(Events.DTSTART, Events.DTEND, Events.ALL_DAY, Events.TITLE, Events.DELETED)
        return try {
            ctx.contentResolver.query(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId), proj, null, null, null
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                if (c.getInt(4) == 1) return@use null
                val b = c.getLong(0)
                val e = if (c.isNull(1)) 0L else c.getLong(1)
                EventState(b, e, c.getInt(2) == 1, c.getString(3) ?: "")
            }
        } catch (e: Exception) {
            null
        }
    }

    fun eventsBetween(ctx: Context, start: Long, end: Long): List<CalEvent> {
        val out = mutableListOf<CalEvent>()
        val b = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, start)
        ContentUris.appendId(b, end)
        val proj = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.DISPLAY_COLOR,
            Instances.CALENDAR_DISPLAY_NAME,
        )
        try {
            ctx.contentResolver.query(b.build(), proj, "${Instances.VISIBLE}=1", null, "${Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    out.add(
                        CalEvent(
                            eventId = c.getLong(0),
                            title = c.getString(1) ?: "(ללא כותרת)",
                            begin = c.getLong(2),
                            end = c.getLong(3),
                            allDay = c.getInt(4) == 1,
                            color = c.getInt(5),
                            calendarName = c.getString(6) ?: "",
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return out
    }

    /** Asks Android to sync the calendar account with Google right now. */
    fun requestSync(cal: CalendarInfo?) {
        if (cal == null) return
        try {
            val extras = Bundle()
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            ContentResolver.requestSync(Account(cal.account, cal.accountType), CalendarContract.AUTHORITY, extras)
        } catch (e: Exception) {
            // ignore
        }
    }
}
