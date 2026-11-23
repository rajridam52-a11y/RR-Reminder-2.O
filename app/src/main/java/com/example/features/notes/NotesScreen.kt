package com.example.features.notes

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.database.NoteEntity
import com.example.core.database.ReminderEntity
import com.example.core.network.NetworkModule
import com.example.ui.components.RRCard
import com.example.ui.components.RRConfirmDialog
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRSearchField
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class ChecklistLine(
    val text: String,
    val checked: Boolean = false,
)

private val checklistAdapter by lazy {
    NetworkModule.moshi.adapter<List<ChecklistLine>>(
        Types.newParameterizedType(List::class.java, ChecklistLine::class.java),
    )
}

@Composable
fun NotesTabContent(
    notes: List<NoteEntity>,
    reminders: List<ReminderEntity>,
    onSaveNote: (NoteEntity) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit,
) {
    val extra = LocalRRExtraColors.current
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<NoteEntity?>(null) }

    val filtered = remember(notes, query, selectedCategory) {
        val q = query.trim().lowercase()
        notes.filter { n ->
            val matchCat = selectedCategory == "All" || n.category.equals(selectedCategory, ignoreCase = true)
            val matchQ = q.isBlank() ||
                n.title.lowercase().contains(q) ||
                n.body.lowercase().contains(q) ||
                n.checklistJson.lowercase().contains(q)
            matchCat && matchQ
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
                placeholder = "Search notes & checklists...",
                testTag = "notes_search_input",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "Personal", "Work", "Ideas", "Shopping").forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RREmptyState(
                        icon = Icons.Rounded.EditNote,
                        title = "No notes yet",
                        subtitle = "Capture quick notes, rich checklists, pin priorities, and link notes to reminders.",
                        actionLabel = "Create Note",
                        onActionClick = {
                            editingNote = null
                            showEditor = true
                        },
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(filtered, key = { it.id }) { note ->
                        val itemsList = remember(note.checklistJson) {
                            runCatching { checklistAdapter.fromJson(note.checklistJson) ?: emptyList() }
                                .getOrDefault(emptyList())
                        }
                        val linkedTitle = remember(note.linkedReminderId, reminders) {
                            reminders.firstOrNull { it.id == note.linkedReminderId }?.title
                        }

                        RRCard(
                            onClick = {
                                editingNote = note
                                showEditor = true
                            },
                            accentGlow = note.isPinned,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.title.ifBlank { "Untitled Note" },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!note.isChecklist && note.body.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = note.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    } else if (note.isChecklist && itemsList.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        itemsList.take(4).forEachIndexed { idx, line ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Checkbox(
                                                    checked = line.checked,
                                                    onCheckedChange = { checked ->
                                                        val updated = itemsList.toMutableList()
                                                        updated[idx] = line.copy(checked = checked)
                                                        onSaveNote(note.copy(checklistJson = checklistAdapter.toJson(updated)))
                                                    },
                                                    modifier = Modifier.size(30.dp),
                                                )
                                                Text(
                                                    text = line.text,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        textDecoration = if (line.checked) TextDecoration.LineThrough else null,
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RRStatusPill(text = note.category, color = extra.accentSecondary)
                                        if (note.isPinned) {
                                            RRStatusPill(text = "Pinned", color = extra.accentPrimary, icon = Icons.Rounded.PushPin)
                                        }
                                        if (linkedTitle != null) {
                                            RRStatusPill(text = "Linked: $linkedTitle", color = extra.info, icon = Icons.Rounded.Link)
                                        }
                                    }
                                }
                                Column {
                                    IconButton(onClick = { onTogglePin(note) }) {
                                        Icon(
                                            Icons.Rounded.PushPin,
                                            contentDescription = if (note.isPinned) "Unpin" else "Pin",
                                            tint = if (note.isPinned) extra.accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { deleteCandidate = note }) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete note",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingNote = null
                showEditor = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("notes_fab"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add Note")
        }
    }

    if (showEditor) {
        NoteEditorDialog(
            initial = editingNote,
            reminders = reminders,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                onSaveNote(saved)
                showEditor = false
            },
        )
    }

    deleteCandidate?.let { candidate ->
        RRConfirmDialog(
            title = "Delete Note",
            message = "Delete \"${candidate.title.ifBlank { "this note" }}\"?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteNote(candidate.id)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@Composable
private fun NoteEditorDialog(
    initial: NoteEntity?,
    reminders: List<ReminderEntity>,
    onDismiss: () -> Unit,
    onSave: (NoteEntity) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var body by remember { mutableStateOf(initial?.body ?: "") }
    var isChecklist by remember { mutableStateOf(initial?.isChecklist ?: false) }
    var category by remember { mutableStateOf(initial?.category ?: "Personal") }
    var isPinned by remember { mutableStateOf(initial?.isPinned ?: false) }
    var linkedReminderId by remember { mutableStateOf(initial?.linkedReminderId) }
    var newLineText by remember { mutableStateOf("") }
    val checklistItems = remember {
        mutableStateListOf<ChecklistLine>().apply {
            val parsed = initial?.checklistJson?.let {
                runCatching { checklistAdapter.fromJson(it) }.getOrNull()
            }.orEmpty()
            addAll(parsed)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Note" else "Edit Note") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isChecklist,
                        onClick = { isChecklist = false },
                        label = { Text("Text Note") },
                    )
                    FilterChip(
                        selected = isChecklist,
                        onClick = { isChecklist = true },
                        label = { Text("Checklist") },
                    )
                    FilterChip(
                        selected = isPinned,
                        onClick = { isPinned = !isPinned },
                        label = { Text("Pin") },
                    )
                }

                if (!isChecklist) {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Content") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = newLineText,
                            onValueChange = { newLineText = it },
                            label = { Text("Add checklist item") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(
                            onClick = {
                                if (newLineText.isNotBlank()) {
                                    checklistItems.add(ChecklistLine(newLineText.trim(), false))
                                    newLineText = ""
                                }
                            },
                        ) { Text("Add") }
                    }
                    checklistItems.forEachIndexed { idx, line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = line.checked,
                                onCheckedChange = { checklistItems[idx] = line.copy(checked = it) },
                            )
                            Text(line.text, modifier = Modifier.weight(1f))
                            IconButton(onClick = { checklistItems.removeAt(idx) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }

                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Personal", "Work", "Ideas", "Shopping").forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) },
                        )
                    }
                }

                if (reminders.isNotEmpty()) {
                    Text("Link to Reminder (Optional)", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = linkedReminderId == null,
                            onClick = { linkedReminderId = null },
                            label = { Text("None") },
                        )
                        reminders.filter { !it.isCompleted }.take(6).forEach { rem ->
                            FilterChip(
                                selected = linkedReminderId == rem.id,
                                onClick = { linkedReminderId = rem.id },
                                label = { Text(rem.title, maxLines = 1) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val entity = (initial ?: NoteEntity(title = title.trim())).copy(
                        title = title.trim().ifBlank { "Quick Note" },
                        body = body,
                        isChecklist = isChecklist,
                        checklistJson = checklistAdapter.toJson(checklistItems.toList()),
                        category = category,
                        isPinned = isPinned,
                        linkedReminderId = linkedReminderId,
                    )
                    onSave(entity)
                },
                enabled = title.isNotBlank() || body.isNotBlank() || checklistItems.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
