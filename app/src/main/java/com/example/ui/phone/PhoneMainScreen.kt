package com.example.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiningTable
import com.example.model.OrderStatus
import com.example.ui.common.LocalEnglish
import com.example.ui.common.tr
import com.example.ui.components.ConnectionBadge
import com.example.ui.manager.ManagerMainScreen
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.waiter.MyOrdersScreen
import com.example.ui.waiter.OrderComposer
import com.example.ui.waiter.QuickCallScreen
import com.example.ui.waiter.TablesScreen
import com.example.ui.waiter.WaiterSettingsScreen
import com.example.viewmodel.LinklyViewModel

/**
 * Waiter phone: tables, my orders, quick calls, settings. Nothing else.
 * The manager phone gets a button to the manager interface (display tools, menu, reports...).
 */
@Composable
fun PhoneMainScreen(viewModel: LinklyViewModel) {
    val english by viewModel.englishUi.collectAsState()
    var managerOpen by rememberSaveable { mutableStateOf(false) }

    if (managerOpen) {
        // The manager interface stays in Arabic, right to left.
        BackHandler { managerOpen = false }
        ManagerMainScreen(viewModel = viewModel, onExit = { managerOpen = false })
        return
    }

    CompositionLocalProvider(
        LocalEnglish provides english,
        LocalLayoutDirection provides if (english) LayoutDirection.Ltr else LayoutDirection.Rtl
    ) {
        WaiterHome(viewModel = viewModel, onOpenManager = { managerOpen = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaiterHome(viewModel: LinklyViewModel, onOpenManager: () -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // null = tables grid; NO_TABLE = an order without a table.
    var composingFor by remember { mutableStateOf<DiningTable?>(null) }
    var composing by rememberSaveable { mutableStateOf(false) }

    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedIp by viewModel.connectedDeviceIp.collectAsState()
    val isManager by viewModel.isManager.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val statusAlert by viewModel.phoneOrderStatusAlert.collectAsState()
    val readyMine = orders.count {
        it.senderDeviceId == viewModel.myDeviceId && it.status == OrderStatus.READY && !it.isHidden && !it.isCancelled
    }

    if (composing) {
        BackHandler { composing = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Linkly", fontWeight = FontWeight.Black, fontSize = 22.sp, color = LinklyIndigo)
                        Spacer(Modifier.width(8.dp))
                        ConnectionBadge(status = connectionStatus, ipAddress = connectedIp)
                    }
                },
                actions = {
                    if (isManager) {
                        IconButton(onClick = onOpenManager) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "واجهة المدير", tint = LinklyAmber)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; composing = false },
                    icon = { Icon(Icons.Default.TableRestaurant, contentDescription = null) },
                    label = { Text(tr("الطاولات"), fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(badge = { if (readyMine > 0) Badge(containerColor = LinklyEmerald) { Text("$readyMine") } }) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null)
                        }
                    },
                    label = { Text(tr("طلباتي"), fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                    label = { Text(tr("نداء"), fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(tr("الإعدادات"), fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            statusAlert?.let { alert ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = LinklyEmerald.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(alert, color = LinklyEmerald, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.dismissPhoneOrderStatusAlert() }) {
                            Icon(Icons.Default.Close, contentDescription = tr("إغلاق"), tint = LinklyEmerald)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> if (composing) {
                        OrderComposer(
                            viewModel = viewModel,
                            table = composingFor,
                            onBack = { composing = false },
                            onSent = { composing = false }
                        )
                    } else {
                        TablesScreen(viewModel = viewModel, onPickTable = {
                            composingFor = it
                            composing = true
                        })
                    }
                    1 -> MyOrdersScreen(viewModel = viewModel)
                    2 -> QuickCallScreen(viewModel = viewModel)
                    else -> WaiterSettingsScreen(viewModel = viewModel, onOpenManager = onOpenManager)
                }
            }
        }
    }
}
