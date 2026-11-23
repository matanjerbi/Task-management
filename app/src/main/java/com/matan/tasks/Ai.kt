package com.matan.tasks

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AiException(msg: String) : Exception(msg)

/** Minimal Claude Messages API client (no extra dependencies). Call from a background thread. */
class ClaudeClient(private val apiKey: String, private val model: String) {

    fun ask(system: String, user: String, maxTokens: Int = 2500): String {
        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            val body = JSONObject()
            body.put("model", model)
            body.put("max_tokens", maxTokens)
            body.put("system", system)
            val msgs = JSONArray()
            msgs.put(JSONObject().put("role", "user").put("content", user))
            body.put("messages", msgs)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val err = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                throw AiException(
                    when (code) {
                        401 -> "מפתח ה-API של Claude לא תקין"
                        429 -> "יותר מדי בקשות, נסה שוב עוד רגע"
                        529, 503 -> "Claude עמוס כרגע, נסה שוב עוד רגע"
                        else -> "שגיאה מ-Claude ($code)" + (err?.let { ": $it" } ?: "")
                    }
                )
            }
            val content = JSONObject(text).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val c = content.getJSONObject(i)
                if (c.optString("type") == "text") sb.append(c.optString("text"))
            }
            return sb.toString()
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException("אין חיבור ל-Claude: " + (e.message ?: e.javaClass.simpleName))
        } finally {
            conn.disconnect()
        }
    }

    fun askJson(system: String, user: String, maxTokens: Int = 2500): JSONObject {
        val raw = ask(system, user, maxTokens)
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("Claude החזיר תשובה לא צפויה")
        return try {
            JSONObject(raw.substring(start, end + 1))
        } catch (e: Exception) {
            throw AiException("Claude החזיר תשובה לא צפויה")
        }
    }
}

/** Prompts and parsing for the assistant features. */
object Assistant {
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    private fun context(lists: List<String>): String {
        val today = LocalDate.now()
        val now = LocalTime.now()
        val sb = StringBuilder()
        sb.append("היום: ").append(today.format(dateFmt)).append(" (").append(fullDate(today)).append(")\n")
        sb.append("השעה עכשיו: ").append(timeLabel(now.hour * 60 + now.minute)).append("\n")
        sb.append("השבוע בישראל מתחיל ביום ראשון. שישי-שבת הם סוף שבוע.\n")
        sb.append("רשימות קיימות: ").append(lists.joinToString(", ")).append("\n")
        return sb.toString()
    }

    private const val TASK_SCHEMA =
        "{\"title\":\"כותרת קצרה שמתחילה בפועל\",\"list\":\"אחת מהרשימות הקיימות\",\"priority\":0|1|2," +
            "\"date\":\"YYYY-MM-DD או null\",\"time\":\"HH:MM או null\",\"duration\":דקות,\"focus\":true|false," +
            "\"notes\":\"\",\"subtasks\":[\"...\"]}"

    fun brainDumpSystem(lists: List<String>) = """
        אתה עוזר אישי לניהול זמן ומשימות, שמדבר עברית. המשתמש "שופך את הראש" – טקסט חופשי ומבולגן עם כל מה שמעסיק אותו.
        התפקיד שלך: להפוך את זה לרשימת משימות ברורות וברות-ביצוע.
        כללים:
        - פרק דברים מורכבים למשימות נפרדות. כל משימה = פעולה אחת ברורה שמתחילה בפועל ("להתקשר ל...", "לשלוח...", "לקנות...").
        - אל תמציא משימות שלא נאמרו. דאגות כלליות הפוך לצעד הבא הקטן והמעשי.
        - תאריך ושעה רק אם נאמרו או משתמעים בבירור ("מחר", "ביום שלישי", "עד סוף השבוע"). אחרת null.
        - priority: 2 = דחוף/חשוב באמת, 1 = רגיל, 0 = נחמד-שיהיה.
        - duration: הערכה ריאלית בדקות (5–240).
        - focus=true לכל היותר ל-3 משימות הכי חשובות.
        - subtasks רק כשבאמת יש כמה שלבים.
        ${context(lists)}
        החזר JSON בלבד בפורמט:
        {"tasks":[$TASK_SCHEMA],"message":"משפט קצר ומעודד בעברית שמסכם מה ראית"}
    """.trimIndent()

    fun organizeSystem(lists: List<String>) = """
        אתה עוזר אישי לניהול משימות בעברית. קיבלת את פריטי "תיבת הדואר הנכנס" של המשתמש – דברים שנרשמו מהר ולא סודרו.
        לכל פריט: נסח כותרת ברורה שמתחילה בפועל, שייך לרשימה, קבע עדיפות, הערך משך, והצע תאריך:
        - דחוף או קצר וחשוב → היום או מחר
        - חשוב אבל לא דחוף → במהלך השבוע הקרוב
        - לא ברור אם בכלל צריך → date=null ("מתישהו")
        ${context(lists)}
        החזר JSON בלבד: {"items":[{"id":מספר,...שדות כמו ב-$TASK_SCHEMA}],"message":"משפט קצר בעברית"}
    """.trimIndent()

