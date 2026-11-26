package com.example.ui.phone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.model.DeviceRole
import com.example.model.TvScreenMode
import com.example.ui.components.ConnectionBadge
import com.example.ui.components.WalkieTalkieFloatingButton
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneMainScreen(viewModel: LinklyViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedIp by viewModel.connectedDeviceIp.collectAsState()
    val activeTvScreen by viewModel.tvScreenMode.collectAsState()
    val isTransmitting by viewModel.isTransmitting.collectAsState()
    val isReceiving by viewModel.isReceiving.collectAsState()
    val amplitude by viewModel.currentAmplitude.collectAsState()

    if (selectedTab == 1) {
        // Fullscreen Floating Whiteboard Mode: Only drawing canvas, tools, and small exit button
        PhoneWhiteboardScreen(
            viewModel = viewModel,
            onExit = { selectedTab = 2 }
        )
    } else {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Linkly",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    color = LinklyIndigo
                                )
                                ConnectionBadge(
                                    status = connectionStatus,
                                    ipAddress = connectedIp
                                )
                            }
                        },
                        actions = {
                            // Wake Up TV Screen button
                            IconButton(
                                onClick = { viewModel.wakeUpTv() },
                                modifier = Modifier.testTag("action_wake_tv")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = "استدعاء التلفاز للواجهة",
                                    tint = LinklyCyan
                                )
                            }

                            // Switch to TV Mode
                            IconButton(
                                onClick = { viewModel.setDeviceRole(DeviceRole.TV) },
                                modifier = Modifier.testTag("action_switch_tv_mode")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = "وضع التلفاز",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // TV Active Screen Remote Switcher Row
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "واجهة التلفاز:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TvScreenSelectorChip(
                                    label = "نص",
                                    isSelected = activeTvScreen == TvScreenMode.TEXT,
                                    onClick = {
                                        viewModel.switchTvScreen(TvScreenMode.TEXT)
                                        selectedTab = 0
                                    }
                                )
                                TvScreenSelectorChip(
                                    label = "رسم وشرح",
                                    isSelected = activeTvScreen == TvScreenMode.WHITEBOARD,
                                    onClick = {
                                        viewModel.switchTvScreen(TvScreenMode.WHITEBOARD)
                                        selectedTab = 1
                                    }
                                )
                                TvScreenSelectorChip(
                                    label = "صورة",
                                    isSelected = activeTvScreen == TvScreenMode.IMAGE,
                                    onClick = {
                                        viewModel.switchTvScreen(TvScreenMode.IMAGE)
                                    }
                                )
                                TvScreenSelectorChip(
                                    label = "طلبات",
                                    isSelected = activeTvScreen == TvScreenMode.ORDERS,
                                    onClick = {
                                        viewModel.switchTvScreen(TvScreenMode.ORDERS)
                                        selectedTab = 2
                                    }
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.TextFields, contentDescription = "النص") },
                        label = { Text("النص", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = LinklyIndigo.copy(alpha = 0.15f))
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Create, contentDescription = "اللوحة") },
                        label = { Text("اللوحة", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = LinklyIndigo.copy(alpha = 0.15f))
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Restaurant, contentDescription = "الطلبات") },
                        label = { Text("الطلبات", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = LinklyIndigo.copy(alpha = 0.15f))
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Image, contentDescription = "الصور") },
                        label = { Text("الصور", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = LinklyIndigo.copy(alpha = 0.15f))
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "الربط") },
                        label = { Text("الربط", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = LinklyIndigo.copy(alpha = 0.15f))
                    )
                }
            },
            floatingActionButton = {
                WalkieTalkieFloatingButton(
                    isTransmitting = isTransmitting,
                    isReceiving = isReceiving,
                    currentAmplitude = amplitude,
                    onToggle = { viewModel.togglePushToTalk() }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(targetState = selectedTab, label = "tabCrossfade") { tab ->
                    when (tab) {
                        0 -> PhoneTextScreen(viewModel = viewModel)
                        2 -> PhoneOrdersScreen(viewModel = viewModel)
                        3 -> PhoneImagesScreen(viewModel = viewModel)
                        4 -> PhoneSettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvScreenSelectorChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) LinklyIndigo else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
