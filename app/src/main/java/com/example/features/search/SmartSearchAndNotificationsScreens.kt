package com.example.features.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.database.NotificationLogEntity
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ScheduleCalculator
import com.example.features.home.SmartSearchResults
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
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateReminders: () -> Unit,
    onNavigateAlarms: () -> Unit,
    onNavigateTasks: () -> Unit,
    onNavigateNotes: () -> Unit,
    onNavigateHabits: () -> Unit,
    onNavigateFocus: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RR Smart Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            RRSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = "Search reminders, tasks, notes, alarms, habits...",
                testTag = "smart_search_input",
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (query.isBlank()) {
                RREmptyState(
                    icon = Icons.Rounded.Search,
                    title = "Search across everything",
                    subtitle = "Type a keyword, tag, or category to search all RR Reminder modules offline.",
                )
            } else if (results.isEmpty) {
                RREmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No matching records",
                    subtitle = "No reminders, tasks, notes, alarms, habits, or routines matched \"$query\".",
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    if (results.reminders.isNotEmpty()) {
                        item { Text("Reminders (${results.reminders.size})", style = MaterialTheme.typography.titleSmall, color = extra.accentPrimary) }
                        items(results.reminders, key = { "r_${it.id}" }) { r ->
                            RRCard(onClick = onNavigateReminders) {
                                Text(r.title, style = MaterialTheme.typography.titleSmall)
                                if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodySmall)
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
                                Text(a.label, style = MaterialTheme.typography.titleSmall)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RR Notification Center") },
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
