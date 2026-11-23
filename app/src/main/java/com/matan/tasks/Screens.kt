@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.matan.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val TAB_TODAY = 0
const val TAB_INBOX = 1
const val TAB_TASKS = 2
const val TAB_CAL = 3
const val TAB_SETTINGS = 4

@Composable
fun App(vm: TaskViewModel, requestCalendar: () -> Unit, startVoice: ((String) -> Unit) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var focusTask by remember { mutableStateOf<Task?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var dumping by remember { mutableStateOf(false) }
    var dumpSeed by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var calDay by remember { mutableStateOf(LocalDate.now()) }
    val snackbar = remember { SnackbarHostState() }
    val msg = vm.message

    LaunchedEffect(msg) {
        if (msg != null) {
            vm.message = null
            snackbar.showSnackbar(msg)
        }
    }

    val openTask: (Task) -> Unit = { editing = it }
    val startFocus: (Task) -> Unit = { focusTask = it }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val drafts = vm.drafts
        val plan = vm.plan
        val ft = focusTask
        val ed = editing
        when {
            ft != null -> {
                BackHandler { focusTask = null }
                FocusScreen(vm, ft, onClose = { focusTask = null })
            }
            drafts != null -> {
                BackHandler { vm.discardDrafts() }
                DraftsReview(vm, drafts)
            }
            plan != null -> {
                BackHandler { vm.dismissPlan() }
                PlanReview(vm, plan)
            }
            ed != null -> {
                BackHandler { editing = null }
                TaskEditor(
                    vm = vm,
                    task = ed,
                    onClose = { editing = null },
                    onSave = { vm.saveTask(it); editing = null },
                    onDelete = { vm.deleteTask(it); editing = null },
                    onFocus = { editing = null; focusTask = it },
                    requestCalendar = requestCalendar,
                )
            }
            dumping -> {
                BackHandler { dumping = false }
                BrainDumpScreen(vm, seed = dumpSeed, startVoice = startVoice, onClose = { dumping = false }, onSubmit = {
                    dumping = false
                    vm.brainDump(it)
                })
            }
            processing -> {
                BackHandler { processing = false }
                ProcessInboxScreen(vm, onClose = { processing = false }, onEdit = openTask)
            }
            else -> {
                if (tab == TAB_SETTINGS) BackHandler { tab = TAB_TODAY }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            TAB_TODAY -> TodayScreen(
                                vm, onOpen = openTask, onFocus = startFocus, requestCalendar = requestCalendar,
                                onSettings = { tab = TAB_SETTINGS },
                                onInbox = { tab = TAB_INBOX },
                                onDump = { dumpSeed = ""; dumping = true },
                            )
                            TAB_INBOX -> InboxScreen(vm, onOpen = openTask, onProcess = { processing = true }, onDump = { dumpSeed = ""; dumping = true })
                            TAB_TASKS -> AllTasksScreen(vm, onOpen = openTask, onFocus = startFocus)
                            TAB_CAL -> CalendarScreen(vm, day = calDay, onDay = { calDay = it }, onOpen = openTask, onAdd = {
                                editing = vm.newTask(calDay)
                            })
                            else -> SettingsScreen(vm, requestCalendar, onBack = { tab = TAB_TODAY })
                        }
                    }
                    BottomBar(tab = tab, inboxCount = vm.inboxTasks.size, onTab = { tab = it }, onCapture = { capturing = true })
                }
                if (capturing) {
                    BackHandler { capturing = false }
                    CaptureOverlay(
                        vm = vm,
                        defaultDate = if (tab == TAB_CAL) calDay else null,
                        startVoice = startVoice,
                        onClose = { capturing = false },
                        onBrainDump = { text ->
                            capturing = false
                            dumpSeed = text
                            dumping = true
                        },
                    )
                }
            }
        }

        // assistant working banner
        AnimatedVisibility(
            visible = vm.aiBusy != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                Modifier
                    .padding(12.dp)
                    .shadow(8.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(AiBrush)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text("✨ " + (vm.aiBusy ?: ""), color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}

@Composable
private fun BottomBar(tab: Int, inboxCount: Int, onTab: (Int) -> Unit, onCapture: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem("היום", Icons.Filled.Home, tab == TAB_TODAY, Modifier.weight(1f)) { onTab(TAB_TODAY) }
            NavItem("תיבה", Icons.Filled.Email, tab == TAB_INBOX, Modifier.weight(1f), badge = inboxCount) { onTab(TAB_INBOX) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .offset(y = (-10).dp)
                        .size(58.dp)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(HeroBrush)
                        .clickable { onCapture() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "הוספה מהירה", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            NavItem("משימות", Icons.Filled.List, tab == TAB_TASKS, Modifier.weight(1f)) { onTab(TAB_TASKS) }
            NavItem("יומן", Icons.Filled.DateRange, tab == TAB_CAL, Modifier.weight(1f)) { onTab(TAB_CAL) }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, badge: Int = 0, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 3.dp)
            ) {
                Icon(icon, contentDescription = label, tint = color)
            }
            if (badge > 0) {
                Text(
                    if (badge > 99) "99+" else badge.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(50))
                        .background(Pink)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(label, color = color, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// =====================================================================
// TODAY
// =====================================================================

@Composable
fun TodayScreen(
    vm: TaskViewModel,
    onOpen: (Task) -> Unit,
    onFocus: (Task) -> Unit,
    requestCalendar: () -> Unit,
    onSettings: () -> Unit,
    onInbox: () -> Unit,
    onDump: () -> Unit,
) {
    val today = LocalDate.now()
    val lists = vm.lists
    val overdue = vm.tasks.filter { !it.done && it.date?.isBefore(today) == true }.sortedWith(dateOrder)
    val todays = vm.tasks.filter { it.date == today }
    val focus = todays.filter { it.focus }.sortedWith(taskOrder)
    val rest = todays.filter { !it.focus }.sortedWith(taskOrder)
    val linked = vm.linkedEventIds()
    val todayEvents = if (vm.showEvents) vm.events.filter { it.isOn(today) && it.eventId !in linked } else emptyList()
    val doneCount = todays.count { it.done }
    val total = todays.size
    val streak = vm.streak()
    val inboxCount = vm.inboxTasks.size
    val next = vm.nextUp
    val nextTask = next?.let { n -> vm.tasks.firstOrNull { it.id == n.first && !it.done } }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(HeroBrush)
                    .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(vm.userName) + " 👋", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text(fullDate(today), color = Color.White.copy(alpha = 0.8f))
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "הגדרות", tint = Color.White) }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        progress = if (total == 0) 0f else doneCount.toFloat() / total,
                        size = 78.dp,
                        stroke = 9.dp,
                        color = Color.White,
                        track = Color.White.copy(alpha = 0.25f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$doneCount/$total", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("היום", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when {
                                total == 0 -> "יום נקי. מה חשוב היום?"
                                doneCount == total -> "סיימת הכל! 🎉"
                                doneCount == 0 -> "בוא נתחיל במשהו קטן"
                                doneCount * 2 >= total -> "יותר מחצי בדרך 💪"
                                else -> "יפה, ממשיכים"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        if (streak > 0) Text("🔥 רצף של $streak ימים", color = Color.White.copy(alpha = 0.9f))
                        if (overdue.isNotEmpty()) Text("⏳ ${overdue.size} באיחור", color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }

        // Assistant actions
        item {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton("✨ תכנן לי את היום", Modifier.weight(1f), enabled = vm.aiBusy == null) { vm.planDay() }
                    SoftButton("🎯 מה עכשיו?", Modifier.weight(1f)) { vm.whatNow() }
                }
                SoftButton("🧠 שפוך את הראש – Claude יעשה סדר", Modifier.fillMaxWidth(), color = Violet) { onDump() }
            }
        }

        if (nextTask != null && next != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎯 עכשיו:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.dismissNext() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "סגור", modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(nextTask.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text(next.second, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GradientButton("▶ התחל פוקוס", Modifier.weight(1f), brush = HeroBrush) { onFocus(nextTask) }
                            SoftButton("✓ בוצע", Modifier.weight(1f), color = Success) {
                                vm.toggleDone(nextTask)
                                vm.dismissNext()
                            }
                        }
                    }
                }
            }
        }

        if (inboxCount > 0) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Amber.copy(alpha = 0.14f))
                        .clickable { onInbox() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("📥", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$inboxCount פריטים מחכים בתיבה", fontWeight = FontWeight.Bold)
                        Text("2 דקות של סדר = ראש שקט", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("לסדר ←", color = Color(0xFFB45309), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!vm.hasCalPermission) {
            item { ConnectCard(requestCalendar) }
        }

        if (overdue.isNotEmpty()) {
            item { SectionTitle("⏳ באיחור", Danger, action = "הכל להיום", onAction = { vm.moveOverdueToToday() }) }
            items(overdue, key = { "o" + it.id }) { t ->
                TaskRow(t, lists, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) }, showDate = true,
                    onStar = { vm.toggleFocus(t) }, onLongClick = { onFocus(t) })
            }
        }

        item { SectionTitle("⭐ 3 החשובות של היום") }
        if (focus.isEmpty()) {
            item {
                EmptyHint("סמן ⭐ עד 3 משימות – אם רק הן ייעשו, היום הצליח. או לחץ \"תכנן לי את היום\".")
            }
        }
        items(focus, key = { "f" + it.id }) { t ->
            TaskRow(t, lists, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) }, onStar = { vm.toggleFocus(t) }, onLongClick = { onFocus(t) })
        }

        item { SectionTitle("📋 שאר היום") }
        if (rest.isEmpty()) {
            item { EmptyHint(if (total == 0) "אין משימות להיום. לחץ + כדי להוסיף" else "אין עוד משימות 🎉") }
        }
        items(rest, key = { "t" + it.id }) { t ->
            TaskRow(t, lists, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) }, onStar = { vm.toggleFocus(t) }, onLongClick = { onFocus(t) })
        }

        if (todayEvents.isNotEmpty()) {
            item { SectionTitle("📅 ביומן היום") }
            items(todayEvents, key = { "e" + it.eventId + "_" + it.begin }) { e ->
                EventRow(e, onImport = { vm.importEvent(e) })
            }
        }
        item {
            Text(
                "טיפ: לחיצה ארוכה על משימה מפעילה מצב פוקוס ⏱",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )
        }
    }
}

