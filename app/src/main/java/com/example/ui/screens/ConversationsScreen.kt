package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.ConversationEntity
import com.example.core.database.JarvisDatabase
import com.example.ui.components.Eyebrow
import com.example.ui.components.PanelCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationsScreen(
    db: JarvisDatabase,
    onOpenConversation: (String) -> Unit,
    onNewConversation: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val allConversations by db.conversationDao().getAll().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ConversationEntity?>(null) }

    val filteredConversations = remember(allConversations, searchQuery) {
        if (searchQuery.isBlank()) allConversations
        else allConversations.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

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
                    Eyebrow(text = "TRANSCRIPT ARCHIVE")
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "CONVERSATION HISTORY",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = onNewConversation,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("new_convo_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("NEW", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search titles and message previews...", style = MaterialTheme.typography.bodySmall, color = TextFaint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonViolet) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("conversation_search_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonMagenta,
                    unfocusedBorderColor = PanelBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = Color(0xCC080411),
                    unfocusedContainerColor = Color(0xCC080411)
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No conversations match '$searchQuery'" else "No saved conversations yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredConversations, key = { it.id }) { conv ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(PanelBg)
                                .border(1.dp, if (conv.pinned) NeonMagenta.copy(alpha = 0.5f) else PanelBorder, RoundedCornerShape(16.dp))
                                .clickable { onOpenConversation(conv.id) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (conv.pinned) {
                                        Icon(
                                            Icons.Default.PushPin,
                                            contentDescription = "Pinned",
                                            tint = NeonMagenta,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = conv.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (conv.lastMessagePreview.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = conv.lastMessagePreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = dateFormat.format(Date(conv.updatedAt)).uppercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextFaint,
                                    fontSize = 9.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            db.conversationDao().update(conv.copy(pinned = !conv.pinned, updatedAt = System.currentTimeMillis()))
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pin",
                                        tint = if (conv.pinned) NeonMagenta else TextFaint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        renameTarget = conv
                                        renameText = conv.title
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = { deleteTarget = conv },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRose, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Color(0xFF150D24),
            title = { Text("Rename Conversation", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
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
                        val target = renameTarget ?: return@Button
                        coroutineScope.launch {
                            db.conversationDao().update(target.copy(title = renameText.trim().ifEmpty { "New conversation" }, updatedAt = System.currentTimeMillis()))
                            renameTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta)
                ) {
                    Text("Save", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Color(0xFF150D24),
            title = { Text("Delete Conversation", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
            text = {
                Text(
                    "Permanently delete \"${deleteTarget!!.title}\" and all of its messages? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = deleteTarget ?: return@Button
                        coroutineScope.launch {
                            db.messageDao().deleteForConversation(target.id)
                            db.conversationDao().delete(target)
                            deleteTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRose)
                ) {
                    Text("Delete Permanently", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
