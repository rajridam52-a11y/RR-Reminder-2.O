package com.example.features.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.calendar.DeviceCalendarEvent
import com.example.core.database.ReminderEntity
import com.example.core.database.TaskWithSubtasks
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.RRCard
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

enum class CalendarViewMode(val label: String) {
    MONTH("Month"),
    WEEK("Week"),
    DAY("Day"),
}

sealed class AgendaItem(
    val idKey: String,
    val title: String,
    val subtitle: String,
    val timeMillis: Long,
    val badge: String,
    val colorKey: String,
) {
    class ReminderAgenda(val reminder: ReminderEntity, timeText: String) : AgendaItem(
        idKey = "rem_${reminder.id}",
        title = reminder.title,
        subtitle = reminder.description.ifBlank { "$timeText • ${reminder.category}" },
        timeMillis = reminder.snoozedUntilMillis ?: reminder.triggerAtMillis,
        badge = "Reminder",
        colorKey = "primary",
    )

    class TaskAgenda(val taskWithSubtasks: TaskWithSubtasks) : AgendaItem(
        idKey = "task_${taskWithSubtasks.task.id}",
        title = taskWithSubtasks.task.title,
        subtitle = taskWithSubtasks.task.description.ifBlank { taskWithSubtasks.task.category },
        timeMillis = taskWithSubtasks.task.dueDateMillis ?: 0L,
        badge = "Task",
        colorKey = "secondary",
    )

    class DeviceEventAgenda(val event: DeviceCalendarEvent, timeText: String) : AgendaItem(
        idKey = "cal_${event.id}_${event.startMillis}",
        title = event.title,
        subtitle = listOfNotNull(timeText, event.location).joinToString(" • "),
        timeMillis = event.startMillis,
        badge = "Calendar",
        colorKey = "info",
    )
}

