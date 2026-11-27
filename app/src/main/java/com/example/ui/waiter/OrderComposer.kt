package com.example.ui.waiter

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiningTable
import com.example.model.MenuItem
import com.example.model.OrderItem
import com.example.model.QuickOrder
import com.example.ui.common.formatPrice
import com.example.ui.common.rememberB64Image
import com.example.ui.common.tr
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel

/**
 * Building an order: sections on top, big items, + / − quantity, a note per item,
 * an "urgent" switch and one big send button.
 */
@Composable
fun OrderComposer(
    viewModel: LinklyViewModel,
    table: DiningTable?,
    onBack: (() -> Unit)?,
    onSent: () -> Unit
) {
    val config by viewModel.config.collectAsState()
    val cartKey = table?.id ?: ""
    val cart: List<OrderItem> = viewModel.draftCarts[cartKey] ?: emptyList()
    fun setCart(list: List<OrderItem>) {
        if (list.isEmpty()) viewModel.draftCarts.remove(cartKey) else viewModel.draftCarts[cartKey] = list
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var isUrgent by remember(cartKey) { mutableStateOf(false) }
    var cartExpanded by remember { mutableStateOf(true) }
    var noteEditIndex by remember { mutableStateOf<Int?>(null) }
    var customName by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }

    val categories = config.categories
    val visibleItems = config.items.filter { selectedCategory == null || it.categoryId == selectedCategory }

    fun addItem(m: MenuItem) {
        val idx = cart.indexOfFirst { it.menuItemId == m.id && it.note.isBlank() }
        val next = cart.toMutableList()
        if (idx >= 0) next[idx] = next[idx].copy(quantity = next[idx].quantity + 1)
        else next.add(OrderItem(name = m.name, quantity = 1, emoji = m.emoji, menuItemId = m.id, categoryId = m.categoryId))
        setCart(next)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: table + back + urgent
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                }
            }
            Text(
                text = if (table != null) "${tr("طاولة")} ${table.name}" else tr("طلب جديد"),
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isUrgent) LinklyRose.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { isUrgent = !isUrgent }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text("🚨 " + tr("عاجل"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isUrgent) LinklyRose else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = isUrgent,
                    onCheckedChange = { isUrgent = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = LinklyRose)
                )
            }
        }

        // Sections
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text(tr("الكل"), fontWeight = FontWeight.Bold) }
                )
            }
            items(categories, key = { it.id }) { cat ->
                FilterChip(
                    selected = selectedCategory == cat.id,
                    onClick = { selectedCategory = cat.id },
                    label = { Text("${cat.emoji} ${cat.name}", fontWeight = FontWeight.Bold) }
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = { showCustom = true },
                    label = { Text("✏️ " + tr("صنف خاص")) }
                )
            }
        }

        // Items grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(visibleItems, key = { it.id }) { m ->
                val qty = cart.filter { it.menuItemId == m.id }.sumOf { it.quantity }
                MenuItemTile(item = m, quantity = qty, currency = config.settings.currency, onClick = { if (m.available) addItem(m) })
            }
            if (visibleItems.isEmpty()) {
                item {
                    Text(
                        tr("لا توجد أصناف في هذا القسم. يضيفها المدير من واجهة المدير."),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Cart
        if (cart.isNotEmpty()) {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cartExpanded = !cartExpanded }
                    ) {
                        Text(
                            text = "${tr("الطلب")}: ${cart.sumOf { it.quantity }} ${tr("صنف")}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { setCart(emptyList()) }) {
                            Text(tr("مسح الكل"), color = LinklyRose)
                        }
                        Icon(if (cartExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, contentDescription = null)
                    }
                    if (cartExpanded) {
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            itemsIndexed(cart) { index, item ->
                                CartRow(
                                    item = item,
                                    onMinus = {
                                        val next = cart.toMutableList()
                                        if (item.quantity > 1) next[index] = item.copy(quantity = item.quantity - 1) else next.removeAt(index)
                                        setCart(next)
                                    },
                                    onPlus = {
                                        val next = cart.toMutableList()
                                        next[index] = item.copy(quantity = item.quantity + 1)
                                        setCart(next)
                                    },
                                    onNote = { noteEditIndex = index }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val summary = cart.joinToString("، ") { if (it.quantity > 1) "${it.quantity} ${it.name}" else it.name }
                            val speechTable = if (table != null) "طاولة ${table.name}: " else ""
                            viewModel.sendQuickOrder(
                                QuickOrder(
                                    title = summary,
                                    items = cart,
                                    isUrgent = isUrgent,
                                    tableId = table?.id ?: "",
                                    tableName = table?.name ?: "",
                                    ttsSpeechText = (if (isUrgent) "طلب عاجل، " else "") + speechTable + summary
                                )
                            )
                            setCart(emptyList())
                            isUrgent = false
                            onSent()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isUrgent) LinklyRose else LinklyEmerald),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isUrgent) tr("إرسال طلب عاجل") else tr("إرسال"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }

    // Note editor
    noteEditIndex?.let { idx ->
        val item = cart.getOrNull(idx)
        if (item == null) {
            noteEditIndex = null
        } else {
            var noteInput by remember(idx) { mutableStateOf(item.note) }
            val quickNotes = listOf("بدون سكر", "سكر قليل", "حار", "بدون ثلج", "سفري", "بدون بصل")
            AlertDialog(
                onDismissRequest = { noteEditIndex = null },
                title = { Text("${tr("ملاحظة على")} ${item.name}") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it.take(60) },
                            placeholder = { Text(tr("مثال: بدون سكر")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(quickNotes) { n ->
                                FilterChip(selected = noteInput == n, onClick = { noteInput = n }, label = { Text(tr(n)) })
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val next = cart.toMutableList()
                        next[idx] = item.copy(note = noteInput.trim())
                        setCart(next)
                        noteEditIndex = null
                    }) { Text(tr("حفظ")) }
                },
                dismissButton = {
                    TextButton(onClick = { noteEditIndex = null }) { Text(tr("إلغاء")) }
                }
            )
        }
    }

    // Free item not on the menu
    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text(tr("صنف خاص")) },
            text = {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it.take(40) },
                    placeholder = { Text(tr("اكتب اسم الطلب")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (customName.isNotBlank()) {
                        setCart(cart + OrderItem(name = customName.trim(), quantity = 1, emoji = "⭐"))
                    }
                    customName = ""
                    showCustom = false
                }) { Text(tr("إضافة")) }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text(tr("إلغاء")) } }
        )
    }
}

