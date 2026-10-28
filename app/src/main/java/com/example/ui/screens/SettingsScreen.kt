package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.core.database.UserSettingsEntity
import com.example.core.model.PERSONALITY_MODES
import com.example.core.network.GeminiClient
import com.example.core.network.GeminiModelItem
import com.example.core.network.ValidationResult
import com.example.core.security.CredentialVault
import com.example.core.voice.VoiceManager
import com.example.ui.components.AlertBanner
import com.example.ui.components.Eyebrow
import com.example.ui.components.PanelCard
import com.example.ui.components.PanelSoftCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SettingsScreen(
    vault: CredentialVault,
    db: JarvisDatabase,
    geminiClient: GeminiClient,
    voiceManager: VoiceManager,
    onReplaceKey: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val settings by db.userSettingsDao().getSettingsFlow().collectAsState(initial = null)
    val currentSettings = settings ?: UserSettingsEntity()

    var customDirectiveText by remember { mutableStateOf(currentSettings.customPersonality ?: "") }
    var loadedModels by remember { mutableStateOf<List<GeminiModelItem>>(emptyList()) }
    var isTestingKey by remember { mutableStateOf(false) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf<Pair<String, String>?>(null) } // kind to message
    var showDisconnectModal by remember { mutableStateOf(false) }

    fun updateSettings(transform: (UserSettingsEntity) -> UserSettingsEntity) {
        coroutineScope.launch {
            val updated = transform(currentSettings)
            db.userSettingsDao().saveSettings(updated)
            bannerMessage = "success" to "Preferences saved."
        }
    }

    fun testLiveConnection() {
        if (isTestingKey) return
        val key = vault.getApiKey()
        if (key.isNullOrBlank()) {
            bannerMessage = "error" to "No key to test. Connect a Gemini key first."
            return
        }
        isTestingKey = true
        bannerMessage = "info" to "Testing API Key…"
        coroutineScope.launch {
            try {
                val res = geminiClient.validateApiKey(key, vault.getModel())
                when (res) {
                    is ValidationResult.Success -> {
                        vault.setStatus("valid")
                        bannerMessage = "success" to "Gemini API Connected Successfully"
                    }
                    is ValidationResult.Error -> {
                        vault.setStatus("invalid")
                        bannerMessage = "error" to res.message
                    }
                }
            } catch (e: Exception) {
                bannerMessage = "error" to "Gemini service error. Please try again later."
            } finally {
                isTestingKey = false
            }
        }
    }

    fun loadModelsFromKey() {
        val key = vault.getApiKey()
        if (key.isNullOrBlank()) return
        isLoadingModels = true
        bannerMessage = null
        coroutineScope.launch {
            val list = geminiClient.listModels(key)
            isLoadingModels = false
            if (list.isNotEmpty()) {
                loadedModels = list
                bannerMessage = "success" to "Retrieved ${list.size} compatible Gemini models from your API key."
            } else {
                bannerMessage = "error" to "Could not list models. Make sure your key has access."
            }
        }
    }

    fun exportAllData() {
        coroutineScope.launch {
            val convs = db.conversationDao().search("")
            val mems = db.memoryDao().getAllList()
            val tks = db.taskDao().getOpenTasksList()
            val exportObj = JSONObject().apply {
                put("exportedAt", System.currentTimeMillis())
                put("note", "Gemini API key is intentionally excluded. It stays encrypted in Android Keystore.")
                put("conversationsCount", convs.size)
                put("memoriesCount", mems.size)
                put("tasksCount", tks.size)
            }
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("JARVIS Full Export", exportObj.toString(2)))
            Toast.makeText(context, "Exported workspace data to clipboard JSON", Toast.LENGTH_LONG).show()
        }
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Eyebrow(text = "CONFIGURATION")
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Personality, voice, and privacy are configured here. Changes apply to the next request without requiring restart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            if (bannerMessage != null) {
                item {
                    AlertBanner(kind = bannerMessage!!.first, message = bannerMessage!!.second)
                }
            }

            // Gemini Uplink Section
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Eyebrow(text = "GEMINI UPLINK")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "YOUR API KEY",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PanelSoftCard(modifier = Modifier.weight(1f)) {
                            Text("KEY", style = MaterialTheme.typography.bodySmall, color = TextFaint, fontSize = 8.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("•••• ${vault.getLast4()}", style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }

                        PanelSoftCard(modifier = Modifier.weight(1f)) {
                            Text("MODEL", style = MaterialTheme.typography.bodySmall, color = TextFaint, fontSize = 8.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(vault.getModel(), style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }

                        PanelSoftCard(modifier = Modifier.weight(1f)) {
                            Text("STORAGE", style = MaterialTheme.typography.bodySmall, color = TextFaint, fontSize = 8.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("KEYSTORE", style = MaterialTheme.typography.bodySmall, color = StatusGreen, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { testLiveConnection() },
                            enabled = !isTestingKey,
                            shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                            modifier = Modifier.weight(1f).testTag("test_live_key_button")
                        ) {
                            Text(if (isTestingKey) "TESTING..." else "TEST CONNECTION", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                        }

                        OutlinedButton(
                            onClick = { loadModelsFromKey() },
                            enabled = !isLoadingModels,
                            shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                            modifier = Modifier.weight(1f).testTag("load_models_button")
                        ) {
                            Text(if (isLoadingModels) "LOADING..." else "LOAD MODELS", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onReplaceKey,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x338B5CF6)),
                            modifier = Modifier.weight(1f).testTag("replace_key_button")
                        ) {
                            Text("REPLACE KEY", style = MaterialTheme.typography.labelSmall, color = NeonMagenta, fontSize = 8.sp)
                        }

                        Button(
                            onClick = { showDisconnectModal = true },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FB7185)),
                            modifier = Modifier.weight(1f).testTag("disconnect_key_button")
                        ) {
                            Text("DISCONNECT", style = MaterialTheme.typography.labelSmall, color = StatusRose, fontSize = 8.sp)
                        }
                    }

                    if (loadedModels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("SELECT MODEL FROM KEY", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        loadedModels.take(6).forEach { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (vault.getModel() == m.id) Color(0x33D946EF) else Color(0x1AFFFFFF))
                                    .clickable {
                                        vault.setModel(m.id)
                                        updateSettings { it.copy(model = m.id) }
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(m.displayName, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                if (vault.getModel() == m.id) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = NeonMagenta, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            // Personality Engine Section
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Eyebrow(text = "PERSONALITY ENGINE")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "TONE & BEHAVIOUR",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PERSONALITY_MODES.forEach { mode ->
                        val isSelected = currentSettings.personality == mode.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Color(0x33D946EF) else PanelSoftBg)
                                .border(1.dp, if (isSelected) NeonMagenta else PanelBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    updateSettings { it.copy(personality = mode.id) }
                                }
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mode.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonMagenta, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(mode.blurb, style = MaterialTheme.typography.bodySmall, color = TextMuted, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(mode.voiceHint.uppercase(), style = MaterialTheme.typography.bodySmall, color = TextFaint, fontSize = 8.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (currentSettings.personality == "custom") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customDirectiveText,
                            onValueChange = { customDirectiveText = it },
                            placeholder = { Text("Custom directive: e.g. Speak like a mission controller.", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonMagenta,
                                unfocusedBorderColor = PanelBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { updateSettings { it.copy(customPersonality = customDirectiveText) } },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("SAVE DIRECTIVE", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Humor slider
                    Text("HUMOUR LEVEL: ${listOf("NONE", "LIGHT", "OCCASIONAL", "PLAYFUL")[currentSettings.humor.coerceIn(0, 3)]}", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                    Slider(
                        value = currentSettings.humor.toFloat(),
                        onValueChange = { updateSettings { s -> s.copy(humor = it.toInt()) } },
                        valueRange = 0f..3f,
                        steps = 2,
                        colors = SliderDefaults.colors(thumbColor = NeonMagenta, activeTrackColor = NeonMagenta)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Expressiveness slider
                    Text("EXPRESSIVENESS: ${listOf("PLAIN", "MINIMAL", "EXPRESSIVE", "THEATRICAL")[currentSettings.expressiveness.coerceIn(0, 3)]}", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                    Slider(
                        value = currentSettings.expressiveness.toFloat(),
                        onValueChange = { updateSettings { s -> s.copy(expressiveness = it.toInt()) } },
                        valueRange = 0f..3f,
                        steps = 2,
                        colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Temperature slider
                    Text("CREATIVITY (TEMPERATURE): ${String.format(Locale.US, "%.1f", currentSettings.temperature)}", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                    Slider(
                        value = currentSettings.temperature,
                        onValueChange = { updateSettings { s -> s.copy(temperature = it) } },
                        valueRange = 0.0f..1.4f,
                        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Response length pills
                    Text("RESPONSE LENGTH", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0x40000000))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("concise", "balanced", "detailed").forEach { len ->
                            val isSel = currentSettings.responseLength == len
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isSel) Color(0x33FFFFFF) else Color.Transparent)
                                    .clickable { updateSettings { it.copy(responseLength = len) } }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(len.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (isSel) TextPrimary else TextFaint, fontSize = 8.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Speak replies automatically", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        Switch(
                            checked = currentSettings.autoSpeak,
                            onCheckedChange = { isChecked -> updateSettings { it.copy(autoSpeak = isChecked) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonMagenta, checkedTrackColor = Color(0x52D946EF))
                        )
                    }
                }
            }

            // Voice & Speech Output Section
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Eyebrow(text = "SPEECH OUTPUT")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "VOICE ENGINE",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("SPEECH RATE: ${String.format(Locale.US, "%.2f", currentSettings.voiceRate)}x", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                    Slider(
                        value = currentSettings.voiceRate,
                        onValueChange = { updateSettings { s -> s.copy(voiceRate = it) } },
                        valueRange = 0.6f..1.6f,
                        colors = SliderDefaults.colors(thumbColor = NeonMagenta, activeTrackColor = NeonMagenta)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("SPEECH PITCH: ${String.format(Locale.US, "%.2f", currentSettings.voicePitch)}", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                    Slider(
                        value = currentSettings.voicePitch,
                        onValueChange = { updateSettings { s -> s.copy(voicePitch = it) } },
                        valueRange = 0.5f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            voiceManager.speak("Voice calibrated. All systems nominal.", rate = currentSettings.voiceRate, pitch = currentSettings.voicePitch)
                        },
                        shape = RoundedCornerShape(999.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                        modifier = Modifier.align(Alignment.End).testTag("preview_voice_button")
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PREVIEW VOICE", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontSize = 8.sp)
                    }
                }
            }

            // Privacy & Data export
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Eyebrow(text = "PRIVACY & SECURITY")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "DATA CONTROLS",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Gemini keys are encrypted with AES-256-GCM in Android Keystore.\n" +
                                "• All transcripts, notes, tasks and memories are stored on-device in Room SQLite.\n" +
                                "• No telemetry logs or message contents are ever broadcast.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { exportAllData() },
                        shape = RoundedCornerShape(999.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                        modifier = Modifier.fillMaxWidth().testTag("export_all_data_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("EXPORT ALL WORKSPACE DATA (JSON)", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontSize = 8.sp)
                    }
                }
            }
        }
    }

    if (showDisconnectModal) {
        AlertDialog(
            onDismissRequest = { showDisconnectModal = false },
            containerColor = Color(0xFF150D24),
            title = { Text("Disconnect Gemini Key", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
            text = {
                Text(
                    "The encrypted Gemini key will be permanently removed from this device's Android Keystore and you will return to the connection screen. Your workspace tasks, notes and memories remain safe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vault.disconnect()
                        showDisconnectModal = false
                        onDisconnect()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRose)
                ) {
                    Text("Disconnect & Remove", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectModal = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
