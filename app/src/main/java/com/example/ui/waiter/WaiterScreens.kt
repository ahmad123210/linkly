package com.example.ui.waiter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.DeviceRole
import com.example.model.OrderStatus
import com.example.model.QuickOrder
import com.example.ui.common.LocalEnglish
import com.example.ui.common.minutesSince
import com.example.ui.common.tr
import com.example.ui.components.ConnectionBadge
import com.example.ui.phone.ManagerAccessCard
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun statusColor(o: QuickOrder): Color = when {
    o.isCancelled || o.status == OrderStatus.CANCELLED -> LinklyRose
    o.status == OrderStatus.READY -> LinklyEmerald
    o.status == OrderStatus.DELIVERED -> Color(0xFF64748B)
    o.status == OrderStatus.PREPARING -> Color(0xFF3B82F6)
    else -> LinklyAmber
}

@Composable
fun statusText(o: QuickOrder): String = when {
    o.isOfflinePending -> tr("لم يُرسل بعد")
    o.isCancelled || o.status == OrderStatus.CANCELLED -> tr("ملغي")
    o.status == OrderStatus.READY -> tr("جاهز")
    o.status == OrderStatus.DELIVERED -> tr("تم التسليم")
    o.status == OrderStatus.PREPARING -> tr("قيد التحضير")
    else -> tr("جديد")
}

/** "My orders": what this waiter sent today, with live status. */
@Composable
fun MyOrdersScreen(viewModel: LinklyViewModel) {
    val orders by viewModel.orders.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000L)
            now = System.currentTimeMillis()
        }
    }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val mine = orders
        .filter { it.senderDeviceId == viewModel.myDeviceId && it.dateStr == today }
        .sortedWith(compareBy<QuickOrder> { it.status == OrderStatus.DELIVERED || it.isCancelled }.thenByDescending { it.timestamp })
    var toCancel by remember { mutableStateOf<QuickOrder?>(null) }

    if (mine.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("لم ترسل أي طلب اليوم"), color = Color.Gray, fontSize = 15.sp)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(mine, key = { it.id }) { o ->
            val color = statusColor(o)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val head = buildString {
                            if (o.tableName.isNotBlank()) append("${tr("طاولة")} ${o.tableName}")
                            if (o.ticketNumber.isNotBlank()) {
                                if (isNotEmpty()) append("  •  ")
                                append("#${o.ticketNumber.trimStart('#')}")
                            }
                            if (isEmpty()) append(tr("طلب"))
                        }
                        Text(head, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(color)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(statusText(o), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    o.items.forEach { item ->
                        Text(
                            text = "${item.emoji} ${item.quantity}× ${item.name}" + if (item.note.isNotBlank()) "  📝 ${item.note}" else "",
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${tr("منذ")} ${minutesSince(o.timestamp, now)} ${tr("د")}" + if (o.isUrgent) "  •  🚨" else "",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (o.status == OrderStatus.READY && !o.isCancelled) {
                            Button(
                                onClick = { viewModel.updateOrderStatus(o.id, OrderStatus.DELIVERED, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = LinklyEmerald),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text(tr("استلمته"), fontWeight = FontWeight.Bold) }
                        }
                        if (o.status == OrderStatus.PENDING && !o.isCancelled) {
                            TextButton(onClick = { toCancel = o }) { Text(tr("إلغاء الطلب"), color = LinklyRose) }
                        }
                    }
                }
            }
        }
    }

    toCancel?.let { o ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            title = { Text(tr("إلغاء الطلب؟")) },
            text = { Text(tr("سيظهر الطلب ملغياً على شاشة التلفاز.")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelOrder(o.id)
                        toCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyRose)
                ) { Text(tr("تأكيد الإلغاء")) }
            },
            dismissButton = { TextButton(onClick = { toCancel = null }) { Text(tr("تراجع")) } }
        )
    }
}

/** Waiter settings: name, connection, the manager entry, and a small "advanced" part. */
@Composable
fun WaiterSettingsScreen(viewModel: LinklyViewModel, onOpenManager: () -> Unit) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedIp by viewModel.connectedDeviceIp.collectAsState()
    val isPaired by viewModel.isPaired.collectAsState()
    val pairingError by viewModel.pairingError.collectAsState()
    val waiterName by viewModel.waiterName.collectAsState()
    val english by viewModel.englishUi.collectAsState()
    var nameInput by remember(waiterName) { mutableStateOf(waiterName) }
    var pinInput by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var manualIp by remember(connectedIp) { mutableStateOf(connectedIp.ifBlank { "192.168.1." }) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsCard(title = tr("الاتصال")) {
            ConnectionBadge(status = connectionStatus, ipAddress = connectedIp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isPaired) tr("مرتبط بالشاشة ✓") else tr("غير مرتبط بالشاشة بعد"),
                color = if (isPaired) LinklyEmerald else Color.Gray,
                fontWeight = FontWeight.Bold
            )
            if (connectionStatus == ConnectionStatus.CONNECTED && !isPaired) {
                Spacer(Modifier.height(8.dp))
                Text(tr("أدخل رمز الربط الظاهر أعلى شاشة التلفاز:"), fontSize = 13.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) pinInput = it.filter(Char::isDigit) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { if (pinInput.isNotBlank()) viewModel.submitPairingPin(pinInput) }) { Text(tr("ربط")) }
                }
                pairingError?.let { Text(it, color = LinklyRose, fontSize = 12.sp) }
            }
        }

        SettingsCard(title = tr("اسمك")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(30) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { if (nameInput.isNotBlank()) viewModel.setWaiterName(nameInput) }) { Text(tr("حفظ")) }
            }
        }

        SettingsCard(title = tr("اللغة")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val arSelected = !english
                Button(
                    onClick = { viewModel.setEnglishUi(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (arSelected) LinklyIndigo else MaterialTheme.colorScheme.surfaceVariant)
                ) { Text("العربية", color = if (arSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(
                    onClick = { viewModel.setEnglishUi(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (!arSelected) LinklyIndigo else MaterialTheme.colorScheme.surfaceVariant)
                ) { Text("English", color = if (!arSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        ManagerAccessCard(viewModel = viewModel, onOpenManager = onOpenManager)

        OutlinedButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showAdvanced) tr("إخفاء الخيارات المتقدمة") else tr("خيارات متقدمة"))
        }
        if (showAdvanced) {
            SettingsCard(title = tr("اتصال يدوي بعنوان IP")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it.trim() },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { viewModel.networkManager.connectToHost(manualIp) }) { Text(tr("اتصال")) }
                }
            }
            SettingsCard(title = tr("وضع الجهاز")) {
                Text(tr("حوّل هذا الجهاز إلى شاشة: شاشة الطلبات الرئيسية، أو شاشة إضافية للمطبخ أو البار، أو شاشة استلام للزبائن."), fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.setDeviceRole(DeviceRole.TV) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("شاشة الطلبات الرئيسية (التلفاز)"))
                }
                OutlinedButton(onClick = { viewModel.setDeviceRole(DeviceRole.DISPLAY) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("شاشة إضافية (مطبخ / بار / استلام)"))
                }
            }
        }
        Text(
            text = "Linkly • Ahmad KN",
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
