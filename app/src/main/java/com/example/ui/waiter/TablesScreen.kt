package com.example.ui.waiter

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiningTable
import com.example.model.OrderStatus
import com.example.model.QuickOrder
import com.example.ui.common.tr
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TableState { EMPTY, WAITING, READY }

fun tableState(tableId: String, orders: List<QuickOrder>, today: String): TableState {
    val active = orders.filter {
        it.tableId == tableId && it.dateStr == today && !it.isHidden && !it.isCancelled && !it.isCall &&
            it.status != OrderStatus.DELIVERED && it.status != OrderStatus.CANCELLED
    }
    return when {
        active.any { it.status == OrderStatus.READY } -> TableState.READY
        active.isNotEmpty() -> TableState.WAITING
        else -> TableState.EMPTY
    }
}

/** Waiter home: the tables, coloured by state. Tap a table to order for it. */
@Composable
fun TablesScreen(viewModel: LinklyViewModel, onPickTable: (DiningTable?) -> Unit) {
    val config by viewModel.config.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val zones = config.tables.map { it.zone.ifBlank { "" } }.distinct()
    var zone by remember { mutableStateOf<String?>(null) }
    val shown = config.tables.filter { zone == null || it.zone == zone }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Legend(tr("فارغة"), Color(0xFF94A3B8))
            Legend(tr("بانتظار التحضير"), LinklyAmber)
            Legend(tr("جاهز"), LinklyEmerald)
        }
        if (zones.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = zone == null, onClick = { zone = null }, label = { Text(tr("الكل")) }) }
                items(zones) { z -> FilterChip(selected = zone == z, onClick = { zone = z }, label = { Text(z.ifBlank { "—" }) }) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 92.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(shown, key = { it.id }) { t ->
                val state = tableState(t.id, orders, today)
                val hasDraft = !viewModel.draftCarts[t.id].isNullOrEmpty()
                TableTile(table = t, state = state, hasDraft = hasDraft, onClick = { onPickTable(t) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(
                    onClick = { onPickTable(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) { Text(tr("طلب بدون طاولة (سفري / كاونتر)"), fontWeight = FontWeight.Bold) }
            }
            if (config.tables.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(tr("لا توجد طاولات. يضيفها المدير من واجهة المدير ← القائمة ← الطاولات."), color = Color.Gray, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(12.dp)
                .height(12.dp)
                .border(6.dp, color, RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun TableTile(table: DiningTable, state: TableState, hasDraft: Boolean, onClick: () -> Unit) {
    val color = when (state) {
        TableState.EMPTY -> Color(0xFF94A3B8)
        TableState.WAITING -> LinklyAmber
        TableState.READY -> LinklyEmerald
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = if (state == TableState.EMPTY) 0.10f else 0.22f)),
        modifier = Modifier
            .aspectRatio(1f)
            .border(2.dp, color, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(table.name, fontWeight = FontWeight.Black, fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = when (state) {
                    TableState.EMPTY -> tr("فارغة")
                    TableState.WAITING -> tr("بانتظار التحضير")
                    TableState.READY -> tr("جاهز")
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
            if (hasDraft) Text("✏️", fontSize = 12.sp)
        }
    }
}

/** Quick calls: ready-made buttons, no typing. */
@Composable
fun QuickCallScreen(viewModel: LinklyViewModel) {
    val config by viewModel.config.collectAsState()
    var table by remember { mutableStateOf<DiningTable?>(null) }
    var sentLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sentLabel) {
        if (sentLabel != null) {
            delay(2500)
            sentLabel = null
        }
    }
    val helpAr = "أحتاج مساعدة"
    val billAr = "الحساب"
    val cleanAr = "تنظيف طاولة"
    val calls = listOf(
        Triple(helpAr, "🙋", LinklyRose),
        Triple(billAr, "💳", LinklyIndigo),
        Triple(cleanAr, "🧹", LinklyCyan)
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(tr("نداء سريع للمطبخ أو الصالة"), fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(tr("اختر الطاولة (اختياري):"), fontSize = 13.sp, color = Color.Gray)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(selected = table == null, onClick = { table = null }, label = { Text(tr("بدون")) }) }
            items(config.tables, key = { it.id }) { t ->
                FilterChip(selected = table?.id == t.id, onClick = { table = t }, label = { Text(t.name) })
            }
        }
        calls.forEach { (label, emoji, color) ->
            val shown = tr(label)
            Button(
                onClick = {
                    viewModel.sendCall(label, emoji, table)
                    sentLabel = label
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
            ) {
                Text("$emoji  $shown", fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        sentLabel?.let {
            Text("✓ ${tr("تم إرسال النداء")}", color = LinklyEmerald, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
