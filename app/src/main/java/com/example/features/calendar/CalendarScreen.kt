package com.example.features.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.calendar.DeviceCalendarEvent
import com.example.core.database.CalendarEventEntity
import com.example.core.database.ReminderEntity
import com.example.core.database.TaskWithSubtasks
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.RRCard
import com.example.ui.components.RRConfirmDialog
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
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

    class RREventAgenda(val event: CalendarEventEntity, timeText: String) : AgendaItem(
        idKey = "rrev_${event.id}",
        title = event.title,
        subtitle = listOfNotNull(
            if (event.isAllDay) "All day" else timeText,
            event.location.ifBlank { null },
            event.category,
        ).joinToString(" • "),
        timeMillis = event.startMillis,
        badge = "Event",
        colorKey = "success",
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
    rrEvents: List<CalendarEventEntity>,
    deviceEvents: List<DeviceCalendarEvent>,
    nowMillis: Long,
    preferences: UserPreferences,
    onLoadDeviceCalendarMonth: (Int, Int) -> Unit,
    onSaveEvent: (CalendarEventEntity) -> Unit,
    onDeleteEvent: (Long) -> Unit,
) {
    val context = LocalContext.current
    val extra = LocalRRExtraColors.current
    val zone = ZoneId.systemDefault()
    val today = remember(nowMillis) {
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    }

    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var selectedDate by remember { mutableStateOf(today) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedAgendaDetail by remember { mutableStateOf<AgendaItem?>(null) }
    var showEventEditor by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEventEntity?>(null) }
    var deleteEventCandidate by remember { mutableStateOf<CalendarEventEntity?>(null) }
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

    val eventDates = remember(reminders, tasks, rrEvents, deviceEvents) {
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
        rrEvents.forEach { ev ->
            val startD = Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalDate()
            val endD = Instant.ofEpochMilli(ev.endMillis.coerceAtLeast(ev.startMillis)).atZone(zone).toLocalDate()
            var cursor = startD
            var guard = 0
            while (!cursor.isAfter(endD) && guard < 60) {
                dates.add(cursor)
                cursor = cursor.plusDays(1)
                guard++
            }
        }
        deviceEvents.forEach { ev ->
            dates.add(Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalDate())
        }
        dates
    }

    val dayAgenda = remember(selectedDate, reminders, tasks, rrEvents, deviceEvents, preferences.use24Hour) {
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
            rrEvents.filter { ev ->
                val startD = Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalDate()
                val endD = Instant.ofEpochMilli(ev.endMillis.coerceAtLeast(ev.startMillis)).atZone(zone).toLocalDate()
                !selectedDate.isBefore(startD) && !selectedDate.isAfter(endD)
            }.forEach { ev ->
                val range = "${ScheduleCalculator.formatTime(ev.startMillis, preferences.use24Hour)} - ${ScheduleCalculator.formatTime(ev.endMillis, preferences.use24Hour)}"
                add(AgendaItem.RREventAgenda(ev, range))
            }
            deviceEvents.filter { ev ->
                Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalDate() == selectedDate
            }.forEach { ev ->
                add(AgendaItem.DeviceEventAgenda(ev, ScheduleCalculator.formatTime(ev.startMillis, preferences.use24Hour)))
            }
        }.sortedBy { it.timeMillis }
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
                                    "Display your Android calendar events alongside RR reminders, tasks, and events.",
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

            val startDayOfWeek = if (preferences.firstDayOfWeek == 7) DayOfWeek.SUNDAY else DayOfWeek.MONDAY

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
                                            val isToday = date == today
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
                    val backDays = ((selectedDate.dayOfWeek.value - startDayOfWeek.value) + 7) % 7
                    val startOfWeek = selectedDate.minusDays(backDays.toLong())
                    RRCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            for (i in 0 until 7) {
                                val d = startOfWeek.plusDays(i.toLong())
                                val sel = d == selectedDate
                                val isToday = d == today
                                val hasDots = d in eventDates
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                sel -> MaterialTheme.colorScheme.primary
                                                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                else -> Color.Transparent
                                            },
                                        )
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
                                    if (hasDots) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (sel) MaterialTheme.colorScheme.onPrimary else extra.accentPrimary),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Agenda • ${selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}, ${selectedDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${selectedDate.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = {
                            editingEvent = null
                            showEventEditor = true
                        },
                        modifier = Modifier.testTag("calendar_add_event_text_btn"),
                    ) {
                        Text("+ Add Event")
                    }
                }
            }

            if (dayAgenda.isEmpty()) {
                item {
                    RREmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = "Clear schedule",
                        subtitle = "No reminders, tasks, or calendar events scheduled for this day.",
                        actionLabel = "Add Event",
                        onActionClick = {
                            editingEvent = null
                            showEventEditor = true
                        },
                    )
                }
            } else {
                items(dayAgenda, key = { it.idKey }) { item ->
                    val pillColor = when (item.colorKey) {
                        "primary" -> extra.accentPrimary
                        "secondary" -> extra.accentSecondary
                        "success" -> extra.success
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

        FloatingActionButton(
            onClick = {
                editingEvent = null
                showEventEditor = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("calendar_add_event_fab"),
        ) {
            Icon(Icons.Rounded.Event, contentDescription = "Create Calendar Event")
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
                    if (detail is AgendaItem.RREventAgenda && detail.event.description.isNotBlank()) {
                        Text(detail.event.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (detail is AgendaItem.RREventAgenda) {
                        TextButton(
                            onClick = {
                                deleteEventCandidate = detail.event
                                selectedAgendaDetail = null
                            },
                        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        TextButton(
                            onClick = {
                                editingEvent = detail.event
                                selectedAgendaDetail = null
                                showEventEditor = true
                            },
                        ) { Text("Edit") }
                    }
                    TextButton(onClick = { selectedAgendaDetail = null }) { Text("Close") }
                }
            },
        )
    }

    if (showEventEditor) {
        CalendarEventEditorDialog(
            initial = editingEvent,
            defaultDate = selectedDate,
            preferences = preferences,
            onDismiss = { showEventEditor = false },
            onSave = { ev ->
                onSaveEvent(ev)
                showEventEditor = false
            },
        )
    }

    deleteEventCandidate?.let { candidate ->
        RRConfirmDialog(
            title = "Delete Event",
            message = "Delete \"${candidate.title}\"?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteEvent(candidate.id)
                deleteEventCandidate = null
            },
            onDismiss = { deleteEventCandidate = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEventEditorDialog(
    initial: CalendarEventEntity?,
    defaultDate: LocalDate,
    preferences: UserPreferences,
    onDismiss: () -> Unit,
    onSave: (CalendarEventEntity) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val startZdt = remember(initial, defaultDate) {
        if (initial != null) Instant.ofEpochMilli(initial.startMillis).atZone(zone)
        else defaultDate.atTime(9, 0).atZone(zone)
    }
    val endZdt = remember(initial, defaultDate) {
        if (initial != null) Instant.ofEpochMilli(initial.endMillis).atZone(zone)
        else defaultDate.atTime(10, 0).atZone(zone)
    }

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Personal") }
    var isAllDay by remember { mutableStateOf(initial?.isAllDay ?: false) }
    var eventDate by remember { mutableStateOf(startZdt.toLocalDate()) }
    var startHour by remember { mutableIntStateOf(startZdt.hour) }
    var startMinute by remember { mutableIntStateOf(startZdt.minute) }
    var endHour by remember { mutableIntStateOf(endZdt.hour) }
    var endMinute by remember { mutableIntStateOf(endZdt.minute) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pickingStartTime by remember { mutableStateOf(false) }
    var pickingEndTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Calendar Event" else "Edit Event") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(eventDate.toString())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("All-Day Event")
                    Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
                }
                if (!isAllDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { pickingStartTime = true }, modifier = Modifier.weight(1f)) {
                            Text("Start: ${ScheduleCalculator.formatHourMinute(startHour, startMinute, preferences.use24Hour)}")
                        }
                        OutlinedButton(onClick = { pickingEndTime = true }, modifier = Modifier.weight(1f)) {
                            Text("End: ${ScheduleCalculator.formatHourMinute(endHour, endMinute, preferences.use24Hour)}")
                        }
                    }
                }
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Personal", "Work", "Health", "Travel", "Social").forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startMs = if (isAllDay) {
                        eventDate.atStartOfDay(zone).toInstant().toEpochMilli()
                    } else {
                        ZonedDateTime.of(eventDate, LocalTime.of(startHour, startMinute), zone).toInstant().toEpochMilli()
                    }
                    val rawEndMs = if (isAllDay) {
                        eventDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
                    } else {
                        ZonedDateTime.of(eventDate, LocalTime.of(endHour, endMinute), zone).toInstant().toEpochMilli()
                    }
                    val endMs = rawEndMs.coerceAtLeast(startMs + 15 * 60_000L)
                    val entity = (initial ?: CalendarEventEntity(title = title.trim(), startMillis = startMs, endMillis = endMs)).copy(
                        title = title.trim(),
                        description = description.trim(),
                        location = location.trim(),
                        category = category,
                        isAllDay = isAllDay,
                        startMillis = startMs,
                        endMillis = endMs,
                    )
                    onSave(entity)
                },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = eventDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { ms ->
                            eventDate = Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }

    if (pickingStartTime || pickingEndTime) {
        val isStart = pickingStartTime
        val timeState = rememberTimePickerState(
            initialHour = if (isStart) startHour else endHour,
            initialMinute = if (isStart) startMinute else endMinute,
            is24Hour = preferences.use24Hour,
        )
        AlertDialog(
            onDismissRequest = {
                pickingStartTime = false
                pickingEndTime = false
            },
            title = { Text(if (isStart) "Start Time" else "End Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isStart) {
                            startHour = timeState.hour
                            startMinute = timeState.minute
                        } else {
                            endHour = timeState.hour
                            endMinute = timeState.minute
                        }
                        pickingStartTime = false
                        pickingEndTime = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pickingStartTime = false
                        pickingEndTime = false
                    },
                ) { Text("Cancel") }
            },
        )
    }
}