@Composable
fun ConnectCard(requestCalendar: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📅 חבר את יומן גוגל", fontWeight = FontWeight.Bold)
            Text("משימות עם תאריך יופיעו ביומן עם תזכורות, ואירועי היומן ייכנסו לתכנון היום.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = requestCalendar) { Text("התחבר") }
        }
    }
}

// =====================================================================
// INBOX
// =====================================================================

@Composable
fun InboxScreen(vm: TaskViewModel, onOpen: (Task) -> Unit, onProcess: () -> Unit, onDump: () -> Unit) {
    val items = vm.inboxTasks.sortedByDescending { it.createdAt }
    var quick by remember { mutableStateOf("") }
    val today = LocalDate.now()

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp)) {
                Text("📥 תיבה", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(
                    "כל מה שנזרק מהר נכנס לכאן. פעם ביום עוברים ומחליטים: עושים, דוחים או מוותרים.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = quick,
                    onValueChange = { quick = it },
                    placeholder = { Text("מה עוד מסתובב לך בראש?") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        vm.capture(quick)?.let { vm.message = it }
                        quick = ""
                    }),
                )
                IconButton(onClick = {
                    vm.capture(quick)?.let { vm.message = it }
                    quick = ""
                }) { Icon(Icons.Filled.Add, contentDescription = "הוסף", tint = MaterialTheme.colorScheme.primary) }
            }
        }
        if (items.isNotEmpty()) {
            item {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton("🗂 עבור אחד-אחד", Modifier.weight(1f), brush = HeroBrush) { onProcess() }
                    GradientButton("✨ Claude יסדר", Modifier.weight(1f), enabled = vm.aiBusy == null) { vm.organizeInbox() }
                }
            }
            items(items, key = { "i" + it.id }) { t ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                        Text(
                            t.title,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(t) },
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { MiniAction("✓ עשיתי", Success) { vm.completeNow(t) } }
                            item { MiniAction("היום", MaterialTheme.colorScheme.primary) { vm.schedule(t, today) } }
                            item { MiniAction("מחר", MaterialTheme.colorScheme.primary) { vm.schedule(t, today.plusDays(1)) } }
                            item { MiniAction("השבוע", MaterialTheme.colorScheme.primary) { vm.schedule(t, today.plusDays(3)) } }
                            item { MiniAction("מתישהו", Color(0xFF64748B)) { vm.schedule(t, null) } }
                            item { MiniAction("🗑", Danger) { vm.deleteTask(t) } }
                        }
                    }
                }
            }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🧘", fontSize = 56.sp)
                    Text("התיבה ריקה. הראש נקי.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "מרגיש עומס? שפוך הכל במקום אחד ו-Claude יהפוך את זה לרשימה מסודרת.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GradientButton("🧠 שפוך את הראש", Modifier.fillMaxWidth()) { onDump() }
                }
            }
        }
    }
}

