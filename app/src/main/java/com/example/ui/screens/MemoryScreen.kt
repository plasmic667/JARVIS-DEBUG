package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.JarvisDatabase
import com.example.core.database.MemoryEntity
import com.example.ui.components.AlertBanner
import com.example.ui.components.Eyebrow
import com.example.ui.components.PanelCard
import com.example.ui.components.PanelSoftCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val CATEGORIES = listOf("preference", "project", "person", "routine", "general")

@Composable
fun MemoryScreen(
    db: JarvisDatabase
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val memories by db.memoryDao().getAll().collectAsState(initial = emptyList())
    val settings by db.userSettingsDao().getSettingsFlow().collectAsState(initial = null)

    var newContent by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(CATEGORIES[0]) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    var editTarget by remember { mutableStateOf<MemoryEntity?>(null) }
    var editContentText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<MemoryEntity?>(null) }
    var showPurgeModal by remember { mutableStateOf(false) }

    var noticeMessage by remember { mutableStateOf<String?>(null) }

    fun addMemory() {
        val trimmed = newContent.trim()
        if (trimmed.length < 3) return
        if (trimmed.contains(Regex("(api[\\s_-]?key|password|secret|token|credit card)", RegexOption.IGNORE_CASE))) {
            noticeMessage = "Memory refused: sensitive secrets cannot be stored in JARVIS memory."
            return
        }

        coroutineScope.launch {
            db.memoryDao().insert(MemoryEntity(content = trimmed, category = selectedCategory))
            newContent = ""
            noticeMessage = "Memory saved. JARVIS can now use it in future conversations."
        }
    }

    fun exportMemories() {
        val jsonArray = JSONArray()
        memories.forEach { m ->
            jsonArray.put(JSONObject().apply {
                put("id", m.id)
                put("content", m.content)
                put("category", m.category)
                put("createdAt", m.createdAt)
            })
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("JARVIS Memories Export", jsonArray.toString(2)))
        Toast.makeText(context, "Exported ${memories.size} memories to clipboard JSON", Toast.LENGTH_LONG).show()
    }

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
                    Eyebrow(text = "MEMORY CORE")
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "WHAT JARVIS REMEMBERS",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportMemories() },
                        shape = RoundedCornerShape(999.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("export_memories_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp), tint = TextPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EXPORT", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontSize = 8.sp)
                    }

                    Button(
                        onClick = { showPurgeModal = true },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FB7185)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("purge_memories_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(12.dp), tint = StatusRose)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PURGE ALL", style = MaterialTheme.typography.labelSmall, color = StatusRose, fontSize = 8.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Only the user-approved facts below are injected into Gemini's system instruction when long-term memory is enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontSize = 11.sp
            )

            if (noticeMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AlertBanner(kind = "info", message = noticeMessage!!)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Memory Toggle switch
            PanelSoftCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use Long-Term Memory",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "When disabled, no memories are sent to Gemini. Chat history still works.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextFaint,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = settings?.memoryEnabled ?: true,
                        onCheckedChange = { isEnabled ->
                            coroutineScope.launch {
                                val s = settings ?: com.example.core.database.UserSettingsEntity()
                                db.userSettingsDao().saveSettings(s.copy(memoryEnabled = isEnabled))
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonMagenta, checkedTrackColor = Color(0x52D946EF))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Add Memory Box
            PanelCard(modifier = Modifier.fillMaxWidth()) {
                Eyebrow(text = "ADD MEMORY")
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = newContent,
                    onValueChange = { newContent = it },
                    placeholder = { Text("e.g. Prefers concise answers with bullet points.", style = MaterialTheme.typography.bodySmall, color = TextFaint) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_content_input"),
                    maxLines = 3,
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        OutlinedButton(
                            onClick = { categoryMenuExpanded = true },
                            shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(selectedCategory.uppercase(), style = MaterialTheme.typography.labelSmall, color = NeonCyan, fontSize = 9.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NeonCyan)
                        }

                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                            modifier = Modifier.background(Color(0xFF150D24))
                        ) {
                            CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.uppercase(), style = MaterialTheme.typography.bodySmall, color = TextPrimary) },
                                    onClick = {
                                        selectedCategory = cat
                                        categoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { addMemory() },
                        enabled = newContent.trim().length >= 3,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("save_memory_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE MEMORY", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Memory List
            Text(
                text = "APPROVED FACTS (${memories.size})",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Memory core is empty. Add a fact or preference above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(memories, key = { it.id }) { memory ->
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
                                Text(
                                    text = memory.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = memory.category.uppercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonMagenta,
                                    fontSize = 8.sp,
                                    letterSpacing = 1.sp
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        editTarget = memory
                                        editContentText = memory.content
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }

                                IconButton(
                                    onClick = { deleteTarget = memory },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRose, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Memory Dialog
    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            containerColor = Color(0xFF150D24),
            title = { Text("Edit Memory", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = editContentText,
                    onValueChange = { editContentText = it },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonMagenta,
                        unfocusedBorderColor = PanelBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = editTarget ?: return@Button
                        coroutineScope.launch {
                            db.memoryDao().update(target.copy(content = editContentText.trim(), updatedAt = System.currentTimeMillis()))
                            editTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta)
                ) {
                    Text("Save", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Delete Memory Dialog
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Color(0xFF150D24),
            title = { Text("Delete Memory", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
            text = {
                Text("Remove \"${deleteTarget!!.content}\" from long-term memory?", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = deleteTarget ?: return@Button
                        coroutineScope.launch {
                            db.memoryDao().delete(target)
                            deleteTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRose)
                ) {
                    Text("Delete", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Purge All Dialog
    if (showPurgeModal) {
        AlertDialog(
            onDismissRequest = { showPurgeModal = false },
            containerColor = Color(0xFF150D24),
            title = { Text("Purge All Memories", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
            text = {
                Text(
                    "This permanently deletes all ${memories.size} stored memories from this device. Conversations and notes will not be affected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            db.memoryDao().deleteAll()
                            showPurgeModal = false
                            noticeMessage = "All long-term memories have been purged."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRose)
                ) {
                    Text("Purge Permanently", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeModal = false }) {
                    Text("Keep Memories", color = TextMuted)
                }
            }
        )
    }
}
