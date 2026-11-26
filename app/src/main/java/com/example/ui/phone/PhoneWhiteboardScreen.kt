package com.example.ui.phone

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ToolType
import com.example.model.TvScreenMode
import com.example.ui.components.WhiteboardCanvas
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.DisposableEffect

/**
 * Phone whiteboard drawing view in full-screen landscape mode.
 * Maximizes drawing canvas area across the full screen, with all tools collapsed into a floating trigger button,
 * and a small corner button to exit the mode and restore portrait orientation.
 */
@Composable
fun PhoneWhiteboardScreen(
    viewModel: LinklyViewModel,
    onExit: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Force landscape mode for maximum drawing area, revert to portrait on exit
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val strokes by viewModel.strokes.collectAsState()
    val currentStroke by viewModel.currentStroke.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val selectedColorHex by viewModel.selectedColorHex.collectAsState()
    val selectedStrokeWidth by viewModel.selectedStrokeWidth.collectAsState()
    val pointerState by viewModel.pointerState.collectAsState()
    val bgImage by viewModel.canvasBackgroundImage.collectAsState()

    var showToolsDialog by remember { mutableStateOf(false) }

    // Android zero-permission Photo Picker for adding background image
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                viewModel.setCanvasBackgroundBitmap(bitmap)
                viewModel.switchTvScreen(TvScreenMode.WHITEBOARD)
            } catch (_: Exception) {}
        }
    }

    val paletteColors = listOf(
        "#38BDF8", // Cyan
        "#EF4444", // Red
        "#F59E0B", // Amber
        "#10B981", // Green
        "#FFFFFF", // White
        "#A855F7", // Purple
        "#EC4899", // Pink
        "#F97316"  // Orange
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        // Fullscreen Drawing Canvas
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            WhiteboardCanvas(
                strokes = strokes,
                currentStroke = currentStroke,
                pointerState = pointerState,
                backgroundImage = bgImage,
                isInteractive = true,
                onDrawStart = { x, y -> viewModel.onDrawStart(x, y) },
                onDrawMove = { x, y -> viewModel.onDrawMove(x, y) },
                onDrawEnd = { viewModel.onDrawEnd() },
                modifier = Modifier.fillMaxSize()
            )

            // Active tool badge / laser tip
            if (activeTool == ToolType.POINTER) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "مؤشر ليزر مباشر على التلفاز",
                        color = LinklyCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Small Exit Button in Corner (to leave whiteboard mode)
        IconButton(
            onClick = {
                viewModel.switchTvScreen(TvScreenMode.ORDERS)
                onExit()
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color(0xCC1E293B)
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(38.dp)
                .border(1.dp, Color(0x44CBD5E1), CircleShape)
                .testTag("whiteboard_exit_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "خروج من وضع الرسم",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Summon Floating Button for Drawing Tools
        FloatingActionButton(
            onClick = { showToolsDialog = true },
            containerColor = LinklyIndigo,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(52.dp)
                .testTag("summon_tools_button")
        ) {
            Icon(
                imageVector = Icons.Default.Create,
                contentDescription = "استدعاء أدوات الرسم",
                modifier = Modifier.size(24.dp)
            )
        }

        // Floating Dialog / Sheet for Drawing Tools
        if (showToolsDialog) {
            Dialog(onDismissRequest = { showToolsDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Dialog Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "أدوات الرسم والتوضيح",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            IconButton(
                                onClick = { showToolsDialog = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق الأدوات",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }

                        // Tools Row
                        Text(text = "اختر الأداة:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolItemButton(
                                icon = Icons.Default.Create,
                                label = "قلم",
                                isSelected = activeTool == ToolType.PEN,
                                onClick = { viewModel.setTool(ToolType.PEN) }
                            )
                            ToolItemButton(
                                icon = Icons.Default.Rectangle,
                                label = "مستطيل",
                                isSelected = activeTool == ToolType.RECTANGLE,
                                onClick = { viewModel.setTool(ToolType.RECTANGLE) }
                            )
                            ToolItemButton(
                                icon = Icons.Default.Circle,
                                label = "دائرة",
                                isSelected = activeTool == ToolType.CIRCLE,
                                onClick = { viewModel.setTool(ToolType.CIRCLE) }
                            )
                            ToolItemButton(
                                icon = Icons.Default.NearMe,
                                label = "ليزر",
                                isSelected = activeTool == ToolType.POINTER,
                                onClick = { viewModel.setTool(ToolType.POINTER) }
                            )
                            ToolItemButton(
                                icon = Icons.Default.Clear,
                                label = "ممحاة",
                                isSelected = activeTool == ToolType.ERASER,
                                onClick = { viewModel.setTool(ToolType.ERASER) }
                            )
                        }

                        // Colors Palette
                        Text(text = "لون الخط:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            paletteColors.forEach { hex ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedColorHex == hex) 3.dp else 1.dp,
                                            color = if (selectedColorHex == hex) LinklyCyan else Color(0x66888888),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setColorHex(hex) }
                                )
                            }
                        }

                        // Stroke Width Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "سماكة الخط:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            Text(
                                text = "${selectedStrokeWidth.toInt()}px",
                                color = LinklyCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Slider(
                            value = selectedStrokeWidth,
                            onValueChange = { viewModel.setStrokeWidth(it) },
                            valueRange = 3f..36f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick Actions (Undo, Clear, Add photo)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.undoStroke() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تراجع", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.clearCanvas() },
                                colors = ButtonDefaults.buttonColors(containerColor = LinklyRose.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = LinklyRose, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مسح", color = LinklyRose, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    showToolsDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("صورة خلفية", fontSize = 11.sp)
                            }
                        }

                        if (bgImage != null) {
                            Button(
                                onClick = { viewModel.setCanvasBackgroundBitmap(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("إزالة الصورة الخلفية", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = { showToolsDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = LinklyEmerald),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تم ومتابعة الرسم", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolItemButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) LinklyIndigo else Color(0xFF1E293B))
            .border(1.dp, if (isSelected) LinklyCyan else Color(0x33475569), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
            )
        }
    }
}
