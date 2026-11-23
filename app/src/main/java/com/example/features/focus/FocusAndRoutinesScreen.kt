package com.example.features.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.database.FocusSessionEntity
import com.example.core.database.RoutineEntity
import com.example.core.database.RoutineStepEntity
import com.example.core.database.RoutineWithSteps
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ScheduleCalculator
import com.example.features.productivity.FocusUiState
import com.example.ui.components.RRCard
import com.example.ui.components.RRConfirmDialog
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRMetricBadge
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockLarge
import java.time.LocalDate

@Composable
fun FocusAndRoutinesTabContent(
    focusState: FocusUiState,
    focusSessions: List<FocusSessionEntity>,
    routines: List<RoutineWithSteps>,
    preferences: UserPreferences,
    onConfigureFocus: (String, Int, Int) -> Unit,
    onStartFocus: () -> Unit,
    onPauseFocus: () -> Unit,
    onResetFocus: () -> Unit,
    onSaveRoutine: (RoutineEntity, List<Pair<String, Int>>) -> Unit,
    onToggleRoutineStep: (RoutineStepEntity, Long) -> Unit,
    onDeleteRoutine: (Long) -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val todayEpochDay = LocalDate.now().toEpochDay()
    var showFocusConfig by remember { mutableStateOf(false) }
    var showRoutineEditor by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<RoutineWithSteps?>(null) }
    var deleteRoutineCandidate by remember { mutableStateOf<RoutineEntity?>(null) }

    val totalFocusMinutes = remember(focusSessions) {
        focusSessions.filter { it.phase == "FOCUS" }.sumOf { it.actualDurationSeconds } / 60
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        ) {
            // Focus Mode Card
            item {
                val totalPhaseMs = ((if (focusState.phase == "FOCUS") focusState.focusMinutes else focusState.breakMinutes) * 60_000L).coerceAtLeast(1L)
                val progress = (focusState.remainingMs.toFloat() / totalPhaseMs.toFloat()).coerceIn(0f, 1f)
                val ringColor = if (focusState.phase == "FOCUS") extra.accentPrimary else extra.success
                val trackColor = MaterialTheme.colorScheme.surfaceVariant

                RRCard(accentGlow = focusState.isRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("FOCUS MODE • ${focusState.phase}", style = MaterialTheme.typography.labelMedium, color = ringColor)
                            Text(focusState.label, style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { showFocusConfig = true }) {
                            Text("${focusState.focusMinutes}m / ${focusState.breakMinutes}m")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.size(148.dp)) {
                            val strokeW = 10.dp.toPx()
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                            )
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                            )
                        }
                        Text(
                            text = ScheduleCalculator.formatDurationMillis(focusState.remainingMs),
                            style = TabularClockLarge.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize),
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { if (focusState.isRunning) onPauseFocus() else onStartFocus() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("focus_start_pause_btn"),
                        ) {
                            Icon(
                                if (focusState.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (focusState.isRunning) "Pause" else "Start ${focusState.phase.lowercase().replaceFirstChar { it.uppercase() }}")
                        }
                        OutlinedButton(onClick = onResetFocus) {
                            Icon(Icons.Rounded.Replay, contentDescription = "Reset Focus")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RRMetricBadge("Focus Sessions", "${focusSessions.count { it.phase == "FOCUS" }}", extra.accentPrimary, Modifier.weight(1f))
                    RRMetricBadge("Focus Minutes", "$totalFocusMinutes min", extra.success, Modifier.weight(1f))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Routines", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = {
                            editingRoutine = null
                            showRoutineEditor = true
                        },
                    ) {
                        Text("+ New Routine")
                    }
                }
            }

            if (routines.isEmpty()) {
                item {
                    RREmptyState(
                        icon = Icons.Rounded.WbTwilight,
                        title = "No routines yet",
                        subtitle = "Build Morning, Evening, or Custom routines with ordered steps and optional reminders.",
                        actionLabel = "Create Routine",
                        onActionClick = {
                            editingRoutine = null
                            showRoutineEditor = true
                        },
                    )
                }
            } else {
                items(routines, key = { it.routine.id }) { item ->
                    val r = item.routine
                    val steps = item.steps.sortedBy { it.sortOrder }
                    val doneCount = steps.count { it.lastCompletedEpochDay == todayEpochDay }
                    val totalMins = steps.sumOf { it.durationMinutes }

                    RRCard(
                        onClick = {
                            editingRoutine = item
                            showRoutineEditor = true
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${r.routineType} • ${ScheduleCalculator.formatHourMinute(r.startHour, r.startMinute, preferences.use24Hour)} • $totalMins min total",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RRStatusPill("$doneCount/${steps.size} today", extra.accentPrimary)
                            IconButton(onClick = { deleteRoutineCandidate = r }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete routine", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        steps.forEach { step ->
                            val isDone = step.lastCompletedEpochDay == todayEpochDay
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isDone,
                                    onCheckedChange = { onToggleRoutineStep(step, todayEpochDay) },
                                    modifier = Modifier.size(32.dp),
                                )
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${step.durationMinutes}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (focusSessions.isNotEmpty()) {
                item { Text("Recent Focus History", style = MaterialTheme.typography.titleMedium) }
                items(focusSessions.take(10), key = { it.id }) { sess ->
                    RRCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(sess.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    ScheduleCalculator.formatTime(sess.completedAtMillis, preferences.use24Hour),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RRStatusPill("${sess.actualDurationSeconds / 60}m ${sess.phase}", extra.success)
                        }
                    }
                }
            }
        }
    }

    if (showFocusConfig) {
        var label by remember { mutableStateOf(focusState.label) }
        var focusMin by remember { mutableIntStateOf(focusState.focusMinutes) }
        var breakMin by remember { mutableIntStateOf(focusState.breakMinutes) }

        AlertDialog(
            onDismissRequest = { showFocusConfig = false },
            title = { Text("Configure Focus Intervals") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Focus Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Focus Duration: $focusMin min", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 25, 45, 60).forEach { m ->
                            FilterChip(selected = focusMin == m, onClick = { focusMin = m }, label = { Text("${m}m") })
                        }
                    }
                    Text("Break Duration: $breakMin min", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15).forEach { m ->
                            FilterChip(selected = breakMin == m, onClick = { breakMin = m }, label = { Text("${m}m") })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfigureFocus(label, focusMin, breakMin)
                        showFocusConfig = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showFocusConfig = false }) { Text("Cancel") } },
        )
    }

    if (showRoutineEditor) {
        RoutineEditorDialog(
            initial = editingRoutine,
            onDismiss = { showRoutineEditor = false },
            onSave = { routine, steps ->
                onSaveRoutine(routine, steps)
                showRoutineEditor = false
            },
        )
    }

    deleteRoutineCandidate?.let { candidate ->
        RRConfirmDialog(
            title = "Delete Routine",
            message = "Delete \"${candidate.name}\"?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteRoutine(candidate.id)
                deleteRoutineCandidate = null
            },
            onDismiss = { deleteRoutineCandidate = null },
        )
    }
}

