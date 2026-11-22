@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.matan.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun App(vm: TaskViewModel, requestCalendar: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var calDay by remember { mutableStateOf(LocalDate.now()) }
    val snackbar = remember { SnackbarHostState() }
    val msg = vm.message

    LaunchedEffect(msg) {
        if (msg != null) {
            vm.message = null
            snackbar.showSnackbar(msg)
        }
    }

    val current = editing
    if (current != null) {
        BackHandler { editing = null }
        TaskEditor(
            vm = vm,
            task = current,
            onClose = { editing = null },
            onSave = { vm.saveTask(it); editing = null },
            onDelete = { vm.deleteTask(it); editing = null },
            requestCalendar = requestCalendar,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Triple("היום", Icons.Filled.Home, 0),
                    Triple("משימות", Icons.Filled.List, 1),
                    Triple("יומן", Icons.Filled.DateRange, 2),
                    Triple("הגדרות", Icons.Filled.Settings, 3),
                )
                for ((label, icon, i) in items) {
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != 3) {
                FloatingActionButton(
                    onClick = {
                        editing = vm.newTask(
                            when (tab) {
                                1 -> null
                                2 -> calDay
                                else -> LocalDate.now()
                            }
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = "משימה חדשה") }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (tab) {
                0 -> TodayScreen(vm, onOpen = { editing = it }, requestCalendar = requestCalendar)
                1 -> AllTasksScreen(vm, onOpen = { editing = it })
                2 -> CalendarScreen(vm, day = calDay, onDay = { calDay = it }, onOpen = { editing = it })
                else -> SettingsScreen(vm, requestCalendar)
            }
        }
    }
}

@Composable
fun ConnectCard(requestCalendar: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("חבר את יומן גוגל", fontWeight = FontWeight.Bold)
            Text(
                "משימות עם תאריך יופיעו ביומן גוגל שלך עם תזכורות, ואירועי היומן יופיעו כאן.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = requestCalendar) { Text("התחבר") }
        }
    }
}

@Composable
fun TodayScreen(vm: TaskViewModel, onOpen: (Task) -> Unit, requestCalendar: () -> Unit) {
    val today = LocalDate.now()
    val overdue = vm.tasks.filter { !it.done && it.date?.isBefore(today) == true }.sortedWith(dateOrder)
    val todays = vm.tasks.filter { it.date == today }.sortedWith(taskOrder)
    val linked = vm.linkedEventIds()
    val todayEvents = if (vm.showEvents) vm.events.filter { it.isOn(today) && it.eventId !in linked } else emptyList()
    val doneCount = todays.count { it.done }
    var quick by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Text(
                    fullDate(today),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                val total = todays.size
                Text(
                    if (total == 0) "אין משימות להיום" else "הושלמו $doneCount מתוך $total",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
                if (total > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { doneCount.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = quick,
                    onValueChange = { quick = it },
                    placeholder = { Text("הוספה מהירה להיום…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        vm.quickAdd(quick, today)
                        quick = ""
                    }),
                )
                IconButton(onClick = {
                    vm.quickAdd(quick, today)
                    quick = ""
                }) { Icon(Icons.Filled.Add, contentDescription = "הוסף", tint = MaterialTheme.colorScheme.primary) }
            }
        }
        if (!vm.hasCalPermission) {
            item { ConnectCard(requestCalendar) }
        }
        if (overdue.isNotEmpty()) {
            item { SectionTitle("באיחור (${overdue.size})", Color(0xFFEF4444)) }
            items(overdue, key = { "o" + it.id }) { t ->
                TaskRow(t, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) }, showDate = true)
            }
        }
        item { SectionTitle("היום") }
        if (todays.isEmpty()) {
            item { EmptyHint("אין משימות להיום 🎉 לחץ על + כדי להוסיף") }
        }
        items(todays, key = { "t" + it.id }) { t ->
            TaskRow(t, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) })
        }
        if (todayEvents.isNotEmpty()) {
            item { SectionTitle("ביומן גוגל היום") }
            items(todayEvents, key = { "e" + it.eventId + "_" + it.begin }) { e ->
                EventRow(e, onImport = { vm.importEvent(e) })
            }
        }
    }
}

@Composable
fun AllTasksScreen(vm: TaskViewModel, onOpen: (Task) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var listFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()

    val filtered = vm.tasks.filter {
        (listFilter == null || it.list == listFilter) &&
            (query.isBlank() || it.title.contains(query, ignoreCase = true) || it.notes.contains(query, ignoreCase = true))
    }
    val open = filtered.filter { !it.done }.sortedWith(dateOrder)
    val groups = listOf(
        "באיחור" to open.filter { it.date?.isBefore(today) == true },
        "היום" to open.filter { it.date == today },
        "מחר" to open.filter { it.date == today.plusDays(1) },
        "השבוע" to open.filter {
            val d = it.date
            d != null && d.isAfter(today.plusDays(1)) && !d.isAfter(today.plusDays(7))
        },
        "בהמשך" to open.filter {
            val d = it.date
            d != null && d.isAfter(today.plusDays(7))
        },
        "ללא תאריך" to open.filter { it.date == null },
    )
    val done = filtered.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("חיפוש משימות") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "נקה") }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                item {
                    FilterChip(selected = listFilter == null, onClick = { listFilter = null }, label = { Text("הכל") })
                }
                items(vm.lists) { l ->
                    FilterChip(
                        selected = listFilter == l,
                        onClick = { listFilter = if (listFilter == l) null else l },
                        label = { Text(l) },
                    )
                }
            }
        }
        for ((name, list) in groups) {
            if (list.isNotEmpty()) {
                item(key = "h$name") {
                    SectionTitle("$name (${list.size})", if (name == "באיחור") Color(0xFFEF4444) else MaterialTheme.colorScheme.primary)
                }
                items(list, key = { "a" + it.id }) { t ->
                    TaskRow(t, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) }, showDate = true)
                }
            }
        }
        if (open.isEmpty()) {
            item { EmptyHint(if (query.isBlank()) "אין משימות פתוחות" else "לא נמצאו משימות") }
        }
        if (done.isNotEmpty()) {
            item {
                TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showDone) "הסתר משימות שהושלמו" else "הצג משימות שהושלמו (${done.size})")
                }
            }
        }
        if (showDone) {
            items(done, key = { "d" + it.id }) { t ->
                TaskRow(t, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) }, showDate = true)
            }
        }
    }
}

