package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.network.GeminiClient
import com.example.core.network.ValidationResult
import com.example.core.security.CredentialVault
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MODEL_OPTIONS = listOf(
    "gemini-2.5-flash" to "Gemini 2.5 Flash — fastest, recommended",
    "gemini-2.5-pro" to "Gemini 2.5 Pro — deepest reasoning",
    "gemini-2.5-flash-lite" to "Gemini 2.5 Flash-Lite — lightest quota",
    "gemini-flash-latest" to "Gemini Flash (latest alias)"
)

private const val AI_STUDIO_URL = "https://aistudio.google.com/app/apikey"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    vault: CredentialVault,
    geminiClient: GeminiClient,
    isChangeMode: Boolean = false,
    onConnected: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(MODEL_OPTIONS[0].first) }
    var isTesting by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<ValidationResult.Error?>(null) }
    var validationSuccess by remember { mutableStateOf<ValidationResult.Success?>(null) }
    var helpExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    fun runConnect() {
        if (isTesting) return
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            validationError = ValidationResult.Error(
                code = "empty",
                message = "Invalid API Key. Please check your key and try again.",
                hint = "Enter your Google AI Studio key to activate JARVIS."
            )
            return
        }

        isTesting = true
        validationError = null
        validationSuccess = null

        coroutineScope.launch {
            try {
                val result = geminiClient.validateApiKey(trimmed, selectedModel)
                when (result) {
                    is ValidationResult.Success -> {
                        validationSuccess = result
                        vault.saveApiKey(trimmed, selectedModel)
                        delay(900)
                        onConnected()
                    }
                    is ValidationResult.Error -> {
                        validationError = result
                    }
                }
            } catch (e: Exception) {
                validationError = ValidationResult.Error(
                    code = "unexpected",
                    message = "Gemini service error. Please try again later.",
                    hint = e.localizedMessage
                )
            } finally {
                isTesting = false
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PanelSoftBg)
                            .border(1.dp, PanelBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "J",
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonMagenta,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "JARVIS",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                        Text(
                            text = "AI ASSISTANT",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMagenta.copy(alpha = 0.8f),
                            fontSize = 8.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }

                StatusChip(
                    connected = validationSuccess != null || vault.hasKey(),
                    model = vault.getModel()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Animated Orb Hero
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .testTag("orb_hero"),
                contentAlignment = Alignment.Center
            ) {
                Orb(
                    modifier = Modifier.fillMaxSize(),
                    state = if (isTesting) OrbState.THINKING else if (validationSuccess != null) OrbState.SPEAKING else OrbState.IDLE,
                    density = 800
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Eyebrow(text = if (isChangeMode) "CREDENTIAL REPLACEMENT • LIVE VALIDATION" else "STEP 01 • SINCE 2025")
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "CONNECT YOUR\nINTELLIGENCE",
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
                letterSpacing = 1.sp,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connect your Gemini API key to activate your personal AI assistant. Your key stays encrypted in your device Keystore and is never shared.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth(0.92f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Credential Panel
            PanelCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Eyebrow(text = "GEMINI UPLINK")
                    Text(
                        text = if (isTesting) "Testing API Key…" else if (validationSuccess != null) "Gemini API Connected Successfully" else "AWAITING KEY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (validationSuccess != null) StatusGreen else if (isTesting) StatusAmber else TextMuted,
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "GEMINI API KEY",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // API Key Field - NO PREFIX RESTRICTION
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_api_key_input"),
                    placeholder = {
                        Text(
                            "Enter Google AI Studio Key",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextFaint
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = NeonMagenta.copy(alpha = 0.8f)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showKey = !showKey },
                            modifier = Modifier.testTag("toggle_key_visibility")
                        ) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key",
                                tint = TextMuted
                            )
                        }
                    },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { runConnect() }),
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

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = TextFaint,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Encrypted with AES-256-GCM in Android Keystore • never logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextFaint,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Model Selection Dropdown
                Text(
                    text = "DEFAULT MODEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = modelMenuExpanded,
                    onExpandedChange = { modelMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = MODEL_OPTIONS.firstOrNull { it.first == selectedModel }?.second ?: selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("model_dropdown"),
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
                    ExposedDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                        modifier = Modifier.background(Color(0xFF150D24))
                    ) {
                        MODEL_OPTIONS.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                },
                                onClick = {
                                    selectedModel = id
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Error Banner
                if (validationError != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    AlertBanner(
                        kind = "error",
                        message = validationError!!.message,
                        hint = validationError!!.hint
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { runConnect() },
                            shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                            modifier = Modifier.testTag("retry_connect_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RETRY", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                        }

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_URL))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x298B5CF6)),
                            modifier = Modifier.testTag("open_ai_studio_button")
                        ) {
                            Text("OPEN AI STUDIO", style = MaterialTheme.typography.labelSmall, color = NeonMagenta, fontSize = 9.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp), tint = NeonMagenta)
                        }
                    }
                }

                // Success Banner
                if (validationSuccess != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    AlertBanner(
                        kind = "success",
                        message = "Gemini API Connected Successfully",
                        hint = "Handshake verified in ${validationSuccess!!.latencyMs}ms on ${validationSuccess!!.model}. Redirecting to dashboard..."
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Connect CTA
                GradientButton(
                    text = if (isTesting) "Testing API Key…" else if (validationSuccess != null) "Gemini API Connected Successfully" else "CONNECT VIA GEMINI API KEY",
                    onClick = { runConnect() },
                    enabled = !isTesting,
                    leadingIcon = if (validationSuccess != null) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "connect_gemini_button"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Guide Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { helpExpanded = !helpExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "How to get a Gemini API key",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonMagenta,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (helpExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = NeonMagenta,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = helpExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PanelSoftBg)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Four quick steps (under a minute):",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Sign in to Google AI Studio with your Google account.\n" +
                                    "2. Click 'Get API key' and create a key in your project.\n" +
                                    "3. Copy the key and paste it above (no prefix restriction).\n" +
                                    "4. Keep it private. You can rotate or revoke it at any time in AI Studio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_URL))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("OPEN GOOGLE AI STUDIO", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keystore Vault Plate
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x33FFFFFF))
                    .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Eyebrow(text = "CREDENTIAL VAULT")
                    Text(
                        text = "Encrypted • device-scoped",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(PanelBorder)
                )
                Column {
                    Eyebrow(text = "NEXT")
                    Text(
                        text = "Operator • Dashboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = StatusGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
