@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.matan.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun ActionChips(chips: List<Triple<String, Boolean, () -> Unit>>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(chips.size) { i ->
            val (label, selected, onClick) = chips[i]
            FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
        }
    }
}

@Composable
fun TaskEditor(
    vm: TaskViewModel,
    task: Task,
    onClose: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onFocus: (Task) -> Unit,
    requestCalendar: () -> Unit,
) {
    val ctx = LocalContext.current
    val isNew = vm.tasks.none { it.id == task.id }
    var d by remember(task.id) { mutableStateOf(task) }
    var newSub by remember(task.id) { mutableStateOf("") }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "משימה חדשה" else "עריכת משימה") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "סגור") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val extra = newSub.trim()
                            val subs = if (extra.isNotEmpty()) d.subtasks + SubTask(extra) else d.subtasks
                            onSave(d.copy(title = d.title.trim(), subtasks = subs, inbox = d.inbox && d.date == null))
                        },
                        enabled = d.title.isNotBlank(),
                    ) { Text("שמור", fontWeight = FontWeight.Bold) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = d.title,
                onValueChange = { d = d.copy(title = it) },
                label = { Text("מה צריך לעשות?") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = d.notes,
                onValueChange = { d = d.copy(notes = it) },
                label = { Text("הערות") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(if (d.focus) "⭐ בפוקוס היום" else "☆ לפוקוס היום", Modifier.weight(1f), color = Amber) {
                    d = d.copy(focus = !d.focus, date = if (!d.focus && d.date == null) today else d.date, inbox = false)
                }
                SoftButton("▶ מצב פוקוס", Modifier.weight(1f), color = Violet) {
                    if (d.title.isNotBlank()) {
                        val saved = d.copy(title = d.title.trim())
                        onSave(saved)
                        onFocus(saved)
                    }
                }
            }
            GradientButton(
                "✨ פרק לצעדים קטנים עם Claude",
                Modifier.fillMaxWidth(),
                enabled = d.title.isNotBlank() && vm.aiBusy == null,
            ) {
                if (!vm.aiEnabled) {
                    vm.message = "חבר את Claude בהגדרות כדי לפרק משימות"
                } else {
                    vm.breakdown(d) { res -> d = d.copy(subtasks = res.subtasks, durationMin = res.durationMin) }
                }
            }

            FieldLabel("רשימה")
            ChoiceChips(vm.lists.map { it to it }, d.list) { d = d.copy(list = it) }

            FieldLabel("עדיפות")
            ChoiceChips(listOf(0 to "נמוכה", 1 to "רגילה", 2 to "גבוהה 🔥"), d.priority) { d = d.copy(priority = it) }

            val date = d.date
            FieldLabel("תאריך: " + (date?.let { fullDate(it) } ?: "ללא תאריך"))
            ActionChips(
                listOf(
                    Triple("היום", date == today) { d = d.copy(date = today) },
                    Triple("מחר", date == today.plusDays(1)) { d = d.copy(date = today.plusDays(1)) },
                    Triple("בעוד שבוע", date == today.plusDays(7)) { d = d.copy(date = today.plusDays(7)) },
                    Triple("בחר תאריך…", false) { pickDate(ctx, d.date) { p -> d = d.copy(date = p) } },
                    Triple("ללא", date == null) { d = d.copy(date = null, timeMin = null, repeat = 0) },
                )
            )

            if (date != null) {
                val tm = d.timeMin
                FieldLabel("שעה: " + (tm?.let { timeLabel(it) } ?: "כל היום"))
                ActionChips(
                    listOf(
                        Triple("כל היום", tm == null) { d = d.copy(timeMin = null) },
                        Triple(if (tm == null) "בחר שעה…" else "שנה שעה…", tm != null) {
                            pickTime(ctx, d.timeMin) { p -> d = d.copy(timeMin = p) }
                        },
                    )
                )
                run {
                    FieldLabel("משך משוער")
                    ChoiceChips(
                        listOf(15 to "15 דק׳", 30 to "30 דק׳", 45 to "45 דק׳", 60 to "שעה", 90 to "שעה וחצי", 120 to "שעתיים", 180 to "3 שעות"),
                        d.durationMin,
                    ) { d = d.copy(durationMin = it) }
                }

                FieldLabel("חזרה")
                ChoiceChips(listOf(0 to "ללא", 1 to "כל יום", 2 to "כל שבוע", 3 to "כל חודש"), d.repeat) { d = d.copy(repeat = it) }
            }

            // Google Calendar
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("הוסף ליומן גוגל", fontWeight = FontWeight.Bold)
                            val sub = when {
                                !vm.hasCalPermission -> "היומן עוד לא מחובר"
                                date == null -> "צריך תאריך כדי שהמשימה תופיע ביומן"
                                !d.ownsEvent -> "מקושר לאירוע קיים ביומן"
                                vm.selectedCalendar != null -> vm.selectedCalendar!!.name
                                else -> "בחר יומן בהגדרות"
                            }
                            Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = d.syncCalendar,
                            onCheckedChange = { d = d.copy(syncCalendar = it) },
                            enabled = vm.hasCalPermission,
                        )
                    }
                    if (!vm.hasCalPermission) {
                        TextButton(onClick = requestCalendar) { Text("חבר את יומן גוגל") }
                    }
                    if (d.syncCalendar && date != null && d.ownsEvent) {
                        Text("תזכורת", style = MaterialTheme.typography.labelLarge)
                        ChoiceChips(ReminderOptions, d.reminderMin) { d = d.copy(reminderMin = it) }
                    }
                }
            }

            FieldLabel("תתי-משימות")
            d.subtasks.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s.done, onCheckedChange = { c ->
                        val l = d.subtasks.toMutableList()
                        l[i] = s.copy(done = c)
                        d = d.copy(subtasks = l)
                    })
                    Text(
                        s.text,
                        modifier = Modifier.weight(1f),
                        textDecoration = if (s.done) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    IconButton(onClick = { d = d.copy(subtasks = d.subtasks.filterIndexed { j, _ -> j != i }) }) {
                        Icon(Icons.Filled.Close, contentDescription = "הסר")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSub,
                    onValueChange = { newSub = it },
                    placeholder = { Text("הוסף תת-משימה") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (newSub.isNotBlank()) {
                        d = d.copy(subtasks = d.subtasks + SubTask(newSub.trim()))
                        newSub = ""
                    }
                }) { Icon(Icons.Filled.Add, contentDescription = "הוסף") }
            }

            if (!isNew) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDelete(task) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (task.eventId != null && task.ownsEvent) "מחק משימה (וגם מהיומן)" else "מחק משימה")
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
