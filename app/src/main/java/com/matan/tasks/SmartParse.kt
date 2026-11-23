package com.matan.tasks

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class Parsed(
    val title: String,
    val date: LocalDate?,
    val timeMin: Int?,
    val priority: Int?,
    val list: String?,
    val durationMin: Int?,
)

/**
 * Local Hebrew "smart quick add" parser. Understands things like:
 * "מחר ב-17 להתקשר לאבא #אישי !"  /  "ביום שלישי בבוקר פגישה עם רואה חשבון 30 דק"
 */
object SmartParse {
    private const val NL = "(?<!\\p{L})"
    private const val NR = "(?!\\p{L})"

    private val days = listOf(
        "ראשון" to DayOfWeek.SUNDAY,
        "שני" to DayOfWeek.MONDAY,
        "שלישי" to DayOfWeek.TUESDAY,
        "רביעי" to DayOfWeek.WEDNESDAY,
        "חמישי" to DayOfWeek.THURSDAY,
        "שישי" to DayOfWeek.FRIDAY,
        "שבת" to DayOfWeek.SATURDAY,
    )

    fun parse(input: String, lists: List<String>, today: LocalDate = LocalDate.now()): Parsed {
        var s = " " + input.trim() + " "
        var date: LocalDate? = null
        var time: Int? = null
        var priority: Int? = null
        var list: String? = null
        var duration: Int? = null

        fun take(regex: Regex, onMatch: (MatchResult) -> Boolean) {
            val m = regex.find(s) ?: return
            if (onMatch(m)) s = s.removeRange(m.range).let { " $it " }
        }

        // #list
        take(Regex("#([\\p{L}\\p{N}_\\-]+)")) { m ->
            val name = m.groupValues[1].replace('_', ' ')
            list = lists.firstOrNull { it.equals(name, ignoreCase = true) }
                ?: lists.firstOrNull { it.contains(name, ignoreCase = true) }
                ?: name
            true
        }

        // priority
        take(Regex("$NL(דחוף|חשוב מאוד|חשוב)$NR")) { priority = 2; true }
        take(Regex("!{1,3}")) { priority = 2; true }

        // explicit date 25/11 or 25.11.2026
        take(Regex("$NL(?:ב-?|עד\\s)?(\\d{1,2})[/.](\\d{1,2})(?:[/.](\\d{2,4}))?(?![\\d])")) { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            var y = m.groupValues[3].toIntOrNull() ?: today.year
            if (y < 100) y += 2000
            val parsed = runCatching { LocalDate.of(y, mo, d) }.getOrNull()
            if (parsed != null) {
                date = if (m.groupValues[3].isEmpty() && parsed.isBefore(today)) parsed.plusYears(1) else parsed
                true
            } else false
        }

        if (date == null) take(Regex("${NL}(?:ל|עד\\s)?מחרתיים$NR")) { date = today.plusDays(2); true }
        if (date == null) take(Regex("${NL}(?:ל|עד\\s)?מחר$NR")) { date = today.plusDays(1); true }
        if (date == null) take(Regex("${NL}(?:ל|עד\\s)?היום$NR")) { date = today; true }
        if (date == null) take(Regex("${NL}בעוד\\s+(\\d{1,3}|שבוע|שבועיים|חודש)\\s*(ימים|יום)?$NR")) { m ->
            date = when (m.groupValues[1]) {
                "שבוע" -> today.plusWeeks(1)
                "שבועיים" -> today.plusWeeks(2)
                "חודש" -> today.plusMonths(1)
                else -> today.plusDays(m.groupValues[1].toLong())
            }
            true
        }
        if (date == null) take(Regex("${NL}(?:ב)?(?:שבוע הבא|בשבוע הבא)$NR")) {
            date = today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)); true
        }
        if (date == null) take(Regex("${NL}(?:ב)?סוף השבוע$NR")) {
            date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)); true
        }
        if (date == null) {
            val names = days.joinToString("|") { it.first }
            take(Regex("$NL(?:ב?יום\\s+|ב)($names)$NR")) { m ->
                val dow = days.first { it.first == m.groupValues[1] }.second
                date = today.with(TemporalAdjusters.next(dow))
                true
            }
        }

        // duration: "30 דק", "שעה", "שעתיים"
        take(Regex("$NL(\\d{1,3})\\s*(?:דק(?:ות|׳|')?|דקה)$NR")) { m -> duration = m.groupValues[1].toInt(); true }
        if (duration == null) take(Regex("${NL}למשך\\s+(שעה|שעתיים|חצי שעה)$NR")) { m ->
            duration = when (m.groupValues[1]) { "שעתיים" -> 120; "חצי שעה" -> 30; else -> 60 }; true
        }

        // time
        take(Regex("$NL(?:בשעה\\s*|ב-?\\s?)?(\\d{1,2}):(\\d{2})(?![\\d])")) { m ->
            val h = m.groupValues[1].toInt()
            val mi = m.groupValues[2].toInt()
            if (h in 0..23 && mi in 0..59) { time = h * 60 + mi; true } else false
        }
        if (time == null) take(Regex("$NL(?:בשעה\\s*|ב-\\s?|ב)(\\d{1,2})(?![\\d/.:])(?!\\s*(?:דק|ימים|יום))")) { m ->
            var h = m.groupValues[1].toInt()
            if (h in 1..7) h += 12 // "ב-5" usually means 17:00
            if (h in 0..23) { time = h * 60; true } else false
        }
        if (time == null) take(Regex("${NL}(?:ב)?(בוקר|צהריים|צהרים|אחה\"צ|אחר הצהריים|ערב|לילה)$NR")) { m ->
            time = when (m.groupValues[1]) {
                "בוקר" -> 9 * 60
                "צהריים", "צהרים" -> 13 * 60
                "אחה\"צ", "אחר הצהריים" -> 16 * 60
                "ערב" -> 19 * 60
                else -> 22 * 60
            }
            true
        }

        if (time != null && date == null) {
            val now = LocalTime.now()
            date = if (time!! <= now.hour * 60 + now.minute) today.plusDays(1) else today
        }

        val title = s.replace(Regex("\\s+"), " ").trim().trim('-', ',', '.').trim()
        return Parsed(
            title = if (title.isEmpty()) input.trim() else title,
            date = date,
            timeMin = time,
            priority = priority,
            list = list,
            durationMin = duration,
        )
    }
}
