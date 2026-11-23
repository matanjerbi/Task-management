@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.matan.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val HE: Locale = Locale("he")

// ---------- palette ----------

val Violet = Color(0xFF7C3AED)
val Pink = Color(0xFFEC4899)
val Danger = Color(0xFFEF4444)
val Amber = Color(0xFFF59E0B)
val Success = Color(0xFF10B981)

val HeroBrush = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFFDB2777)))
val AiBrush = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899)))
val FocusBrush = Brush.verticalGradient(listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)))

private val ListPalette = listOf(
    Color(0xFF6366F1), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEC4899),
    Color(0xFF06B6D4), Color(0xFF8B5CF6), Color(0xFFEF4444), Color(0xFF84CC16),
)

fun listColor(name: String, lists: List<String>): Color {
    val i = lists.indexOf(name)
    val idx = if (i >= 0) i else (name.hashCode() and 0x7fffffff)
    return ListPalette[idx % ListPalette.size]
}

// ---------- formatting ----------

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

fun timeLabel(m: Int): String = String.format(Locale.US, "%02d:%02d", (m / 60) % 24, m % 60)

fun durationLabel(m: Int): String = when {
    m < 60 -> "$m דק׳"
    m % 60 == 0 -> if (m == 60) "שעה" else if (m == 120) "שעתיים" else "${m / 60} שע׳"
    else -> "${m / 60}:${String.format(Locale.US, "%02d", m % 60)} שע׳"
}

fun greeting(name: String): String {
    val h = LocalTime.now().hour
    val g = when (h) {
        in 5..11 -> "בוקר טוב"
        in 12..16 -> "צהריים טובים"
        in 17..20 -> "ערב טוב"
        else -> "לילה טוב"
    }
    return if (name.isBlank()) g else "$g, $name"
}

fun eventTimeLabel(e: CalEvent): String {
    if (e.allDay) return "כל היום"
    val f = DateTimeFormatter.ofPattern("HH:mm")
    val z = ZoneId.systemDefault()
    val s = Instant.ofEpochMilli(e.begin).atZone(z).format(f)
    val en = Instant.ofEpochMilli(e.end).atZone(z).format(f)
    return "$s–$en"
}

fun priorityColor(p: Int): Color = when (p) {
    2 -> Danger
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

// ---------- building blocks ----------

@Composable
fun SectionTitle(text: String, color: Color = MaterialTheme.colorScheme.onBackground, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                action,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
fun ProgressRing(progress: Float, size: Dp, stroke: Dp, color: Color, track: Color, content: @Composable () -> Unit = {}) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val d = this.size.minDimension - sw
            val topLeft = Offset(sw / 2, sw / 2)
            drawArc(track, -90f, 360f, false, topLeft, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
            if (p > 0f) drawArc(color, -90f, 360f * p, false, topLeft, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun RoundCheck(checked: Boolean, color: Color, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (checked) color else Color.Transparent, label = "chk")
    val scale by animateFloatAsState(if (checked) 1f else 0.6f, animationSpec = spring(dampingRatio = 0.4f), label = "chkScale")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(bg)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(Icons.Filled.Check, contentDescription = "בוצע", tint = Color.White, modifier = Modifier.size(16.dp).scale(scale))
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun TaskRow(
    t: Task,
    lists: List<String>,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    showDate: Boolean = false,
    onStar: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val today = LocalDate.now()
    val date = t.date
    val overdue = !t.done && date != null && date.isBefore(today)
    val lc = listColor(t.list, lists)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (t.done) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (t.done) 0.dp else 1.dp),
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundCheck(checked = t.done, color = if (t.priority == 2) Danger else lc, onClick = onToggle)
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    t.title,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showDate && date != null) Pill(dateLabel(date), if (overdue) Danger else MaterialTheme.colorScheme.primary)
                    val tm = t.timeMin
                    if (tm != null) Pill("⏰ " + timeLabel(tm), MaterialTheme.colorScheme.primary)
                    Pill(t.list, lc)
                    if (t.subtasks.isNotEmpty()) Pill("${t.subtasks.count { it.done }}/${t.subtasks.size}", Color(0xFF64748B))
                    if (t.priority == 2 && !t.done) Pill("🔥", Danger)
                    if (t.repeat != 0) Pill("🔁", Color(0xFF64748B))
                }
            }
            if (t.eventId != null) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = "ביומן גוגל",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
            if (onStar != null && !t.done) {
                IconButton(onClick = onStar) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "פוקוס",
                        tint = if (t.focus) Amber else MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun EventRow(e: CalEvent, onImport: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(30.dp)
                .background(Color(e.color or 0xFF000000.toInt()), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                eventTimeLabel(e) + " · " + e.calendarName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onImport != null) {
            IconButton(onClick = onImport) {
                Icon(Icons.Filled.AddCircle, contentDescription = "הפוך למשימה", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
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
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** A big, colorful action button used for assistant actions. */
@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, brush: Brush = AiBrush, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
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
