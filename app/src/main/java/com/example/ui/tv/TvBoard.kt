package com.example.ui.tv

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.LinklySettings
import com.example.model.OrderStatus
import com.example.model.QuickOrder
import com.example.ui.common.minutesSince
import com.example.ui.common.stationLabel
import com.example.ui.common.tr
import com.example.ui.components.TvFocusCard
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyRose
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private val ColNew = Color(0xFFF59E0B)
private val ColPrep = Color(0xFF3B82F6)
private val ColReady = Color(0xFF22C55E)
private val CardBg = Color(0xFF131B2E)

/** Kitchen screens (by category) that must prepare something in this order. */
fun stationsOf(order: QuickOrder, categoryStation: Map<String, String>): Set<String> =
    order.items.mapNotNull { categoryStation[it.categoryId]?.takeIf { s -> s.isNotBlank() } }.toSet()

fun isActiveOnBoard(o: QuickOrder): Boolean =
    !o.isHidden && o.status != OrderStatus.DELIVERED

/** Card colour from waiting time: green, then orange, then red (minutes chosen by the manager). */
fun timerColor(o: QuickOrder, settings: LinklySettings, now: Long): Color {
    if (o.status == OrderStatus.READY) return ColReady
    val m = minutesSince(o.timestamp, now)
    return when {
        m >= settings.lateMinutes -> Color(0xFFEF4444)
        m >= settings.warnMinutes -> Color(0xFFF97316)
        else -> Color(0xFF22C55E)
    }
}

/**
 * The main kitchen / hall board: three columns (new, preparing, ready),
 * a timer on each order, and a side strip that adds up what is still to prepare.
 *
 * When [station] is set (an extra kitchen or bar screen), only that station's items are shown,
 * and "done" marks only that station's part as ready.
 */
