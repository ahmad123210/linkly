package com.example.ui.tv

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.DeviceRole
import com.example.model.OrderStatus
import com.example.model.QuickOrder
import com.example.model.TextDisplayData
import com.example.model.TvScreenMode
import com.example.service.LinklyBackgroundService
import com.example.ui.components.TvFocusCard
import com.example.ui.components.WhiteboardCanvas
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.ui.theme.TvDarkBackground
import com.example.viewmodel.LinklyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TvMainScreen(viewModel: LinklyViewModel) {
    val screenMode by viewModel.tvScreenMode.collectAsState()
    val textData by viewModel.textData.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val currentStroke by viewModel.currentStroke.collectAsState()
    val pointerState by viewModel.pointerState.collectAsState()
    val bgImage by viewModel.canvasBackgroundImage.collectAsState()
    val galleryImages by viewModel.galleryImages.collectAsState()
    val galleryIndex by viewModel.currentGalleryIndex.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val activeUnservedNumber by viewModel.activeUnservedOrderNumber.collectAsState()
    val showArchivedOrders by viewModel.showArchivedOrders.collectAsState()
    val isTransmitting by viewModel.isTransmitting.collectAsState()
    val isReceiving by viewModel.isReceiving.collectAsState()
    val audioState by viewModel.audioPlaybackState.collectAsState()
    val tvPairingPin by viewModel.tvPairingPin.collectAsState()
    val connectedClientsCount by viewModel.connectedClientsCount.collectAsState()
    val newOrderAlert by viewModel.tvNewOrderAlert.collectAsState()
    val managerPassword by viewModel.tvManagerPassword.collectAsState()
    val managerName by viewModel.tvManagerName.collectAsState()

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("linkly_tv_prefs", android.content.Context.MODE_PRIVATE) }
    var showPermissionsDialog by remember {
        val alreadyPrompted = prefs.getBoolean("bg_permissions_prompted_once", false)
        val hasOverlay = LinklyBackgroundService.isOverlayPermissionGranted(context)
        val isBatteryExempt = LinklyBackgroundService.isBatteryOptimizationIgnored(context)
        val needsPermission = !hasOverlay || !isBatteryExempt
        mutableStateOf(needsPermission && !alreadyPrompted)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDarkBackground)
    ) {
        // Main Screen Viewports
        Crossfade(targetState = screenMode, label = "tvScreenCrossfade") { mode ->
            when (mode) {
                TvScreenMode.TEXT -> {
                    TvTextView(textData = textData)
                }
                TvScreenMode.WHITEBOARD -> {
                    WhiteboardCanvas(
                        strokes = strokes,
                        currentStroke = currentStroke,
                        pointerState = pointerState,
                        backgroundImage = bgImage,
                        isInteractive = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                TvScreenMode.IMAGE -> {
                    TvImageView(
                        galleryImages = galleryImages,
                        currentIndex = galleryIndex,
                        singleBgImage = bgImage
                    )
                }
                TvScreenMode.ORDERS -> {
                    TvOrdersView(
                        orders = orders,
                        activeUnservedNumber = activeUnservedNumber,
                        showArchived = showArchivedOrders,
                        tvPairingPin = tvPairingPin,
                        connectedClientsCount = connectedClientsCount,
                        newOrderAlert = newOrderAlert,
                        onUpdateActiveNumber = { viewModel.setActiveUnservedOrderNumber(it) },
                        onUpdateTvPin = { viewModel.setTvPairingPin(it) },
                        onDismissNewOrderAlert = { viewModel.dismissTvNewOrderAlert() },
                        onToggleArchived = { viewModel.toggleArchivedOrders() },
                        onStatusChange = { orderId, newStatus, hideOrder ->
                            viewModel.updateOrderStatus(orderId, newStatus, hideOrder)
                        },
                        managerPassword = managerPassword,
                        managerName = managerName,
                        onResetManager = { viewModel.resetTvManager() },
                        onClearBlocks = { viewModel.clearAllBlockedDevices() }
                    )
                }
            }
        }

        // Discrete Background Audio Badge (if playing audio in background)
        if (audioState.isPlaying && screenMode != TvScreenMode.IMAGE && screenMode != TvScreenMode.WHITEBOARD) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC0F172A))
                    .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = LinklyCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = audioState.currentTrackTitle,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }



        // Voice transmission audio overlay indicator: small circular mic icon on side
        AnimatedVisibility(
            visible = isReceiving,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xE610B981))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "الميكروفون قيد التشغيل",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Background & Permissions Guidance Dialog (shown on first start if ungranted)
        if (showPermissionsDialog) {
            TvBackgroundPermissionsDialog(
                onDismiss = {
                    prefs.edit().putBoolean("bg_permissions_prompted_once", true).apply()
                    showPermissionsDialog = false
                }
            )
        }
    }
}

