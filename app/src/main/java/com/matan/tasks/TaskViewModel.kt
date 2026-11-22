package com.matan.tasks

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val store = TaskStore(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var tasks by mutableStateOf(store.load())
        private set
    var calendarId by mutableStateOf(prefs.getLong("calendarId", -1L))
        private set
    var defaultReminder by mutableStateOf(prefs.getInt("defaultReminder", 15).let { if (it < 0) null else it })
        private set
    var defaultSync by mutableStateOf(prefs.getBoolean("defaultSync", true))
        private set
    var showEvents by mutableStateOf(prefs.getBoolean("showEvents", true))
        private set
    var lists by mutableStateOf(loadLists())
        private set
    var hasCalPermission by mutableStateOf(CalendarSync.hasPermission(app))
        private set
    var calendars by mutableStateOf<List<CalendarInfo>>(emptyList())
        private set
    var events by mutableStateOf<List<CalEvent>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    val selectedCalendar: CalendarInfo? get() = calendars.firstOrNull { it.id == calendarId }
    val calendarReady: Boolean get() = hasCalPermission && calendarId >= 0

    init {
        if (hasCalPermission) refresh()
    }

    private fun loadLists(): List<String> {
        val s = prefs.getString("lists", null) ?: return listOf("כללי", "עבודה", "אישי", "לימודים", "בית")
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { a.getString(it) }.ifEmpty { listOf("כללי") }
        } catch (e: Exception) {
            listOf("כללי")
        }
    }

    private fun saveLists() {
        val a = JSONArray()
        lists.forEach { a.put(it) }
        prefs.edit().putString("lists", a.toString()).apply()
    }

    private fun persist() = store.save(tasks)

    // ---------- lifecycle / permission ----------

    fun onResume() {
        val had = hasCalPermission
        hasCalPermission = CalendarSync.hasPermission(ctx)
        if (hasCalPermission) {
            refresh(syncPending = !had)
        }
    }

    fun onPermissionResult() {
        hasCalPermission = CalendarSync.hasPermission(ctx)
        if (hasCalPermission) {
            message = "מחובר ליומן ✓"
            refresh(syncPending = true)
        } else {
            message = "בלי הרשאת יומן אין סנכרון ליומן גוגל"
        }
    }

    /** Reload calendars, pull changes made in Google Calendar, load events. */
    fun refresh(syncPending: Boolean = false) {
        if (!hasCalPermission) return
        viewModelScope.launch {
            val cals = withContext(Dispatchers.IO) { CalendarSync.listCalendars(ctx) }
            calendars = cals
            if (cals.none { it.id == calendarId }) {
                val pick = cals.firstOrNull { it.isGoogle && it.name == it.account } ?: cals.firstOrNull { it.isGoogle } ?: cals.firstOrNull()
                setCalendar(pick?.id ?: -1L)
            }
            pullFromCalendar()
            if (syncPending) pushPending()
            loadEvents()
        }
    }

    private suspend fun loadEvents() {
        if (!hasCalPermission) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.minusDays(14).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(90).atStartOfDay(zone).toInstant().toEpochMilli()
        events = withContext(Dispatchers.IO) { CalendarSync.eventsBetween(ctx, start, end) }
    }

    /** Apply changes made directly in Google Calendar (moved/renamed/deleted) back to the tasks. */
    private suspend fun pullFromCalendar() {
        val snapshot = tasks.filter { it.syncCalendar && it.eventId != null }
        if (snapshot.isEmpty()) return
        val changed = withContext(Dispatchers.IO) {
            val out = mutableMapOf<Long, Task>()
            for (t in snapshot) {
                val ev = CalendarSync.readEvent(ctx, t.eventId!!)
                if (ev == null) {
                    out[t.id] = t.copy(eventId = null, syncCalendar = false)
                    continue
                }
                var nt = t
                if (ev.allDay) {
                    val d = Instant.ofEpochMilli(ev.begin).atZone(ZoneOffset.UTC).toLocalDate()
                    nt = nt.copy(date = d, timeMin = null)
                } else {
                    val z = Instant.ofEpochMilli(ev.begin).atZone(ZoneId.systemDefault())
                    val dur = if (ev.end > ev.begin) ((ev.end - ev.begin) / 60_000L).toInt() else t.durationMin
                    nt = nt.copy(date = z.toLocalDate(), timeMin = z.hour * 60 + z.minute, durationMin = dur)
                }
                val title = ev.title.removePrefix(CalendarSync.DONE_PREFIX).trim()
                if (title.isNotEmpty()) nt = nt.copy(title = title)
                if (nt != t) out[t.id] = nt
            }
            out
        }
        if (changed.isNotEmpty()) {
            tasks = tasks.map { changed[it.id] ?: it }
            persist()
        }
    }

    /** Push tasks that should be in the calendar but aren't yet (e.g. created before connecting). */
    private suspend fun pushPending() {
        if (!calendarReady) return
        val pending = tasks.filter { it.syncCalendar && it.date != null && it.eventId == null }
        if (pending.isEmpty()) return
        val ids = withContext(Dispatchers.IO) {
            pending.associate { it.id to CalendarSync.upsert(ctx, it, calendarId) }
        }
        tasks = tasks.map { t -> ids[t.id]?.let { t.copy(eventId = it) } ?: t }
        persist()
        CalendarSync.requestSync(selectedCalendar)
    }

    // ---------- settings ----------

    fun setCalendar(id: Long) {
        calendarId = id
        prefs.edit().putLong("calendarId", id).apply()
    }

    fun updateDefaultReminder(m: Int?) {
        defaultReminder = m
        prefs.edit().putInt("defaultReminder", m ?: -1).apply()
    }

    fun updateDefaultSync(b: Boolean) {
        defaultSync = b
        prefs.edit().putBoolean("defaultSync", b).apply()
    }

    fun updateShowEvents(b: Boolean) {
        showEvents = b
        prefs.edit().putBoolean("showEvents", b).apply()
    }

    fun addList(name: String) {
        val n = name.trim()
        if (n.isEmpty() || lists.contains(n)) return
        lists = lists + n
        saveLists()
    }

    fun removeList(name: String) {
        if (lists.size <= 1) return
        lists = lists - name
        saveLists()
    }

    // ---------- tasks ----------

    fun newTask(date: LocalDate? = LocalDate.now(), list: String? = null): Task = Task(
        id = System.currentTimeMillis(),
        title = "",
        list = list ?: lists.firstOrNull() ?: "כללי",
        date = date,
        reminderMin = defaultReminder,
        syncCalendar = defaultSync,
    )

    fun quickAdd(title: String, date: LocalDate?) {
        if (title.isBlank()) return
        saveTask(newTask(date).copy(title = title.trim()))
    }

    fun saveTask(t: Task) {
        val exists = tasks.any { it.id == t.id }
        tasks = if (exists) tasks.map { if (it.id == t.id) t else it } else tasks + t
        persist()
        syncTask(t)
    }

    private fun syncTask(t: Task) {
        if (!hasCalPermission) return
        val wantEvent = t.syncCalendar && t.date != null
        viewModelScope.launch {
            if (wantEvent) {
                if (calendarId < 0 && t.eventId == null) return@launch
                val id = withContext(Dispatchers.IO) { CalendarSync.upsert(ctx, t, calendarId) }
                if (id != t.eventId) {
                    tasks = tasks.map { if (it.id == t.id) it.copy(eventId = id) else it }
                    persist()
                }
                if (id == null) message = "לא הצלחתי לעדכן את היומן"
            } else if (t.eventId != null) {
                val evId = t.eventId
                if (t.ownsEvent) withContext(Dispatchers.IO) { CalendarSync.delete(ctx, evId) }
                tasks = tasks.map { if (it.id == t.id) it.copy(eventId = null) else it }
                persist()
            }
            CalendarSync.requestSync(selectedCalendar)
            loadEvents()
        }
    }

    fun deleteTask(t: Task) {
        tasks = tasks.filter { it.id != t.id }
        persist()
        val evId = t.eventId
        if (evId != null && t.ownsEvent && hasCalPermission) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { CalendarSync.delete(ctx, evId) }
                CalendarSync.requestSync(selectedCalendar)
                loadEvents()
            }
        }
        message = "המשימה נמחקה"
    }

    fun toggleDone(t: Task) {
        val nowDone = !t.done
        val updated = t.copy(done = nowDone, doneAt = if (nowDone) System.currentTimeMillis() else null)
        saveTask(updated)
        val d = t.date
        if (nowDone && t.repeat != 0 && d != null) {
            val next = when (t.repeat) {
                1 -> d.plusDays(1)
                2 -> d.plusWeeks(1)
                else -> d.plusMonths(1)
            }
            saveTask(
                t.copy(
                    id = System.currentTimeMillis() + 1,
                    date = next,
                    done = false,
                    doneAt = null,
                    eventId = null,
                    ownsEvent = true,
                    createdAt = System.currentTimeMillis(),
                    subtasks = t.subtasks.map { it.copy(done = false) },
                )
            )
            message = "נוצרה המשימה הבאה ל-${next.dayOfMonth}/${next.monthValue}"
        }
    }

    fun toggleSubtask(t: Task, index: Int) {
        val subs = t.subtasks.toMutableList()
        if (index !in subs.indices) return
        subs[index] = subs[index].copy(done = !subs[index].done)
        saveTask(t.copy(subtasks = subs))
    }

    /** Turn an existing Google Calendar event into a task linked to it. */
    fun importEvent(e: CalEvent) {
        if (tasks.any { it.eventId == e.eventId }) {
            message = "האירוע כבר מקושר למשימה"
            return
        }
        val date: LocalDate
        val time: Int?
        val dur: Int
        if (e.allDay) {
            date = Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate()
            time = null
            dur = 60
        } else {
            val z = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault())
            date = z.toLocalDate()
            time = z.hour * 60 + z.minute
            dur = if (e.end > e.begin) ((e.end - e.begin) / 60_000L).toInt() else 60
        }
        val t = Task(
            id = System.currentTimeMillis(),
            title = e.title.removePrefix(CalendarSync.DONE_PREFIX),
            list = lists.firstOrNull() ?: "כללי",
            date = date,
            timeMin = time,
            durationMin = dur,
            reminderMin = null,
            syncCalendar = true,
            eventId = e.eventId,
            ownsEvent = false,
        )
        tasks = tasks + t
        persist()
        message = "נוספה משימה מהיומן"
    }

    fun syncAllNow() {
        if (!calendarReady) {
            message = "בחר יומן בהגדרות קודם"
            return
        }
        busy = true
        viewModelScope.launch {
            pullFromCalendar()
            val toSync = tasks.filter { it.syncCalendar && it.date != null }
            val ids = withContext(Dispatchers.IO) {
                toSync.associate { it.id to CalendarSync.upsert(ctx, it, calendarId) }
            }
            tasks = tasks.map { t -> if (ids.containsKey(t.id)) t.copy(eventId = ids[t.id]) else t }
            persist()
            CalendarSync.requestSync(selectedCalendar)
            loadEvents()
            busy = false
            message = "סונכרנו ${ids.values.count { it != null }} משימות ליומן"
        }
    }

    fun linkedEventIds(): Set<Long> = tasks.mapNotNull { it.eventId }.toSet()
}
