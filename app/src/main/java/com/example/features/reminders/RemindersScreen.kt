package com.example.features.reminders

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.common.VoiceReminderParser
import com.example.core.database.ReminderEntity
import com.example.core.database.ReminderPriority
import com.example.core.database.RepeatType
import com.example.core.datastore.UserPreferences
import com.example.core.location.SimpleLocation
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.RRCard
import com.example.ui.components.RRConfirmDialog
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRSearchField
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    reminders: List<ReminderEntity>,
    nowMillis: Long,
    searchQuery: String,
    selectedFilter: ReminderFilter,
    selectedCategory: String,
    preferences: UserPreferences,
    capturedLocation: SimpleLocation?,
    locationMessage: String?,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilter: (ReminderFilter) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSaveReminder: (ReminderEntity) -> Unit,
    onToggleComplete: (ReminderEntity) -> Unit,
    onSnooze: (ReminderEntity, Int) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onCaptureLocation: () -> Unit,
    onClearCapturedLocation: () -> Unit,
    onNavigateNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val extra = LocalRRExtraColors.current
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<ReminderEntity?>(null) }
    var showSearchBar by remember { mutableStateOf(searchQuery.isNotBlank()) }

    var notifAllowed by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var exactAlarmAllowed by remember { mutableStateOf(PermissionHelper.canScheduleExactAlarms(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifAllowed = PermissionHelper.hasNotificationPermission(context)
                exactAlarmAllowed = PermissionHelper.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifAllowed = granted || PermissionHelper.hasNotificationPermission(context)
    }

    val primaryZone = remember(preferences.primaryClockZone) { preferences.primaryClockZone.toZoneId() }
    val todayDate = remember(nowMillis, primaryZone) { Instant.ofEpochMilli(nowMillis).atZone(primaryZone).toLocalDate() }
    val todayCount = remember(reminders, todayDate, primaryZone) {
        reminders.count { rem ->
            !rem.isCompleted &&
                Instant.ofEpochMilli(rem.snoozedUntilMillis ?: rem.triggerAtMillis).atZone(primaryZone).toLocalDate() == todayDate
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Reminders",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = onNavigateNotifications,
                        modifier = Modifier.testTag("reminders_notif_history_btn"),
                    ) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = "Notification History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = extra.glassFill,
                        border = BorderStroke(1.dp, extra.cardBorder),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { showSearchBar = !showSearchBar }
                            .testTag("reminders_search_toggle"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Search Reminders",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF38BDF8))))
                            .clickable {
                                onClearCapturedLocation()
                                editingReminder = null
                                showEditor = true
                            }
                            .testTag("reminders_add_top_btn"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "Create Reminder",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            AnimatedVisibility(visible = showSearchBar || searchQuery.isNotBlank()) {
                Column {
                    RRSearchField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = "Search reminders, tags, notes...",
                        testTag = "reminders_search_input",
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Primary Filter Pills (All | Today | This Week | This Month + additional filters)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReminderFilter.entries.forEach { filter ->
                    val selected = filter == selectedFilter
                    Surface(
                        shape = CircleShape,
                        color = if (selected) Color(0xFF1E40AF) else extra.glassFill,
                        border = BorderStroke(
                            1.dp,
                            if (selected) Color(0xFF2563EB) else extra.cardBorder,
                        ),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onSelectFilter(filter) }
                            .testTag("reminder_filter_${filter.name.lowercase()}"),
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            val categories = listOf("All", "General", "Work", "Personal", "Health", "Finance")
            if (selectedCategory != "All" || showSearchBar) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { cat ->
                        val catSelected = selectedCategory == cat
                        Surface(
                            shape = CircleShape,
                            color = if (catSelected) MaterialTheme.colorScheme.primaryContainer else extra.glassFill,
                            border = BorderStroke(1.dp, extra.cardBorder),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onSelectCategory(cat) },
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (catSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            if (!notifAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x33D97706),
                    border = BorderStroke(1.dp, Color(0x77FBBF24)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                        Text(
                            text = "Notifications disabled — alerts will not pop up.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Allow")
                        }
                    }
                }
            } else if (!exactAlarmAllowed) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x33E11D48),
                    border = BorderStroke(1.dp, Color(0x77FB7185)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.AlarmOff, contentDescription = null, tint = Color(0xFFFB7185), modifier = Modifier.size(18.dp))
                        Text(
                            text = "Exact alarm permission off — reminder alerts may be delayed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { PermissionHelper.openExactAlarmSettings(context) }) {
                            Text("Enable")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (reminders.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RREmptyState(
                        icon = Icons.Rounded.NotificationsNone,
                        title = "No reminders match",
                        subtitle = "Create a timed, recurring, voice, or location reminder.",
                        actionLabel = "Create Reminder",
                        onActionClick = {
                            editingReminder = null
                            showEditor = true
                        },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(reminders, key = { it.id }) { item ->
                        ReminderRowCard(
                            reminder = item,
                            nowMillis = nowMillis,
                            preferences = preferences,
                            onToggleComplete = { onToggleComplete(item) },
                            onEdit = {
                                editingReminder = item
                                showEditor = true
                            },
                            onSnooze = { mins -> onSnooze(item, mins) },
                            onDelete = { deleteCandidate = item },
                        )
                    }
                }
            }

            // Bottom summary bar matching Screen 4 ("✓ X Reminders Today >")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = extra.glassFill,
                border = BorderStroke(1.dp, extra.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onSelectFilter(
                            if (selectedFilter == ReminderFilter.COMPLETED) ReminderFilter.ALL
                            else ReminderFilter.COMPLETED,
                        )
                    }
                    .testTag("reminders_summary_bar"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = if (selectedFilter == ReminderFilter.COMPLETED) Icons.Rounded.History else Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (selectedFilter == ReminderFilter.COMPLETED) {
                                "Viewing Completed History (${reminders.size}) • Tap for Active"
                            } else {
                                "${todayCount.coerceAtLeast(reminders.count { !it.isCompleted })} Reminders Today"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Toggle history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showEditor) {
        ReminderEditorDialog(
            initial = editingReminder,
            nowMillis = nowMillis,
            preferences = preferences,
            capturedLocation = capturedLocation,
            locationMessage = locationMessage,
            onCaptureLocation = onCaptureLocation,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                if (!notifAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onSaveReminder(saved)
                showEditor = false
            },
        )
    }

    deleteCandidate?.let { candidate ->
        RRConfirmDialog(
            title = "Delete Reminder",
            message = "Delete \"${candidate.title}\"? Scheduled notifications for this item will be cancelled.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteReminder(candidate.id)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@Composable
private fun ReminderRowCard(
    reminder: ReminderEntity,
    nowMillis: Long,
    preferences: UserPreferences,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val priority = ReminderPriority.fromName(reminder.priority)
    val repeat = RepeatType.fromName(reminder.repeatType)
    val targetMs = reminder.snoozedUntilMillis ?: reminder.triggerAtMillis
    val isOverdue = !reminder.isCompleted && targetMs < nowMillis

    val badgeGradient = when {
        reminder.category.equals("Health", ignoreCase = true) -> listOf(Color(0xFF10B981), Color(0xFF059669))
        reminder.category.equals("Personal", ignoreCase = true) -> listOf(Color(0xFFA855F7), Color(0xFF7C3AED))
        reminder.category.equals("Finance", ignoreCase = true) -> listOf(Color(0xFFF59E0B), Color(0xFFD97706))
        reminder.category.equals("Work", ignoreCase = true) -> listOf(Color(0xFFEF4444), Color(0xFFF97316))
        else -> listOf(Color(0xFF2563EB), Color(0xFF38BDF8))
    }
    val badgeIcon = when {
        reminder.category.equals("Health", ignoreCase = true) -> Icons.Rounded.EventAvailable
        reminder.category.equals("Personal", ignoreCase = true) -> Icons.Rounded.CardGiftcard
        reminder.category.equals("Finance", ignoreCase = true) -> Icons.Rounded.ShoppingCart
        reminder.category.equals("Work", ignoreCase = true) -> Icons.Rounded.Groups
        else -> Icons.Rounded.MenuBook
    }

    var showSnoozeMenu by remember { mutableStateOf(false) }

    RRCard(
        onClick = onEdit,
        accentGlow = isOverdue,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(badgeGradient)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = reminder.category,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val primaryZone = remember(preferences.primaryClockZone) { preferences.primaryClockZone.toZoneId() }
                Text(
                    text = "${ScheduleCalculator.formatDate(targetMs, preferences.dateFormatStyle.pattern, primaryZone)} • ${ScheduleCalculator.formatTime(targetMs, preferences.use24Hour, primaryZone)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isOverdue || repeat != RepeatType.NONE || reminder.locationTriggerType != "NONE" || priority == ReminderPriority.URGENT) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (isOverdue) {
                            RRStatusPill(
                                text = "Overdue",
                                color = MaterialTheme.colorScheme.error,
                                icon = Icons.Rounded.WarningAmber,
                            )
                        }
                        if (priority == ReminderPriority.URGENT || priority == ReminderPriority.HIGH) {
                            RRStatusPill(text = priority.label, color = extra.warning)
                        }
                        if (repeat != RepeatType.NONE) {
                            RRStatusPill(
                                text = if (repeat == RepeatType.WEEKDAYS) {
                                    ScheduleCalculator.formatWeekdaysMask(reminder.repeatWeekdaysMask)
                                } else {
                                    repeat.label
                                },
                                color = extra.info,
                                icon = Icons.Rounded.Repeat,
                            )
                        }
                        if (reminder.locationTriggerType != "NONE") {
                            RRStatusPill(
                                text = reminder.locationName ?: reminder.locationTriggerType,
                                color = extra.success,
                                icon = Icons.Rounded.LocationOn,
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!reminder.isCompleted) {
                    Box {
                        IconButton(
                            onClick = { showSnoozeMenu = true },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "More actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showSnoozeMenu,
                            onDismissRequest = { showSnoozeMenu = false },
                        ) {
                            listOf(10, 30, 60).forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text("Snooze ${mins}m") },
                                    leadingIcon = { Icon(Icons.Rounded.Snooze, contentDescription = null) },
                                    onClick = {
                                        showSnoozeMenu = false
                                        onSnooze(mins)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showSnoozeMenu = false
                                    onDelete()
                                },
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete reminder",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Checkbox(
                    checked = reminder.isCompleted,
                    onCheckedChange = { onToggleComplete() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorDialog(
    initial: ReminderEntity?,
    nowMillis: Long,
    preferences: UserPreferences,
    capturedLocation: SimpleLocation?,
    locationMessage: String?,
    onCaptureLocation: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (ReminderEntity) -> Unit,
) {
    val context = LocalContext.current
    val primaryZone = remember(preferences.primaryClockZone) { preferences.primaryClockZone.toZoneId() }
    val initialZdt = remember(initial, primaryZone) {
        Instant.ofEpochMilli(initial?.triggerAtMillis ?: (nowMillis + 3_600_000L))
            .atZone(primaryZone)
    }

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "General") }
    var tagsCsv by remember { mutableStateOf(initial?.tagsCsv ?: "") }
    var priority by remember { mutableStateOf(ReminderPriority.fromName(initial?.priority)) }
    var repeatType by remember { mutableStateOf(RepeatType.fromName(initial?.repeatType)) }
    var repeatInterval by remember { mutableIntStateOf(initial?.repeatIntervalValue ?: 1) }
    var repeatMask by remember { mutableIntStateOf(initial?.repeatWeekdaysMask ?: 0) }
    var selectedDate by remember { mutableStateOf(initialZdt.toLocalDate()) }
    var selectedHour by remember { mutableIntStateOf(initialZdt.hour) }
    var selectedMinute by remember { mutableIntStateOf(initialZdt.minute) }
    var attachmentUri by remember { mutableStateOf(initial?.attachmentUri) }
    var locationTriggerType by remember { mutableStateOf(initial?.locationTriggerType ?: "NONE") }
    var locationName by remember { mutableStateOf(initial?.locationName ?: "") }
    var locationLat by remember { mutableStateOf(initial?.locationLat) }
    var locationLon by remember { mutableStateOf(initial?.locationLon) }
    var voiceConfirmationNote by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(capturedLocation) {
        if (capturedLocation != null) {
            locationName = capturedLocation.displayName
            locationLat = capturedLocation.latitude
            locationLon = capturedLocation.longitude
            if (locationTriggerType == "NONE") locationTriggerType = "ARRIVE"
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val parsed = VoiceReminderParser.parse(spoken)
                title = parsed.title
                val zdt = Instant.ofEpochMilli(parsed.suggestedTriggerMillis).atZone(primaryZone)
                selectedDate = zdt.toLocalDate()
                selectedHour = zdt.hour
                selectedMinute = zdt.minute
                voiceConfirmationNote = parsed.detectedTimeDescription
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) attachmentUri = uri.toString()
    }

    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it } || PermissionHelper.hasAnyLocationPermission(context)) {
            onCaptureLocation()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (initial == null) "New Reminder" else "Edit Reminder")
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictate reminder...")
                        }
                        runCatching { voiceLauncher.launch(intent) }.onFailure {
                            voiceConfirmationNote = "Speech input unavailable on this device — type manually."
                        }
                    },
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice dictation")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (voiceConfirmationNote != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = voiceConfirmationNote.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reminder_title_input"),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(selectedDate.toString())
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(ScheduleCalculator.formatHourMinute(selectedHour, selectedMinute, preferences.use24Hour))
                    }
                }

                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ReminderPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) },
                        )
                    }
                }

                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("General", "Work", "Personal", "Health", "Finance").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) },
                        )
                    }
                }

                Text("Repeat Schedule", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RepeatType.entries.forEach { r ->
                        FilterChip(
                            selected = repeatType == r,
                            onClick = { repeatType = r },
                            label = { Text(r.label) },
                        )
                    }
                }

                if (repeatType == RepeatType.WEEKDAYS) {
                    val days = listOf("M", "T", "W", "T", "F", "S", "S")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        days.forEachIndexed { idx, label ->
                            val active = (repeatMask and (1 shl idx)) != 0
                            FilterChip(
                                selected = active,
                                onClick = { repeatMask = repeatMask xor (1 shl idx) },
                                label = { Text(label) },
                            )
                        }
                    }
                } else if (repeatType == RepeatType.CUSTOM_HOURS || repeatType == RepeatType.CUSTOM_DAYS) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Interval:", style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { if (repeatInterval > 1) repeatInterval-- }) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease")
                        }
                        Text("$repeatInterval", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { repeatInterval++ }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase")
                        }
                    }
                }

                OutlinedTextField(
                    value = tagsCsv,
                    onValueChange = { tagsCsv = it },
                    label = { Text("Tags (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (attachmentUri == null) "Attach Image" else "Change Image")
                    }
                    if (attachmentUri != null) {
                        TextButton(onClick = { attachmentUri = null }) { Text("Remove") }
                    }
                }

                Text("Location Trigger (Battery-Conscious)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NONE" to "Off", "ARRIVE" to "On Arrive", "DEPART" to "On Depart").forEach { (key, lbl) ->
                        FilterChip(
                            selected = locationTriggerType == key,
                            onClick = { locationTriggerType = key },
                            label = { Text(lbl) },
                        )
                    }
                }
                if (locationTriggerType != "NONE") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = locationName,
                            onValueChange = { locationName = it },
                            label = { Text("Place label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(
                            onClick = {
                                if (PermissionHelper.hasAnyLocationPermission(context)) {
                                    onCaptureLocation()
                                } else {
                                    locPermLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
                                }
                            },
                        ) {
                            Text("Pin GPS")
                        }
                    }
                    if (!locationMessage.isNullOrBlank()) {
                        Text(
                            text = locationMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val zone = primaryZone
                    val triggerZdt = ZonedDateTime.of(selectedDate, LocalTime.of(selectedHour, selectedMinute), zone)
                    val entity = (initial ?: ReminderEntity(title = title.trim(), triggerAtMillis = 0L)).copy(
                        title = title.trim(),
                        description = description.trim(),
                        triggerAtMillis = triggerZdt.toInstant().toEpochMilli(),
                        zoneId = zone.id,
                        repeatType = repeatType.name,
                        repeatIntervalValue = repeatInterval,
                        repeatWeekdaysMask = repeatMask,
                        priority = priority.name,
                        category = category,
                        tagsCsv = tagsCsv.trim(),
                        notes = notes.trim(),
                        attachmentUri = attachmentUri,
                        locationName = locationName.ifBlank { null },
                        locationLat = locationLat,
                        locationLon = locationLon,
                        locationTriggerType = locationTriggerType,
                    )
                    onSave(entity)
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("reminder_save_btn"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { ms ->
                            selectedDate = Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = preferences.use24Hour,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedHour = timeState.hour
                        selectedMinute = timeState.minute
                        showTimePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}
