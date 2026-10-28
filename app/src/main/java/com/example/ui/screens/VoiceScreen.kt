package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.ConversationEntity
import com.example.core.database.JarvisDatabase
import com.example.core.database.MessageEntity
import com.example.core.model.PersonalityEngine
import com.example.core.network.GeminiClient
import com.example.core.security.CredentialVault
import com.example.core.tools.JarvisTools
import com.example.core.voice.VoiceManager
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class VoiceTurn(val role: String, val content: String)

@Composable
fun VoiceScreen(
    vault: CredentialVault,
    db: JarvisDatabase,
    geminiClient: GeminiClient,
    voiceManager: VoiceManager,
    onOpenInChat: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val isListening by voiceManager.isListening.collectAsState()
    val isSpeaking by voiceManager.isSpeaking.collectAsState()
    val interimText by voiceManager.interimText.collectAsState()
    val voiceError by voiceManager.errorMessage.collectAsState()

    var isThinking by remember { mutableStateOf(false) }
    var turns by remember { mutableStateOf(listOf<VoiceTurn>()) }
    var fallbackText by remember { mutableStateOf("") }
    var activeConversationId by remember { mutableStateOf<String?>(null) }
    var repliesMuted by remember { mutableStateOf(false) }

    val jarvisTools = remember { JarvisTools(db) }

    val orbState = when {
        isListening -> OrbState.LISTENING
        isThinking -> OrbState.THINKING
        isSpeaking -> OrbState.SPEAKING
        else -> OrbState.IDLE
    }

    val statusCopy = when {
        isListening -> "Listening — speak now"
        isThinking -> "Thinking with Gemini..."
        isSpeaking -> "Speaking — tap stop to interrupt"
        else -> "Push to talk"
    }

    fun processQuery(userQuery: String) {
        val query = userQuery.trim()
        if (query.isEmpty() || isThinking) return
        fallbackText = ""

        turns = turns + VoiceTurn("user", query)
        isThinking = true

        coroutineScope.launch {
            val key = vault.getApiKey()
            if (key.isNullOrBlank()) {
                turns = turns + VoiceTurn("assistant", "No Gemini key connected. Please connect in Settings.")
                isThinking = false
                return@launch
            }

            var convId = activeConversationId
            if (convId == null) {
                val newConv = ConversationEntity(
                    title = PersonalityEngine.deriveTitle(query),
                    lastMessagePreview = query
                )
                db.conversationDao().insert(newConv)
                convId = newConv.id
                activeConversationId = convId
            }

            db.messageDao().insert(MessageEntity(conversationId = convId, role = "user", content = query))

            try {
                val settings = db.userSettingsDao().getSettings() ?: com.example.core.database.UserSettingsEntity()
                val memories = if (settings.memoryEnabled) db.memoryDao().getAllList() else emptyList()
                val systemInstruction = PersonalityEngine.buildSystemInstruction(settings, memories)

                val contentsJson = JSONArray().apply {
                    turns.takeLast(6).forEach { t ->
                        put(JSONObject().apply {
                            put("role", if (t.role == "assistant") "model" else "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", t.content) })
                            })
                        })
                    }
                }

                val toolsJson = jarvisTools.getToolDeclarationsJson()
                var fullResponse = ""

                val result = geminiClient.streamChat(
                    apiKey = key,
                    model = vault.getModel(),
                    contentsJson = contentsJson,
                    systemInstruction = systemInstruction,
                    temperature = settings.temperature,
                    maxOutputTokens = 1024,
                    toolsJson = toolsJson,
                    onToken = { chunk -> fullResponse += chunk }
                )

                if (result.toolCalls.isNotEmpty()) {
                    result.toolCalls.forEach { tc ->
                        jarvisTools.execute(tc.name, tc.args)
                    }
                }

                val finalReply = if (fullResponse.isNotBlank()) fullResponse else if (result.fullText.isNotBlank()) result.fullText else "Understood, command received."
                turns = turns + VoiceTurn("assistant", finalReply)

                db.messageDao().insert(MessageEntity(conversationId = convId, role = "assistant", content = finalReply))

                if (!repliesMuted) {
                    voiceManager.speak(finalReply, rate = settings.voiceRate, pitch = settings.voicePitch)
                }
            } catch (e: Exception) {
                turns = turns + VoiceTurn("assistant", "Voice processing failed: ${e.localizedMessage}")
            } finally {
                isThinking = false
            }
        }
    }

    Scaffold(
        containerColor = BgDark,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Eyebrow(text = "VOICE UPLINK")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "TALK TO JARVIS",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Central Glowing Orb
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .testTag("voice_orb"),
                contentAlignment = Alignment.Center
            ) {
                Orb(modifier = Modifier.fillMaxSize(), state = orbState, density = 700)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusCopy,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isListening) NeonMagenta else if (isSpeaking) StatusGreen else TextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            if (isListening && interimText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"$interimText\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Push to talk & Speech Controls
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (isListening) {
                            voiceManager.stopListening()
                        } else {
                            voiceManager.startListening { query -> processQuery(query) }
                        }
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) StatusRose else NeonMagenta
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("push_to_talk_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isListening) "STOP LISTENING" else "PUSH TO TALK",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (isSpeaking) {
                    OutlinedButton(
                        onClick = { voiceManager.stopSpeaking() },
                        shape = RoundedCornerShape(999.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("STOP", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    }
                } else {
                    OutlinedButton(
                        onClick = { repliesMuted = !repliesMuted },
                        shape = RoundedCornerShape(999.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (repliesMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (repliesMuted) StatusAmber else TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (repliesMuted) "MUTED" else "MUTE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (repliesMuted) StatusAmber else TextPrimary
                        )
                    }
                }
            }

            if (voiceError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                AlertBanner(kind = "warning", message = voiceError!!)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text fallback input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = fallbackText,
                    onValueChange = { fallbackText = it },
                    placeholder = { Text("Type if mic is unavailable...", style = MaterialTheme.typography.bodySmall, color = TextFaint) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
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
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { processQuery(fallbackText) },
                    enabled = fallbackText.trim().isNotEmpty() && !isThinking,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (fallbackText.trim().isNotEmpty()) NeonMagenta else Color(0x33000000))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Transcript Section
            PanelCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Eyebrow(text = "LIVE TRANSCRIPT")
                    if (activeConversationId != null) {
                        Text(
                            text = "OPEN IN TEXT CHAT",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMagenta,
                            fontSize = 8.sp,
                            modifier = Modifier.clickable { onOpenInChat(activeConversationId!!) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (turns.isEmpty()) {
                    Text(
                        text = "Press 'Push to talk' and speak. Your request and JARVIS's voice reply will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(turns) { turn ->
                            val isUser = turn.role == "user"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isUser) Color(0x33D946EF) else Color(0x22FFFFFF))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = if (isUser) "YOU" else "JARVIS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) NeonMagenta else NeonCyan,
                                    fontSize = 8.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = turn.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
