@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.matan.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

// =====================================================================
// Quick capture overlay (bottom sheet style)
// =====================================================================

@Composable
fun CaptureOverlay(
    vm: TaskViewModel,
    defaultDate: LocalDate?,
    startVoice: ((String) -> Unit) -> Unit,
    onClose: () -> Unit,
    onBrainDump: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val parsed = if (text.isBlank()) null else SmartParse.parse(text, vm.lists)

    fun save(keepOpen: Boolean) {
        val r = vm.capture(text, defaultDate)
        if (r != null) vm.message = "✓ " + r
        text = ""
        if (!keepOpen) onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Text("מה בראש?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("למשל: מחר ב-17 להתקשר לאבא #אישי !") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) save(keepOpen = true) }),
                trailingIcon = {
                    IconButton(onClick = { startVoice { heard -> text = (text + " " + heard).trim() } }) {
                        Text("🎙", fontSize = 20.sp)
                    }
                },
            )
            // live preview of what was understood
            if (parsed != null) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val d = parsed.date ?: defaultDate
                    item { Pill(if (d == null) "📥 לתיבה" else "📅 " + dateLabel(d), MaterialTheme.colorScheme.primary) }
                    parsed.timeMin?.let { item { Pill("⏰ " + timeLabel(it), MaterialTheme.colorScheme.primary) } }
                    parsed.durationMin?.let { item { Pill("⏱ " + durationLabel(it), Violet) } }
                    parsed.list?.let { item { Pill("# $it", listColor(it, vm.lists)) } }
                    if (parsed.priority == 2) item { Pill("🔥 דחוף", Danger) }
                }
            } else {
                Text(
                    "מבין עברית: היום/מחר/ביום שלישי/25/11, ב-17 או בערב, #רשימה, ! לדחוף, 30 דק׳",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("🧠 יש לי הרבה בראש", Modifier.weight(1f), color = Violet) { onBrainDump(text) }
                GradientButton("הוסף", Modifier.weight(1f), brush = HeroBrush, enabled = text.isNotBlank()) { save(keepOpen = false) }
            }
        }
    }
}

// =====================================================================
// Brain dump
// =====================================================================

@Composable
fun BrainDumpScreen(
    vm: TaskViewModel,
    seed: String,
    startVoice: ((String) -> Unit) -> Unit,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf(seed) }
    val prompts = listOf("עבודה", "בית", "כסף וחשבונות", "אנשים שצריך לחזור אליהם", "לימודים", "בריאות וספורט", "דברים שמציקים לי", "רעיונות")
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(AiBrush)
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "סגור", tint = Color.White) }
            Column(Modifier.padding(start = 12.dp)) {
                Text("🧠 שפוך את הראש", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "כתוב או תגיד כל מה שמסתובב לך בראש – משימות, דאגות, רעיונות. בלי סדר ובלי לסנן. " +
                        if (vm.aiEnabled) "Claude יהפוך את זה לרשימה מסודרת." else "אפרק את זה למשימות (עם Claude זה הרבה יותר חכם).",
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            items(prompts) { p ->
                MiniAction("+ $p", Violet) { text = (if (text.isBlank()) "" else text.trimEnd() + "\n") + p + ": " }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("צריך לסיים את המצגת לעבודה עד חמישי, לקבוע תור לרופא, לא שכחתי שאני חייב לחזור לדני, הרכב צריך טסט...") },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp),
        )
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SoftButton("🎙 הקלטה", Modifier.weight(1f), color = Violet) {
                startVoice { heard -> text = (if (text.isBlank()) "" else text.trimEnd() + "\n") + heard }
            }
            GradientButton("✨ סדר לי את זה", Modifier.weight(1.6f), enabled = text.isNotBlank() && vm.aiBusy == null) { onSubmit(text) }
        }
    }
}

// =====================================================================
// Review tasks proposed by the assistant
// =====================================================================