@Composable
private fun MenuItemTile(item: MenuItem, quantity: Int, currency: String, onClick: () -> Unit) {
    val image = rememberB64Image(item.imageB64)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quantity > 0) LinklyIndigo.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (quantity > 0) 2.dp else 0.dp, if (quantity > 0) LinklyIndigo else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(enabled = item.available, onClick = onClick)
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text(item.emoji, fontSize = 40.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (item.available) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
                val price = formatPrice(item.price, currency)
                if (!item.available) {
                    Text(tr("نفد"), color = LinklyRose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else if (price.isNotBlank()) {
                    Text(price, color = Color.Gray, fontSize = 11.sp)
                }
            }
            if (quantity > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(LinklyIndigo)
                ) {
                    Text("$quantity", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CartRow(item: OrderItem, onMinus: () -> Unit, onPlus: () -> Unit, onNote: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(item.emoji, fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.note.isNotBlank()) {
                Text("📝 ${item.note}", fontSize = 12.sp, color = LinklyIndigo)
            }
        }
        IconButton(onClick = onNote) {
            Icon(Icons.Default.EditNote, contentDescription = tr("ملاحظة"), tint = if (item.note.isNotBlank()) LinklyIndigo else Color.Gray)
        }
        RoundIconButton(onClick = onMinus) { Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp)) }
        Text("${item.quantity}", fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 10.dp))
        RoundIconButton(onClick = onPlus) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) { content() }
}
