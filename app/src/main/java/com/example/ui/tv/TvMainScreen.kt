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
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.ui.common.tr
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

    val config by viewModel.config.collectAsState()
    val slideshowInterval by viewModel.slideshowIntervalSec.collectAsState()
    val categoryStation = remember(config) { config.categories.associate { it.id to it.station } }
    var showManagerDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Ticking clock for timers and idle mode.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }
    // Idle (rest) mode: no orders for a while -> images / text / clock; back with the first order.
    val activeCount = orders.count { isActiveOnBoard(it) }
    var lastBusy by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeCount, orders.size) { lastBusy = System.currentTimeMillis() }
    val isIdle = config.settings.idleEnabled && activeCount == 0 &&
        now - lastBusy > config.settings.idleDelaySec * 1000L

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
                    if (isIdle) {
                        Box(Modifier.fillMaxSize().clickable { lastBusy = System.currentTimeMillis() }) {
                            TvIdleView(galleryImages = galleryImages, textData = textData, intervalSec = slideshowInterval)
                        }
                    } else {
                        TvOrdersBoard(
                            orders = orders,
                            settings = config.settings,
                            categoryStation = categoryStation,
                            now = now,
                            station = null,
                            header = {
                                TvBoardHeader(
                                    pin = tvPairingPin,
                                    phones = connectedClientsCount,
                                    activeCount = orders.count { isActiveOnBoard(it) },
                                    hiddenCount = orders.count { it.isHidden },
                                    now = now,
                                    onManager = { showManagerDialog = true },
                                    onHistory = { showHistoryDialog = true }
                                )
                            },
                            onAdvance = { viewModel.advanceOrder(it) },
                            onHide = { viewModel.hideOrder(it.id) }
                        )
                    }
                }
            }
        }

        if (showManagerDialog) {
            ManagerAccessDialog(
                managerPassword = managerPassword,
                managerName = managerName,
                onReset = { viewModel.resetTvManager() },
                onClearBlocks = { viewModel.clearAllBlockedDevices() },
                onDismiss = { showManagerDialog = false }
            )
        }
        if (showHistoryDialog) {
            TvHistoryDialog(
                orders = orders,
                now = now,
                onRestore = { viewModel.restoreOrder(it.id) },
                onDismiss = { showHistoryDialog = false }
            )
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

@Composable
fun TvBoardHeader(
    pin: String,
    phones: Int,
    activeCount: Int,
    hiddenCount: Int,
    now: Long,
    onManager: () -> Unit,
    onHistory: () -> Unit
) {
    val time = remember(now / 30_000L) { SimpleDateFormat("h:mm a", Locale("ar")).format(Date(now)) }
    val date = remember(now / 3_600_000L) { SimpleDateFormat("EEEE d MMMM", Locale("ar")).format(Date(now)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Linkly", color = LinklyCyan, fontWeight = FontWeight.Black, fontSize = 20.sp)
        HeaderPill(icon = Icons.Default.Key, text = "${tr("رمز الربط")}: $pin", color = LinklyAmber)
        HeaderPill(icon = Icons.Default.Group, text = "$phones", color = LinklyEmerald)
        Spacer(Modifier.weight(1f))
        HeaderPill(icon = Icons.Default.AccessTime, text = "$time  •  $date", color = LinklyCyan)
        Spacer(Modifier.weight(1f))
        Text("$activeCount ${tr("طلب")}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        TvFocusCard(onClick = onHistory, cornerRadius = 12.dp, backgroundColor = Color(0xFF1E293B)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = LinklyCyan, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (hiddenCount > 0) "${tr("السجل")} ($hiddenCount)" else tr("السجل"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        TvFocusCard(onClick = onManager, cornerRadius = 12.dp, backgroundColor = Color(0xFF1E293B)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = LinklyAmber, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("المدير"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeaderPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color(0xFFE2E8F0), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