@Composable
fun TvOrdersBoard(
    orders: List<QuickOrder>,
    settings: LinklySettings,
    categoryStation: Map<String, String>,
    now: Long,
    station: String?,
    header: @Composable () -> Unit,
    onAdvance: (QuickOrder) -> Unit,
    onHide: (QuickOrder) -> Unit
) {
    val relevant = orders.filter { isActiveOnBoard(it) }.let { list ->
        if (station == null) list else list.filter { o ->
            station in stationsOf(o, categoryStation) && station !in o.readyStations &&
                (o.status == OrderStatus.PENDING || o.status == OrderStatus.PREPARING) && !o.isCancelled
        }
    }
    fun itemsFor(o: QuickOrder) = if (station == null) o.items else o.items.filter { categoryStation[it.categoryId] == station }

    val sortKey = compareByDescending<QuickOrder> { it.isUrgent }.thenBy { it.timestamp }
    val newOrders = relevant.filter { it.status == OrderStatus.PENDING || it.isCancelled || it.status == OrderStatus.CANCELLED }.sortedWith(sortKey)
    val prepOrders = relevant.filter { it.status == OrderStatus.PREPARING && !it.isCancelled }.sortedWith(sortKey)
    val readyOrders = relevant.filter { it.status == OrderStatus.READY && !it.isCancelled }.sortedBy { it.readyAt }

    // What is still to make, added up across all waiting orders.
    val toMake = remember(relevant) {
        val sums = LinkedHashMap<String, Pair<String, Int>>()
        relevant.filter { !it.isCall && !it.isCancelled && (it.status == OrderStatus.PENDING || it.status == OrderStatus.PREPARING) }
            .forEach { o ->
                itemsFor(o).forEach { item ->
                    val old = sums[item.name]
                    sums[item.name] = item.emoji to ((old?.second ?: 0) + item.quantity)
                }
            }
        sums.entries.sortedByDescending { it.value.second }.map { Triple(it.value.first, it.key, it.value.second) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
    ) {
        header()
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val cols = if (station == null) listOf(
                Triple(tr("جديد"), ColNew, newOrders),
                Triple(tr("قيد التحضير"), ColPrep, prepOrders),
                Triple(tr("جاهز"), ColReady, readyOrders)
            ) else listOf(
                Triple(tr("جديد"), ColNew, newOrders),
                Triple(tr("قيد التحضير"), ColPrep, prepOrders)
            )
            cols.forEach { (title, color, list) ->
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0D1322))
                        .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(color.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, color = color, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text("${list.size}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(list, key = { it.id }) { o ->
                            BoardCard(
                                order = o,
                                items = itemsFor(o),
                                settings = settings,
                                now = now,
                                stations = if (station == null) stationsOf(o, categoryStation) else emptySet(),
                                onAdvance = { onAdvance(o) },
                                onHide = { onHide(o) }
                            )
                        }
                    }
                }
            }
            // Side strip: totals to prepare
            Column(
                Modifier
                    .width(190.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D1322))
                    .border(1.dp, LinklyCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                Text(tr("المطلوب تحضيره"), color = LinklyCyan, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                if (toMake.isEmpty()) Text("—", color = Color.Gray, fontSize = 16.sp)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(toMake, key = { it.second }) { (emoji, name, qty) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$qty", color = Color(0xFFFDE047), fontWeight = FontWeight.Black, fontSize = 22.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("$emoji $name", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCard(
    order: QuickOrder,
    items: List<com.example.model.OrderItem>,
    settings: LinklySettings,
    now: Long,
    stations: Set<String>,
    onAdvance: () -> Unit,
    onHide: () -> Unit
) {
    val cancelled = order.isCancelled || order.status == OrderStatus.CANCELLED
    val tColor = timerColor(order, settings, now)
    val border = when {
        cancelled -> LinklyRose
        order.isUrgent -> Color(0xFFEF4444)
        order.isCall -> LinklyCyan
        else -> tColor
    }
    TvFocusCard(
        onClick = onAdvance,
        cornerRadius = 14.dp,
        backgroundColor = if (cancelled) Color(0xFF3B0707) else if (order.isUrgent) Color(0xFF3F0A0A) else CardBg,
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, border, RoundedCornerShape(14.dp))
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val big = when {
                    order.tableName.isNotBlank() -> "${tr("طاولة")} ${order.tableName}"
                    order.ticketNumber.isNotBlank() -> "#${order.ticketNumber.trimStart('#')}"
                    else -> tr("طلب")
                }
                Text(big, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.weight(1f), maxLines = 1)
                // Timer pill
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(tColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("${minutesSince(order.timestamp, now)} ${tr("د")}", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sub = buildString {
                    if (order.tableName.isNotBlank() && order.ticketNumber.isNotBlank()) append("#${order.ticketNumber.trimStart('#')}  •  ")
                    append("👤 ${order.senderName}")
                }
                Text(sub, color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                if (order.isUrgent) Tag(tr("عاجل"), Color(0xFFEF4444))
                if (cancelled) Tag(tr("ملغي"), LinklyRose)
                if (order.isCall) Tag(tr("نداء"), LinklyCyan)
            }
            Spacer(Modifier.height(6.dp))
            items.forEach { item ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 1.dp)) {
                    if (!order.isCall) {
                        Text("${item.quantity}×", color = Color(0xFFFDE047), fontWeight = FontWeight.Black, fontSize = 19.sp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Column {
                        Text(
                            "${item.emoji} ${item.name}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (order.isCall) 20.sp else 17.sp,
                            textDecoration = if (cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        )
                        if (item.note.isNotBlank()) {
                            Text("📝 ${item.note}", color = Color(0xFFFBBF24), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (stations.size > 1 || order.readyStations.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    stations.forEach { s ->
                        val done = s in order.readyStations
                        Tag((if (done) "✓ " else "… ") + stationLabel(s), if (done) ColReady else Color(0xFF475569))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val action = when {
                    cancelled -> tr("إخفاء")
                    order.isCall -> tr("تم ✓")
                    order.status == OrderStatus.PENDING -> tr("بدء التحضير ▶")
                    order.status == OrderStatus.PREPARING -> tr("جاهز ✓")
                    order.status == OrderStatus.READY -> tr("تم التسليم ✓")
                    else -> ""
                }
                Text(action, color = border, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (!cancelled) {
                    Text(
                        tr("إخفاء"),
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable(onClick = onHide)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

/** Customer pickup screen: big numbers only — "being prepared" and "ready". */
@Composable
fun TvPickupScreen(orders: List<QuickOrder>, now: Long) {
    val active = orders.filter { !it.isHidden && !it.isCall && !it.isCancelled && it.status != OrderStatus.DELIVERED && it.status != OrderStatus.CANCELLED }
    fun label(o: QuickOrder) = o.ticketNumber.trimStart('#').ifBlank { o.tableName.ifBlank { "•" } }
    val preparing = active.filter { it.status == OrderStatus.PENDING || it.status == OrderStatus.PREPARING }.sortedBy { it.timestamp }
    // A ready number stays up for 10 minutes, unless it is handed over first.
    val ready = active.filter { it.status == OrderStatus.READY && (it.readyAt == 0L || now - it.readyAt < 10 * 60_000L) }.sortedByDescending { it.readyAt }

    Row(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        listOf(
            Triple(tr("قيد التحضير"), ColPrep, preparing),
            Triple(tr("جاهز للاستلام"), ColReady, ready)
        ).forEach { (title, color, list) ->
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(color.copy(alpha = 0.10f))
                    .border(2.dp, color, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, color = color, fontSize = 40.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list.size) { i ->
                        val o = list[i]
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (color == ColReady) color else Color(0xFF1E293B))
                                .padding(vertical = 14.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label(o), color = if (color == ColReady) Color.Black else Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** QR code for the manager phone: linkly://manager?code=XXXXXX */
fun makeQrBitmap(content: String, size: Int = 512): Bitmap? = try {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) {
        pixels[y * size + x] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
} catch (_: Exception) {
    null
}

@Composable
fun ManagerAccessDialog(
    managerPassword: String,
    managerName: String,
    onReset: () -> Unit,
    onClearBlocks: () -> Unit,
    onDismiss: () -> Unit
) {
    val qr = remember(managerPassword) { makeQrBitmap("linkly://manager?code=$managerPassword")?.asImageBitmap() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF0F172A), border = BorderStroke(1.dp, Color(0xFF334155))) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("واجهة المدير", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                if (managerName.isBlank()) {
                    Text(
                        "من هاتف المدير: الإعدادات ← واجهة المدير ← امسح الرمز، أو أدخل كلمة السر. مرة واحدة فقط.",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (qr != null) {
                            Image(qr, contentDescription = "رمز QR للمدير", modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(12.dp)))
                        }
                        Text(managerPassword, color = LinklyAmber, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
                    }
                } else {
                    Text(
                        "المدير المسجّل: $managerName\nلا تُفتح واجهة المدير إلا من هذا الهاتف. يمكن نقلها من واجهة المدير نفسها، أو إعادة التعيين من هنا.",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onReset, colors = ButtonDefaults.buttonColors(containerColor = LinklyRose)) { Text("إعادة تعيين المدير") }
                    Button(onClick = onClearBlocks, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) { Text("إلغاء كل الحظر") }
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))) { Text("إغلاق") }
                }
            }
        }
    }
}

/** History of finished / hidden orders on the TV, with "back on screen". */
@Composable
fun TvHistoryDialog(orders: List<QuickOrder>, now: Long, onRestore: (QuickOrder) -> Unit, onDismiss: () -> Unit) {
    val history = orders.filter { it.isHidden || it.status == OrderStatus.DELIVERED || it.isCancelled }.sortedByDescending { it.timestamp }.take(200)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF0F172A), border = BorderStroke(1.dp, Color(0xFF334155)), modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.85f)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("السجل (${history.size})", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text("رجوع") }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history, key = { it.id }) { o ->
                        TvFocusCard(onClick = { onRestore(o) }, cornerRadius = 12.dp, backgroundColor = Color(0xFF1E293B), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val head = listOfNotNull(
                                        o.ticketNumber.takeIf { it.isNotBlank() }?.let { "#${it.trimStart('#')}" },
                                        o.tableName.takeIf { it.isNotBlank() }?.let { "طاولة $it" },
                                        "👤 ${o.senderName}",
                                        "منذ ${minutesSince(o.timestamp, now)} د"
                                    ).joinToString("  •  ")
                                    Text(head, color = if (o.isCancelled) Color(0xFFFCA5A5) else Color.White, fontWeight = FontWeight.Bold)
                                    Text(o.items.joinToString("   ") { "${it.emoji} ${it.quantity}× ${it.name}" }, color = Color(0xFFCBD5E1), fontSize = 13.sp)
                                }
                                Text("إعادة للشاشة", color = LinklyCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shown on the main TV when there are no orders for a while: images, the manager's text, or a clock. */
@Composable
fun TvIdleView(galleryImages: List<Bitmap>, textData: com.example.model.TextDisplayData, intervalSec: Int) {
    if (galleryImages.isNotEmpty()) {
        var index by remember { mutableStateOf(0) }
        LaunchedEffect(galleryImages.size, intervalSec) {
            while (true) {
                kotlinx.coroutines.delay(intervalSec.coerceAtLeast(3) * 1000L)
                index = (index + 1) % galleryImages.size
            }
        }
        TvImageView(galleryImages = galleryImages, currentIndex = index.coerceIn(0, galleryImages.size - 1), singleBgImage = null)
    } else {
        TvTextView(textData = textData)
    }
}

@Suppress("unused")
private val keepEmerald = LinklyEmerald
