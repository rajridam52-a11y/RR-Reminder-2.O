package com.example.features.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.StopwatchSessionEntity
import com.example.core.database.TimerEntity
import com.example.core.database.TimerHistoryEntity
import com.example.core.database.TimerPresetEntity
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ScheduleCalculator
import com.example.ui.components.RRCircularProgressRing
import com.example.ui.components.RRGradientButton
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
    var selectedDurationMs by remember { mutableLongStateOf(15 * 60_000L) }
    var selectedPresetLabel by remember { mutableStateOf("15 min") }
    var selectedTimerId by remember { mutableStateOf<Long?>(null) }
    var previewPresetMode by remember { mutableStateOf(false) }

    val latestRunningId = timers.filter { it.isRunning }.maxOfOrNull { it.id }
    LaunchedEffect(latestRunningId) {
        if (latestRunningId != null) {
            selectedTimerId = latestRunningId
            previewPresetMode = false
        }
    }

    val primaryTimer = if (previewPresetMode) {
        null
    } else {
        timers.firstOrNull { it.id == selectedTimerId }
            ?: timers.firstOrNull { it.isRunning }
            ?: timers.firstOrNull()
    }
    val heroRemainingMs = if (primaryTimer != null) {
        if (primaryTimer.isRunning && primaryTimer.endEpochMillis != null) {
            (primaryTimer.endEpochMillis - tickNowMillis).coerceAtLeast(0L)
        } else {
            primaryTimer.remainingMillis
        }
    } else {
        selectedDurationMs
    }
    val heroProgress = if (primaryTimer != null && primaryTimer.durationMillis > 0L) {
        (heroRemainingMs.toFloat() / primaryTimer.durationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030816),
                        Color(0xFF091534),
                        Color(0xFF040B1C),
                    ),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp),
        ) {
            // Screen 9 Hero Ring + Quick Presets + Purple-Blue Start CTA
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RRCircularProgressRing(
                        progress = heroProgress,
                        size = 216.dp,
                        strokeWidth = 10.dp,
                        primaryColor = Color(0xFF38BDF8),
                        secondaryColor = Color(0xFFA855F7),
                        trackColor = Color(0x331E3A8A),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.HourglassEmpty,
                                contentDescription = "Timer",
                                tint = Color(0xFF7DD3FC),
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ScheduleCalculator.formatDurationMillis(heroRemainingMs),
                                style = TabularClockLarge.copy(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = primaryTimer?.label ?: selectedPresetLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA2B8DE),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Quick duration pills matching Screen 9 ("1 min | 5 min | 15 min | 30 min")
                    val quickDurations = listOf(
                        60_000L to "1 min",
                        5 * 60_000L to "5 min",
                        15 * 60_000L to "15 min",
                        30 * 60_000L to "30 min",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        quickDurations.forEach { (ms, lbl) ->
                            val selected = primaryTimer == null && selectedDurationMs == ms
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) Color(0xFF4F46E5) else Color(0xBB0D1B3A),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFF818CF8) else Color(0x3360A5FA),
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedDurationMs = ms
                                        selectedPresetLabel = lbl
                                        previewPresetMode = true
                                    },
                            ) {
                                Text(
                                    text = lbl,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xBB0D1B3A),
                            border = BorderStroke(1.dp, Color(0x4438BDF8)),
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showCreateDialog = true }
                                .testTag("timer_add_fab"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Custom Timer", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                Text("Custom", style = MaterialTheme.typography.labelLarge, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Primary Start / Pause CTA button (Screen 9 purple-to-blue pill)
                    if (primaryTimer != null) {
                        val actionLabel = when {
                            primaryTimer.isRunning -> "Pause"
                            primaryTimer.remainingMillis == 0L -> "Restart"
                            primaryTimer.remainingMillis < primaryTimer.durationMillis -> "Resume"
                            else -> "Start"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RRGradientButton(
                                text = actionLabel,
                                leadingIcon = if (primaryTimer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                colors = listOf(Color(0xFF7C3AED), Color(0xFF2563EB)),
                                onClick = {
                                    if (primaryTimer.isRunning) onPauseTimer(primaryTimer)
                                    else onResumeTimer(primaryTimer)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("timer_hero_toggle_btn"),
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color(0xCC0E1C3A),
                                border = BorderStroke(1.dp, Color(0x5538BDF8)),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .clickable { onResetTimer(primaryTimer) }
                                    .testTag("timer_hero_reset_btn"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Replay, contentDescription = "Reset Timer", tint = Color.White)
                                }
                            }
                        }
                    } else {
                        RRGradientButton(
                            text = "Start $selectedPresetLabel",
                            leadingIcon = Icons.Rounded.PlayArrow,
                            colors = listOf(Color(0xFF7C3AED), Color(0xFF2563EB)),
                            onClick = {
                                previewPresetMode = false
                                onCreateTimer(selectedPresetLabel, selectedDurationMs, false)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(52.dp)
                                .testTag("timer_hero_start_btn"),
                        )
                    }
                }
            }

            // Custom saved user presets (if any beyond quick ones)
            if (presets.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xCC0D1A38),
                        border = BorderStroke(1.dp, Color(0x3360A5FA)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Saved Presets", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                presets.forEach { preset ->
                                    AssistChip(
                                        onClick = { onCreateTimer(preset.label, preset.durationMillis, false) },
                                        label = { Text(preset.label, color = Color.White) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Multi-timer queue list
            if (timers.isNotEmpty()) {
                item {
                    Text(
                        "Active Timers (${timers.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
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

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xCC0D1A38),
                        border = BorderStroke(
                            1.dp,
                            if (timer.id == primaryTimer?.id) Color(0xAA38BDF8)
                            else if (timer.isRunning) Color(0x6638BDF8)
                            else Color(0x3360A5FA),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                selectedTimerId = timer.id
                                previewPresetMode = false
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RRCircularProgressRing(
                                progress = progress,
                                size = 50.dp,
                                strokeWidth = 5.dp,
                                primaryColor = Color(0xFF38BDF8),
                                secondaryColor = Color(0xFFA855F7),
                            ) {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(timer.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                Text(
                                    text = ScheduleCalculator.formatDurationMillis(remaining),
                                    style = TabularClockMedium,
                                    color = Color(0xFF38BDF8),
                                )
                                if (timer.isRepeat) {
                                    RRStatusPill(text = "Auto-repeat", color = extra.info)
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (timer.isRunning) onPauseTimer(timer) else onResumeTimer(timer)
                                },
                            ) {
                                Icon(
                                    if (timer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (timer.isRunning) "Pause" else "Start",
                                    tint = Color.White,
                                )
                            }
                            IconButton(onClick = { onResetTimer(timer) }) {
                                Icon(Icons.Rounded.Replay, contentDescription = "Reset", tint = Color(0xFF93C5FD))
                            }
                            IconButton(onClick = { onDeleteTimer(timer.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFFB7185))
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
                        Text("Timer History", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        TextButton(onClick = onClearHistory) { Text("Clear", color = Color(0xFF38BDF8)) }
                    }
                }
                items(history.take(15), key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xCC0D1A38),
                        border = BorderStroke(1.dp, Color(0x2860A5FA)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(item.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                Text(
                                    ScheduleCalculator.formatTime(item.completedAtMillis, preferences.use24Hour),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF9EB5DF),
                                )
                            }
                            Text(
                                ScheduleCalculator.formatDurationMillis(item.durationMillis),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF38BDF8),
                            )
                        }
                    }
                }
            }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030816),
                        Color(0xFF081430),
                        Color(0xFF040A18),
                    ),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 96.dp),
        ) {
            // Screen 8 Digital Readout + Circular Play/Pause + Lap/Reset Pills
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = ScheduleCalculator.formatStopwatchMillis(state.totalElapsedMillis),
                        style = TabularClockLarge.copy(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                        modifier = Modifier.testTag("stopwatch_readout"),
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    // Big glowing blue circle Play / Pause CTA
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8)),
                                ),
                            )
                            .clickable { if (state.isRunning) onPause() else onStart() }
                            .testTag("stopwatch_start_pause_btn"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isRunning) "Pause" else "Start",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC0D1A38),
                            border = BorderStroke(1.dp, Color(0x4460A5FA)),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(CircleShape)
                                .clickable(enabled = state.isRunning || state.totalElapsedMillis > 0L, onClick = onLap)
                                .testTag("stopwatch_lap_btn"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Lap",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (state.isRunning || state.totalElapsedMillis > 0L) Color.White else Color(0xFF6B84B3),
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC0D1A38),
                            border = BorderStroke(1.dp, Color(0x4460A5FA)),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(CircleShape)
                                .clickable(enabled = state.totalElapsedMillis > 0L) {
                                    if (state.totalElapsedMillis > 1000L) showSaveDialog = true else onReset(null)
                                }
                                .testTag("stopwatch_reset_btn"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (state.totalElapsedMillis > 0L) Color.White else Color(0xFF6B84B3),
                                )
                            }
                        }
                    }
                }
            }

            // Lap History card matching Screen 8
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xCC0B1733),
                    border = BorderStroke(1.dp, Color(0x4438BDF8)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Lap History",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (state.lapsMillis.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 22.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No laps yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF8AA4D6),
                                )
                            }
                        } else {
                            val splitCumulative = mutableListOf<Long>()
                            var acc = 0L
                            state.lapsMillis.forEach { lap ->
                                acc += lap
                                splitCumulative.add(acc)
                            }
                            state.lapsMillis.indices.reversed().forEach { idx ->
                                val lapMs = state.lapsMillis[idx]
                                val splitMs = splitCumulative[idx]
                                val isBest = state.bestLapIndex == idx
                                val isSlowest = state.slowestLapIndex == idx

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text("Lap ${idx + 1}", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                        if (isBest) RRStatusPill("Best", extra.success)
                                        if (isSlowest) RRStatusPill("Slowest", MaterialTheme.colorScheme.error)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            ScheduleCalculator.formatStopwatchMillis(lapMs),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color(0xFF38BDF8),
                                        )
                                        Text(
                                            "Split ${ScheduleCalculator.formatStopwatchMillis(splitMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF9EB5DF),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (sessions.isNotEmpty()) {
                item {
                    Text("Saved Sessions", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                items(sessions, key = { it.id }) { session ->
                    val lapCount = session.lapsCsvMillis.split(",").count { it.isNotBlank() }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xCC0D1A38),
                        border = BorderStroke(1.dp, Color(0x3360A5FA)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                Text(
                                    "${ScheduleCalculator.formatStopwatchMillis(session.totalElapsedMillis)} • $lapCount laps • ${ScheduleCalculator.formatDate(session.recordedAtMillis, preferences.dateFormatStyle.pattern)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF9EB5DF),
                                )
                            }
                            IconButton(onClick = { onDeleteSession(session.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete session", tint = Color(0xFFFB7185))
                            }
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
