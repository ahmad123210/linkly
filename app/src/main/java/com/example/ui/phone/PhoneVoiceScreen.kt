package com.example.ui.phone

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel

@Composable
fun PhoneVoiceScreen(viewModel: LinklyViewModel) {
    val isTransmitting by viewModel.isTransmitting.collectAsState()
    val isReceiving by viewModel.isReceiving.collectAsState()
    val amplitude by viewModel.currentAmplitude.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "التحدث المباشر (تكي وكي)",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "صوت فوري منخفض التأخير ينطلق مباشرة من سماعات التلفاز",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }

        // Interactive Mic Button & Ripples (Reduced by half)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(130.dp)
            ) {
                // Audio wave pulses when active
                if (isTransmitting || isReceiving) {
                    val waveColor = if (isTransmitting) LinklyRose else LinklyEmerald
                    val dynScale = (1.1f + amplitude * 0.7f).coerceAtLeast(pulse)

                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(dynScale)
                            .clip(CircleShape)
                            .background(waveColor.copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .size(95.dp)
                            .scale(dynScale * 0.85f)
                            .clip(CircleShape)
                            .background(waveColor.copy(alpha = 0.25f))
                    )
                }

                val buttonColor = when {
                    isTransmitting -> LinklyRose
                    isReceiving -> LinklyEmerald
                    else -> LinklyIndigo
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(70.dp)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(buttonColor)
                        .testTag("large_talk_button")
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    viewModel.togglePushToTalk()
                                }
                            )
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "تحدث",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Text(
                text = if (isTransmitting) "جاري التحدث..." else "اضغط للتحدث",
                color = if (isTransmitting) LinklyRose else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // Live Status & Level Meter
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val statusText = when {
                    isTransmitting -> "جاري البث الصوتي المباشر للتلفاز..."
                    isReceiving -> "صوت قادم من ميكروفون ريموت التلفاز..."
                    else -> "اضغط مطولاً على الزر وتحدث بصوت واضح"
                }

                val statusColor = when {
                    isTransmitting -> LinklyRose
                    isReceiving -> LinklyEmerald
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Text(
                    text = statusText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = statusColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Simulated / Live volume bar meter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bars = 18
                    for (i in 0 until bars) {
                        val active = isTransmitting && (i / bars.toFloat()) < (amplitude * 1.5f + 0.1f)
                        val barColor = if (active) LinklyCyan else MaterialTheme.colorScheme.surfaceVariant
                        val heightDp = if (active) (8 + (i % 5) * 4).dp else 8.dp

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(6.dp)
                                .height(heightDp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(barColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "يعمل التحدث الصوتي حتى عندما يكون تطبيق التلفاز في الخلفية",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
