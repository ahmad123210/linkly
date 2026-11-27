package com.example.ui.manager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ConnectionBadge
import com.example.ui.phone.ManagerDevicesTab
import com.example.ui.phone.PhoneWhiteboardScreen
import com.example.ui.theme.LinklyAmber
import com.example.viewmodel.LinklyViewModel

/**
 * The manager interface (one phone only): today's numbers, menu & tables, the TV display tools,
 * devices, and more (settings, history & reports, backup).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerMainScreen(viewModel: LinklyViewModel, onExit: () -> Unit) {
    val isManager by viewModel.isManager.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedIp by viewModel.connectedDeviceIp.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var whiteboardOpen by rememberSaveable { mutableStateOf(false) }

    // Lost the manager role (released, transferred or reset on the TV): back to the waiter screens.
    LaunchedEffect(isManager) { if (!isManager) onExit() }

    if (whiteboardOpen) {
        PhoneWhiteboardScreen(viewModel = viewModel, onExit = { whiteboardOpen = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع لواجهة النادل")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("واجهة المدير", fontWeight = FontWeight.Black, fontSize = 19.sp, color = LinklyAmber)
                        Spacer(Modifier.width(8.dp))
                        ConnectionBadge(status = connectionStatus, ipAddress = connectedIp)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val tabs = listOf(
                    "اليوم" to Icons.Default.Insights,
                    "القائمة" to Icons.Default.RestaurantMenu,
                    "الشاشة" to Icons.Default.Tv,
                    "الأجهزة" to Icons.Default.Devices,
                    "المزيد" to Icons.Default.MoreHoriz
                )
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> DashboardTab(viewModel)
                    1 -> MenuAndTablesTab(viewModel)
                    2 -> DisplayControlTab(viewModel, onOpenWhiteboard = { whiteboardOpen = true })
                    3 -> ManagerDevicesTab(viewModel, onLeftManager = onExit)
                    else -> MoreTab(viewModel)
                }
            }
        }
    }
}