@Composable
fun DraftsReview(vm: TaskViewModel, drafts: List<Task>) {
    var excluded by remember(drafts) { mutableStateOf(setOf<Long>()) }
    var edited by remember(drafts) { mutableStateOf(drafts) }
    var editingId by remember(drafts) { mutableStateOf<Long?>(null) }
    val selected = edited.filter { it.id !in excluded }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.discardDrafts() }) { Icon(Icons.Filled.Close, contentDescription = "ביטול") }
            Text(
                if (vm.draftsReplace) "✨ הצעה לסידור התיבה" else "✨ ${drafts.size} משימות מהראש שלך",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        if (vm.draftsMessage.isNotBlank()) {
            Text(
                vm.draftsMessage,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Violet.copy(alpha = 0.1f))
                    .padding(14.dp),
            )
        }
        Text(
            "סמן מה להשאיר. לחיצה על כותרת – עריכה.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(edited, key = { it.id }) { t ->
                val on = t.id !in excluded
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                    ),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                .clickable { excluded = if (on) excluded + t.id else excluded - t.id },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            if (editingId == t.id) {
                                OutlinedTextField(
                                    value = t.title,
                                    onValueChange = { v -> edited = edited.map { if (it.id == t.id) it.copy(title = v) else it } },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { editingId = null }),
                                )
                            } else {
                                Text(
                                    t.title,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { editingId = t.id },
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                val d = t.date
                                item { Pill(if (d == null) (if (t.inbox) "📥 תיבה" else "💤 מתישהו") else "📅 " + dateLabel(d), MaterialTheme.colorScheme.primary) }
                                t.timeMin?.let { item { Pill("⏰ " + timeLabel(it), MaterialTheme.colorScheme.primary) } }
                                item { Pill(t.list, listColor(t.list, vm.lists)) }
                                item { Pill("⏱ " + durationLabel(t.durationMin), Violet) }
                                if (t.priority == 2) item { Pill("🔥", Danger) }
                                if (t.focus) item { Pill("⭐", Amber) }
                                if (t.subtasks.isNotEmpty()) item { Pill("${t.subtasks.size} צעדים", Color(0xFF64748B)) }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("ביטול", Modifier.weight(1f), color = Color(0xFF64748B)) { vm.discardDrafts() }
            GradientButton(
                if (vm.draftsReplace) "החל על ${selected.size}" else "הוסף ${selected.size} משימות",
                Modifier.weight(1.6f),
                brush = HeroBrush,
                enabled = selected.isNotEmpty(),
            ) { vm.acceptDrafts(selected) }
        }
    }
}

// =====================================================================
// Review a day plan
// =====================================================================

private data class Row2(val start: Int, val dur: Int, val title: String, val isEvent: Boolean, val focus: Boolean)

@Composable
fun PlanReview(vm: TaskViewModel, plan: DayPlan) {
    val byId = vm.tasks.associateBy { it.id }
    val today = LocalDate.now()
    val linked = vm.linkedEventIds()
    val events = vm.events.filter { it.isOn(today) && !it.allDay && it.eventId !in linked }
    val rows = (plan.schedule.mapNotNull { s ->
        byId[s.taskId]?.let { Row2(s.start, s.duration, it.title, false, s.taskId in plan.focus) }
    } + events.map {
        Row2(it.startMinOfDay(), ((it.end - it.begin) / 60_000L).toInt(), it.title, true, false)
    }).sortedBy { it.start }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(AiBrush)
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
        ) {
            IconButton(onClick = { vm.dismissPlan() }) { Icon(Icons.Filled.Close, contentDescription = "סגור", tint = Color.White) }
            Column(Modifier.padding(start = 12.dp)) {
                Text("✨ התוכנית שלך להיום", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                if (plan.message.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(plan.message, color = Color.White.copy(alpha = 0.92f))
                }
                if (!plan.byAi) {
                    Spacer(Modifier.height(4.dp))
                    Text("(תכנון אוטומטי בסיסי – עם Claude התכנון חכם יותר)", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (plan.focus.isNotEmpty()) {
                item { SectionTitle("⭐ הפוקוס של היום") }
                items(plan.focus.mapNotNull { byId[it] }, key = { "pf" + it.id }) { t ->
                    Text(
                        "⭐  " + t.title,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Amber.copy(alpha = 0.13f))
                            .padding(12.dp),
                    )
                }
            }
            item { SectionTitle("🕒 לוח הזמנים") }
            if (rows.isEmpty()) item { EmptyHint("לא נמצא זמן פנוי לשבץ משימות") }
            items(rows.size) { i ->
                val r = rows[i]
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(timeLabel(r.start), fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    r.isEvent -> MaterialTheme.colorScheme.surfaceVariant
                                    r.focus -> Amber.copy(alpha = 0.18f)
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (r.isEvent) "📅 " else if (r.focus) "⭐ " else "") + r.title,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (r.isEvent) FontWeight.Normal else FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(durationLabel(r.dur), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val deferred = plan.defer.mapNotNull { byId[it] }
            if (deferred.isNotEmpty()) {
                item { SectionTitle("🌙 עובר למחר") }
                items(deferred, key = { "pd" + it.id }) { t ->
                    Text(
                        t.title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("לא עכשיו", Modifier.weight(1f), color = Color(0xFF64748B)) { vm.dismissPlan() }
            GradientButton(if (vm.calendarReady) "החל ושבץ ביומן 📅" else "החל את התוכנית", Modifier.weight(1.6f), brush = HeroBrush) { vm.applyPlan() }
        }
        TextButton(onClick = { vm.planDay() }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)) {
            Text("🔄 תכנן מחדש")
        }
    }
}