@Composable
private fun RoutineEditorDialog(
    initial: RoutineWithSteps?,
    onDismiss: () -> Unit,
    onSave: (RoutineEntity, List<Pair<String, Int>>) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.routine?.name ?: "") }
    var type by remember { mutableStateOf(initial?.routine?.routineType ?: "MORNING") }
    var hour by remember { mutableIntStateOf(initial?.routine?.startHour ?: 7) }
    var minute by remember { mutableIntStateOf(initial?.routine?.startMinute ?: 0) }
    var reminderEnabled by remember { mutableStateOf(initial?.routine?.reminderEnabled ?: false) }
    var stepTitle by remember { mutableStateOf("") }
    var stepMins by remember { mutableIntStateOf(10) }
    val steps = remember {
        mutableStateListOf<Pair<String, Int>>().apply {
            initial?.steps?.sortedBy { it.sortOrder }?.forEach { add(it.title to it.durationMinutes) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Routine" else "Edit Routine") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("MORNING" to "Morning", "EVENING" to "Evening", "CUSTOM" to "Custom").forEach { (k, lbl) ->
                        FilterChip(selected = type == k, onClick = { type = k }, label = { Text(lbl) })
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Daily Reminder (${String.format("%02d:%02d", hour, minute)})")
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }
                Text("Routine Steps", style = MaterialTheme.typography.labelMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = stepTitle,
                        onValueChange = { stepTitle = it },
                        label = { Text("Step title") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = {
                            if (stepTitle.isNotBlank()) {
                                steps.add(stepTitle.trim() to stepMins)
                                stepTitle = ""
                            }
                        },
                    ) { Text("Add") }
                }
                steps.forEachIndexed { idx, (st, mins) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${idx + 1}. $st (${mins}m)", modifier = Modifier.weight(1f))
                        IconButton(onClick = { steps.removeAt(idx) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove step", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val saved = (initial?.routine ?: RoutineEntity(name = name.trim())).copy(
                        name = name.trim(),
                        routineType = type,
                        startHour = hour,
                        startMinute = minute,
                        reminderEnabled = reminderEnabled,
                    )
                    onSave(saved, steps.toList())
                },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
