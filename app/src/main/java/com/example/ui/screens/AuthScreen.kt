package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.JarvisDatabase
import com.example.core.database.UserSettingsEntity
import com.example.core.security.CredentialVault
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    vault: CredentialVault,
    db: JarvisDatabase,
    onAuthenticated: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isRegisterMode by remember { mutableStateOf(false) }
    var operatorName by remember { mutableStateOf("Operator") }
    var email by remember { mutableStateOf("operator@domain.com") }
    var password by remember { mutableStateOf("••••••••") }
    var confirmPassword by remember { mutableStateOf("••••••••") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val existing = db.userSettingsDao().getSettings()
        if (existing != null) {
            operatorName = existing.operatorName
            email = existing.email
        }
    }

    fun submit() {
        errorMessage = null
        if (email.isBlank() || !email.contains("@")) {
            errorMessage = "Please enter a valid email address."
            return
        }
        if (isRegisterMode && operatorName.isBlank()) {
            errorMessage = "Please enter an operator name."
            return
        }
        if (isRegisterMode && password != confirmPassword) {
            errorMessage = "Passwords do not match."
            return
        }

        coroutineScope.launch {
            val existing = db.userSettingsDao().getSettings() ?: UserSettingsEntity()
            db.userSettingsDao().saveSettings(
                existing.copy(
                    operatorName = operatorName.trim().ifEmpty { "Operator" },
                    email = email.trim(),
                    emailVerified = true
                )
            )
            onAuthenticated()
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
            // Top Bar
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
                        Text("J", style = MaterialTheme.typography.titleLarge, color = NeonMagenta, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("JARVIS", style = MaterialTheme.typography.titleMedium, color = TextPrimary, letterSpacing = 3.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x1A4ADE80))
                        .border(1.dp, Color(0x664ADE80), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GEMINI SECURED • ${vault.getLast4()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB9F6CD),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Orb
            Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                Orb(modifier = Modifier.fillMaxSize(), state = OrbState.LISTENING, density = 600)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Eyebrow(text = "STEP 02 • IDENTITY")
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "VERIFY YOUR\nOPERATOR",
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
                lineHeight = 30.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your Gemini uplink is live on ${vault.getModel()}. Now secure the local operator workspace.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth(0.9f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Form panel
            PanelCard(modifier = Modifier.fillMaxWidth()) {
                // Tab switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x66080411))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (!isRegisterMode) PanelSoftBg else Color.Transparent)
                            .clickable { isRegisterMode = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SIGN IN",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isRegisterMode) TextPrimary else TextMuted,
                            fontSize = 9.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isRegisterMode) PanelSoftBg else Color.Transparent)
                            .clickable { isRegisterMode = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CREATE ACCOUNT",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRegisterMode) TextPrimary else TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isRegisterMode) {
                    Text("OPERATOR NAME", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = operatorName,
                        onValueChange = { operatorName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("operator_name_input"),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = NeonViolet) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonMagenta,
                            unfocusedBorderColor = PanelBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Color(0xCC080411),
                            unfocusedContainerColor = Color(0xCC080411)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text("EMAIL", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("operator_email_input"),
                    leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null, tint = NeonViolet) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonMagenta,
                        unfocusedBorderColor = PanelBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = Color(0xCC080411),
                        unfocusedContainerColor = Color(0xCC080411)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("PASSWORD", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("operator_password_input"),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = NeonViolet) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonMagenta,
                        unfocusedBorderColor = PanelBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = Color(0xCC080411),
                        unfocusedContainerColor = Color(0xCC080411)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                if (isRegisterMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("CONFIRM PASSWORD", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("operator_confirm_password_input"),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = NeonViolet) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonMagenta,
                            unfocusedBorderColor = PanelBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Color(0xCC080411),
                            unfocusedContainerColor = Color(0xCC080411)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AlertBanner(kind = "error", message = errorMessage!!)
                }

                Spacer(modifier = Modifier.height(18.dp))

                GradientButton(
                    text = if (isRegisterMode) "CREATE OPERATOR & ENTER" else "ENTER JARVIS",
                    onClick = { submit() },
                    leadingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "auth_submit_button"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
