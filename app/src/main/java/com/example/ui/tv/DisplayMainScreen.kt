package com.example.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.DeviceRole
import com.example.model.OrderStatus
import com.example.ui.common.LocalEnglish
import com.example.ui.common.stationLabel
import com.example.ui.common.tr
import com.example.ui.components.ConnectionBadge
import com.example.ui.components.TvFocusCard
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.TvDarkBackground
import com.example.viewmodel.LinklyViewModel
import kotlinx.coroutines.delay

/**
 * An extra screen connected to the main TV:
 * - "kitchen" / "bar": shows only that station's items, marks only its part ready;
 * - "pickup": big numbers for customers, "being prepared" and "ready".
 */
@Composable
fun DisplayMainScreen(viewModel: LinklyViewModel) {
    val english by viewModel.englishUi.collectAsState()
    CompositionLocalProvider(
        LocalEnglish provides english,
        LocalLayoutDirection provides if (english) LayoutDirection.Ltr else LayoutDirection.Rtl
    ) {
        DisplayContent(viewModel)
    }
}

@Composable
private fun DisplayContent(viewModel: LinklyViewModel) {
    val mode by viewModel.displayMode.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val config by viewModel.config.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedIp by viewModel.connectedDeviceIp.collectAsState()
    val isPaired by viewModel.isPaired.collectAsState()
    val pairingError by viewModel.pairingError.collectAsState()
    var showSetup by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    val categoryStation = remember(config) { config.categories.associate { it.id to it.station } }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    // Pickup screen: a short sound when a number becomes ready.
    val readyIds = orders.filter { it.status == OrderStatus.READY && !it.isCall }.map { it.id }.toSet()
    var lastReady by remember { mutableStateOf(readyIds) }
    LaunchedEffect(readyIds) {
        if (mode == "pickup" && (readyIds - lastReady).isNotEmpty()) viewModel.tvAudioPlayer.playOrderChime()
        lastReady = readyIds
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TvDarkBackground)
    ) {
        if (!isPaired || showSetup) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(tr("شاشة إضافية"), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                ConnectionBadge(status = connectionStatus, ipAddress = connectedIp)
                Spacer(Modifier.height(16.dp))
                Text(tr("ماذا تعرض هذه الشاشة؟"), color = Color(0xFFCBD5E1), fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("kitchen" to "🍳 " + stationLabel("kitchen"), "bar" to "🍹 " + stationLabel("bar"), "pickup" to "🔢 " + "شاشة الاستلام للزبائن")
                        .forEach { (id, label) ->
                            TvFocusCard(
                                onClick = { viewModel.setDisplayMode(id) },
                                cornerRadius = 14.dp,
                                backgroundColor = if (mode == id) LinklyCyan else Color(0xFF1E293B)
                            ) {
                                Text(tr(label), color = if (mode == id) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
                            }
                        }
                }
                if (connectionStatus == ConnectionStatus.CONNECTED && !isPaired) {
                    Spacer(Modifier.height(20.dp))
                    Text(tr("أدخل رمز الربط الظاهر أعلى شاشة التلفاز الرئيسية:"), color = Color(0xFFCBD5E1))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 6) pinInput = it.filter(Char::isDigit) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                        Button(onClick = { if (pinInput.isNotBlank()) viewModel.submitPairingPin(pinInput) }) { Text(tr("ربط")) }
                    }
                    pairingError?.let { Text(it, color = Color(0xFFF87171)) }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isPaired) {
                        Button(onClick = { showSetup = false }, colors = ButtonDefaults.buttonColors(containerColor = LinklyCyan)) { Text(tr("عرض"), color = Color.Black) }
                    }
                    Button(onClick = { viewModel.setDeviceRole(DeviceRole.PHONE) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                        Text(tr("رجوع لوضع الهاتف"))
                    }
                }
            }
        } else if (mode == "pickup") {
            TvPickupScreen(orders = orders, now = now)
            SetupButton(Modifier.align(Alignment.BottomEnd)) { showSetup = true }
        } else {
            TvOrdersBoard(
                orders = orders,
                settings = config.settings,
                categoryStation = categoryStation,
                now = now,
                station = mode,
                header = {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stationLabel(mode), color = LinklyAmber, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        ConnectionBadge(status = connectionStatus, ipAddress = connectedIp)
                        Spacer(Modifier.weight(1f))
                        TvFocusCard(onClick = { showSetup = true }, cornerRadius = 12.dp, backgroundColor = Color(0xFF1E293B)) {
                            Text(tr("الإعداد"), color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                },
                onAdvance = { viewModel.advanceOrderAtStation(it, mode) },
                onHide = { viewModel.advanceOrderAtStation(it.copy(status = OrderStatus.PREPARING), mode) }
            )
        }
    }
}

@Composable
private fun SetupButton(modifier: Modifier, onClick: () -> Unit) {
    TvFocusCard(onClick = onClick, cornerRadius = 12.dp, backgroundColor = Color(0x661E293B), modifier = modifier.padding(12.dp)) {
        Text("⚙", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Suppress("unused")
private val keepShape = RoundedCornerShape(0.dp)
