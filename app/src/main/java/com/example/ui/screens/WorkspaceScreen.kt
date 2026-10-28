package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.*
import com.example.core.network.GeminiClient
import com.example.core.security.CredentialVault
import com.example.ui.components.AlertBanner
import com.example.ui.components.Eyebrow
import com.example.ui.components.PanelCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class WorkspaceTab(val title: String) {
    NOTES("Notes"),
    TASKS("Tasks"),
    REMINDERS("Reminders"),
    SUMMARIZE("Summarize")
}

@Composable
fun WorkspaceScreen(
    initialTab: String? = null,
    vault: CredentialVault,
    db: JarvisDatabase,
    geminiClient: GeminiClient
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember {
        mutableStateOf(
            when (initialTab?.lowercase(Locale.ROOT)) {
                "tasks" -> WorkspaceTab.TASKS
                "reminders" -> WorkspaceTab.REMINDERS
                "summarize" -> WorkspaceTab.SUMMARIZE
                else -> WorkspaceTab.NOTES
            }
        )
    }

    // Notes state
    val notes by db.noteDao().getAll().collectAsState(initial = emptyList())
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }

    // Tasks state
    val tasks by db.taskDao().getAll().collectAsState(initial = emptyList())
    var taskTitle by remember { mutableStateOf("") }
    var taskPriority by remember { mutableStateOf("normal") }

    // Reminders state
    val reminders by db.reminderDao().getAll().collectAsState(initial = emptyList())
    var reminderTitle by remember { mutableStateOf("") }
    var reminderHoursFromNow by remember { mutableStateOf("2") }

    // Summarizer state
    var docText by remember { mutableStateOf("") }
    var summaryResult by remember { mutableStateOf<String?>(null) }
    var isSummarizing by remember { mutableStateOf(false) }

    var noticeMessage by remember { mutableStateOf<String?>(null) }
    val timeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Scaffold(
        containerColor = BgDark,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Eyebrow(text = "SMART TOOLS")
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "WORKSPACE",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }

                // Tab Switcher Pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x66080411))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WorkspaceTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSelected) PanelSoftBg else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) TextPrimary else TextFaint,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }

            if (noticeMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AlertBanner(kind = "info", message = noticeMessage!!)
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (activeTab) {
                WorkspaceTab.NOTES -> {
                    // New Note form
                    PanelCard(modifier = Modifier.fillMaxWidth()) {
                        Eyebrow(text = "NEW NOTE")
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            placeholder = { Text("Note title...", style = MaterialTheme.typography.bodySmall, color = TextFaint) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonMagenta,
                                unfocusedBorderColor = PanelBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = Color(0xCC080411),
                                unfocusedContainerColor = Color(0xCC080411)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = noteContent,
                            onValueChange = { noteContent = it },
                            placeholder = { Text("Write anything — markdown supported", style = MaterialTheme.typography.bodySmall, color = TextFaint) },
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_content_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonMagenta,
                                unfocusedBorderColor = PanelBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = Color(0xCC080411),
                                unfocusedContainerColor = Color(0xCC080411)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (noteTitle.trim().isNotEmpty()) {
                                    coroutineScope.launch {
                                        db.noteDao().insert(NoteEntity(title = noteTitle.trim(), content = noteContent.trim()))
                                        noteTitle = ""
                                        noteContent = ""
                                        noticeMessage = "Note saved."
                                    }
                                }
                            },
                            enabled = noteTitle.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.align(Alignment.End).testTag("save_note_button")
                        ) {
                            Text("SAVE NOTE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("SAVED NOTES (${notes.size})", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (notes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No saved notes yet.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(notes, key = { it.id }) { note ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(PanelBg)
                                        .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(note.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        if (note.content.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(note.content, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(timeFormat.format(Date(note.updatedAt)).uppercase(), style = MaterialTheme.typography.bodySmall, color = TextFaint, fontSize = 8.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch { db.noteDao().delete(note) }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRose, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                WorkspaceTab.TASKS -> {
                    // New Task Form
                    PanelCard(modifier = Modifier.fillMaxWidth()) {
                        Eyebrow(text = "NEW TASK")
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = taskTitle,
                            onValueChange = { taskTitle = it },
                            placeholder = { Text("Task description...", style = MaterialTheme.typography.bodySmall, color = TextFaint) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("task_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonMagenta,
                                unfocusedBorderColor = PanelBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = Color(0xCC080411),
                                unfocusedContainerColor = Color(0xCC080411)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("low", "normal", "high").forEach { p ->
                                    val isSelected = taskPriority == p
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isSelected) Color(0x40D946EF) else PanelSoftBg)
                                            .border(1.dp, if (isSelected) NeonMagenta else PanelBorder, RoundedCornerShape(999.dp))
                                            .clickable { taskPriority = p }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(p.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (isSelected) NeonMagenta else TextFaint, fontSize = 8.sp)
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    if (taskTitle.trim().isNotEmpty()) {
                                        coroutineScope.launch {
                                            db.taskDao().insert(TaskEntity(title = taskTitle.trim(), priority = taskPriority))
                                            taskTitle = ""
                                            noticeMessage = "Task created."
                                        }
                                    }
                                },
                                enabled = taskTitle.trim().isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("add_task_button")
                            ) {
                                Text("ADD TASK", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("TASK QUEUE (${tasks.count { !it.completed }} open)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (tasks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No tasks found.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(tasks, key = { it.id }) { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (task.completed) Color(0x1AFFFFFF) else PanelBg)
                                        .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                db.taskDao().update(task.copy(completed = !task.completed, updatedAt = System.currentTimeMillis()))
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Toggle task",
                                            tint = if (task.completed) StatusGreen else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (task.completed) TextFaint else TextPrimary,
                                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                        Text(
                                            text = "${task.priority.uppercase()} PRIORITY",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (task.priority) {
                                                "high" -> StatusRose
                                                "low" -> NeonCyan
                                                else -> NeonMagenta
                                            },
                                            fontSize = 8.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { coroutineScope.launch { db.taskDao().delete(task) } },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRose, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                WorkspaceTab.REMINDERS -> {
                    // New Reminder Form
                    PanelCard(modifier = Modifier.fillMaxWidth()) {
                        Eyebrow(text = "SCHEDULE REMINDER")
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = reminderTitle,
                            onValueChange = { reminderTitle = it },
                            placeholder = { Text("Remind me to...", style = MaterialTheme.typography.bodySmall, color = TextFaint) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reminder_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonMagenta,
                                unfocusedBorderColor = PanelBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = Color(0xCC080411),
                                unfocusedContainerColor = Color(0xCC080411)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("In hours:", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                Spacer(modifier = Modifier.width(6.dp))
                                listOf("1", "2", "4", "24").forEach { h ->
                                    val isSelected = reminderHoursFromNow == h
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isSelected) Color(0x40D946EF) else PanelSoftBg)
                                            .clickable { reminderHoursFromNow = h }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("${h}h", style = MaterialTheme.typography.labelSmall, color = if (isSelected) NeonMagenta else TextFaint, fontSize = 8.sp)
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    if (reminderTitle.trim().isNotEmpty()) {
                                        coroutineScope.launch {
                                            val hours = reminderHoursFromNow.toLongOrNull() ?: 2L
                                            val targetTime = System.currentTimeMillis() + (hours * 3600_000L)
                                            db.reminderDao().insert(ReminderEntity(title = reminderTitle.trim(), remindAt = targetTime))
                                            reminderTitle = ""
                                            noticeMessage = "Reminder scheduled."
                                        }
                                    }
                                },
                                enabled = reminderTitle.trim().isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("schedule_reminder_button")
                            ) {
                                Text("SCHEDULE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("SCHEDULED REMINDERS (${reminders.size})", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (reminders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No scheduled reminders.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(reminders, key = { it.id }) { rem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(PanelBg)
                                        .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = NeonViolet, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rem.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text(timeFormat.format(Date(rem.remindAt)), style = MaterialTheme.typography.bodySmall, color = TextFaint, fontSize = 9.sp)
                                    }
                                    IconButton(onClick = { coroutineScope.launch { db.reminderDao().delete(rem) } }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRose, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                WorkspaceTab.SUMMARIZE -> {
                    // Document Summarization via real Gemini call
                    PanelCard(modifier = Modifier.fillMaxWidth()) {
                        Eyebrow(text = "DOCUMENT ANALYSIS & SUMMARY")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Paste text, notes, or articles. JARVIS sends it to Gemini with your key and generates a faithful summary with action items.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = docText,
                            onValueChange = { docText = it },
                            placeholder = { Text("Paste document text (up to 20,000 characters)...", style = MaterialTheme.typography.bodySmall, color = TextFaint) },
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("doc_text_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonMagenta,
                                unfocusedBorderColor = PanelBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = Color(0xCC080411),
                                unfocusedContainerColor = Color(0xCC080411)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${docText.length} chars", style = MaterialTheme.typography.bodySmall, color = TextFaint, fontSize = 9.sp)

                            Button(
                                onClick = {
                                    val key = vault.getApiKey()
                                    if (key.isNullOrBlank()) {
                                        noticeMessage = "No Gemini API key connected."
                                        return@Button
                                    }
                                    isSummarizing = true
                                    summaryResult = null
                                    coroutineScope.launch {
                                        try {
                                            val summary = geminiClient.generateContent(
                                                apiKey = key,
                                                model = vault.getModel(),
                                                prompt = "Summarize the following text accurately. Provide a one-sentence key takeaway, bullet points of key details, and action items:\n\n$docText",
                                                systemInstruction = "You are JARVIS. Summarize user-provided documents faithfully."
                                            )
                                            summaryResult = summary
                                        } catch (e: Exception) {
                                            summaryResult = "Summarization failed: ${e.localizedMessage}"
                                        } finally {
                                            isSummarizing = false
                                        }
                                    }
                                },
                                enabled = docText.trim().length >= 20 && !isSummarizing,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("summarize_button")
                            ) {
                                if (isSummarizing) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = TextPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(if (isSummarizing) "ANALYZING..." else "SUMMARIZE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                            }
                        }
                    }

                    if (summaryResult != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        PanelCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Eyebrow(text = "ANALYSIS RESULT")
                                Text(
                                    text = "SAVE AS NOTE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan,
                                    fontSize = 8.sp,
                                    modifier = Modifier.clickable {
                                        coroutineScope.launch {
                                            db.noteDao().insert(
                                                NoteEntity(
                                                    title = "Summary — ${timeFormat.format(Date())}",
                                                    content = summaryResult ?: ""
                                                )
                                            )
                                            Toast.makeText(context, "Saved summary as note", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text(
                                        text = summaryResult!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
