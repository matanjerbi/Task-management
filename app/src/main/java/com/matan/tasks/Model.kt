package com.matan.tasks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class SubTask(val text: String, val done: Boolean = false)

/** repeat: 0 none, 1 daily, 2 weekly, 3 monthly. priority: 0 low, 1 normal, 2 high */
data class Task(
    val id: Long,
    val title: String,
    val notes: String = "",
    val list: String = "כללי",
    val priority: Int = 1,
    val date: LocalDate? = null,
    val timeMin: Int? = null,
    val durationMin: Int = 60,
    val reminderMin: Int? = null,
    val repeat: Int = 0,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncCalendar: Boolean = true,
    val eventId: Long? = null,
    val ownsEvent: Boolean = true,
    val subtasks: List<SubTask> = emptyList(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("title", title)
        o.put("notes", notes)
        o.put("list", list)
        o.put("priority", priority)
        if (date != null) o.put("date", date.toString())
        if (timeMin != null) o.put("timeMin", timeMin)
        o.put("durationMin", durationMin)
        if (reminderMin != null) o.put("reminderMin", reminderMin)
        o.put("repeat", repeat)
        o.put("done", done)
        if (doneAt != null) o.put("doneAt", doneAt)
        o.put("createdAt", createdAt)
        o.put("syncCalendar", syncCalendar)
        if (eventId != null) o.put("eventId", eventId)
        o.put("ownsEvent", ownsEvent)
        val arr = JSONArray()
        for (s in subtasks) {
            val so = JSONObject()
            so.put("text", s.text)
            so.put("done", s.done)
            arr.put(so)
        }
        o.put("subtasks", arr)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Task {
            val subs = mutableListOf<SubTask>()
            val arr = o.optJSONArray("subtasks")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val so = arr.getJSONObject(i)
                    subs.add(SubTask(so.optString("text"), so.optBoolean("done")))
                }
            }
            return Task(
                id = o.getLong("id"),
                title = o.optString("title"),
                notes = o.optString("notes"),
                list = o.optString("list", "כללי"),
                priority = o.optInt("priority", 1),
                date = if (o.has("date")) LocalDate.parse(o.getString("date")) else null,
                timeMin = if (o.has("timeMin")) o.getInt("timeMin") else null,
                durationMin = o.optInt("durationMin", 60),
                reminderMin = if (o.has("reminderMin")) o.getInt("reminderMin") else null,
                repeat = o.optInt("repeat", 0),
                done = o.optBoolean("done"),
                doneAt = if (o.has("doneAt")) o.getLong("doneAt") else null,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                syncCalendar = o.optBoolean("syncCalendar", true),
                eventId = if (o.has("eventId")) o.getLong("eventId") else null,
                ownsEvent = o.optBoolean("ownsEvent", true),
                subtasks = subs,
            )
        }
    }
}

class TaskStore(private val ctx: Context) {
    private val file = File(ctx.filesDir, "tasks.json")

    fun load(): List<Task> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { Task.fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(it.toJson()) }
        val tmp = File(ctx.filesDir, "tasks.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
        }
    }
}
