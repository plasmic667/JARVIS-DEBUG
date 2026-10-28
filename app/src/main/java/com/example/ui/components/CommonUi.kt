package com.example.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PanelBg)
            .border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun PanelSoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PanelSoftBg)
            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    testTag: String = "gradient_button"
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .testTag(testTag)
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) JarvisButtonGradient else Brush.horizontalGradient(listOf(Color(0xFF332048), Color(0xFF251636))))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) TextPrimary else TextFaint,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun StatusChip(
    connected: Boolean,
    model: String? = null,
    last4: String? = null,
    modifier: Modifier = Modifier
) {
    val chipBg = if (connected) Color(0x1A4ADE80) else Color(0x1AFBBF24)
    val chipBorder = if (connected) Color(0x594ADE80) else Color(0x59FBBF24)
    val dotColor = if (connected) StatusGreen else StatusAmber

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(chipBg)
            .border(1.dp, chipBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (connected) "GEMINI LIVE • ${model?.replace("gemini-", "") ?: "ONLINE"}" else "GEMINI OFFLINE",
            style = MaterialTheme.typography.bodySmall,
            color = if (connected) Color(0xFFB9F6CD) else Color(0xFFFDE68A),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        letterSpacing = 3.sp,
        modifier = modifier
    )
}

@Composable
fun AlertBanner(
    kind: String, // "error", "success", "info", "warning"
    message: String,
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, textColor, icon) = when (kind) {
        "success" -> Quad(Color(0x244ADE80), Color(0x664ADE80), Color(0xFFE2FBE9), Icons.Default.CheckCircle)
        "warning" -> Quad(Color(0x24FBBF24), Color(0x66FBBF24), Color(0xFFFEF3C7), Icons.Default.Warning)
        "info" -> Quad(Color(0x248B5CF6), Color(0x668B5CF6), Color(0xFFEDE9FE), Icons.Default.Info)
        else -> Quad(Color(0x24FB7185), Color(0x66FB7185), Color(0xFFFFE4E6), Icons.Default.Error)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (!hint.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hint,
                    color = textColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun WaveformView(
    active: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 18
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformLoop")
    val anim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveAnim"
    )

    Row(
        modifier = modifier.height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (i in 0 until bars) {
            val factor = if (active) {
                0.2f + 0.8f * kotlin.math.abs(kotlin.math.sin((anim * Math.PI * 2 + i * 0.5).toFloat()))
            } else {
                0.2f
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(factor)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(NeonPink, NeonViolet)
                        )
                    )
            )
        }
    }
}
