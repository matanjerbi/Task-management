package com.matan.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun FocusScreen(vm: TaskViewModel, task: Task, onClose: () -> Unit) {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val live = vm.tasks.firstOrNull { it.id == task.id } ?: task
    val initial = live.durationMin.coerceIn(5, 90).let { if (it > 50) 50 else it }
    var totalMin by remember { mutableIntStateOf(if (initial < 10) 25 else initial) }
    var remaining by remember { mutableIntStateOf(totalMin * 60) }
    var elapsed by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining--
            elapsed++
        }
        if (running && remaining <= 0) {
            running = false
            finished = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun exit(markDone: Boolean) {
        val minutes = elapsed / 60
        if (minutes > 0) vm.addFocusMinutes(live, minutes)
        if (markDone) {
            val cur = vm.tasks.firstOrNull { it.id == live.id }
            if (cur != null && !cur.done) vm.toggleDone(cur)
            vm.message = "כל הכבוד! 🎉" + if (minutes > 0) " $minutes דק׳ של פוקוס" else ""
        }
        onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(FocusBrush)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { exit(false) }) { Icon(Icons.Filled.Close, contentDescription = "יציאה", tint = Color.White) }
            Text("מצב פוקוס", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            live.title,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        val progress = if (totalMin == 0) 0f else 1f - remaining.toFloat() / (totalMin * 60)
        ProgressRing(progress = progress, size = 250.dp, stroke = 14.dp, color = Color(0xFFA78BFA), track = Color.White.copy(alpha = 0.1f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    String.format(Locale.US, "%02d:%02d", remaining / 60, remaining % 60),
                    color = Color.White,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    when {
                        finished -> "הזמן נגמר ✨"
                        running -> "רק זה. שום דבר אחר."
                        elapsed > 0 -> "בהפסקה"
                        else -> "מוכן?"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        if (!running && elapsed == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in listOf(10, 15, 25, 45, 60)) {
                    val sel = m == totalMin
                    Text(
                        "$m",
                        color = if (sel) Color(0xFF1E1B4B) else Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Color.White else Color.White.copy(alpha = 0.12f))
                            .clickable {
                                totalMin = m
                                remaining = m * 60
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Text("דקות", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (finished) {
                GradientButton("+5 דקות", Modifier.weight(1f), brush = Brush2(Color.White.copy(alpha = 0.15f))) {
                    totalMin += 5
                    remaining = 5 * 60
                    finished = false
                    running = true
                }
            } else {
                GradientButton(
                    if (running) "⏸ הפסקה" else if (elapsed > 0) "▶ המשך" else "▶ התחל",
                    Modifier.weight(1f),
                    brush = AiBrush,
                ) { running = !running }
            }
            GradientButton("✓ סיימתי", Modifier.weight(1f), brush = Brush2(Success)) { exit(true) }
        }

        if (live.subtasks.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("הצעדים", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                live.subtasks.forEachIndexed { i, s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.toggleSubtask(live, i) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (s.done) "✅" else "⬜", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            s.text,
                            color = if (s.done) Color.White.copy(alpha = 0.5f) else Color.White,
                            textDecoration = if (s.done) TextDecoration.LineThrough else TextDecoration.None,
                        )
                    }
                }
            }
        }
        if (live.notes.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(live.notes, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(30.dp))
    }
}

