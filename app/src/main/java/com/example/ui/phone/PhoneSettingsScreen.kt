package com.example.ui.phone

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DeviceRole
import com.example.ui.components.ConnectionBadge
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.text.input.KeyboardType
import com.example.model.ConnectionStatus

@Composable
fun PhoneSettingsScreen(viewModel: LinklyViewModel) {
    val context = LocalContext.current
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedIp by viewModel.connectedDeviceIp.collectAsState()
    val dailyHistory by viewModel.dailyHistory.collectAsState()
    val audioPlaylist by viewModel.audioPlaylist.collectAsState()
    val audioPlaybackState by viewModel.audioPlaybackState.collectAsState()
    val useTtsForOrders by viewModel.useTtsForOrders.collectAsState()
    val waiterName by viewModel.waiterName.collectAsState()
    val isPaired by viewModel.isPaired.collectAsState()
    val pairingError by viewModel.pairingError.collectAsState()
    val offlinePendingOrders by viewModel.offlinePendingOrders.collectAsState()

    val savedPin = remember { context.getSharedPreferences("linkly_app_prefs", android.content.Context.MODE_PRIVATE).getString("phone_saved_pin", "") ?: "" }
    var manualIpInput by remember(connectedIp) { mutableStateOf(connectedIp.ifBlank { "192.168.1." }) }
    var manualPortInput by remember { mutableStateOf("8888") }
    var expandedDateStr by remember { mutableStateOf<String?>(null) }
    var waiterNameInput by remember(waiterName) { mutableStateOf(waiterName) }
    var pinPairingInput by remember { mutableStateOf(savedPin) }

    // Audio file picker to upload audio from phone to TV
    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val bytes = input?.readBytes()
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME) ?: -1
                var fileName = "track_${System.currentTimeMillis()}.mp3"
                if (cursor != null && cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                    cursor.close()
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.uploadAudioFile(fileName, bytes)
                }
            } catch (e: Exception) {
                android.util.Log.e("PhoneSettingsScreen", "Error picking audio file", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen PIN Pairing & Security Card - Displayed at the very top of connect screen
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = LinklyAmber)
                        Text(text = "اقتران الشاشة الآمن (PIN)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPaired) LinklyEmerald.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isPaired) "مقترن ومصرح ✓" else "غير مقترن",
                            color = if (isPaired) LinklyEmerald else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pinPairingInput,
                        onValueChange = { if (it.length <= 6) pinPairingInput = it.filter { ch -> ch.isDigit() } },
                        placeholder = { Text("رمز PIN (4-6 أرقام)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (pinPairingInput.isNotBlank()) {
                                viewModel.submitPairingPin(pinPairingInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo)
                    ) {
                        Text("اقتران")
                    }
                }
                if (pairingError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = pairingError ?: "", color = LinklyRose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (isPaired) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.clearSavedPinAndUnpair() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إلغاء الاقتران وحذف الرمز المحفوظ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // User Identity Card (اسم المستخدم)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = LinklyIndigo)
                    Text(text = "اسم المستخدم", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = waiterNameInput,
                        onValueChange = { waiterNameInput = it },
                        placeholder = { Text("مثال: احمد، نادل 1") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (waiterNameInput.isNotBlank()) {
                                viewModel.setWaiterName(waiterNameInput.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo)
                    ) {
                        Text("حفظ")
                    }
                }
            }
        }

        // Manager screen (one manager phone, password from the TV)
        ManagerAccessCard(viewModel = viewModel)

        // Offline Data Storage & Automatic Sync Status
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = LinklyCyan)
                    Text(text = "المزامنة والعمل بدون اتصال (Offline)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "يعمل النظام بالكامل عبر شبكة الواي فاي المحلية دون الحاجة لإنترنت. في حال انقطاع الاتصال المؤقت، تُحفظ الطلبات محلياً وتُزامن تلقائياً فور عودة الاتصال.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الطلبات بانتظار المزامنة: ${offlinePendingOrders.size}",
                        fontWeight = FontWeight.Bold,
                        color = if (offlinePendingOrders.isNotEmpty()) LinklyAmber else LinklyEmerald,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = { viewModel.syncPendingOfflineOrders() },
                        enabled = offlinePendingOrders.isNotEmpty() && connectionStatus == ConnectionStatus.CONNECTED && isPaired,
                        colors = ButtonDefaults.buttonColors(containerColor = LinklyEmerald)
                    ) {
                        Text("مزامنة فورية الآن")
                    }
                }
            }
        }

        // Global Order Sound Alert Settings (أصوات الإشعارات)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = LinklyCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "أصوات الإشعارات",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Switch(
                        checked = useTtsForOrders,
                        onCheckedChange = { viewModel.setUseTtsForOrders(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LinklyCyan
                        ),
                        modifier = Modifier.testTag("global_tts_toggle_switch")
                    )
                }
            }
        }

        // Daily Orders History & Restore Feature (سجل الطلبات اليومي واستعادة الدور)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = LinklyIndigo)
                    Column {
                        Text(text = "سجل الطلبات اليومي والأرشيف", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = "حفظ السجل باليوم مع إمكانية استعادة طلبات أي يوم لاستكمال الدور",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (dailyHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد طلبات مؤرشفة بعد. ستظهر هنا الأيام المسجلة تلقائياً.",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dailyHistory.forEach { daySummary ->
                            val isExpanded = expandedDateStr == daySummary.dateStr
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isExpanded) LinklyIndigo.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedDateStr = if (isExpanded) null else daySummary.dateStr
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = daySummary.formattedDateAr,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "إجمالي: ${daySummary.orders.size} طلبات (${daySummary.orders.count { it.status == com.example.model.OrderStatus.DELIVERED }} منجز)",
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = Color.Gray
                                            )
                                        }
                                    }

                                    // Expanded orders details & Restore Action Button
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp)
                                        ) {
                                            daySummary.orders.take(6).forEach { ord ->
                                                Text(
                                                    text = "• #${ord.ticketNumber.ifBlank { ord.id }}: ${ord.title}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                            }
                                            if (daySummary.orders.size > 6) {
                                                Text(
                                                    text = "... و ${daySummary.orders.size - 6} طلبات أخرى",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Button(
                                                onClick = {
                                                    viewModel.restoreDailyOrders(daySummary.dateStr)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = LinklyEmerald),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(imageVector = Icons.Default.Restore, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(text = "استعادة طلبات هذا اليوم واستكمال الدور", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Background Audio Player on TV (التحكم بالصوتيات بالخلفية على التلفاز)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = LinklyCyan)
                        Column {
                            Text(text = "تشغيل الصوتيات بالخلفية على التلفاز", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = if (audioPlaybackState.isPlaying) "قيد التشغيل: ${audioPlaybackState.currentTrackTitle}" else "متوقف حالياً",
                                color = if (audioPlaybackState.isPlaying) LinklyEmerald else Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Play/Pause button
                    IconButton(
                        onClick = { viewModel.toggleAudioPlayback() },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (audioPlaybackState.isPlaying) LinklyRose else LinklyEmerald)
                    ) {
                        Icon(
                            imageVector = if (audioPlaybackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (audioPlaybackState.isPlaying) "إيقاف مؤقت" else "تشغيل",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Upload Audio button from Phone to TV
                Button(
                    onClick = {
                        audioFilePicker.launch("audio/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "رفع ملف صوتي من الهاتف للتلفاز (MP3)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Playlist tracks
                Text(text = "قائمة التشغيل المتاحة على التلفاز:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    audioPlaylist.forEach { track ->
                        val isCurrent = audioPlaybackState.currentTrackTitle == track.title && audioPlaybackState.isPlaying
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) LinklyCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable {
                                    viewModel.playAudioTrack(track.id)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = track.title,
                                fontSize = 13.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) LinklyCyan else MaterialTheme.colorScheme.onSurface
                            )
                            if (isCurrent) {
                                Text(text = "▶ شغال", color = LinklyCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Manual Connection Form
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "الاتصال المباشر بعنوان IP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        label = { Text("عنوان IP للتلفاز") },
                        placeholder = { Text("192.168.1.50") },
                        modifier = Modifier
                            .weight(2f)
                            .testTag("manual_ip_input")
                    )

                    OutlinedTextField(
                        value = manualPortInput,
                        onValueChange = { manualPortInput = it },
                        label = { Text("المنفذ") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val port = manualPortInput.toIntOrNull() ?: 8888
                        viewModel.networkManager.connectToHost(manualIpInput.trim(), port)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connect_manual_button")
                ) {
                    Icon(imageVector = Icons.Default.Wifi, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "اتصال يدوي بالشاشة", fontWeight = FontWeight.Bold)
                }
            }
        }



        // Switch to TV Mode button (for testing on emulator or phone)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "تبديل وضع التطبيق", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "يمكنك تحويل هذا الجهاز إلى شاشة عرض تلفاز ذكية لتجربة واجهة التلفاز والتحكم بالريموت.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.setDeviceRole(DeviceRole.TV) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "التحويل إلى وضع شاشة التلفاز (TV Mode)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // About the App & Developer Credits Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "نبذة عن التطبيق",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = LinklyIndigo
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "تطبيق Linkly للربط التفاعلي الفوري بين أجهزة الهاتف وشاشات التلفاز الذكية لإدارة وعرض الطلبات ومشاركة النصوص والوسائط في بيئات العمل والمطاعم بسلاسة.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "المطور:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ahmad KN ; knatoura@gmail.com",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = LinklyCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}