private data class AgendaItem(val minute: Int, val task: Task?, val event: CalEvent?)

@Composable
fun CalendarScreen(vm: TaskViewModel, day: LocalDate, onDay: (LocalDate) -> Unit, onOpen: (Task) -> Unit) {
    val today = LocalDate.now()
    val days = (-7..60).map { today.plusDays(it.toLong()) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 5)
    val linked = vm.linkedEventIds()
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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                day.format(DateTimeFormatter.ofPattern("MMMM yyyy", HE)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (day != today) {
                TextButton(onClick = { onDay(today) }) { Text("היום") }
            }
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "רענן")
            }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(days) { d ->
                val selected = d == day
                Column(
                    Modifier
                        .width(52.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { onDay(d) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    Text(d.format(DateTimeFormatter.ofPattern("EEEEE", HE)), color = fg, style = MaterialTheme.typography.labelMedium)
                    Text(
                        d.dayOfMonth.toString(),
                        color = fg,
                        fontWeight = if (d == today) FontWeight.ExtraBold else FontWeight.Normal,
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
        Text(
            fullDate(day),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            if (agenda.isEmpty()) {
                item { EmptyHint("אין כלום ביום הזה") }
            }
            items(agenda, key = { a -> a.task?.let { "t" + it.id } ?: ("e" + a.event!!.eventId + "_" + a.event.begin) }) { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (a.minute < 0) "כל היום" else timeLabel(a.minute),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .width(64.dp)
                            .padding(start = 12.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        val t = a.task
                        val e = a.event
                        if (t != null) {
                            TaskRow(t, onToggle = { vm.toggleDone(t) }, onClick = { onOpen(t) })
                        } else if (e != null) {
                            EventRow(e, onImport = { vm.importEvent(e) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: TaskViewModel, requestCalendar: () -> Unit) {
    var newList by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("הגדרות", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        SettingsCard("יומן גוגל") {
            if (!vm.hasCalPermission) {
                Text(
                    "האפליקציה משתמשת בחשבון הגוגל שכבר מחובר לטלפון. צריך רק לאשר גישה ליומן.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = requestCalendar) { Text("חבר את יומן גוגל") }
            } else {
                if (vm.calendars.isEmpty()) {
                    Text(
                        "לא נמצאו יומנים. ודא שחשבון גוגל מחובר לטלפון ושסנכרון היומן פעיל בהגדרות החשבון.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("לאיזה יומן להוסיף משימות:", style = MaterialTheme.typography.bodyMedium)
                }
                for (c in vm.calendars) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.setCalendar(c.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c.id == vm.calendarId, onClick = { vm.setCalendar(c.id) })
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(Color(c.color or 0xFF000000.toInt()), CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name)
                            Text(
                                c.account + if (c.isGoogle) " · Google" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                SwitchRow(
                    title = "משימות חדשות נכנסות ליומן",
                    subtitle = "אפשר לשנות לכל משימה בנפרד",
                    checked = vm.defaultSync,
                    onChange = { vm.updateDefaultSync(it) },
                )
                SwitchRow(
                    title = "הצג אירועי יומן באפליקציה",
                    subtitle = "פגישות ואירועים מגוגל יופיעו במסך היום וביומן",
                    checked = vm.showEvents,
                    onChange = { vm.updateShowEvents(it) },
                )
                Button(onClick = { vm.syncAllNow() }, enabled = !vm.busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.busy) "מסנכרן…" else "סנכרן הכל עכשיו")
                }
            }
        }

        SettingsCard("תזכורת ברירת מחדל") {
            Text(
                "התזכורת נשלחת מיומן גוגל, כך שתקבל אותה גם בשעון ובמחשב.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceChips(ReminderOptions, vm.defaultReminder) { vm.updateDefaultReminder(it) }
        }

        SettingsCard("רשימות") {
            for (l in vm.lists) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(l, modifier = Modifier.weight(1f))
                    val count = vm.tasks.count { it.list == l && !it.done }
                    if (count > 0) {
                        Text("$count פתוחות", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    vm.addList(newList)
                    newList = ""
                }) { Icon(Icons.Filled.Add, contentDescription = "הוסף רשימה") }
            }
        }

        SettingsCard("איך הסנכרון עובד") {
            Text(
                "• משימה עם תאריך נוצרת כאירוע ביומן גוגל (עם שעה – אירוע רגיל, בלי שעה – אירוע של כל היום).\n" +
                    "• סימון משימה כהושלמה מוסיף ✓ לכותרת האירוע.\n" +
                    "• הזזת אירוע או שינוי שם ביומן גוגל מתעדכנים חזרה במשימה כשפותחים את האפליקציה.\n" +
                    "• מחיקת אירוע ביומן מנתקת אותו מהמשימה (המשימה נשארת).\n" +
                    "• כפתור ➕ ליד אירוע מהיומן הופך אותו למשימה.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}
