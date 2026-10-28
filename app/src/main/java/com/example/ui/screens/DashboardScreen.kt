package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.JarvisDatabase
import com.example.core.database.TaskEntity
import com.example.core.security.CredentialVault
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class QuickAction(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val targetScreen: String,
    val prompt: String? = null
)

@Composable
fun DashboardScreen(
    vault: CredentialVault,
    db: JarvisDatabase,
    onNavigate: (String) -> Unit,
    onStartPrompt: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val settings by db.userSettingsDao().getSettingsFlow().collectAsState(initial = null)
    val conversations by db.conversationDao().getAll().collectAsState(initial = emptyList())
    val openTasks by db.taskDao().getOpenTasks().collectAsState(initial = emptyList())
    val memories by db.memoryDao().getAll().collectAsState(initial = emptyList())
    val notes by db.noteDao().getAll().collectAsState(initial = emptyList())
    val reminders by db.reminderDao().getUpcoming().collectAsState(initial = emptyList())
    val totalMessages by db.messageDao().getMessageCount().collectAsState(initial = 0)

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val displayHeading = when {
        hour < 12 -> "Systems primed for the day"
        hour < 18 -> "All systems nominal"
        else -> "Evening protocols active"
    }

    val operatorName = settings?.operatorName ?: "Operator"

    val quickActions = remember {
        listOf(
            QuickAction("New conversation", "Text the assistant", Icons.Default.ChatBubbleOutline, "chat"),
            QuickAction("Voice session", "Push to talk", Icons.Default.Mic, "voice"),
            QuickAction("Capture note", "Save a thought", Icons.Default.EditNote, "workspace", "notes"),
            QuickAction("Add task", "Track an action", Icons.Default.Checklist, "workspace", "tasks"),
            QuickAction("Memory core", "Review what JARVIS knows", Icons.Default.Psychology, "memory"),
            QuickAction("Reminder", "Time-zone aware", Icons.Default.Alarm, "workspace", "reminders")
        )
    }

    Scaffold(
        containerColor = BgDark,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            // Hero Section
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusChip(connected = true, model = vault.getModel())
                        Text(
                            text = "KEY • ${vault.getLast4()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(Color(0x33000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Orb(
                                modifier = Modifier.fillMaxSize(),
                                state = OrbState.IDLE,
                                density = 500
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Eyebrow(text = "$greeting, $operatorName")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayHeading,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 22.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Your personal intelligence layer is connected to your own Gemini key. Conversations, memory and tools are stored securely on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Metrics Plate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x40000000))
                            .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalMessages",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Eyebrow(text = "TOTAL EXCHANGES", modifier = Modifier.padding(top = 2.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(PanelBorder)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${conversations.size}",
                                style = MaterialTheme.typography.titleLarge,
                                color = NeonMagenta,
                                fontWeight = FontWeight.Bold
                            )
                            Eyebrow(text = "CONVERSATIONS", modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigate("chat") },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("open_assistant_button")
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ASSISTANT", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                        }

                        OutlinedButton(
                            onClick = { onNavigate("voice") },
                            shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("open_voice_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VOICE", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontSize = 9.sp)
                        }

                        Button(
                            onClick = { onStartPrompt("Brief me on my day, then suggest the single highest-leverage next action.") },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x298B5CF6)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("daily_briefing_button")
                        ) {
                            Text("BRIEFING", style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontSize = 9.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp), tint = NeonCyan)
                        }
                    }
                }
            }

            // 4 Stats Tiles
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val stats = listOf(
                        "CONVERSATIONS" to conversations.size,
                        "MEMORIES" to memories.size,
                        "OPEN TASKS" to openTasks.size,
                        "SAVED NOTES" to notes.size
                    )
                    stats.forEach { (label, count) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PanelSoftBg)
                                .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NeonMagenta,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextFaint,
                                    fontSize = 7.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Quick Actions Deck
            item {
                Text(
                    text = "COMMAND DECK",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickActions.chunked(2).forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowActions.forEach { action ->
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(PanelSoftBg)
                                        .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
                                        .clickable {
                                            if (action.prompt != null) {
                                                onStartPrompt(action.prompt)
                                            } else {
                                                onNavigate(action.targetScreen)
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x33D946EF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(action.icon, contentDescription = null, tint = NeonMagenta, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = action.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = action.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextFaint,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Open Tasks Queue
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Eyebrow(text = "MISSION QUEUE • OPEN TASKS")
                        Text(
                            text = "MANAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMagenta,
                            fontSize = 9.sp,
                            modifier = Modifier.clickable { onNavigate("workspace") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (openTasks.isEmpty()) {
                        Text(
                            text = "Queue is clear. Ask JARVIS to create a task, or add one in the workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            openTasks.take(4).forEach { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x33000000))
                                        .border(1.dp, PanelBorder, RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                db.taskDao().update(task.copy(completed = true, updatedAt = System.currentTimeMillis()))
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Complete task",
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Priority: ${task.priority.uppercase()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextFaint,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Conversations
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Eyebrow(text = "RECENT TRANSCRIPTS")
                        Text(
                            text = "ALL HISTORY",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMagenta,
                            fontSize = 9.sp,
                            modifier = Modifier.clickable { onNavigate("conversations") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (conversations.isEmpty()) {
                        Text(
                            text = "No saved conversations yet. Start a session with the assistant.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            conversations.take(3).forEach { conv ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x33000000))
                                        .border(1.dp, PanelBorder, RoundedCornerShape(12.dp))
                                        .clickable { onNavigate("chat?id=${conv.id}") }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (conv.pinned) Icons.Default.PushPin else Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = if (conv.pinned) NeonMagenta else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = conv.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (conv.lastMessagePreview.isNotEmpty()) {
                                            Text(
                                                text = conv.lastMessagePreview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextFaint,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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
    }
}
