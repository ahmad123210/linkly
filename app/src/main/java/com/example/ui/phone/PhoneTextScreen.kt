package com.example.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TextDisplayData
import com.example.model.TvScreenMode
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyIndigo
import com.example.viewmodel.LinklyViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneTextScreen(viewModel: LinklyViewModel) {
    val currentTextData by viewModel.textData.collectAsState()

    var textInput by remember(currentTextData.text) { mutableStateOf(currentTextData.text) }
    var fontSize by remember(currentTextData.fontSizeSp) { mutableFloatStateOf(currentTextData.fontSizeSp) }
    var selectedTextColor by remember(currentTextData.textColorHex) { mutableStateOf(currentTextData.textColorHex) }
    var selectedBgColor by remember(currentTextData.bgColorHex) { mutableStateOf(currentTextData.bgColorHex) }
    var alignment by remember(currentTextData.alignment) { mutableStateOf(currentTextData.alignment) }

    val presetMessages = listOf(
        "أهلاً وسهلاً بكم جميعاً! 🌟",
        "الاجتماع سيبدأ بعد قليل ⏱️",
        "عروض حصرية اليوم بخصم 30% 🎉",
        "يرجى الهدوء والمحافظة على النظافة ☕",
        "المحل مغلق حالياً، نلقاكم غداً إن شاء الله 🌙",
        "يرجى استلام الطلبات من نقطة التسليم 🛎️"
    )

    val colorOptions = listOf(
        "#FFFFFF", "#38BDF8", "#F59E0B", "#10B981", "#EF4444", "#EC4899", "#A855F7"
    )

    val bgOptions = listOf(
        "#0A0E17", "#1E1B4B", "#064E3B", "#7F1D1D", "#1E293B", "#312E81"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Text Input Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("اكتب رسالتك أو إعلانك هنا...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("tv_text_input")
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val updated = currentTextData.copy(
                            text = textInput,
                            fontSizeSp = fontSize,
                            textColorHex = selectedTextColor,
                            bgColorHex = selectedBgColor,
                            alignment = alignment,
                            showClock = currentTextData.showClock,
                            is12HourClock = true
                        )
                        viewModel.updateTextData(updated)
                        viewModel.switchTvScreen(TvScreenMode.TEXT)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_text_button")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "إرسال", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // Quick Presets
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "نماذج وعبارات شائعة جاهزة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetMessages.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    textInput = preset
                                    val updated = currentTextData.copy(
                                        text = preset,
                                        fontSizeSp = fontSize,
                                        textColorHex = selectedTextColor,
                                        bgColorHex = selectedBgColor,
                                        alignment = alignment,
                                        showClock = currentTextData.showClock,
                                        is12HourClock = true
                                    )
                                    viewModel.updateTextData(updated)
                                    viewModel.switchTvScreen(TvScreenMode.TEXT)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Typography & Style Controls
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Font Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "حجم الخط:", fontWeight = FontWeight.Medium)
                    Text(text = "${fontSize.toInt()} sp", fontWeight = FontWeight.Bold, color = LinklyIndigo)
                }
                Slider(
                    value = fontSize,
                    onValueChange = {
                        fontSize = it
                        viewModel.updateTextData(currentTextData.copy(fontSizeSp = it))
                    },
                    valueRange = 28f..110f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Text Alignment Buttons
                Text(text = "محاذاة النص:", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            alignment = "START"
                            viewModel.updateTextData(currentTextData.copy(alignment = "START"))
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (alignment == "START") LinklyIndigo else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (alignment == "START") Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.FormatAlignRight, contentDescription = "محاذاة لليمين")
                    }
                    IconButton(
                        onClick = {
                            alignment = "CENTER"
                            viewModel.updateTextData(currentTextData.copy(alignment = "CENTER"))
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (alignment == "CENTER") LinklyIndigo else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (alignment == "CENTER") Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.FormatAlignCenter, contentDescription = "توسيط")
                    }
                    IconButton(
                        onClick = {
                            alignment = "END"
                            viewModel.updateTextData(currentTextData.copy(alignment = "END"))
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (alignment == "END") LinklyIndigo else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (alignment == "END") Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.FormatAlignLeft, contentDescription = "محاذاة لليسار")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Text Color Row
                Text(text = "لون النص:", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colorOptions.forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedTextColor == hex) 3.dp else 1.dp,
                                    color = if (selectedTextColor == hex) LinklyCyan else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedTextColor = hex
                                    viewModel.updateTextData(currentTextData.copy(textColorHex = hex))
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Background Color Row
                Text(text = "لون خلفية الشاشة:", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    bgOptions.forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedBgColor == hex) 3.dp else 1.dp,
                                    color = if (selectedBgColor == hex) LinklyCyan else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedBgColor = hex
                                    viewModel.updateTextData(currentTextData.copy(bgColorHex = hex))
                                }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(60.dp))
    }
}