/**
 * TV Text View with 12-Hour Digital Clock display and compact discrete scroll control.
 */
@Composable
fun TvTextView(textData: TextDisplayData) {
    val textColor = try {
        Color(android.graphics.Color.parseColor(textData.textColorHex))
    } catch (_: Exception) {
        Color.White
    }
    val bgColor = try {
        Color(android.graphics.Color.parseColor(textData.bgColorHex))
    } catch (_: Exception) {
        TvDarkBackground
    }

    val textAlign = when (textData.alignment) {
        "START" -> TextAlign.Start
        "END" -> TextAlign.End
        else -> TextAlign.Center
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // 12-Hour Live Digital Clock without seconds
    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTimeString = SimpleDateFormat("h:mm a", Locale("ar")).format(now)
            currentDateString = SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(now)
            delay(1000L)
        }
    }

    if (textData.text.isBlank()) {
        // Clock centered in large font without borders, like sent text
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentTimeString,
                    color = textColor,
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
                if (currentDateString.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = currentDateString,
                        color = Color(0xFF94A3B8),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        // Text is present: Clock moves to top in small font in a single row, sent text is centered
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            // Top single-row compact clock
            if (currentTimeString.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = LinklyCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$currentTimeString   •   $currentDateString",
                        color = Color(0xFF94A3B8),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Main Scrollable Content Container for sent text
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, end = 64.dp, top = 56.dp, bottom = 80.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = textData.text,
                    color = textColor,
                    fontSize = textData.fontSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = textAlign,
                    lineHeight = (textData.fontSizeSp * 1.35f).sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Compact & Discrete Side Scroll Buttons (Minimized as requested)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x990F172A))
                    .border(1.dp, Color(0x2238BDF8), RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Small Scroll Up Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .clickable {
                            coroutineScope.launch {
                                scrollState.animateScrollTo((scrollState.value - 300).coerceAtLeast(0))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "تمرير لأعلى",
                        tint = LinklyCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Small Scroll Down Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .clickable {
                            coroutineScope.launch {
                                scrollState.animateScrollTo((scrollState.value + 300).coerceAtMost(scrollState.maxValue))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "تمرير لأسفل",
                        tint = LinklyCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * TV Multi-Image View with 100% Full Screen display without ANY buttons!
 * Auto-slide and multi-image transitions controlled directly from phone.
 */
@Composable
fun TvImageView(
    galleryImages: List<Bitmap>,
    currentIndex: Int,
    singleBgImage: Bitmap?
) {
    val activeBitmap = if (galleryImages.isNotEmpty()) {
        galleryImages.getOrNull(currentIndex)
    } else {
        singleBgImage
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (activeBitmap != null) {
            Crossfade(targetState = activeBitmap, label = "imageCrossfade") { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "صورة معروضة على التلفاز",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "في انتظار اختيار ومشاركة صور من الهاتف...",
                    color = Color(0xFF94A3B8),
                    fontSize = 22.sp
                )
            }
        }
    }
}

@Composable
fun TvOrdersView(
    orders: List<QuickOrder>,
    activeUnservedNumber: String,
    showArchived: Boolean,
    tvPairingPin: String,
    connectedClientsCount: Int,
    newOrderAlert: QuickOrder?,
    onUpdateActiveNumber: (String) -> Unit,
    onUpdateTvPin: (String) -> Unit,
    onDismissNewOrderAlert: () -> Unit,
    onToggleArchived: () -> Unit,
    onStatusChange: (orderId: String, newStatus: OrderStatus, hideOrder: Boolean) -> Unit,
    managerPassword: String = "",
    managerName: String = "",
    onResetManager: () -> Unit = {},
    onClearBlocks: () -> Unit = {}
) {
    var showNumberEditDialog by remember { mutableStateOf(false) }
    var showManagerDialog by remember { mutableStateOf(false) }
    var numberEditInput by remember(activeUnservedNumber) { mutableStateOf(activeUnservedNumber) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Live Clock for Orders screen (no seconds, 12-hour format)
    var liveTimeString by remember { mutableStateOf("") }
    var liveDateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            liveTimeString = SimpleDateFormat("h:mm a", Locale("ar")).format(now)
            liveDateString = SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(now)
            delay(1000L)
        }
    }

    // Filter active (unhidden) orders sorted: Urgent first, then oldest order by ticket number/timestamp first
    val displayedOrders = orders.filter { !it.isHidden }.sortedWith(
        compareByDescending<QuickOrder> { it.isUrgent }
            .thenBy { it.isCancelled }
            .thenBy { it.ticketNumber.trimStart('#').toIntOrNull() ?: Int.MAX_VALUE }
            .thenBy { it.timestamp }
    )

    val historyOrders = orders.filter { it.isHidden || it.status == OrderStatus.DELIVERED || it.isCancelled }
        .sortedByDescending { it.timestamp }

    val hiddenCount = orders.count { it.isHidden }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 4.dp, start = 14.dp, end = 14.dp, bottom = 10.dp)
    ) {
        // Top Header (compact, moved to very top edge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Active / Last Unserved Order Number: "رقم الطلب:" and value without #
                TvFocusCard(
                    onClick = {
                        numberEditInput = activeUnservedNumber.trimStart('#')
                        showNumberEditDialog = true
                    },
                    cornerRadius = 12.dp,
                    backgroundColor = Color(0xFF1E1B4B),
                    modifier = Modifier
                        .border(1.dp, LinklyCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .testTag("active_order_number_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "رقم الطلب:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val displayNum = activeUnservedNumber.trimStart('#').ifBlank { "—" }
                        Text(
                            text = displayNum,
                            color = LinklyCyan,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل الرقم",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Connected Waitstaff Badge: Only shown if connectedClientsCount > 1, displaying only the number
                if (connectedClientsCount > 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF064E3B))
                            .border(1.dp, LinklyEmerald.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = LinklyEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$connectedClientsCount",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Center: Live Clock in single row
            if (liveTimeString.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = LinklyCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$liveTimeString   •   $liveDateString",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right side: "السجل" Button & Orders Count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Manager button: shows the manager password for the manager phone
                TvFocusCard(
                    onClick = { showManagerDialog = true },
                    cornerRadius = 12.dp,
                    backgroundColor = Color(0xFF1E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = LinklyAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "المدير",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // "السجل" Button (opens large floating window)
                TvFocusCard(
                    onClick = { showHistoryDialog = true },
                    cornerRadius = 12.dp,
                    backgroundColor = Color(0xFF1E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = LinklyCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (hiddenCount > 0) "السجل ($hiddenCount)" else "السجل",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${displayedOrders.size} طلبات",
                        color = LinklyCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (displayedOrders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "لا توجد طلبات معروضة حالياً",
                        color = Color(0xFF64748B),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (hiddenCount > 0) "تم إنجاز وإخفاء $hiddenCount طلبات. اضغط 'السجل' لعرضها واستعادتها" else "أرسل طلباً من هواتف النُّدل ليظهر هنا تلقائياً",
                        color = Color(0xFF475569),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Square Grid for Order Cards
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedOrders, key = { it.id }) { order ->
                    TvOrderCompactCard(
                        order = order,
                        onStatusChange = { newStatus, hideOrder ->
                            onStatusChange(order.id, newStatus, hideOrder)
                        }
                    )
                }
            }
        }

        // Dialog for editing active order number directly on TV
        if (showNumberEditDialog) {
            AlertDialog(
                onDismissRequest = { showNumberEditDialog = false },
                title = { Text(text = "تعديل رقم الطلب") },
                text = {
                    Column {
                        Text(
                            text = "أدخل رقم الدور المطلوب عرضه في أعلى الشاشة:",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = numberEditInput,
                            onValueChange = { numberEditInput = it },
                            placeholder = { Text("مثال: 15") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onUpdateActiveNumber(numberEditInput.trim().trimStart('#'))
                            showNumberEditDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo)
                    ) {
                        Text("حفظ")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showNumberEditDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("إلغاء", color = Color.Gray)
                    }
                }
            )
        }

        if (showManagerDialog) {
            AlertDialog(
                onDismissRequest = { showManagerDialog = false },
                title = { Text(text = "واجهة المدير") },
                text = {
                    Column {
                        Text(
                            text = if (managerName.isBlank())
                                "لا يوجد مدير مسجّل بعد. من هاتف المدير: الضبط ← واجهة المدير، ثم أدخل كلمة السر التالية مرة واحدة فقط:"
                            else
                                "المدير المسجّل: $managerName\nلا يمكن فتح واجهة المدير إلا من هذا الهاتف. لنقلها إلى هاتف آخر أعد تعيين المدير.",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        if (managerName.isBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = managerPassword,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Black,
                                color = LinklyAmber,
                                letterSpacing = 6.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onClearBlocks,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إلغاء حظر كل الأجهزة")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onResetManager,
                        colors = ButtonDefaults.buttonColors(containerColor = LinklyRose)
                    ) {
                        Text("إعادة تعيين المدير وكلمة السر")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showManagerDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("إغلاق", color = Color.Gray)
                    }
                }
            )
        }

        // Large Floating Dialog for History ("السجل") with Back Button
        if (showHistoryDialog) {
            Dialog(onDismissRequest = { showHistoryDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.85f)
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        // Dialog Header with Title and "رجوع" Back Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = LinklyCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "سجل الطلبات والمنجزات (${historyOrders.size})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            Button(
                                onClick = { showHistoryDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "رجوع", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (historyOrders.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لا توجد طلبات سابقة في السجل حتى الآن",
                                    color = Color(0xFF64748B),
                                    fontSize = 16.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(historyOrders, key = { it.id }) { hOrder ->
                                    val isCanc = hOrder.isCancelled || hOrder.status == OrderStatus.CANCELLED
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCanc) Color(0xFF2D1515) else Color(0xFF1E293B)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    val ticketDisplay = if (hOrder.ticketNumber.isNotBlank()) {
                                                        "دور ${hOrder.ticketNumber.trimStart('#')}"
                                                    } else {
                                                        hOrder.title.ifBlank { "طلب" }
                                                    }
                                                    Text(
                                                        text = ticketDisplay,
                                                        color = if (isCanc) Color(0xFFFCA5A5) else Color.White,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 16.sp
                                                    )
                                                    Text(
                                                        text = "•   👤 ${hOrder.senderName}",
                                                        color = LinklyCyan,
                                                        fontSize = 12.sp
                                                    )
                                                    val elapsed = ((System.currentTimeMillis() - hOrder.timestamp) / 60000).coerceAtLeast(0)
                                                    Text(
                                                        text = "•   منذ $elapsed د",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.sp
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = hOrder.items.joinToString("   ") { "${it.emoji} ${it.quantity}× ${it.name}" },
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            // Action to unhide / restore
                                            Button(
                                                onClick = {
                                                    onStatusChange(hOrder.id, OrderStatus.PENDING, false)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(text = "استعادة للشاشة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    }
}

/**
 * Compact Square Order Card for TV
 * Styled in vibrant royal blue matching the user's reference photo.
 * Features:
 * - Header: Status pill on left, big centered order number (no #) with elapsed timer beneath, waiter badge pill on right.
 * - Divider line.
 * - Order items: Prominent highlighted quantities (e.g. 3 ×), name, and emoji.
 * - Bottom row: "إخفاء" pill on the left, "جاهز؟ اضغط" action on the right.
 */
@Composable
fun TvOrderCompactCard(
    order: QuickOrder,
    onStatusChange: (OrderStatus, Boolean) -> Unit
) {
    val isUrgent = order.isUrgent
    val isCancelled = order.isCancelled || order.status == OrderStatus.CANCELLED

    // Solid Royal Blue background matching user photo (or dark red/crimson if urgent/cancelled)
    val cardBgColor = when {
        isCancelled -> Color(0xFF3B0707)
        isUrgent -> Color(0xFF450A0A)
        else -> Color(0xFF1D4ED8) // Deep vibrant blue from the reference photo
    }

    val cardBorderColor = when {
        isCancelled -> Color(0xFFDC2626)
        isUrgent -> Color(0xFFEF4444)
        order.status == OrderStatus.READY -> Color(0xFF86EFAC)
        else -> Color(0x3360A5FA)
    }

    TvFocusCard(
        onClick = {
            if (!isCancelled) {
                when (order.status) {
                    OrderStatus.PENDING -> onStatusChange(OrderStatus.READY, false)
                    OrderStatus.READY -> onStatusChange(OrderStatus.DELIVERED, true) // Auto-hide when delivered
                    OrderStatus.DELIVERED -> onStatusChange(OrderStatus.PENDING, false)
                    OrderStatus.CANCELLED -> {}
                }
            } else {
                onStatusChange(OrderStatus.CANCELLED, true)
            }
        },
        cornerRadius = 16.dp,
        backgroundColor = cardBgColor,
        modifier = Modifier
            .testTag("tv_order_card_${order.id}")
            .border(if (isUrgent || isCancelled) 2.dp else 1.dp, cardBorderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: Status Pill (Left), Giant Ticket Number & Timer (Center), Waiter Pill (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Pill
                val statusBg = when {
                    isCancelled -> Color(0xFFDC2626)
                    isUrgent -> LinklyRose
                    order.status == OrderStatus.READY -> Color(0xFF22C55E)
                    else -> Color(0xFFEAB308) // Bright yellow from reference photo
                }
                val statusTextColor = when {
                    isCancelled || isUrgent -> Color.White
                    order.status == OrderStatus.READY -> Color(0xFF052E16)
                    else -> Color(0xFF451A03)
                }
                val statusLabel = when {
                    isCancelled -> "ملغي"
                    isUrgent -> "عاجل"
                    order.status == OrderStatus.READY -> "جاهز للاستلام"
                    order.status == OrderStatus.DELIVERED -> "تم التسليم"
                    else -> "جاري التحضير"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusTextColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }

                // Center: Big Ticket Number without # and Order Timer beneath
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val ticketNumberOnly = if (order.ticketNumber.isNotBlank()) {
                        order.ticketNumber.trimStart('#')
                    } else {
                        order.title.trimStart('#').ifBlank { "1" }
                    }
                    Text(
                        text = ticketNumberOnly,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center
                    )

                    // Order Timer (Elapsed Time in minutes, e.g. "منذ 8 د")
                    val elapsedMinutes = ((System.currentTimeMillis() - order.timestamp) / 60000).coerceAtLeast(0)
                    val timerLabel = if (elapsedMinutes == 0L) "الآن" else "منذ $elapsedMinutes د"
                    Text(
                        text = timerLabel,
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Waiter Pill (e.g. 👤 نادل 1)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF3B82F6))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "👤 ${order.senderName}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Thin Divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x33FFFFFF))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // List of items: Highlighted quantities prominently displayed in bold yellow
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                order.items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        // Prominently highlighted quantity (e.g. 3 ×)
                        Text(
                            text = "${item.quantity} ×",
                            color = Color(0xFFFDE047),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.emoji,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom action row: "إخفاء" pill on the left, "جاهز؟ اضغط" action on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "إخفاء" Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x4DFFFFFF))
                        .clickable { onStatusChange(order.status, true) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "إخفاء",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Action Label on the right
                val actionText = when (order.status) {
                    OrderStatus.PENDING -> "جاهز؟ اضغط"
                    OrderStatus.READY -> "✓ تسليم وإخفاء"
                    OrderStatus.DELIVERED -> "مكتمل"
                    OrderStatus.CANCELLED -> "ملغي"
                }

                Text(
                    text = actionText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        when (order.status) {
                            OrderStatus.PENDING -> onStatusChange(OrderStatus.READY, false)
                            OrderStatus.READY -> onStatusChange(OrderStatus.DELIVERED, true)
                            OrderStatus.DELIVERED -> onStatusChange(OrderStatus.PENDING, false)
                            OrderStatus.CANCELLED -> onStatusChange(OrderStatus.CANCELLED, true)
                        }
                    }
                )
            }
        }
    }
}

/**
 * TV Background & Permissions Guidance Dialog
 * Simple, concise direct permission checklist with activation buttons.
 */
@Composable
fun TvBackgroundPermissionsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hasOverlay by remember { mutableStateOf(LinklyBackgroundService.isOverlayPermissionGranted(context)) }
    var isBatteryExempt by remember { mutableStateOf(LinklyBackgroundService.isBatteryOptimizationIgnored(context)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = LinklyCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "أذونات العمل بالخلفية والاستدعاء",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                // 1. Overlay
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "1. الظهور فوق التطبيقات (Overlay)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (hasOverlay) "✓ الإذن مفعل" else "مطلوب لاستدعاء الشاشة",
                                color = if (hasOverlay) LinklyEmerald else LinklyAmber,
                                fontSize = 11.sp
                            )
                        }

                        TvFocusCard(
                            onClick = {
                                LinklyBackgroundService.openOverlayPermissionSettings(context)
                                hasOverlay = LinklyBackgroundService.isOverlayPermissionGranted(context)
                            },
                            cornerRadius = 10.dp,
                            backgroundColor = if (hasOverlay) Color(0xFF064E3B) else LinklyIndigo
                        ) {
                            Text(
                                text = if (hasOverlay) "مفعل" else "تفعيل",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // 2. Battery Optimization Exemption
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "2. إعفاء البطارية وتوفير الطاقة",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isBatteryExempt) "✓ الإعفاء مفعل" else "مطلوب لمنع إيقاف الخادم",
                                color = if (isBatteryExempt) LinklyEmerald else LinklyAmber,
                                fontSize = 11.sp
                            )
                        }

                        TvFocusCard(
                            onClick = {
                                LinklyBackgroundService.openBatteryOptimizationSettings(context)
                                isBatteryExempt = LinklyBackgroundService.isBatteryOptimizationIgnored(context)
                            },
                            cornerRadius = 10.dp,
                            backgroundColor = if (isBatteryExempt) Color(0xFF064E3B) else Color(0xFF0369A1)
                        ) {
                            Text(
                                text = if (isBatteryExempt) "مفعل" else "تفعيل",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "تم", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
