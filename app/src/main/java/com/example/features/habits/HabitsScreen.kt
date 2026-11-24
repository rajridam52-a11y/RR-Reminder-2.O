package com.example.features.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.database.HabitEntity
import com.example.core.database.HabitWithLogs
import com.example.ui.components.RRCard
import com.example.ui.components.RRConfirmDialog
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import java.time.LocalDate

@Composable
fun HabitsTabContent(
    habits: List<HabitWithLogs>,
    onSaveHabit: (HabitEntity) -> Unit,
    onToggleCheckIn: (Long, Long, Boolean) -> Unit,
    onSetPaused: (HabitEntity, Boolean) -> Unit,
    onSetArchived: (HabitEntity, Boolean) -> Unit,
    onDeleteHabit: (Long) -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val todayEpochDay = LocalDate.now().toEpochDay()
    var showArchived by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<HabitEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<HabitEntity?>(null) }

    val displayed = remember(habits, showArchived) {
        habits.filter { it.habit.isArchived == showArchived }
    }

    // 14-day aggregate check-in counts for chart
    val chartCounts = remember(habits, todayEpochDay) {
        val activeLogs = habits.filter { !it.habit.isArchived }.flatMap { it.logs }
        (13 downTo 0).map { daysAgo ->
            val day = todayEpochDay - daysAgo
            activeLogs.count { it.dateEpochDay == day }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        ) {
            item {
                RRCard(accentGlow = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("14-Day Momentum", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Daily consistency across active habits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilterChip(
                            selected = showArchived,
                            onClick = { showArchived = !showArchived },
                            label = { Text(if (showArchived) "Active" else "Archived") },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val barColor = extra.accentPrimary
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp),
                    ) {
                        val maxVal = (chartCounts.maxOrNull() ?: 1).coerceAtLeast(1)
                        val count = chartCounts.size
                        val spacing = 8.dp.toPx()
                        val barWidth = ((size.width - spacing * (count - 1)) / count).coerceAtLeast(4f)
                        chartCounts.forEachIndexed { idx, value ->
                            val x = idx * (barWidth + spacing)
                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset(x, 0f),
                                size = Size(barWidth, size.height),
                                cornerRadius = CornerRadius(6f, 6f),
                            )
                            val h = (value.toFloat() / maxVal.toFloat()) * size.height
                            if (h > 0f) {
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(x, size.height - h),
                                    size = Size(barWidth, h),
                                    cornerRadius = CornerRadius(6f, 6f),
                                )
                            }
                        }
                    }
                }
            }

            if (displayed.isEmpty()) {
                item {
                    RREmptyState(
                        icon = Icons.Rounded.TrackChanges,
                        title = if (showArchived) "No archived habits" else "Build steady habits",
                        subtitle = "Track daily or weekly habits with streaks, charts, and stress-free pause controls.",
                        actionLabel = if (showArchived) null else "Create Habit",
                        onActionClick = {
                            editingHabit = null
                            showEditor = true
                        },
                    )
                }
            } else {
                items(displayed, key = { it.habit.id }) { item ->
                    val h = item.habit
                    val logDays = remember(item.logs) { item.logs.map { it.dateEpochDay }.toSet() }
                    val doneToday = todayEpochDay in logDays
                    val streak = remember(logDays, todayEpochDay) {
                        var s = 0
                        var cursor = if (todayEpochDay in logDays) todayEpochDay else todayEpochDay - 1
                        while (cursor in logDays) {
                            s++
                            cursor--
                        }
                        s
                    }
                    val last7Done = (0..6).count { (todayEpochDay - it) in logDays }
                    val last30Done = (0..29).count { (todayEpochDay - it) in logDays }

                    RRCard(
                        onClick = {
                            editingHabit = h
                            showEditor = true
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    if (!h.isPaused && !h.isArchived) {
                                        onToggleCheckIn(h.id, todayEpochDay, doneToday)
                                    }
                                },
                                enabled = !h.isPaused && !h.isArchived,
                            ) {
                                Icon(
                                    imageVector = if (doneToday) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = "Check in today",
                                    tint = if (doneToday) extra.success else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(h.name, style = MaterialTheme.typography.titleMedium)
                                if (h.description.isNotBlank()) {
                                    Text(
                                        h.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    RRStatusPill("${streak}d streak", extra.accentPrimary, icon = Icons.Rounded.LocalFireDepartment)
                                    RRStatusPill("$last7Done/7 wk", extra.info)
                                    RRStatusPill("$last30Done/30 mo", extra.accentSecondary)
                                    if (h.isPaused) RRStatusPill("Paused", extra.warning)
                                }
                            }
                            Column {
                                IconButton(onClick = { onSetPaused(h, !h.isPaused) }) {
                                    Icon(
                                        if (h.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                        contentDescription = if (h.isPaused) "Resume habit" else "Pause habit",
                                    )
                                }
                                IconButton(onClick = { onSetArchived(h, !h.isArchived) }) {
                                    Icon(
                                        if (h.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                        contentDescription = if (h.isArchived) "Unarchive" else "Archive",
                                    )
                                }
                                IconButton(onClick = { deleteCandidate = h }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete habit",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingHabit = null
                showEditor = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("habits_fab"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Create Habit")
        }
    }

    if (showEditor) {
        HabitEditorDialog(
            initial = editingHabit,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                onSaveHabit(saved)
                showEditor = false
            },
        )
    }

    deleteCandidate?.let { candidate ->
        RRConfirmDialog(
            title = "Delete Habit",
            message = "Delete \"${candidate.name}\" and its completion history?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteHabit(candidate.id)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@Composable
private fun HabitEditorDialog(
    initial: HabitEntity?,
    onDismiss: () -> Unit,
    onSave: (HabitEntity) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var frequencyType by remember { mutableStateOf(initial?.frequencyType ?: "DAILY") }
    var targetMask by remember { mutableIntStateOf(initial?.targetDaysMask ?: 0b1111111) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Habit" else "Edit Habit") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Goal or note") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Schedule", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "CUSTOM" to "Custom Days").forEach { (k, lbl) ->
                        FilterChip(
                            selected = frequencyType == k,
                            onClick = { frequencyType = k },
                            label = { Text(lbl) },
                        )
                    }
                }
                if (frequencyType == "CUSTOM") {
                    val days = listOf("M", "T", "W", "T", "F", "S", "S")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        days.forEachIndexed { idx, txt ->
                            val active = (targetMask and (1 shl idx)) != 0
                            FilterChip(
                                selected = active,
                                onClick = { targetMask = targetMask xor (1 shl idx) },
                                label = { Text(txt) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val saved = (initial ?: HabitEntity(name = name.trim())).copy(
                        name = name.trim(),
                        description = description.trim(),
                        frequencyType = frequencyType,
                        targetDaysMask = targetMask,
                    )
                    onSave(saved)
                },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