@Composable
fun MiniAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** Process the inbox one item at a time (GTD-style). */
@Composable
fun ProcessInboxScreen(vm: TaskViewModel, onClose: () -> Unit, onEdit: (Task) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val items = vm.inboxTasks.sortedBy { it.createdAt }
    var startCount by remember { mutableIntStateOf(items.size) }
    if (items.size > startCount) startCount = items.size
    val today = LocalDate.now()
    val t = items.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "סגור") }
            Text("עושים סדר", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("${startCount - items.size}/$startCount", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { if (startCount == 0) 1f else (startCount - items.size).toFloat() / startCount },
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
        )
        if (t == null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🎉", fontSize = 72.sp)
                Text("התיבה ריקה!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("כל דבר נמצא במקום שלו. הראש פנוי.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                GradientButton("חזרה", Modifier.fillMaxWidth(), brush = HeroBrush) { onClose() }
            }
            return@Column
        }
        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                if (t.notes.isNotBlank()) Text(t.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("לאיזו רשימה?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceChips(vm.lists.map { it to it }, t.list) { l -> vm.saveTask(t.copy(list = l)) }
                TextButton(onClick = { onEdit(t) }) { Text("✏️ עריכה מלאה") }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("מה עושים עם זה?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton("⚡ לוקח פחות מ-2 דקות – עשיתי עכשיו", Modifier.fillMaxWidth(), brush = Brush2(Success)) { vm.completeNow(t) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("היום", Modifier.weight(1f)) { vm.schedule(t, today) }
                SoftButton("מחר", Modifier.weight(1f)) { vm.schedule(t, today.plusDays(1)) }
                SoftButton("בחר יום…", Modifier.weight(1f)) { pickDate(ctx, today) { d -> vm.schedule(t, d) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("⭐ פוקוס היום", Modifier.weight(1f), color = Amber) { vm.toggleFocus(t) }
                SoftButton("💤 מתישהו", Modifier.weight(1f), color = Color(0xFF64748B)) { vm.schedule(t, null) }
                SoftButton("🗑 לוותר", Modifier.weight(1f), color = Danger) { vm.deleteTask(t) }
            }
        }
    }
}

fun Brush2(c: Color) = androidx.compose.ui.graphics.Brush.linearGradient(listOf(c, c.copy(alpha = 0.8f)))

// =====================================================================
// ALL TASKS (list + Eisenhower matrix)
// =====================================================================

@Composable
fun AllTasksScreen(vm: TaskViewModel, onOpen: (Task) -> Unit, onFocus: (Task) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var listFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var matrix by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val lists = vm.lists

    val filtered = vm.tasks.filter {
        !it.inbox &&
            (listFilter == null || it.list == listFilter) &&
            (query.isBlank() || it.title.contains(query, ignoreCase = true) || it.notes.contains(query, ignoreCase = true))
    }
    val open = filtered.filter { !it.done }.sortedWith(dateOrder)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 22.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("משימות", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)
            ) {
                Segment("רשימה", !matrix) { matrix = false }
                Segment("מטריצה", matrix) { matrix = true }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) {
            item { FilterChip(selected = listFilter == null, onClick = { listFilter = null }, label = { Text("הכל") }, shape = RoundedCornerShape(50)) }
            items(lists) { l ->
                FilterChip(
                    selected = listFilter == l,
                    onClick = { listFilter = if (listFilter == l) null else l },
                    label = { Text(l) },
                    shape = RoundedCornerShape(50),
                    leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(listColor(l, lists))) },
                )
            }
        }

        if (matrix) {
            MatrixView(vm, open, onOpen)
            return@Column
        }

        val groups = listOf(
            "⏳ באיחור" to open.filter { it.date?.isBefore(today) == true },
            "☀️ היום" to open.filter { it.date == today },
            "🌙 מחר" to open.filter { it.date == today.plusDays(1) },
            "📆 השבוע" to open.filter {
                val d = it.date
                d != null && d.isAfter(today.plusDays(1)) && !d.isAfter(today.plusDays(7))
            },
            "🔭 בהמשך" to open.filter {
                val d = it.date
                d != null && d.isAfter(today.plusDays(7))
            },
            "💤 מתישהו" to open.filter { it.date == null },
        )
        val done = filtered.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("חיפוש") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "נקה") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
            for ((name, list) in groups) {
                if (list.isNotEmpty()) {
                    item(key = "h$name") {
                        SectionTitle("$name  ${list.size}", if (name.contains("באיחור")) Danger else MaterialTheme.colorScheme.onBackground)
                    }
                    items(list, key = { "a" + it.id }) { t ->
                        TaskRow(t, lists, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) }, showDate = true,
                            onStar = { vm.toggleFocus(t) }, onLongClick = { onFocus(t) })
                    }
                }
            }
            if (open.isEmpty()) {
                item { EmptyHint(if (query.isBlank()) "אין משימות פתוחות ✨" else "לא נמצאו משימות") }
            }
            if (done.isNotEmpty()) {
                item {
                    TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(if (showDone) "הסתר שהושלמו" else "✓ הושלמו (${done.size})")
                    }
                }
            }
            if (showDone) {
                items(done, key = { "d" + it.id }) { t ->
                    TaskRow(t, lists, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) }, showDate = true)
                }
            }
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun MatrixView(vm: TaskViewModel, open: List<Task>, onOpen: (Task) -> Unit) {
    val today = LocalDate.now()
    fun important(t: Task) = t.priority == 2 || t.focus
    fun urgent(t: Task): Boolean {
        val d = t.date ?: return false
        return !d.isAfter(today.plusDays(1))
    }
    val q1 = open.filter { important(it) && urgent(it) }
    val q2 = open.filter { important(it) && !urgent(it) }
    val q3 = open.filter { !important(it) && urgent(it) }
    val q4 = open.filter { !important(it) && !urgent(it) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            "חשוב = עדיפות גבוהה או ⭐ · דחוף = עד מחר",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Row {
            Quadrant("🔥 עשה עכשיו", "חשוב ודחוף", Danger, q1, vm, onOpen, Modifier.weight(1f))
            Quadrant("🗓 תכנן", "חשוב, לא דחוף", Color(0xFF6366F1), q2, vm, onOpen, Modifier.weight(1f))
        }
        Row {
            Quadrant("⚡ קצר / האצל", "דחוף, פחות חשוב", Amber, q3, vm, onOpen, Modifier.weight(1f))
            Quadrant("🧹 לוותר?", "לא חשוב ולא דחוף", Color(0xFF64748B), q4, vm, onOpen, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Quadrant(title: String, sub: String, color: Color, tasks: List<Task>, vm: TaskViewModel, onOpen: (Task) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .padding(5.dp)
            .height(270.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.ExtraBold, color = color, modifier = Modifier.weight(1f), maxLines = 1)
            Text(tasks.size.toString(), fontWeight = FontWeight.Bold, color = color)
        }
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (tasks.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (t in tasks) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpen(t) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { vm.toggleDone(t) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(16.dp).clip(CircleShape).border(2.dp, color, CircleShape))
                    }
                    Text(t.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// =====================================================================
// CALENDAR
// =====================================================================

private data class AgendaItem(val minute: Int, val task: Task?, val event: CalEvent?)

@Composable
fun CalendarScreen(vm: TaskViewModel, day: LocalDate, onDay: (LocalDate) -> Unit, onOpen: (Task) -> Unit, onAdd: () -> Unit) {
    val today = LocalDate.now()
    val days = (-7..60).map { today.plusDays(it.toLong()) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 5)
    val linked = vm.linkedEventIds()
    val lists = vm.lists
    val busyDays = remember(vm.tasks, vm.events, vm.showEvents) {
        val s = mutableSetOf<LocalDate>()
        vm.tasks.filter { !it.done }.forEach { t -> t.date?.let { s.add(it) } }
        if (vm.showEvents) {
            for (d in days) if (vm.events.any { it.isOn(d) }) s.add(d)
        }
        s
    }
    val dayTasks = vm.tasks.filter { it.date == day }
    val dayEvents = if (vm.showEvents) vm.events.filter { it.isOn(day) && it.eventId !in linked } else emptyList()
    val agenda = (dayTasks.map { AgendaItem(it.timeMin ?: -1, it, null) } +
        dayEvents.map { AgendaItem(it.startMinOfDay(), null, it) })
        .sortedWith(compareBy<AgendaItem>({ it.minute }, { it.task?.done ?: false }))
    val load = dayTasks.filter { !it.done }.sumOf { it.durationMin }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                day.format(DateTimeFormatter.ofPattern("MMMM yyyy", HE)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            if (day != today) TextButton(onClick = { onDay(today) }) { Text("היום") }
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "רענן") }
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "משימה ביום הזה") }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(days) { d ->
                val selected = d == day
                Column(
                    Modifier
                        .width(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) HeroBrush else Brush2(MaterialTheme.colorScheme.surface)
                        )
                        .clickable { onDay(d) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                    Text(d.format(DateTimeFormatter.ofPattern("EEEEE", HE)), color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                    Text(
                        d.dayOfMonth.toString(),
                        color = fg,
                        fontWeight = if (d == today) FontWeight.ExtraBold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(
                                if (d in busyDays) (if (selected) Color.White else MaterialTheme.colorScheme.primary) else Color.Transparent,
                                CircleShape,
                            )
                    )
                }
            }
        }
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fullDate(day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (load > 0) Pill("עומס: " + durationLabel(load), if (load > 8 * 60) Danger else MaterialTheme.colorScheme.primary)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (agenda.isEmpty()) {
                item { EmptyHint("יום פנוי 🌿") }
            }
            items(agenda, key = { a -> a.task?.let { "t" + it.id } ?: ("e" + a.event!!.eventId + "_" + a.event.begin) }) { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (a.minute < 0) "כל היום" else timeLabel(a.minute),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(60.dp).padding(start = 14.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        val t = a.task
                        val e = a.event
                        if (t != null) {
                            TaskRow(t, lists, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) })
                        } else if (e != null) {
                            EventRow(e, onImport = { vm.importEvent(e) })
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// SETTINGS + STATS
// =====================================================================

@Composable
fun SettingsScreen(vm: TaskViewModel, requestCalendar: () -> Unit, onBack: () -> Unit) {
    var newList by remember { mutableStateOf("") }
    var keyField by remember { mutableStateOf(vm.apiKey) }
    var nameField by remember { mutableStateOf(vm.userName) }
    val today = LocalDate.now()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "חזרה") }
            Text("הגדרות ונתונים", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(8.dp))

        // stats
        SettingsCard("📊 השבוע שלך") {
            val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
            val counts = week.map { vm.completedOn(it) }
            val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                week.forEachIndexed { i, d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(counts[i].toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier
                                .width(22.dp)
                                .height((8 + 80 * counts[i] / max).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (d == today) HeroBrush else Brush2(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                        )
                        Text(d.format(DateTimeFormatter.ofPattern("EEEEE", HE)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("🔥 רצף ${vm.streak()} ימים", Amber)
                Pill("✓ ${counts.sum()} השבוע", Success)
                Pill("⏱ " + durationLabel(vm.focusMinutesTotal()) + " פוקוס", Violet)
            }
        }

        SettingsCard("✨ Claude – העוזר האישי") {
            Text(
                "Claude מפרק את מה שבראש למשימות, מסדר את התיבה, בונה תוכנית יומית ובוחר מה לעשות עכשיו. " +
                    "צריך מפתח API מ-console.anthropic.com (תשלום לפי שימוש, סנטים בודדים ליום).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = keyField,
                onValueChange = { keyField = it },
                label = { Text("מפתח API (sk-ant-…)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    vm.updateApiKey(keyField)
                    vm.message = if (keyField.isBlank()) "Claude נותק" else "Claude מחובר ✨"
                }) { Text("שמור מפתח") }
                Text(
                    if (vm.aiEnabled) "● מחובר" else "○ לא מחובר",
                    color = if (vm.aiEnabled) Success else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("מודל", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(
                listOf("claude-sonnet-5-5" to "Sonnet – חכם", "claude-haiku-5-5" to "Haiku – מהיר וזול", "claude-opus-5-5" to "Opus – הכי חכם"),
                vm.aiModel,
            ) { vm.updateAiModel(it) }
            Text("סוף יום העבודה (לתכנון)", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(18 * 60 to "18:00", 20 * 60 to "20:00", 22 * 60 to "22:00", 23 * 60 + 30 to "23:30"), vm.dayEnd) { vm.updateDayEnd(it) }
        }

        SettingsCard("👤 פרופיל") {
            OutlinedTextField(
                value = nameField,
                onValueChange = {
                    nameField = it
                    vm.updateUserName(it.trim())
                },
                label = { Text("איך לקרוא לך?") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SettingsCard("📅 יומן גוגל") {
            if (!vm.hasCalPermission) {
                Text("האפליקציה משתמשת בחשבון הגוגל שכבר מחובר לטלפון. צריך רק לאשר גישה ליומן.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = requestCalendar) { Text("חבר את יומן גוגל") }
            } else {
                if (vm.calendars.isEmpty()) {
                    Text("לא נמצאו יומנים. ודא שחשבון גוגל מחובר לטלפון ושסנכרון היומן פעיל.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("לאיזה יומן להוסיף משימות:", style = MaterialTheme.typography.bodyMedium)
                }
                for (c in vm.calendars) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.setCalendar(c.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c.id == vm.calendarId, onClick = { vm.setCalendar(c.id) })
                        Box(Modifier.size(12.dp).background(Color(c.color or 0xFF000000.toInt()), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name)
                            Text(c.account, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                SwitchRow(title = "משימות חדשות נכנסות ליומן", subtitle = "אפשר לשנות לכל משימה בנפרד", checked = vm.defaultSync, onChange = { vm.updateDefaultSync(it) })
                SwitchRow(title = "הצג אירועי יומן באפליקציה", checked = vm.showEvents, onChange = { vm.updateShowEvents(it) })
                Button(onClick = { vm.syncAllNow() }, enabled = !vm.busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.busy) "מסנכרן…" else "סנכרן הכל עכשיו")
                }
            }
        }

        SettingsCard("🔔 תזכורת ברירת מחדל") {
            ChoiceChips(ReminderOptions, vm.defaultReminder) { vm.updateDefaultReminder(it) }
        }

        SettingsCard("🗂 רשימות") {
            for (l in vm.lists) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(listColor(l, vm.lists)))
                    Spacer(Modifier.width(10.dp))
                    Text(l, modifier = Modifier.weight(1f))
                    val count = vm.tasks.count { it.list == l && !it.done }
                    if (count > 0) Text("$count פתוחות", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { vm.removeList(l) }, enabled = vm.lists.size > 1) {
                        Icon(Icons.Filled.Delete, contentDescription = "מחק רשימה")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newList,
                    onValueChange = { newList = it },
                    placeholder = { Text("רשימה חדשה") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.addList(newList); newList = "" }) { Icon(Icons.Filled.Add, contentDescription = "הוסף רשימה") }
            }
        }

        SettingsCard("💡 איך עובדים עם זה") {
            Text(
                "1. זורקים הכל מהר ל-+ (אפשר לכתוב \"מחר ב-17 להתקשר לאבא #אישי !\").\n" +
                    "2. פעם ביום עוברים על התיבה ומחליטים.\n" +
                    "3. בבוקר: \"תכנן לי את היום\" – בוחרים 3 חשובות ומשבצים ביומן.\n" +
                    "4. כשלא יודעים מה עכשיו – \"מה עכשיו?\" ומצב פוקוס.\n" +
                    "5. מרגישים עומס – \"שפוך את הראש\".",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}
