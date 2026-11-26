package com.example.ui.phone

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OrderItem
import com.example.model.OrderStatus
import com.example.model.QuickOrder
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import com.example.model.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneOrdersScreen(viewModel: LinklyViewModel) {
    val orders by viewModel.orders.collectAsState()
    val activeUnservedNumber by viewModel.activeUnservedOrderNumber.collectAsState()
    val statusAlert by viewModel.phoneOrderStatusAlert.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isPaired by viewModel.isPaired.collectAsState()
    val pairingError by viewModel.pairingError.collectAsState()
    val waiterName by viewModel.waiterName.collectAsState()
    val offlinePendingOrders by viewModel.offlinePendingOrders.collectAsState()
    val nextTicketNumber by viewModel.nextTicketNumber.collectAsState()

    var nextTicketNum by remember { mutableIntStateOf(1) }
    var ticketNumberInput by remember { mutableStateOf("$nextTicketNum") }
    var isUrgentOrder by remember { mutableStateOf(false) }
    var customItemName by remember { mutableStateOf("") }

    LaunchedEffect(nextTicketNumber) {
        ticketNumberInput = nextTicketNumber.toString()
    }

    // PIN pairing input state
    var pinInput by remember { mutableStateOf("") }
    var showWaiterNameDialog by remember { mutableStateOf(false) }
    var waiterNameEditInput by remember(waiterName) { mutableStateOf(waiterName) }

    // Dialog state for confirming order cancellation
    var orderToCancel by remember { mutableStateOf<QuickOrder?>(null) }
    var showArchivedDialog by remember { mutableStateOf(false) }
    var showDailyHistoryDialog by remember { mutableStateOf(false) }

    val currentCart = remember { mutableStateListOf<OrderItem>() }

    val presetCatalog = remember {
        mutableStateListOf(
            OrderItem("شاي بالنعناع", 1, "🍵"),
            OrderItem("قهوة سريعة", 1, "☕"),
            OrderItem("قارورة ماء", 1, "💧"),
            OrderItem("وجبة طعام", 1, "🍽️"),
            OrderItem("عصير طازج", 1, "🍹"),
            OrderItem("طبق حلوى", 1, "🍰"),
            OrderItem("تنظيف الطاولة", 1, "🧹"),
            OrderItem("الحساب / الفاتورة", 1, "💳")
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // PIN Pairing Card (Appears if connected to TV WiFi but not yet paired)
        if (connectionStatus == ConnectionStatus.CONNECTED && !isPaired) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B).copy(alpha = 0.15f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LinklyAmber)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = LinklyAmber)
                            Text(text = "مطلوب رمز اقتران الشاشة (PIN)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "أدخل رمز الـ PIN المكون من 4 أرقام المعروض في أعلى شاشة التلفاز لإرسال الطلبات:",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { if (it.length <= 6) pinInput = it.filter { ch -> ch.isDigit() } },
                                placeholder = { Text("مثال: 1234") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (pinInput.isNotBlank()) {
                                        viewModel.submitPairingPin(pinInput)
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
                    }
                }
            }
        }

        // Offline Mode Banner / Pending Sync Status
        if (connectionStatus != ConnectionStatus.CONNECTED || !isPaired) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF78350F).copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LinklyAmber.copy(alpha = 0.5f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.WifiOff, contentDescription = null, tint = LinklyAmber, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "وضع عدم الاتصال بالشبكة (أوفلاين)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = LinklyAmber
                            )
                            Text(
                                text = if (offlinePendingOrders.isNotEmpty())
                                    "يمكنك تسجيل الطلبات بشكل طبيعي. يوجد ${offlinePendingOrders.size} طلبات بانتظار المزامنة التلقائية فور عودة الاتصال."
                                else
                                    "يمكنك تسجيل الطلبات مؤقتاً وسيتم حفظها ومزامنتها تلقائياً للشاشة.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        } else if (offlinePendingOrders.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LinklyEmerald.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LinklyEmerald)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = LinklyEmerald)
                            Text(
                                text = "يوجد ${offlinePendingOrders.size} طلبات غير متصلة بانتظار المزامنة",
                                color = LinklyEmerald,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { viewModel.syncPendingOfflineOrders() },
                            colors = ButtonDefaults.buttonColors(containerColor = LinklyEmerald),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("مزامنة الآن", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Real-Time Order Status Alert from TV
        if (statusAlert != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LinklyEmerald.copy(alpha = 0.15f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LinklyEmerald)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = LinklyEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = statusAlert ?: "",
                                color = LinklyEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        IconButton(
                            onClick = { viewModel.dismissPhoneOrderStatusAlert() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = LinklyEmerald
                            )
                        }
                    }
                }
            }
        }

        // Active Queue Header & Quick Setting on Phone
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B).copy(alpha = 0.12f)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LinklyIndigo.copy(alpha = 0.4f))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "آخر دور قيد الانتظار بالشاشة:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (activeUnservedNumber.isNotBlank()) "#$activeUnservedNumber" else "—",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = LinklyIndigo
                        )
                    }

                    // Button to sync/set next ticket
                    Button(
                        onClick = {
                            viewModel.setActiveUnservedOrderNumber(ticketNumberInput.trim())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("مزامنة للشاشة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Ticket Number & Sound Option Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Queue/Ticket Number Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ticketNumberInput,
                            onValueChange = { ticketNumberInput = it },
                            label = { Text("رقم الدور") },
                            placeholder = { Text("مثال: 12") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("order_ticket_input")
                        )

                        // Quick increment button (+1)
                        Button(
                            onClick = {
                                val current = ticketNumberInput.toIntOrNull() ?: nextTicketNum
                                val next = current + 1
                                ticketNumberInput = next.toString()
                                nextTicketNum = next
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "+1 دور", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Urgent / High Priority Toggle (Red styling)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isUrgentOrder) LinklyRose.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = if (isUrgentOrder) 2.dp else 1.dp,
                                color = if (isUrgentOrder) LinklyRose else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { isUrgentOrder = !isUrgentOrder }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "🚨", fontSize = 18.sp)
                            Column {
                                Text(
                                    text = "طلب عاجل (أولوية قصوى)",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUrgentOrder) LinklyRose else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "يُميز بلون أحمر بارز في شاشة التلفاز",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Switch(
                            checked = isUrgentOrder,
                            onCheckedChange = { isUrgentOrder = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = LinklyRose
                            )
                        )
                    }
                }
            }
        }

        // Quick Catalog Selection
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "اختر من قائمة الطلبات السريعة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presetCatalog) { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val existing = currentCart.indexOfFirst { it.name == preset.name }
                                        if (existing >= 0) {
                                            val old = currentCart[existing]
                                            currentCart[existing] = old.copy(quantity = old.quantity + 1)
                                        } else {
                                            currentCart.add(preset.copy(quantity = 1))
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = preset.emoji, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = preset.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Custom item adder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customItemName,
                            onValueChange = { customItemName = it },
                            placeholder = { Text("أو اكتب طلباً خاصاً...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (customItemName.isNotBlank()) {
                                    val newItem = OrderItem(customItemName.trim(), 1, "⭐")
                                    currentCart.add(newItem)
                                    presetCatalog.add(newItem)
                                    customItemName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo)
                        ) {
                            Text("إضافة")
                        }
                    }
                }
            }
        }

        // Active Cart / Selected Items for Current Request
        if (currentCart.isNotEmpty()) {
            item {
                val cardBorderColor = if (isUrgentOrder) LinklyRose else LinklyIndigo
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUrgentOrder) LinklyRose.copy(alpha = 0.08f) else LinklyIndigo.copy(alpha = 0.08f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(cardBorderColor.copy(alpha = 0.5f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isUrgentOrder) {
                                    Text(text = "🚨", fontSize = 16.sp)
                                }
                                Text(
                                    text = if (ticketNumberInput.isNotBlank()) "الطلب الحالي (دور #${ticketNumberInput}):" else "الطلب الحالي:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isUrgentOrder) LinklyRose else LinklyIndigo
                                )
                            }
                            Text(
                                text = "مسح الكل",
                                color = LinklyRose,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { currentCart.clear() }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        currentCart.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = item.emoji, fontSize = 18.sp)
                                    Text(text = item.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                if (item.quantity > 1) {
                                                    currentCart[index] = item.copy(quantity = item.quantity - 1)
                                                } else {
                                                    currentCart.removeAt(index)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(12.dp))
                                    }

                                    Text(text = "${item.quantity}", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                currentCart[index] = item.copy(quantity = item.quantity + 1)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Send Order Action
                        val sendBtnColor = if (isUrgentOrder) LinklyRose else LinklyEmerald
                        Button(
                            onClick = {
                                val itemsSummary = currentCart.joinToString("، ") { if (it.quantity > 1) "${it.quantity} ${it.name}" else it.name }
                                val speech = if (isUrgentOrder) "طلب عاجل: $itemsSummary" else itemsSummary

                                val order = QuickOrder(
                                    title = itemsSummary,
                                    ticketNumber = ticketNumberInput.trim(),
                                    isUrgent = isUrgentOrder,
                                    items = currentCart.toList(),
                                    ttsSpeechText = speech
                                )

                                viewModel.sendQuickOrder(order)

                                // Prepare next ticket number automatically
                                val currentNum = ticketNumberInput.toIntOrNull()
                                if (currentNum != null) {
                                    nextTicketNum = currentNum + 1
                                    ticketNumberInput = "$nextTicketNum"
                                }

                                currentCart.clear()
                                isUrgentOrder = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = sendBtnColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_order_button")
                        ) {
                            Icon(imageVector = Icons.Default.Tv, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isUrgentOrder) "إرسال طلب عاجل لشاشة التلفاز 🚨" else "إرسال الطلب لشاشة التلفاز 🚀",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Active Orders List on Phone (Ultra-Compact Cards with Swipe Left to Hide, Swipe Right to Cancel)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الطلبات المسجلة (${orders.filter { !it.isHidden }.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val archivedCount = orders.count { it.isHidden || it.isCancelled }
                    OutlinedButton(
                        onClick = { showArchivedDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (archivedCount > 0) "المخفية ($archivedCount)" else "المخفية والملغاة",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = { showDailyHistoryDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "السجل",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val visibleOrders = orders.filter { !it.isHidden }

        if (visibleOrders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد طلبات معروضة حالياً\nاختر من القائمة أعلاه لإرسال طلب للشاشة",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(visibleOrders, key = { it.id }) { order ->
                PhoneOrderSwipeableCard(
                    order = order,
                    onHide = { viewModel.hideOrder(order.id) },
                    onCancelRequest = { orderToCancel = order }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }

    // Cancellation Confirmation Dialog
    if (orderToCancel != null) {
        val target = orderToCancel!!
        AlertDialog(
            onDismissRequest = { orderToCancel = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    tint = LinklyRose,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "تأكيد إلغاء الطلب",
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في إلغاء طلب دور #${target.ticketNumber.ifBlank { target.id }}؟ سيظهر الطلب كملغي باللون الأحمر على الشاشة مع تنبيه صوتي.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelOrder(target.id)
                        orderToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyRose)
                ) {
                    Text("تأكيد الإلغاء", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { orderToCancel = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("تراجع", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Hidden & Cancelled Orders Management Dialog
    if (showArchivedDialog) {
        val archivedList = orders.filter { it.isHidden || it.isCancelled }
        AlertDialog(
            onDismissRequest = { showArchivedDialog = false },
            title = {
                Text(
                    text = "الطلبات المخفية والملغاة (${archivedList.size})",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (archivedList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا توجد طلبات مخفية أو ملغاة حالياً", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(archivedList, key = { it.id }) { order ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "دور #${order.ticketNumber.ifBlank { order.id.takeLast(4) }}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (order.isCancelled) "ملغي ❌" else "مخفي 👁️",
                                            color = if (order.isCancelled) LinklyRose else Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = order.items.joinToString(" • ") { "${it.name} ×${it.quantity}" },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { viewModel.restoreOrder(order.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = LinklyEmerald),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("استرجاع", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { viewModel.deleteOrderPermanently(order.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = LinklyRose),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("حذف نهائي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showArchivedDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("إغلاق", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Daily History Archives Dialog
    if (showDailyHistoryDialog) {
        val dailyHistory by viewModel.dailyHistory.collectAsState()
        AlertDialog(
            onDismissRequest = { showDailyHistoryDialog = false },
            title = {
                Text(
                    text = "سجل الطلبات اليومية",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (dailyHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا يوجد سجل لأيام سابقة بعد", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dailyHistory, key = { it.dateStr }) { day ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = day.formattedDateAr.ifBlank { day.dateStr },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${day.orders.size} طلبات",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.restoreDailyOrders(day.dateStr)
                                            showDailyHistoryDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("استعادة اليوم", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDailyHistoryDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("إغلاق", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Waiter Identity Selection Dialog
    if (showWaiterNameDialog) {
        AlertDialog(
            onDismissRequest = { showWaiterNameDialog = false },
            title = { Text(text = "تحديد اسم / رقم النادل لهذا الهاتف") },
            text = {
                Column {
                    Text(
                        text = "اختر من الخيارات السريعة أو اكتب اسماً مخصصاً يظهر مع كل طلب ترسلُه للشاشة:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(listOf("نادل 1", "نادل 2", "نادل 3", "نادل 4", "كابتن صالة")) { presetName ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (waiterNameEditInput == presetName) LinklyIndigo else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { waiterNameEditInput = presetName }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = presetName,
                                    color = if (waiterNameEditInput == presetName) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = waiterNameEditInput,
                        onValueChange = { waiterNameEditInput = it },
                        placeholder = { Text("أو اكتب اسماً هنا...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (waiterNameEditInput.isNotBlank()) {
                            viewModel.setWaiterName(waiterNameEditInput.trim())
                        }
                        showWaiterNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo)
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showWaiterNameDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }
}

/**
 * Ultra-Compact Phone Order Card with Swipe-To-Dismiss Gestures:
 * - Swipe to Left: Hide order (إخفاء الطلب)
 * - Swipe to Right: Cancel order with confirmation (إلغاء الطلب)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneOrderSwipeableCard(
    order: QuickOrder,
    onHide: () -> Unit,
    onCancelRequest: () -> Unit
) {
    val isCancelled = order.isCancelled || order.status == OrderStatus.CANCELLED
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swiped Right -> Cancel order
                    onCancelRequest()
                    false // Don't auto dismiss until confirmed
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swiped Left -> Hide order
                    onHide()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFFDC2626) // Red for Cancel
                SwipeToDismissBoxValue.EndToStart -> Color(0xFF475569) // Slate for Hide
                else -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(text = "إلغاء الطلب", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "إخفاء", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        content = {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isCancelled -> Color(0xFF7F1D1D).copy(alpha = 0.15f)
                        order.isUrgent -> LinklyRose.copy(alpha = 0.08f)
                        order.status == OrderStatus.READY -> LinklyEmerald.copy(alpha = 0.08f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                border = when {
                    isCancelled -> CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFDC2626)))
                    order.isUrgent -> CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LinklyRose))
                    order.status == OrderStatus.READY -> CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LinklyEmerald))
                    else -> CardDefaults.outlinedCardBorder()
                },
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_order_card_${order.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Ticket Number + Waiter + Items in a clean row
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isCancelled) {
                                Text(text = "❌", fontSize = 13.sp)
                            } else if (order.isUrgent) {
                                Text(text = "🚨", fontSize = 13.sp)
                            }
                            Text(
                                text = if (order.ticketNumber.isNotBlank()) "دور #${order.ticketNumber}" else "#${order.id}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                                color = when {
                                    isCancelled -> Color(0xFFDC2626)
                                    order.isUrgent -> LinklyRose
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )

                            // Status Chip
                            val statusColor = when {
                                isCancelled -> Color(0xFFDC2626)
                                order.status == OrderStatus.READY -> LinklyEmerald
                                else -> LinklyAmber
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(statusColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isCancelled) "ملغي" else order.status.titleAr,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Waiter Identity Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(LinklyIndigo.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "👤 ${order.senderName}",
                                    color = LinklyIndigo,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Offline Pending Badge
                            if (order.isOfflinePending) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF78350F).copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "⏳ غير متزامن",
                                        color = LinklyAmber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        // Compact Items Summary
                        val itemsText = order.items.joinToString("، ") { "${it.emoji} ${it.quantity}x ${it.name}" }
                        Text(
                            text = itemsText.ifBlank { order.title },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Right: Quick Action Buttons (Cancel / Hide)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!isCancelled) {
                            IconButton(
                                onClick = onCancelRequest,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "إلغاء الطلب",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onHide,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "إخفاء من القائمة",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}
