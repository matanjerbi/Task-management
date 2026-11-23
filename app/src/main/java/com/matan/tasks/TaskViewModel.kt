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
import org.json.JSONObject
import java.time.LocalTime
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

    // ---- assistant state ----
    var userName by mutableStateOf(prefs.getString("userName", "מתן") ?: "מתן")
        private set
    var apiKey by mutableStateOf(prefs.getString("apiKey", "") ?: "")
        private set
    var aiModel by mutableStateOf(prefs.getString("aiModel", "claude-sonnet-5-5") ?: "claude-sonnet-5-5")
        private set
    var dayEnd by mutableStateOf(prefs.getInt("dayEnd", 22 * 60))
        private set
    /** Non-null while an assistant request runs; holds a short status line. */
    var aiBusy by mutableStateOf<String?>(null)
        private set
    /** Tasks proposed by the assistant, waiting for review. */
    var drafts by mutableStateOf<List<Task>?>(null)
        private set
    var draftsMessage by mutableStateOf("")
        private set
    var draftsReplace by mutableStateOf(false)
        private set
    var plan by mutableStateOf<DayPlan?>(null)
        private set
    var nextUp by mutableStateOf<Pair<Long, String>?>(null)
        private set

    val aiEnabled: Boolean get() = apiKey.isNotBlank()

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

    // =====================================================================
    // Smart capture, inbox, focus, stats
    // =====================================================================

    val inboxTasks: List<Task> get() = tasks.filter { it.inbox && !it.done }

    /** Quick capture with local Hebrew parsing. Returns a short description of what was understood. */
    fun capture(text: String, defaultDate: LocalDate? = null): String? {
        if (text.isBlank()) return null
        val p = SmartParse.parse(text, lists)
        val list = p.list
        if (list != null && !lists.contains(list)) addList(list)
        val date = p.date ?: defaultDate
        val t = newTask(date, list).copy(
            id = nextId(),
            title = p.title,
            timeMin = p.timeMin,
            priority = p.priority ?: 1,
            durationMin = p.durationMin ?: if (p.timeMin != null) 60 else 30,
            inbox = date == null && list == null,
        )
        saveTask(t)
        return when {
            t.inbox -> "נכנס לתיבה 📥"
            date != null -> "נקבע ל" + dateLabel(date) + (p.timeMin?.let { " ב-" + timeLabel(it) } ?: "")
            else -> "נשמר ברשימת " + t.list
        }
    }

    private var lastId = 0L
    private fun nextId(): Long {
        var id = System.currentTimeMillis()
        if (id <= lastId) id = lastId + 1
        lastId = id
        return id
    }

    fun schedule(t: Task, date: LocalDate?, someday: Boolean = false) {
        saveTask(
            t.copy(
                date = date,
                timeMin = if (date == null) null else t.timeMin,
                inbox = false,
                list = if (someday && !lists.contains("מתישהו")) t.list else t.list,
            )
        )
    }

    fun completeNow(t: Task) {
        if (!t.done) toggleDone(t.copy(inbox = false))
    }

    fun toggleFocus(t: Task) {
        val turningOn = !t.focus
        if (turningOn) {
            val today = LocalDate.now()
            val count = tasks.count { it.focus && !it.done && it.id != t.id }
            if (count >= 3) message = "כבר יש 3 משימות פוקוס – פחות זה יותר 🙂"
            saveTask(t.copy(focus = true, date = t.date ?: today, inbox = false))
        } else {
            saveTask(t.copy(focus = false))
        }
    }

    fun moveOverdueToToday() {
        val today = LocalDate.now()
        val overdue = tasks.filter { !it.done && it.date?.isBefore(today) == true }
        overdue.forEach { saveTask(it.copy(date = today, timeMin = null)) }
        message = "הועברו ${overdue.size} משימות להיום"
    }

    fun addFocusMinutes(t: Task, minutes: Int) {
        val cur = tasks.firstOrNull { it.id == t.id } ?: return
        val updated = cur.copy(focusMinutes = cur.focusMinutes + minutes)
        tasks = tasks.map { if (it.id == t.id) updated else it }
        persist()
    }

    fun completedOn(day: LocalDate): Int {
        val zone = ZoneId.systemDefault()
        return tasks.count { t ->
            val at = t.doneAt
            t.done && at != null && Instant.ofEpochMilli(at).atZone(zone).toLocalDate() == day
        }
    }

    fun streak(): Int {
        var d = LocalDate.now()
        if (completedOn(d) == 0) d = d.minusDays(1)
        var n = 0
        while (completedOn(d) > 0 && n < 365) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun focusMinutesTotal(): Int = tasks.sumOf { it.focusMinutes }

    /** Local "what's most important now" score. */
    fun score(t: Task): Int {
        val today = LocalDate.now()
        val d = t.date
        var s = t.priority * 15
        if (t.focus) s += 50
        if (d != null && d.isBefore(today)) s += 40
        if (d == today) s += 25
        if (d == today.plusDays(1)) s += 8
        if (t.durationMin <= 15) s += 6
        val tm = t.timeMin
        if (d == today && tm != null) {
            val now = LocalTime.now().let { it.hour * 60 + it.minute }
            if (tm in (now - 30)..(now + 60)) s += 35
        }
        return s
    }

    // ---- settings for assistant ----

    fun updateUserName(n: String) {
        userName = n
        prefs.edit().putString("userName", n).apply()
    }

    fun updateApiKey(k: String) {
        apiKey = k.trim()
        prefs.edit().putString("apiKey", apiKey).apply()
    }

    fun updateAiModel(m: String) {
        aiModel = m
        prefs.edit().putString("aiModel", m).apply()
    }

    fun updateDayEnd(m: Int) {
        dayEnd = m
        prefs.edit().putInt("dayEnd", m).apply()
    }

    private fun client() = ClaudeClient(apiKey, aiModel)

    private fun runAi(status: String, block: suspend () -> Unit) {
        if (aiBusy != null) return
        aiBusy = status
        viewModelScope.launch {
            try {
                block()
            } catch (e: AiException) {
                message = e.message
            } catch (e: Exception) {
                message = "משהו השתבש: " + (e.message ?: "")
            } finally {
                aiBusy = null
            }
        }
    }

    // ---- brain dump ----

    fun brainDump(text: String) {
        if (text.isBlank()) return
        if (!aiEnabled) {
            // Without Claude: split lines / sentences and parse each locally.
            val parts = text.split('\n', ';', '•').flatMap { it.split(Regex("(?<=[.!?])\\s+")) }
                .map { it.trim().trimStart('-', '*').trim() }.filter { it.length > 1 }
            val base = nextId()
            drafts = parts.mapIndexed { i, line ->
                val p = SmartParse.parse(line, lists)
                newTask(p.date, p.list).copy(
                    id = base + i,
                    title = p.title,
                    timeMin = p.timeMin,
                    priority = p.priority ?: 1,
                    durationMin = p.durationMin ?: 30,
                    inbox = p.date == null,
                )
            }
            lastId = base + parts.size
            draftsMessage = "פירקתי ל-${parts.size} משימות. חבר את Claude בהגדרות כדי לקבל פירוק חכם יותר ✨"
            draftsReplace = false
            return
        }
        runAi("Claude עושה סדר בראש שלך…") {
            val res = withContext(Dispatchers.IO) { client().askJson(Assistant.brainDumpSystem(lists), text, 4000) }
            val arr = res.optJSONArray("tasks") ?: JSONArray()
            val base = nextId()
            val out = (0 until arr.length()).map { i ->
                Assistant.taskFrom(arr.getJSONObject(i), newTask(null).copy(id = base + i, inbox = true, durationMin = 30), lists)
            }.filter { it.title.isNotBlank() }
            lastId = base + out.size
            drafts = out
            draftsMessage = res.optString("message", "")
            draftsReplace = false
        }
    }

    fun organizeInbox() {
        val items = inboxTasks
        if (items.isEmpty()) {
            message = "התיבה ריקה 🎉"
            return
        }
        if (!aiEnabled) {
            message = "חבר את Claude בהגדרות כדי לסדר אוטומטית"
            return
        }
        runAi("Claude מסדר את התיבה…") {
            val res = withContext(Dispatchers.IO) {
                client().askJson(Assistant.organizeSystem(lists), Assistant.describeTasks(items), 4000)
            }
            val arr = res.optJSONArray("items") ?: JSONArray()
            val byId = items.associateBy { it.id }
            val out = mutableListOf<Task>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.opt("id")?.toString()?.toLongOrNull() ?: continue
                val base = byId[id] ?: continue
                out.add(Assistant.taskFrom(o, base.copy(inbox = false), lists).copy(inbox = false))
            }
            drafts = out
            draftsMessage = res.optString("message", "")
            draftsReplace = true
        }
    }

    fun acceptDrafts(selected: List<Task>) {
        selected.forEach { saveTask(it) }
        val n = selected.size
        drafts = null
        message = if (draftsReplace) "סודרו $n משימות ✓" else "נוספו $n משימות ✓"
    }

    fun discardDrafts() {
        drafts = null
    }

    // ---- break down a task ----

    fun breakdown(t: Task, onResult: (Task) -> Unit) {
        if (!aiEnabled) {
            message = "חבר את Claude בהגדרות כדי לפרק משימות"
            return
        }
        runAi("Claude מפרק את המשימה לצעדים…") {
            val user = "משימה: ${t.title}" + (if (t.notes.isNotBlank()) "\nהערות: ${t.notes}" else "")
            val res = withContext(Dispatchers.IO) { client().askJson(Assistant.BREAKDOWN_SYSTEM, user, 1200) }
            val subs = Assistant.strings(res.optJSONArray("subtasks"))
            val dur = res.optInt("duration", t.durationMin).coerceIn(5, 600)
            onResult(t.copy(subtasks = t.subtasks + subs.map { SubTask(it) }, durationMin = dur))
            val tip = res.optString("tip", "")
            if (tip.isNotBlank()) message = "💡 $tip"
        }
    }

    // ---- what now ----

    fun whatNow() {
        val today = LocalDate.now()
        val candidates = tasks.filter { !it.done && !it.inbox && (it.date == null || !it.date.isAfter(today)) }
        if (candidates.isEmpty()) {
            nextUp = null
            message = "אין משימות פתוחות להיום – זמן מנוחה 😌"
            return
        }
        val best = candidates.maxByOrNull { score(it) }!!
        if (!aiEnabled) {
            nextUp = best.id to reasonFor(best)
            return
        }
        runAi("Claude חושב מה הכי נכון עכשיו…") {
            val todayEvents = events.filter { it.isOn(today) }
            val user = Assistant.describeTasks(candidates.sortedByDescending { score(it) }.take(25)) +
                "\nאירועים היום:\n" + Assistant.describeEvents(todayEvents) +
                "\nהשעה: " + LocalTime.now().let { timeLabel(it.hour * 60 + it.minute) }
            val res = withContext(Dispatchers.IO) { client().askJson(Assistant.NEXT_SYSTEM, user, 500) }
            val id = res.opt("id")?.toString()?.toLongOrNull()
            val pick = candidates.firstOrNull { it.id == id } ?: best
            nextUp = pick.id to res.optString("reason", reasonFor(pick))
        }
    }

    private fun reasonFor(t: Task): String {
        val today = LocalDate.now()
        return when {
            t.date?.isBefore(today) == true -> "היא באיחור – בוא נוריד אותה מהראש"
            t.focus -> "זו אחת משלוש החשובות של היום"
            t.priority == 2 -> "מסומנת כדחופה"
            t.durationMin <= 15 -> "קצרה – ניצחון מהיר שנותן מומנטום"
            else -> "הכי גבוהה בעדיפות כרגע"
        }
    }

    fun dismissNext() {
        nextUp = null
    }

    // ---- plan my day ----

    fun planDay() {
        val today = LocalDate.now()
        val candidates = tasks.filter { !it.done && !it.inbox && it.date != null && !it.date.isAfter(today) }
        if (candidates.isEmpty()) {
            message = "אין משימות להיום לתכנן. הוסף משימות או סדר את התיבה קודם"
            return
        }
        val todayEvents = events.filter { it.isOn(today) && it.eventId !in linkedEventIds() }
        if (!aiEnabled) {
            plan = localPlan(candidates, todayEvents)
            return
        }
        runAi("Claude בונה לך תוכנית ליום…") {
            val user = "משימות:\n" + Assistant.describeTasks(candidates) +
                "\nאירועים ביומן היום:\n" + Assistant.describeEvents(todayEvents)
            val res = withContext(Dispatchers.IO) { client().askJson(Assistant.planSystem(lists, dayEnd), user, 2500) }
            val ids = candidates.map { it.id }.toSet()
            val sched = mutableListOf<PlanSlot>()
            val arr = res.optJSONArray("schedule") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.opt("id")?.toString()?.toLongOrNull() ?: continue
                val start = Assistant.parseTime(o, "start") ?: continue
                if (id !in ids) continue
                val dur = o.optInt("duration", candidates.first { it.id == id }.durationMin).coerceIn(5, 600)
                sched.add(PlanSlot(id, start, dur))
            }
            plan = DayPlan(
                focus = Assistant.longs(res.optJSONArray("focus")).filter { it in ids }.take(3),
                schedule = sched.sortedBy { it.start },
                defer = Assistant.longs(res.optJSONArray("defer")).filter { it in ids },
                message = res.optString("message", ""),
                byAi = true,
            )
        }
    }

    private fun localPlan(candidates: List<Task>, todayEvents: List<CalEvent>): DayPlan {
        val today = LocalDate.now()
        val nowT = LocalTime.now()
        var cursor = ((nowT.hour * 60 + nowT.minute + 14) / 15) * 15
        val busy = mutableListOf<IntRange>()
        for (e in todayEvents) {
            if (e.allDay) continue
            val s = e.startMinOfDay()
            val len = ((e.end - e.begin) / 60_000L).toInt().coerceAtLeast(15)
            busy.add(s until (s + len))
        }
        val fixed = candidates.filter { it.date == today && it.timeMin != null }
        fixed.forEach { t -> val s = t.timeMin!!; busy.add(s until (s + t.durationMin)) }
        val flexible = candidates.filter { it !in fixed }.sortedByDescending { score(it) }
        val sched = mutableListOf<PlanSlot>()
        val defer = mutableListOf<Long>()
        fixed.forEach { sched.add(PlanSlot(it.id, it.timeMin!!, it.durationMin)) }
        for (t in flexible) {
            val dur = t.durationMin.coerceIn(10, 240)
            var start = cursor
            var guard = 0
            while (busy.any { r -> start < r.last + 1 && start + dur > r.first } && guard < 200) {
                val blocking = busy.filter { r -> start < r.last + 1 && start + dur > r.first }.maxOf { it.last + 1 }
                start = ((blocking + 10 + 4) / 5) * 5
                guard++
            }
            if (start + dur > dayEnd) {
                if (t.priority < 2 && !t.focus) defer.add(t.id)
                continue
            }
            sched.add(PlanSlot(t.id, start, dur))
            busy.add(start until (start + dur))
            cursor = start + dur + 10
        }
        val focus = candidates.sortedByDescending { score(it) }.take(3).map { it.id }
        val first = sched.minByOrNull { it.start }?.let { s -> candidates.firstOrNull { it.id == s.taskId } }
        val msg = buildString {
            append("שיבצתי ${sched.size} משימות עד ${timeLabel(dayEnd)}")
            if (defer.isNotEmpty()) append(", ו-${defer.size} עוברות למחר כדי שהיום יהיה ריאלי")
            append(".")
            if (first != null) append(" תתחיל ב\"${first.title}\".")
        }
        return DayPlan(focus, sched.sortedBy { it.start }, defer, msg, byAi = false)
    }

    fun applyPlan() {
        val p = plan ?: return
        val today = LocalDate.now()
        val slots = p.schedule.associateBy { it.taskId }
        val focusSet = p.focus.toSet()
        val deferSet = p.defer.toSet()
        val affected = tasks.filter { it.id in slots || it.id in deferSet || it.id in focusSet || (it.focus && !it.done) }
        for (t in affected) {
            val slot = slots[t.id]
            var nt = t.copy(focus = t.id in focusSet)
            if (slot != null) nt = nt.copy(date = today, timeMin = slot.start, durationMin = slot.duration, inbox = false)
            if (t.id in deferSet) nt = nt.copy(date = today.plusDays(1), timeMin = null, focus = false)
            if (nt != t) saveTask(nt)
        }
        plan = null
        message = "התוכנית הוחלה" + (if (calendarReady) " ונכנסה ליומן 📅" else " ✓")
    }

    fun dismissPlan() {
        plan = null
    }
}

data class PlanSlot(val taskId: Long, val start: Int, val duration: Int)

data class DayPlan(
    val focus: List<Long>,
    val schedule: List<PlanSlot>,
    val defer: List<Long>,
    val message: String,
    val byAi: Boolean,
)
