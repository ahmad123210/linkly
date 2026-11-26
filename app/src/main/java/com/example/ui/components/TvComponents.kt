package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.ui.theme.TvFocusBorder
import com.example.ui.theme.TvFocusHighlight

/**
 * TV D-Pad focus helper with glowing border and subtle scale effect
 */
fun Modifier.tvFocusable(
    cornerRadius: Dp = 12.dp,
    focusedScale: Float = 1.05f,
    onFocusChange: (Boolean) -> Unit = {}
): Modifier = this.then(
    Modifier
        .onFocusChanged { focusState ->
            onFocusChange(focusState.isFocused)
        }
        .focusable()
)

@Composable
fun TvFocusCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = tween(150),
        label = "tvScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) TvFocusHighlight else Color.Transparent,
        animationSpec = tween(150),
        label = "tvBorder"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
        modifier = modifier
            .scale(scale)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(),
        shadowElevation = if (isFocused) 12.dp else 2.dp
    ) {
        content()
    }
}

/**
 * Floating Tiki-Wike Walkie-Talkie Button for Phone & TV
 */
@Composable
fun WalkieTalkieFloatingButton(
    isTransmitting: Boolean,
    isReceiving: Boolean,
    currentAmplitude: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val activeColor = when {
        isTransmitting -> LinklyRose
        isReceiving -> LinklyEmerald
        else -> LinklyIndigo
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Outer sound ripple wave when transmitting or receiving
        if (isTransmitting || isReceiving) {
            val waveScale = (1.1f + currentAmplitude * 0.5f).coerceAtLeast(pulseScale)
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(waveScale)
                    .clip(CircleShape)
                    .background(activeColor.copy(alpha = 0.25f))
            )
        }

        // Inner circular button - Toggle on single click
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(activeColor)
                .testTag("walkie_talkie_button")
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onToggle()
                        }
                    )
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isTransmitting || isReceiving) Icons.Default.Mic else Icons.Default.MicNone,
                    contentDescription = "التحدث المباشر",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                if (isTransmitting) {
                    Text(
                        text = "مباشر",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionBadge(
    status: ConnectionStatus,
    ipAddress: String,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor) = when (status) {
        ConnectionStatus.CONNECTED -> "متصل: $ipAddress" to LinklyEmerald
        ConnectionStatus.LISTENING -> "في الانتظار (منفذ 8888)" to LinklyCyan
        ConnectionStatus.SEARCHING -> "جاري البحث..." to LinklyIndigo
        ConnectionStatus.DISCONNECTED -> "غير متصل" to LinklyRose
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(statusColor.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (status == ConnectionStatus.CONNECTED) Icons.Default.Wifi else Icons.Default.WifiOff,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
