package com.example.features.tasks

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.core.database.ReminderPriority
import com.example.core.database.SubtaskEntity
import com.example.core.database.TaskEntity
import com.example.core.database.TaskWithSubtasks
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ScheduleCalculator
import com.example.ui.components.RRCard
import com.example.ui.components.RRConfirmDialog
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRSearchField
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors

@Composable
fun TasksTabContent(
    tasks: List<TaskWithSubtasks>,
    preferences: UserPreferences,
    onSaveTask: (TaskEntity, List<String>) -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onToggleSubtask: (SubtaskEntity) -> Unit,
    onDeleteTask: (Long) -> Unit,
) {
    val extra = LocalRRExtraColors.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Open") }
    var showEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskWithSubtasks?>(null) }
    var deleteCandidate by remember { mutableStateOf<TaskEntity?>(null) }

    val filtered = remember(tasks, query, filter) {
        val q = query.trim().lowercase()
        tasks.filter { item ->
            val matchFilter = when (filter) {
                "Open" -> !item.task.isCompleted
                "Completed" -> item.task.isCompleted
                "High" -> !item.task.isCompleted &&
                    (item.task.priority == ReminderPriority.HIGH.name || item.task.priority == ReminderPriority.URGENT.name)
                else -> true
            }
            val matchQ = q.isBlank() ||
                item.task.title.lowercase().contains(q) ||
                item.task.description.lowercase().contains(q) ||
                item.task.category.lowercase().contains(q)
            matchFilter && matchQ
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            RRSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search tasks & categories...",
                testTag = "tasks_search_input",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Open", "High", "All", "Completed").forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RREmptyState(
                        icon = Icons.Rounded.TaskAlt,
                        title = "No tasks found",
                        subtitle = "Create tasks with subtasks, priorities, and due dates.",
                        actionLabel = "Add Task",
                        onActionClick = {
                            editingTask = null
                            showEditor = true
                        },
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(filtered, key = { it.task.id }) { item ->
                        val priority = ReminderPriority.fromName(item.task.priority)
                        RRCard(
                            onClick = {
                                editingTask = item
                                showEditor = true
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Checkbox(
                                    checked = item.task.isCompleted,
                                    onCheckedChange = { onToggleTask(item.task) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.task.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            textDecoration = if (item.task.isCompleted) TextDecoration.LineThrough else null,
                                        ),
                                    )
                                    if (item.task.description.isNotBlank()) {
                                        Text(
                                            text = item.task.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RRStatusPill(text = priority.label, color = extra.accentPrimary)
                                        RRStatusPill(text = item.task.category, color = extra.accentSecondary)
                                        item.task.dueDateMillis?.let { due ->
                                            RRStatusPill(
                                                text = ScheduleCalculator.formatDate(due, preferences.dateFormatStyle.pattern),
                                                color = extra.info,
                                            )
                                        }
                                    }

                                    if (item.subtasks.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        item.subtasks.sortedBy { it.sortOrder }.forEach { sub ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Checkbox(
                                                    checked = sub.isDone,
                                                    onCheckedChange = { onToggleSubtask(sub) },
                                                    modifier = Modifier.size(32.dp),
                                                )
                                                Text(
                                                    text = sub.title,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        textDecoration = if (sub.isDone) TextDecoration.LineThrough else null,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                IconButton(onClick = { deleteCandidate = item.task }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete task",
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
                editingTask = null
                showEditor = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("tasks_fab"),
        ) {
            Icon(Icons.Rounded.AddTask, contentDescription = "Add Task")
        }
    }

    if (showEditor) {
        TaskEditorDialog(
            initial = editingTask,
            onDismiss = { showEditor = false },
            onSave = { task, subs ->
                onSaveTask(task, subs)
                showEditor = false
            },
        )
    }

    deleteCandidate?.let { candidate ->
        RRConfirmDialog(
            title = "Delete Task",
            message = "Delete \"${candidate.title}\" and its subtasks?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteTask(candidate.id)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@Composable
private fun TaskEditorDialog(
    initial: TaskWithSubtasks?,
    onDismiss: () -> Unit,
    onSave: (TaskEntity, List<String>) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.task?.title ?: "") }
    var description by remember { mutableStateOf(initial?.task?.description ?: "") }
    var category by remember { mutableStateOf(initial?.task?.category ?: "Work") }
    var priority by remember { mutableStateOf(ReminderPriority.fromName(initial?.task?.priority)) }
    var dueOffsetDays by remember { mutableIntStateOf(1) }
    var hasDueDate by remember { mutableStateOf(initial?.task?.dueDateMillis != null) }
    var newSubtask by remember { mutableStateOf("") }
    val subtasks = remember {
        mutableStateListOf<String>().apply {
            initial?.subtasks?.sortedBy { it.sortOrder }?.forEach { add(it.title) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Task" else "Edit Task") },
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
                    label = { Text("Task title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes / Details") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    listOf("Work", "Personal", "Errands", "Study", "Home").forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Set Due Date")
                    Switch(checked = hasDueDate, onCheckedChange = { hasDueDate = it })
                }
                if (hasDueDate) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Today", 1 to "Tomorrow", 7 to "Next Week").forEach { (days, lbl) ->
                            FilterChip(
                                selected = dueOffsetDays == days,
                                onClick = { dueOffsetDays = days },
                                label = { Text(lbl) },
                            )
                        }
                    }
                }

                Text("Subtasks", style = MaterialTheme.typography.labelMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = newSubtask,
                        onValueChange = { newSubtask = it },
                        label = { Text("Add subtask") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = {
                            if (newSubtask.isNotBlank()) {
                                subtasks.add(newSubtask.trim())
                                newSubtask = ""
                            }
                        },
                    ) { Text("Add") }
                }
                subtasks.forEachIndexed { idx, s ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("• $s", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { subtasks.removeAt(idx) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove subtask", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dueMs = if (hasDueDate) {
                        initial?.task?.dueDateMillis ?: (System.currentTimeMillis() + dueOffsetDays * 86_400_000L)
                    } else null
                    val entity = (initial?.task ?: TaskEntity(title = title.trim())).copy(
                        title = title.trim(),
                        description = description.trim(),
                        priority = priority.name,
                        category = category,
                        dueDateMillis = dueMs,
                    )
                    onSave(entity, subtasks.toList())
                },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