    fun planSystem(lists: List<String>, dayEnd: Int) = """
        אתה מאמן תכנון יום אישי, מדבר עברית, ישיר וחם. בנה למשתמש תוכנית ריאלית להמשך היום.
        - בחר עד 3 משימות פוקוס (הכי חשובות להיום).
        - שבץ משימות בחלונות הפנויים מעכשיו ועד ${timeLabel(dayEnd)}, בלי חפיפה לאירועים ביומן. השאר מרווח של 10–15 דק' בין דברים.
        - משימות עם שעה קבועה – השאר בשעתן.
        - היה ריאלי: אם אין מספיק זמן, העבר משימות פחות חשובות ל-defer (יידחו למחר).
        - דברים כבדים ודורשי ריכוז עדיף מוקדם.
        ${context(lists)}
        החזר JSON בלבד:
        {"focus":[ids],"schedule":[{"id":מספר,"start":"HH:MM","duration":דקות}],"defer":[ids],"message":"2–3 משפטים בעברית: הגישה להיום וטיפ אחד"}
    """.trimIndent()

    const val BREAKDOWN_SYSTEM = """אתה עוזר שמפרק משימות גדולות לצעדים קטנים וברורים בעברית.
כל צעד קצר (עד 8 מילים), מתחיל בפועל, וכזה שאפשר להתחיל בו מיד. 3–7 צעדים.
החזר JSON בלבד: {"subtasks":["..."],"duration":הערכת זמן כוללת בדקות,"tip":"טיפ קצר אחד להתחלה"}"""

    const val NEXT_SYSTEM = """אתה מאמן פרודוקטיביות בעברית. המשתמש שואל "מה לעשות עכשיו?".
בחר משימה אחת מהרשימה שהכי נכון להתחיל בה עכשיו, בהתחשב בשעה, בעדיפות, בדדליין, באירוע הבא ביומן ובזמן שיש.
החזר JSON בלבד: {"id":מספר,"reason":"משפט אחד קצר ומניע בעברית למה דווקא זה"}"""

    fun describeTasks(tasks: List<Task>): String {
        val today = LocalDate.now()
        val sb = StringBuilder()
        for (t in tasks) {
            sb.append("- id=").append(t.id).append(" | ").append(t.title)
            sb.append(" | רשימה: ").append(t.list)
            sb.append(" | עדיפות: ").append(t.priority)
            sb.append(" | משך: ").append(t.durationMin).append(" דק'")
            val d = t.date
            if (d != null) {
                sb.append(" | תאריך: ").append(d.format(dateFmt))
                if (d.isBefore(today)) sb.append(" (באיחור)")
            }
            t.timeMin?.let { sb.append(" | שעה: ").append(timeLabel(it)) }
            if (t.focus) sb.append(" | פוקוס")
            if (t.notes.isNotBlank()) sb.append(" | הערות: ").append(t.notes.take(120).replace('\n', ' '))
            sb.append("\n")
        }
        return sb.toString()
    }

    fun describeEvents(events: List<CalEvent>): String {
        if (events.isEmpty()) return "אין אירועים ביומן."
        return events.joinToString("\n") { "- " + eventTimeLabel(it) + " " + it.title }
    }

    private fun optStringOrNull(o: JSONObject, k: String): String? {
        if (!o.has(k) || o.isNull(k)) return null
        val v = o.optString(k).trim()
        return if (v.isEmpty() || v == "null") null else v
    }

    fun parseDate(o: JSONObject): LocalDate? =
        optStringOrNull(o, "date")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    fun parseTime(o: JSONObject, key: String = "time"): Int? {
        val s = optStringOrNull(o, key) ?: return null
        val p = s.split(":")
        if (p.size < 2) return null
        val h = p[0].trim().toIntOrNull() ?: return null
        val m = p[1].trim().take(2).toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } }
    }

    fun longs(arr: JSONArray?): List<Long> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.opt(i)?.toString()?.trim()?.toLongOrNull() }
    }

    /** Converts one task JSON object into a Task draft. */
    fun taskFrom(o: JSONObject, base: Task, lists: List<String>): Task {
        val list = optStringOrNull(o, "list")?.let { l -> lists.firstOrNull { it == l } ?: lists.firstOrNull { it.contains(l) || l.contains(it) } }
        val date = parseDate(o)
        return base.copy(
            title = optStringOrNull(o, "title") ?: base.title,
            list = list ?: base.list,
            priority = o.optInt("priority", base.priority).coerceIn(0, 2),
            date = date,
            timeMin = if (date != null) parseTime(o) else null,
            durationMin = o.optInt("duration", base.durationMin).coerceIn(5, 600),
            focus = o.optBoolean("focus", false),
            notes = optStringOrNull(o, "notes") ?: base.notes,
            subtasks = strings(o.optJSONArray("subtasks")).map { SubTask(it) }.ifEmpty { base.subtasks },
            inbox = date == null && base.inbox,
        )
    }
}
