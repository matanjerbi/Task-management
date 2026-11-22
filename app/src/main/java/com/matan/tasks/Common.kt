@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.matan.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val HE: Locale = Locale("he")

fun dateLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "היום"
        today.plusDays(1) -> "מחר"
        today.minusDays(1) -> "אתמול"
        else -> d.format(DateTimeFormatter.ofPattern("EEE, d/M", HE))
    }
}

fun fullDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE, d 'ב'MMMM", HE))

fun timeLabel(m: Int): String = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

fun eventTimeLabel(e: CalEvent): String {
    if (e.allDay) return "כל היום"
    val f = DateTimeFormatter.ofPattern("HH:mm")
    val z = ZoneId.systemDefault()
    val s = Instant.ofEpochMilli(e.begin).atZone(z).format(f)
    val en = Instant.ofEpochMilli(e.end).atZone(z).format(f)
    return "$s–$en"
}

fun priorityColor(p: Int): Color = when (p) {
    2 -> Color(0xFFEF4444)
    0 -> Color(0xFF94A3B8)
    else -> Color(0xFF6366F1)
}

val ReminderOptions: List<Pair<Int?, String>> = listOf(
    null to "ללא",
    0 to "בזמן",
    5 to "5 דק׳",
    15 to "15 דק׳",
    30 to "30 דק׳",
    60 to "שעה",
    1440 to "יום לפני",
)

val taskOrder: Comparator<Task> = compareBy<Task>({ it.done }, { it.timeMin ?: Int.MAX_VALUE }, { -it.priority }, { it.createdAt })
val dateOrder: Comparator<Task> = compareBy<Task>({ it.date ?: LocalDate.MAX }, { it.timeMin ?: Int.MAX_VALUE }, { -it.priority }, { it.createdAt })

fun pickDate(ctx: Context, initial: LocalDate?, onPick: (LocalDate) -> Unit) {
    val i = initial ?: LocalDate.now()
    DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day)) }, i.year, i.monthValue - 1, i.dayOfMonth).show()
}

fun pickTime(ctx: Context, initial: Int?, onPick: (Int) -> Unit) {
    val i = initial ?: (9 * 60)
    TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, i / 60, i % 60, true).show()
}

@Composable
fun SectionTitle(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
fun TaskRow(t: Task, onToggle: () -> Unit, onClick: () -> Unit, showDate: Boolean = false) {
    val today = LocalDate.now()
    val date = t.date
    val overdue = !t.done && date != null && date.isBefore(today)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(priorityColor(t.priority), RoundedCornerShape(2.dp))
            )
            Checkbox(checked = t.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    t.title,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = mutableListOf<String>()
                if (showDate && date != null) meta.add(dateLabel(date))
                val tm = t.timeMin
                if (tm != null) meta.add(timeLabel(tm))
                meta.add(t.list)
                if (t.subtasks.isNotEmpty()) meta.add("☑ ${t.subtasks.count { it.done }}/${t.subtasks.size}")
                if (t.repeat != 0) meta.add("🔁")
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (t.eventId != null) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = "ביומן גוגל",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun EventRow(e: CalEvent, onImport: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(Color(e.color or 0xFF000000.toInt()), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    eventTimeLabel(e) + " · " + e.calendarName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onImport != null) {
                IconButton(onClick = onImport) {
                    Icon(Icons.Filled.AddCircle, contentDescription = "הפוך למשימה", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(options) { opt ->
            FilterChip(
                selected = opt.first == selected,
                onClick = { onSelect(opt.first) },
                label = { Text(opt.second) },
            )
        }
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
