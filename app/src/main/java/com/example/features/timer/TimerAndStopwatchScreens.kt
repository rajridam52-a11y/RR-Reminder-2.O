package com.example.features.timer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.database.StopwatchSessionEntity
import com.example.core.database.TimerEntity
import com.example.core.database.TimerHistoryEntity
import com.example.core.database.TimerPresetEntity
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ScheduleCalculator
import com.example.ui.components.RRCard
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockLarge
import com.example.ui.theme.TabularClockMedium

@Composable
fun TimerTabContent(
    timers: List<TimerEntity>,
    presets: List<TimerPresetEntity>,
    history: List<TimerHistoryEntity>,
    tickNowMillis: Long,
    preferences: UserPreferences,
    onCreateTimer: (String, Long, Boolean) -> Unit,
    onPauseTimer: (TimerEntity) -> Unit,
    onResumeTimer: (TimerEntity) -> Unit,
    onResetTimer: (TimerEntity) -> Unit,
    onDeleteTimer: (Long) -> Unit,
    onAddPreset: (String, Long) -> Unit,
    onClearHistory: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        ) {
            item {
                RRCard {
                    Text("Quick Presets", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presets.forEach { preset ->
                            AssistChip(
                                onClick = { onCreateTimer(preset.label, preset.durationMillis, false) },
                                label = { Text(preset.label) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                            )
                        }
                    }
                }
            }

            if (timers.isEmpty()) {
                item {
                    RREmptyState(
                        icon = Icons.Rounded.Timer,
                        title = "No active timers",
                        subtitle = "Tap a preset above or create a custom countdown timer.",
                        actionLabel = "Custom Timer",
                        onActionClick = { showCreateDialog = true },
                    )
                }
            } else {
                items(timers, key = { it.id }) { timer ->
                    val remaining = if (timer.isRunning && timer.endEpochMillis != null) {
                        (timer.endEpochMillis - tickNowMillis).coerceAtLeast(0L)
                    } else {
                        timer.remainingMillis
                    }
                    val progress = if (timer.durationMillis > 0) {
                        (remaining.toFloat() / timer.durationMillis.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    RRCard(accentGlow = timer.isRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(timer.label, style = MaterialTheme.typography.titleMedium)
                                if (timer.isRepeat) {
                                    RRStatusPill(text = "Auto-repeat", color = extra.info)
                                }
                            }
                            Text(
                                text = ScheduleCalculator.formatDurationMillis(remaining),
                                style = TabularClockMedium,
                                color = if (timer.isRunning) extra.accentPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (timer.isRunning) {
                                FilledTonalButton(onClick = { onPauseTimer(timer) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pause")
                                }
                            } else {
                                Button(onClick = { onResumeTimer(timer) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start")
                                }
                            }
                            OutlinedButton(onClick = { onResetTimer(timer) }) {
                                Icon(Icons.Rounded.Replay, contentDescription = "Reset")
                            }
                            IconButton(onClick = { onDeleteTimer(timer.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete timer", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Timer History", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onClearHistory) { Text("Clear") }
                    }
                }
                items(history.take(15), key = { it.id }) { item ->
                    RRCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(item.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    ScheduleCalculator.formatTime(item.completedAtMillis, preferences.use24Hour),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                ScheduleCalculator.formatDurationMillis(item.durationMillis),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("timer_add_fab"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New Timer")
        }
    }

    if (showCreateDialog) {
        CreateTimerDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { label, ms, repeat, saveAsPreset ->
                onCreateTimer(label, ms, repeat)
                if (saveAsPreset) onAddPreset(label, ms)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun CreateTimerDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Long, Boolean, Boolean) -> Unit,
) {
    var label by remember { mutableStateOf("Countdown") }
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }
    var repeat by remember { mutableStateOf(false) }
    var savePreset by remember { mutableStateOf(false) }

    val totalMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Countdown Timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DurationStepper("Hrs", hours, 0..23) { hours = it }
                    DurationStepper("Min", minutes, 0..59) { minutes = it }
                    DurationStepper("Sec", seconds, 0..59) { seconds = it }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Repeat automatically")
                    Switch(checked = repeat, onCheckedChange = { repeat = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Save as preset")
                    Switch(checked = savePreset, onCheckedChange = { savePreset = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(label, totalMs, repeat, savePreset) },
                enabled = totalMs > 0L,
                modifier = Modifier.testTag("timer_start_create_btn"),
            ) {
                Text("Start Timer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DurationStepper(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelSmall)
        IconButton(onClick = { onChange((value + 1).let { if (it > range.last) range.first else it }) }) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Increase $title")
        }
        Text(String.format("%02d", value), style = TabularClockMedium)
        IconButton(onClick = { onChange((value - 1).let { if (it < range.first) range.last else it }) }) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Decrease $title")
        }
    }
}

@Composable
fun StopwatchTabContent(
    state: StopwatchUiState,
    sessions: List<StopwatchSessionEntity>,
    preferences: UserPreferences,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onLap: () -> Unit,
    onReset: (String?) -> Unit,
    onDeleteSession: (Long) -> Unit,
) {
    val extra = LocalRRExtraColors.current
    var showSaveDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
    ) {
        item {
            RRCard(accentGlow = state.isRunning) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("MONOTONIC CHRONOMETER", style = MaterialTheme.typography.labelMedium, color = extra.accentPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = ScheduleCalculator.formatStopwatchMillis(state.totalElapsedMillis),
                        style = TabularClockLarge,
                        modifier = Modifier.testTag("stopwatch_readout"),
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { if (state.isRunning) onPause() else onStart() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("stopwatch_start_pause_btn"),
                        ) {
                            Icon(
                                if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.isRunning) "Pause" else if (state.totalElapsedMillis > 0L) "Resume" else "Start")
                        }
                        FilledTonalButton(
                            onClick = onLap,
                            enabled = state.isRunning || state.totalElapsedMillis > 0L,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("stopwatch_lap_btn"),
                        ) {
                            Icon(Icons.Rounded.Flag, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lap")
                        }
                        OutlinedButton(
                            onClick = {
                                if (state.totalElapsedMillis > 1000L) showSaveDialog = true else onReset(null)
                            },
                            enabled = state.totalElapsedMillis > 0L,
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("stopwatch_reset_btn"),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Reset")
                        }
                    }
                }
            }
        }

        if (state.lapsMillis.isNotEmpty()) {
            item {
                Text("Laps (${state.lapsMillis.size})", style = MaterialTheme.typography.titleMedium)
            }
            // Show newest lap at top while computing exact cumulative split times
            val splitCumulative = mutableListOf<Long>()
            var acc = 0L
            state.lapsMillis.forEach { lap ->
                acc += lap
                splitCumulative.add(acc)
            }
            val reversedIndices = state.lapsMillis.indices.reversed().toList()
            items(reversedIndices) { idx ->
                val lapMs = state.lapsMillis[idx]
                val splitMs = splitCumulative[idx]
                val isBest = state.bestLapIndex == idx
                val isSlowest = state.slowestLapIndex == idx

                RRCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Lap ${idx + 1}", style = MaterialTheme.typography.titleSmall)
                            if (isBest) RRStatusPill("Best", extra.success)
                            if (isSlowest) RRStatusPill("Slowest", MaterialTheme.colorScheme.error)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(ScheduleCalculator.formatStopwatchMillis(lapMs), style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Split ${ScheduleCalculator.formatStopwatchMillis(splitMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (sessions.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Timing History", style = MaterialTheme.typography.titleMedium)
            }
            items(sessions, key = { it.id }) { session ->
                val lapCount = session.lapsCsvMillis.split(",").count { it.isNotBlank() }
                RRCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${ScheduleCalculator.formatStopwatchMillis(session.totalElapsedMillis)} • $lapCount laps • ${ScheduleCalculator.formatDate(session.recordedAtMillis, preferences.dateFormatStyle.pattern)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteSession(session.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete session")
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        var sessionLabel by remember { mutableStateOf("Timing Session") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Reset Stopwatch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save this timing session to history before resetting?")
                    OutlinedTextField(
                        value = sessionLabel,
                        onValueChange = { sessionLabel = it },
                        label = { Text("Session label") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReset(sessionLabel)
                        showSaveDialog = false
                    },
                ) { Text("Save & Reset") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onReset(null)
                        showSaveDialog = false
                    },
                ) { Text("Discard") }
            },
        )
    }
}