@Composable
fun CalendarTabContent(
    reminders: List<ReminderEntity>,
    tasks: List<TaskWithSubtasks>,
    deviceEvents: List<DeviceCalendarEvent>,
    preferences: UserPreferences,
    onLoadDeviceCalendarMonth: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val extra = LocalRRExtraColors.current
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedAgendaDetail by remember { mutableStateOf<AgendaItem?>(null) }
    var calendarGranted by remember { mutableStateOf(PermissionHelper.hasCalendarReadPermission(context)) }

    val calPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        calendarGranted = granted
        if (granted) onLoadDeviceCalendarMonth(visibleMonth.year, visibleMonth.monthValue)
    }

    LaunchedEffect(visibleMonth, calendarGranted) {
        if (calendarGranted) {
            onLoadDeviceCalendarMonth(visibleMonth.year, visibleMonth.monthValue)
        }
    }

    val zone = ZoneId.systemDefault()

    // Index dates that have items
    val eventDates = remember(reminders, tasks, deviceEvents) {
        val dates = mutableSetOf<LocalDate>()
        reminders.forEach { r ->
            val ms = r.snoozedUntilMillis ?: r.triggerAtMillis
            dates.add(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate())
        }
        tasks.forEach { t ->
            t.task.dueDateMillis?.let { ms ->
                dates.add(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate())
            }
        }
        deviceEvents.forEach { ev ->
            dates.add(Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalDate())
        }
        dates
    }

    val dayAgenda = remember(selectedDate, reminders, tasks, deviceEvents, preferences.use24Hour) {
        buildList {
            reminders.filter { r ->
                val ms = r.snoozedUntilMillis ?: r.triggerAtMillis
                Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == selectedDate
            }.forEach { r ->
                val ms = r.snoozedUntilMillis ?: r.triggerAtMillis
                add(AgendaItem.ReminderAgenda(r, ScheduleCalculator.formatTime(ms, preferences.use24Hour)))
            }
            tasks.filter { t ->
                val ms = t.task.dueDateMillis ?: return@filter false
                Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == selectedDate
            }.forEach { t ->
                add(AgendaItem.TaskAgenda(t))
            }
            deviceEvents.filter { ev ->
                Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalDate() == selectedDate
            }.forEach { ev ->
                add(AgendaItem.DeviceEventAgenda(ev, ScheduleCalculator.formatTime(ev.startMillis, preferences.use24Hour)))
            }
        }.sortedBy { it.timeMillis }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalendarViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = viewMode == mode,
                            onClick = { viewMode = mode },
                            label = { Text(mode.label) },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        val today = LocalDate.now()
                        selectedDate = today
                        visibleMonth = YearMonth.from(today)
                    },
                ) {
                    Text("Today")
                }
            }
        }

        if (!calendarGranted) {
            item {
                RRCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Event, contentDescription = null, tint = extra.accentPrimary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sync Device Calendar (Optional)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Display your Android calendar events alongside RR reminders and tasks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { calPermLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                            Text("Connect")
                        }
                    }
                }
            }
        }

        if (viewMode == CalendarViewMode.MONTH) {
            item {
                RRCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
                        }
                        Text(
                            text = "${visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${visibleMonth.year}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val startDayOfWeek = if (preferences.firstDayOfWeek == 7) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
                    val headerDays = (0..6).map { startDayOfWeek.plus(it.toLong()) }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        headerDays.forEach { dow ->
                            Text(
                                text = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val firstOfMonth = visibleMonth.atDay(1)
                    val offset = ((firstOfMonth.dayOfWeek.value - startDayOfWeek.value) + 7) % 7
                    val daysInMonth = visibleMonth.lengthOfMonth()
                    val totalCells = ((offset + daysInMonth + 6) / 7) * 7

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (rowStart in 0 until totalCells step 7) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val cellIdx = rowStart + col
                                    val dayNum = cellIdx - offset + 1
                                    if (dayNum in 1..daysInMonth) {
                                        val date = visibleMonth.atDay(dayNum)
                                        val isSelected = date == selectedDate
                                        val isToday = date == LocalDate.now()
                                        val hasDots = date in eventDates
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                        else -> Color.Transparent
                                                    },
                                                )
                                                .clickable { selectedDate = date }
                                                .padding(vertical = 7.dp),
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            )
                                            if (hasDots) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                            else extra.accentPrimary,
                                                        ),
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (viewMode == CalendarViewMode.WEEK) {
            item {
                val startOfWeek = selectedDate.minusDays(((selectedDate.dayOfWeek.value - 1).toLong()))
                RRCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        for (i in 0 until 7) {
                            val d = startOfWeek.plusDays(i.toLong())
                            val sel = d == selectedDate
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { selectedDate = d }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "${d.dayOfMonth}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Agenda • ${selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${selectedDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${selectedDate.dayOfMonth}",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (dayAgenda.isEmpty()) {
            item {
                RREmptyState(
                    icon = Icons.Rounded.EventAvailable,
                    title = "Clear schedule",
                    subtitle = "No reminders, tasks, or calendar events scheduled for this day.",
                )
            }
        } else {
            items(dayAgenda, key = { it.idKey }) { item ->
                val pillColor = when (item.colorKey) {
                    "primary" -> extra.accentPrimary
                    "secondary" -> extra.accentSecondary
                    else -> extra.info
                }
                RRCard(onClick = { selectedAgendaDetail = item }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RRStatusPill(text = item.badge, color = pillColor)
                    }
                }
            }
        }
    }

    selectedAgendaDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = { selectedAgendaDetail = null },
            title = { Text(detail.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RRStatusPill(text = detail.badge, color = extra.accentPrimary)
                    Text(detail.subtitle, style = MaterialTheme.typography.bodyMedium)
                    if (detail.timeMillis > 0L) {
                        Text(
                            "Time: ${ScheduleCalculator.formatTime(detail.timeMillis, preferences.use24Hour)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedAgendaDetail = null }) { Text("Close") }
            },
        )
    }
}
