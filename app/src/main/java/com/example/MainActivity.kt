package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.database.JarvisDatabase
import com.example.core.network.GeminiClient
import com.example.core.security.CredentialVault
import com.example.core.voice.VoiceManager
import com.example.ui.components.StatusChip
import com.example.ui.screens.*
import com.example.ui.theme.*

private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    private lateinit var vault: CredentialVault
    private lateinit var db: JarvisDatabase
    private lateinit var geminiClient: GeminiClient
    private lateinit var voiceManager: VoiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        vault = CredentialVault(this)
        db = JarvisDatabase.getInstance(this)
        geminiClient = GeminiClient()
        voiceManager = VoiceManager(this)

        setContent {
            MyApplicationTheme {
                JarvisApp(
                    vault = vault,
                    db = db,
                    geminiClient = geminiClient,
                    voiceManager = voiceManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.destroy()
    }
}

@Composable
fun JarvisApp(
    vault: CredentialVault,
    db: JarvisDatabase,
    geminiClient: GeminiClient,
    voiceManager: VoiceManager
) {
    val context = LocalContext.current

    // Request Audio and Notification permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Determine initial route based on credential vault
    val initialRoute = remember {
        if (!vault.hasKey()) "connect" else "dashboard"
    }

    var currentScreen by remember { mutableStateOf(initialRoute) }
    var chatConversationId by remember { mutableStateOf<String?>(null) }
    var chatInitialPrompt by remember { mutableStateOf<String?>(null) }
    var workspaceInitialTab by remember { mutableStateOf<String?>(null) }
    var isReplacingKey by remember { mutableStateOf(false) }

    val userSettings by db.userSettingsDao().getSettingsFlow().collectAsState(initial = null)

    val navItems = remember {
        listOf(
            NavItem("dashboard", "Dashboard", Icons.Default.Dashboard, Icons.Outlined.Dashboard),
            NavItem("chat", "Assistant", Icons.Default.ChatBubble, Icons.Outlined.ChatBubbleOutline),
            NavItem("voice", "Voice", Icons.Default.Mic, Icons.Outlined.Mic),
            NavItem("workspace", "Workspace", Icons.Default.ViewQuilt, Icons.Outlined.ViewQuilt),
            NavItem("settings", "Settings", Icons.Default.Settings, Icons.Outlined.Settings)
        )
    }

    // Handle Back Button
    BackHandler(enabled = currentScreen != "dashboard" && currentScreen != "connect") {
        if (currentScreen == "auth") {
            currentScreen = "connect"
        } else {
            currentScreen = "dashboard"
        }
    }

    val isTopLevelScreen = currentScreen in listOf("dashboard", "chat", "voice", "workspace", "settings", "conversations", "memory")

    Scaffold(
        containerColor = BgDark,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (isTopLevelScreen && currentScreen != "chat" && currentScreen != "voice") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xE6080411))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { currentScreen = "dashboard" }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PanelSoftBg)
                                .border(1.dp, PanelBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("J", style = MaterialTheme.typography.titleMedium, color = NeonMagenta, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("JARVIS", style = MaterialTheme.typography.titleMedium, color = TextPrimary, letterSpacing = 2.sp, fontSize = 13.sp)
                            Text("AI ASSISTANT", style = MaterialTheme.typography.labelSmall, color = NeonMagenta, fontSize = 7.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(connected = vault.hasKey(), model = vault.getModel(), last4 = vault.getLast4())
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x33D946EF))
                                .clickable { currentScreen = "settings" },
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = userSettings?.operatorName?.firstOrNull()?.uppercase() ?: "O"
                            Text(initial, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (isTopLevelScreen) {
                NavigationBar(
                    containerColor = Color(0xF2080411),
                    contentColor = TextPrimary,
                    modifier = Modifier
                        .height(64.dp)
                        .border(width = 1.dp, color = Color(0x1FB292FF))
                        .testTag("bottom_nav_bar")
                ) {
                    navItems.forEach { item ->
                        val selected = currentScreen == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (item.route == "chat") {
                                    chatConversationId = null
                                    chatInitialPrompt = null
                                }
                                currentScreen = item.route
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    letterSpacing = 1.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonMagenta,
                                selectedTextColor = NeonMagenta,
                                unselectedIconColor = TextFaint,
                                unselectedTextColor = TextFaint,
                                indicatorColor = Color(0x33D946EF)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "connect" -> {
                    ConnectScreen(
                        vault = vault,
                        geminiClient = geminiClient,
                        isChangeMode = isReplacingKey,
                        onConnected = {
                            isReplacingKey = false
                            currentScreen = "auth"
                        }
                    )
                }

                "auth" -> {
                    AuthScreen(
                        vault = vault,
                        db = db,
                        onAuthenticated = {
                            currentScreen = "dashboard"
                        }
                    )
                }

                "dashboard" -> {
                    DashboardScreen(
                        vault = vault,
                        db = db,
                        onNavigate = { route ->
                            if (route.startsWith("chat?id=")) {
                                chatConversationId = route.substringAfter("chat?id=")
                                chatInitialPrompt = null
                                currentScreen = "chat"
                            } else {
                                currentScreen = route
                            }
                        },
                        onStartPrompt = { prompt ->
                            chatConversationId = null
                            chatInitialPrompt = prompt
                            currentScreen = "chat"
                        }
                    )
                }

                "chat" -> {
                    ChatScreen(
                        conversationId = chatConversationId,
                        initialPrompt = chatInitialPrompt,
                        vault = vault,
                        db = db,
                        geminiClient = geminiClient,
                        voiceManager = voiceManager,
                        onNavigateBack = { currentScreen = "dashboard" },
                        onOpenConnect = {
                            isReplacingKey = true
                            currentScreen = "connect"
                        }
                    )
                }

                "voice" -> {
                    VoiceScreen(
                        vault = vault,
                        db = db,
                        geminiClient = geminiClient,
                        voiceManager = voiceManager,
                        onOpenInChat = { convId ->
                            chatConversationId = convId
                            chatInitialPrompt = null
                            currentScreen = "chat"
                        }
                    )
                }

                "workspace" -> {
                    WorkspaceScreen(
                        initialTab = workspaceInitialTab,
                        vault = vault,
                        db = db,
                        geminiClient = geminiClient
                    )
                }

                "conversations" -> {
                    ConversationsScreen(
                        db = db,
                        onOpenConversation = { convId ->
                            chatConversationId = convId
                            chatInitialPrompt = null
                            currentScreen = "chat"
                        },
                        onNewConversation = {
                            chatConversationId = null
                            chatInitialPrompt = null
                            currentScreen = "chat"
                        }
                    )
                }

                "memory" -> {
                    MemoryScreen(db = db)
                }

                "settings" -> {
                    SettingsScreen(
                        vault = vault,
                        db = db,
                        geminiClient = geminiClient,
                        voiceManager = voiceManager,
                        onReplaceKey = {
                            isReplacingKey = true
                            currentScreen = "connect"
                        },
                        onDisconnect = {
                            currentScreen = "connect"
                        }
                    )
                }

                else -> {
                    DashboardScreen(
                        vault = vault,
                        db = db,
                        onNavigate = { currentScreen = it },
                        onStartPrompt = {
                            chatConversationId = null
                            chatInitialPrompt = it
                            currentScreen = "chat"
                        }
                    )
                }
            }
        }
    }
}
