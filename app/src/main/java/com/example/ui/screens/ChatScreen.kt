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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import java.util.UUID

private val QUICK_PROMPTS = listOf(
    "Brief me on my day and prioritize my tasks.",
    "Remember that I prefer concise, factual answers.",
    "Draft a polite follow-up email about a delayed project.",
    "Calculate 18% of 4,250 and explain the result."
)

@Composable
fun ChatScreen(
    conversationId: String?,
    initialPrompt: String?,
    vault: CredentialVault,
    db: JarvisDatabase,
    geminiClient: GeminiClient,
    voiceManager: VoiceManager,
    onNavigateBack: () -> Unit,
    onOpenConnect: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var activeConversationId by remember { mutableStateOf(conversationId) }
    var inputText by remember { mutableStateOf(initialPrompt ?: "") }
    var isGenerating by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var streamToolSummaries by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val messages by if (activeConversationId != null) {
        db.messageDao().getMessagesForConversation(activeConversationId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<MessageEntity>()) }
    }

    val isListening by voiceManager.isListening.collectAsState()
    val interimVoiceText by voiceManager.interimText.collectAsState()
    val isSpeaking by voiceManager.isSpeaking.collectAsState()

    val jarvisTools = remember { JarvisTools(db) }

    LaunchedEffect(messages.size, streamingText) {
        if (messages.isNotEmpty() || streamingText.isNotEmpty()) {
            listState.animateScrollToItem((messages.size + if (streamingText.isNotEmpty()) 1 else 0).coerceAtLeast(0))
        }
    }

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("JARVIS", text))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun sendMessage(textToSend: String) {
        val prompt = textToSend.trim()
        if (prompt.isEmpty() || isGenerating) return
        inputText = ""
        errorMessage = null

        coroutineScope.launch {
            val key = vault.getApiKey()
            if (key.isNullOrBlank()) {
                errorMessage = "No Gemini API key connected. Please connect your key."
                onOpenConnect()
                return@launch
            }

            var currentConvId = activeConversationId
            if (currentConvId == null) {
                val newConv = ConversationEntity(
                    title = PersonalityEngine.deriveTitle(prompt),
                    lastMessagePreview = prompt
                )
                db.conversationDao().insert(newConv)
                currentConvId = newConv.id
                activeConversationId = currentConvId
            }

            // Insert user message
            val userMsg = MessageEntity(
                conversationId = currentConvId,
                role = "user",
                content = prompt
            )
            db.messageDao().insert(userMsg)

            isGenerating = true
            streamingText = ""
            streamToolSummaries = emptyList()

            try {
                val history = db.messageDao().getMessagesList(currentConvId)
                val settings = db.userSettingsDao().getSettings() ?: com.example.core.database.UserSettingsEntity()
                val memories = if (settings.memoryEnabled) db.memoryDao().getAllList() else emptyList()
                val systemInstruction = PersonalityEngine.buildSystemInstruction(settings, memories)

                // Build contents JSON
                val contentsJson = JSONArray()
                history.takeLast(14).forEach { msg ->
                    contentsJson.put(JSONObject().apply {
                        put("role", if (msg.role == "assistant") "model" else "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", msg.content) })
                        })
                    })
                }

                val toolsJson = jarvisTools.getToolDeclarationsJson()
                val result = geminiClient.streamChat(
                    apiKey = key,
                    model = vault.getModel(),
                    contentsJson = contentsJson,
                    systemInstruction = systemInstruction,
                    temperature = settings.temperature,
                    maxOutputTokens = 2048,
                    toolsJson = toolsJson,
                    onToken = { chunk ->
                        streamingText += chunk
                    }
                )

                if (result.error != null) {
                    errorMessage = result.error
                }

                // If tool calls were made, execute them
                if (result.toolCalls.isNotEmpty()) {
                    val toolSummaries = mutableListOf<String>()
                    result.toolCalls.forEach { tc ->
                        val res = jarvisTools.execute(tc.name, tc.args)
                        toolSummaries.add(res.summary)
                    }
                    streamToolSummaries = toolSummaries
                }

                val finalAssistantText = if (streamingText.isNotBlank()) streamingText else if (result.fullText.isNotBlank()) result.fullText else "Understood. Protocols executed."

                val assistantMsg = MessageEntity(
                    conversationId = currentConvId,
                    role = "assistant",
                    content = finalAssistantText,
                    toolsJson = if (streamToolSummaries.isNotEmpty()) streamToolSummaries.joinToString(" • ") else null
                )
                db.messageDao().insert(assistantMsg)

                // Update conversation preview
                val conv = db.conversationDao().getById(currentConvId)
                if (conv != null) {
                    db.conversationDao().update(
                        conv.copy(
                            lastMessagePreview = finalAssistantText.take(120),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                if (settings.autoSpeak) {
                    voiceManager.speak(finalAssistantText, rate = settings.voiceRate, pitch = settings.voicePitch)
                }
            } catch (e: Exception) {
                errorMessage = "Failed: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isGenerating = false
                streamingText = ""
            }
        }
    }

    Scaffold(
        containerColor = BgDark,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Orb(
                            modifier = Modifier.fillMaxSize(),
                            state = if (isGenerating) OrbState.THINKING else if (isSpeaking) OrbState.SPEAKING else if (isListening) OrbState.LISTENING else OrbState.IDLE,
                            density = 300
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "JARVIS ASSISTANT",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${vault.getModel()} • ${if (isGenerating) "streaming" else if (isSpeaking) "speaking" else if (isListening) "listening" else "ready"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextFaint,
                            fontSize = 9.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSpeaking) {
                        IconButton(onClick = { voiceManager.stopSpeaking() }) {
                            Icon(Icons.Default.VolumeOff, contentDescription = "Stop speech", tint = NeonMagenta)
                        }
                    }
                    IconButton(
                        onClick = {
                            activeConversationId = null
                            inputText = ""
                            streamingText = ""
                            errorMessage = null
                        },
                        modifier = Modifier.testTag("new_chat_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New chat", tint = TextPrimary)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Messages List or Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && streamingText.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                            Orb(modifier = Modifier.fillMaxSize(), state = OrbState.IDLE, density = 600)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "JARVIS IS LISTENING",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your key is connected and encrypted. Ask anything, or choose a prompt:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QUICK_PROMPTS.forEach { prompt ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PanelSoftBg)
                                        .border(1.dp, PanelBorder, RoundedCornerShape(12.dp))
                                        .clickable { sendMessage(prompt) }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = prompt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val isUser = msg.role == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                if (!isUser) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .padding(top = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Orb(modifier = Modifier.fillMaxSize(), state = OrbState.IDLE, density = 150)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column(
                                    modifier = Modifier.widthIn(max = 290.dp),
                                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                ) {
                                    Box(
                                        modifier = if (isUser) {
                                            Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Brush.linearGradient(listOf(Color(0x52D946EF), Color(0x408B5CF6))))
                                                .border(1.dp, Color(0x66D946EF), RoundedCornerShape(16.dp))
                                                .padding(12.dp)
                                        } else {
                                            Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(PanelSoftBg)
                                                .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
                                                .padding(12.dp)
                                        }
                                    ) {
                                        Text(
                                            text = msg.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            lineHeight = 20.sp
                                        )
                                    }

                                    if (!msg.toolsJson.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0x334ADE80))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Build, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(10.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(msg.toolsJson, style = MaterialTheme.typography.bodySmall, color = StatusGreen, fontSize = 9.sp)
                                        }
                                    }

                                    if (!isUser) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = "Copy",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextFaint,
                                                fontSize = 10.sp,
                                                modifier = Modifier.clickable { copyToClipboard(msg.content) }
                                            )
                                            Text(
                                                text = "Speak",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextFaint,
                                                fontSize = 10.sp,
                                                modifier = Modifier.clickable { voiceManager.speak(msg.content) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Live streaming bubble
                        if (streamingText.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                        Orb(modifier = Modifier.fillMaxSize(), state = OrbState.THINKING, density = 200)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .widthIn(max = 290.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(PanelSoftBg)
                                            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = "$streamingText ▍",
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

            // Error display
            if (errorMessage != null) {
                AlertBanner(
                    kind = "error",
                    message = errorMessage!!,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            // Dictation interim text
            if (isListening && interimVoiceText.isNotBlank()) {
                Text(
                    text = "Listening: $interimVoiceText...",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonMagenta,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Mic Button
                IconButton(
                    onClick = {
                        if (isListening) {
                            voiceManager.stopListening()
                        } else {
                            voiceManager.startListening { spoken ->
                                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
                            }
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isListening) Color(0x40D946EF) else Color(0x33000000))
                        .border(1.dp, if (isListening) NeonMagenta else PanelBorder, RoundedCornerShape(14.dp))
                        .testTag("dictation_mic_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice dictation",
                        tint = if (isListening) NeonMagenta else TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    placeholder = { Text("Ask JARVIS anything...", style = MaterialTheme.typography.bodySmall, color = TextFaint) },
                    maxLines = 4,
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

                Spacer(modifier = Modifier.width(8.dp))

                // Send / Stop Button
                if (isGenerating) {
                    IconButton(
                        onClick = { isGenerating = false },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x33FB7185))
                            .border(1.dp, StatusRose, RoundedCornerShape(14.dp))
                            .testTag("stop_generating_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = StatusRose)
                    }
                } else {
                    IconButton(
                        onClick = { sendMessage(inputText) },
                        enabled = inputText.trim().isNotEmpty(),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (inputText.trim().isNotEmpty()) NeonMagenta else Color(0x33000000))
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.trim().isNotEmpty()) TextPrimary else TextFaint
                        )
                    }
                }
            }
        }
    }
}
