package com.example.features.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.ai.AssistantUiState
import com.example.core.database.NotificationLogEntity
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ScheduleCalculator
import com.example.features.home.SmartSearchResults
import com.example.ui.components.RRBackgroundContainer
import com.example.ui.components.RRCard
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRSearchField
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSearchScreen(
    query: String,
    results: SmartSearchResults,
    assistantState: AssistantUiState,
    preferences: UserPreferences,
    onQueryChange: (String) -> Unit,
    onAskAssistant: (String) -> Unit,
    onClearAssistant: () -> Unit,
    onBack: () -> Unit,
    onNavigateReminders: () -> Unit,
    onNavigateAlarms: () -> Unit,
    onNavigateCalendar: () -> Unit,
    onNavigateTasks: () -> Unit,
    onNavigateNotes: () -> Unit,
    onNavigateHabits: () -> Unit,
    onNavigateFocus: () -> Unit,
    onNavigateWeather: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    var aiPrompt by remember { mutableStateOf("") }

    RRBackgroundContainer {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Smart Search & Assistant") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp),
            ) {
                item {
                    RRSearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = "Search reminders, events, tasks, notes, alarms...",
                        testTag = "smart_search_input",
                    )
                }

                if (query.isNotBlank()) {
                    item {
                        AssistChip(
                            onClick = onNavigateWeather,
                            label = { Text("Search city \"$query\" in Weather Center") },
                            leadingIcon = {
                                Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                        )
                    }
                }

                item {
                    RRCard(accentGlow = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = extra.accentPrimary)
                                Column {
                                    Text("Intelligent Productivity Assistant", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Optional planning assistant powered by your local schedule",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (assistantState !is AssistantUiState.Idle) {
                                IconButton(onClick = onClearAssistant, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear assistant response", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                "Plan my day around upcoming reminders",
                                "What is overdue or high priority?",
                                "Suggest focus blocks for my open tasks",
                            ).forEach { preset ->
                                AssistChip(
                                    onClick = {
                                        aiPrompt = preset
                                        onAskAssistant(preset)
                                    },
                                    label = { Text(preset, maxLines = 1) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = aiPrompt,
                                onValueChange = { aiPrompt = it },
                                placeholder = { Text("Ask for a daily plan or schedule advice...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_assistant_input"),
                            )
                            FilledIconButton(
                                onClick = { if (aiPrompt.isNotBlank()) onAskAssistant(aiPrompt.trim()) },
                                enabled = aiPrompt.isNotBlank() && assistantState !is AssistantUiState.Loading,
                                modifier = Modifier.testTag("ai_assistant_send_btn"),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask Assistant")
                            }
                        }

                        when (assistantState) {
                            is AssistantUiState.Idle -> Unit
                            is AssistantUiState.Loading -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            is AssistantUiState.Streaming -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(assistantState.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            is AssistantUiState.Success -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(assistantState.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            is AssistantUiState.Error -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = assistantState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { if (aiPrompt.isNotBlank()) onAskAssistant(aiPrompt) }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }

                if (query.isBlank()) {
                    item {
                        RREmptyState(
                            icon = Icons.Rounded.Search,
                            title = "Search across everything",
                            subtitle = "Type a keyword, tag, location, or category to search all RR Reminder modules.",
                        )
                    }
                } else if (results.isEmpty) {
                    item {
                        RREmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = "No matching records",
                            subtitle = "No reminders, events, tasks, notes, alarms, habits, or routines matched \"$query\".",
                        )
                    }
                } else {
                    if (results.reminders.isNotEmpty()) {
                        item { Text("Reminders (${results.reminders.size})", style = MaterialTheme.typography.titleSmall, color = extra.accentPrimary) }
                        items(results.reminders, key = { "r_${it.id}" }) { r ->
                            RRCard(onClick = onNavigateReminders) {
                                Text(r.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    ScheduleCalculator.formatTime(r.snoozedUntilMillis ?: r.triggerAtMillis, preferences.use24Hour),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extra.accentPrimary,
                                )
                            }
                        }
                    }
                    if (results.calendarEvents.isNotEmpty()) {
                        item { Text("Calendar Events (${results.calendarEvents.size})", style = MaterialTheme.typography.titleSmall, color = extra.success) }
                        items(results.calendarEvents, key = { "ev_${it.id}" }) { ev ->
                            RRCard(onClick = onNavigateCalendar) {
                                Text(ev.title, style = MaterialTheme.typography.titleSmall)
                                if (ev.location.isNotBlank()) Text(ev.location, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (results.tasks.isNotEmpty()) {
                        item { Text("Tasks (${results.tasks.size})", style = MaterialTheme.typography.titleSmall, color = extra.accentSecondary) }
                        items(results.tasks, key = { "t_${it.task.id}" }) { t ->
                            RRCard(onClick = onNavigateTasks) {
                                Text(t.task.title, style = MaterialTheme.typography.titleSmall)
                                Text(t.task.category, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (results.notes.isNotEmpty()) {
                        item { Text("Notes (${results.notes.size})", style = MaterialTheme.typography.titleSmall, color = extra.info) }
                        items(results.notes, key = { "n_${it.id}" }) { n ->
                            RRCard(onClick = onNavigateNotes) {
                                Text(n.title, style = MaterialTheme.typography.titleSmall)
                                if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                    if (results.alarms.isNotEmpty()) {
                        item { Text("Alarms (${results.alarms.size})", style = MaterialTheme.typography.titleSmall, color = extra.warning) }
                        items(results.alarms, key = { "a_${it.id}" }) { a ->
                            RRCard(onClick = onNavigateAlarms) {
                                Text(
                                    "${ScheduleCalculator.formatHourMinute(a.hour, a.minute, preferences.use24Hour)} • ${a.label}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                    if (results.habits.isNotEmpty()) {
                        item { Text("Habits (${results.habits.size})", style = MaterialTheme.typography.titleSmall, color = extra.success) }
                        items(results.habits, key = { "h_${it.habit.id}" }) { h ->
                            RRCard(onClick = onNavigateHabits) {
                                Text(h.habit.name, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    if (results.routines.isNotEmpty()) {
                        item { Text("Routines (${results.routines.size})", style = MaterialTheme.typography.titleSmall) }
                        items(results.routines, key = { "rt_${it.routine.id}" }) { rt ->
                            RRCard(onClick = onNavigateFocus) {
                                Text(rt.routine.name, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    logs: List<NotificationLogEntity>,
    preferences: UserPreferences,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    LaunchedEffect(Unit) { onMarkAllRead() }

    RRBackgroundContainer {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("RR Notification Center") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (logs.isNotEmpty()) {
                            TextButton(onClick = onClearAll, modifier = Modifier.testTag("notif_clear_btn")) {
                                Text("Clear")
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                ) {
                    RREmptyState(
                        icon = Icons.Rounded.NotificationsNone,
                        title = "Notification history is clear",
                        subtitle = "Reminders, alarms, timers, focus sessions, and location alerts appear here.",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    items(logs, key = { it.id }) { log ->
                        RRCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.title, style = MaterialTheme.typography.titleSmall)
                                    if (log.body.isNotBlank()) {
                                        Text(
                                            log.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${ScheduleCalculator.formatDate(log.postedAtMillis, preferences.dateFormatStyle.pattern)} • ${ScheduleCalculator.formatTime(log.postedAtMillis, preferences.use24Hour)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                RRStatusPill(text = log.sourceType, color = extra.accentPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
