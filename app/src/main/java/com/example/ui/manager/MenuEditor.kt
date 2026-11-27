package com.example.ui.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Codec
import com.example.model.DiningTable
import com.example.model.MenuCategory
import com.example.model.MenuItem
import com.example.ui.common.bitmapToThumbB64
import com.example.ui.common.formatPrice
import com.example.ui.common.rememberB64Image
import com.example.ui.common.stationLabel
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel

@Composable
fun MenuAndTablesTab(viewModel: LinklyViewModel) {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = sub) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("الأصناف والأقسام") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("الطاولات") })
        }
        if (sub == 0) MenuEditor(viewModel) else TablesEditor(viewModel)
    }
}

// ---------------------------------------------------------------------------------- menu

@Composable
private fun MenuEditor(viewModel: LinklyViewModel) {
    val config by viewModel.config.collectAsState()
    val managerMessage by viewModel.managerMessage.collectAsState()
    var selectedCat by remember { mutableStateOf<String?>(null) }
    var editCategory by remember { mutableStateOf<MenuCategory?>(null) }
    var editItem by remember { mutableStateOf<MenuItem?>(null) }
    var deleteCategory by remember { mutableStateOf<MenuCategory?>(null) }

    val cats = config.categories
    val currentCat = cats.firstOrNull { it.id == selectedCat } ?: cats.firstOrNull()
    val items = config.items.filter { it.categoryId == currentCat?.id }
    val uncategorized = config.items.filter { item -> cats.none { it.id == item.categoryId } }

    fun saveItems(newItems: List<MenuItem>) = viewModel.saveMenu(config.categories, newItems)

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("التغييرات تُحفظ على التلفاز وتصل لكل الهواتف فوراً.", fontSize = 12.sp, color = Color.Gray)
            managerMessage?.let { Text(it, fontSize = 12.sp, color = LinklyAmber) }
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cats, key = { it.id }) { c ->
                    FilterChip(
                        selected = c.id == currentCat?.id,
                        onClick = { selectedCat = c.id },
                        label = { Text("${c.emoji} ${c.name}") }
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { editCategory = MenuCategory(id = Codec.newId(), name = "") },
                        label = { Text("+ قسم جديد") }
                    )
                }
            }
        }
        if (currentCat != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${currentCat.emoji} ${currentCat.name}", fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("يُحضّر في: ${stationLabel(currentCat.station)}", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editCategory = currentCat }) { Icon(Icons.Default.Edit, contentDescription = "تعديل القسم") }
                        IconButton(onClick = { deleteCategory = currentCat }) { Icon(Icons.Default.Delete, contentDescription = "حذف القسم", tint = LinklyRose) }
                    }
                }
            }
            items(items, key = { it.id }) { m ->
                ItemRow(
                    item = m,
                    currency = config.settings.currency,
                    onToggle = { avail -> saveItems(config.items.map { if (it.id == m.id) it.copy(available = avail) else it }) },
                    onEdit = { editItem = m }
                )
            }
            item {
                Button(
                    onClick = { editItem = MenuItem(id = Codec.newId(), name = "", categoryId = currentCat.id, emoji = currentCat.emoji) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("إضافة صنف إلى ${currentCat.name}")
                }
            }
        } else {
            item { Text("ابدأ بإضافة قسم، ثم أضف الأصناف إليه.", color = Color.Gray) }
        }
        if (uncategorized.isNotEmpty()) {
            item { Text("أصناف بلا قسم", fontWeight = FontWeight.Bold, color = LinklyAmber) }
            items(uncategorized, key = { "u_" + it.id }) { m ->
                ItemRow(
                    item = m,
                    currency = config.settings.currency,
                    onToggle = { avail -> saveItems(config.items.map { if (it.id == m.id) it.copy(available = avail) else it }) },
                    onEdit = { editItem = m }
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    editCategory?.let { cat ->
        CategoryDialog(
            initial = cat,
            onDismiss = { editCategory = null },
            onSave = { updated ->
                val exists = config.categories.any { it.id == updated.id }
                val newCats = if (exists) config.categories.map { if (it.id == updated.id) updated else it } else config.categories + updated
                viewModel.saveMenu(newCats, config.items)
                selectedCat = updated.id
                editCategory = null
            }
        )
    }

    deleteCategory?.let { cat ->
        val count = config.items.count { it.categoryId == cat.id }
        AlertDialog(
            onDismissRequest = { deleteCategory = null },
            title = { Text("حذف قسم ${cat.name}؟") },
            text = { Text(if (count > 0) "سيُحذف القسم و$count صنف بداخله." else "القسم فارغ.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveMenu(config.categories.filter { it.id != cat.id }, config.items.filter { it.categoryId != cat.id })
                        selectedCat = null
                        deleteCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyRose)
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteCategory = null }) { Text("إلغاء") } }
        )
    }

    editItem?.let { item ->
        ItemDialog(
            initial = item,
            categories = config.categories,
            onDismiss = { editItem = null },
            onDelete = {
                saveItems(config.items.filter { it.id != item.id })
                editItem = null
            },
            onSave = { updated ->
                val exists = config.items.any { it.id == updated.id }
                saveItems(if (exists) config.items.map { if (it.id == updated.id) updated else it } else config.items + updated)
                editItem = null
            }
        )
    }
}

@Composable
private fun ItemRow(item: MenuItem, currency: String, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val image = rememberB64Image(item.imageB64)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)))
            } else {
                Text(item.emoji, fontSize = 30.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (item.available) MaterialTheme.colorScheme.onSurface else Color.Gray)
                val p = formatPrice(item.price, currency)
                Text(if (item.available) p.ifBlank { "متوفر" } else "نفد مؤقتاً", fontSize = 12.sp, color = if (item.available) Color.Gray else LinklyRose)
            }
            Switch(checked = item.available, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
        }
    }
}

private val EMOJIS = listOf("☕", "🍵", "🧋", "🥤", "🍹", "🧃", "💧", "🥛", "🍽️", "🍔", "🍕", "🌯", "🥗", "🍟", "🍗", "🥩", "🍳", "🥪", "🍰", "🍨", "🍩", "🧁", "🍪", "🥐", "🍋", "🍊", "🍓", "⭐")

@Composable
private fun EmojiPicker(selected: String, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(EMOJIS) { e ->
            FilterChip(selected = e == selected, onClick = { onPick(e) }, label = { Text(e, fontSize = 18.sp) })
        }
    }
}

@Composable
private fun CategoryDialog(initial: MenuCategory, onDismiss: () -> Unit, onSave: (MenuCategory) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var station by remember { mutableStateOf(initial.station) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "قسم جديد" else "تعديل القسم") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text("اسم القسم") }, singleLine = true)
                EmojiPicker(selected = emoji, onPick = { emoji = it })
                Text("أين يُحضّر؟ (يفيد إذا كان عندك شاشة للمطبخ وأخرى للبار)", fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("" to "الرئيسية فقط", "kitchen" to "المطبخ", "bar" to "البار").forEach { (id, label) ->
                        FilterChip(selected = station == id, onClick = { station = id }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSave(initial.copy(name = name.trim(), emoji = emoji, station = station)) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ItemDialog(
    initial: MenuItem,
    categories: List<MenuCategory>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (MenuItem) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var priceText by remember { mutableStateOf(if (initial.price > 0) formatPrice(initial.price, "") else "") }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var imageB64 by remember { mutableStateOf(initial.imageB64) }
    val image = rememberB64Image(imageB64)
    val isNew = initial.name.isBlank()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            decodeSampled(context, uri, 512)?.let { imageB64 = bitmapToThumbB64(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "صنف جديد" else "تعديل ${initial.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("اسم الصنف") }, singleLine = true)
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8) },
                    label = { Text("السعر (اختياري)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text("الرمز", fontSize = 12.sp, color = Color.Gray)
                EmojiPicker(selected = emoji, onPick = { emoji = it })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (image != null) {
                        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp)))
                        TextButton(onClick = { imageB64 = "" }) { Text("إزالة الصورة", color = LinklyRose) }
                    } else {
                        OutlinedButton(onClick = { picker.launch("image/*") }) { Text("إضافة صورة") }
                    }
                }
                Text("القسم", fontSize = 12.sp, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories, key = { it.id }) { c ->
                        FilterChip(selected = c.id == categoryId, onClick = { categoryId = c.id }, label = { Text("${c.emoji} ${c.name}") })
                    }
                }
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("حذف هذا الصنف", color = LinklyRose) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            emoji = emoji,
                            price = priceText.toDoubleOrNull() ?: 0.0,
                            categoryId = categoryId,
                            imageB64 = imageB64
                        )
                    )
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** Reads a picked photo at a reduced size, so a 12 MP picture does not fill memory. */
fun decodeSampled(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
} catch (_: Exception) {
    null
}

// ---------------------------------------------------------------------------------- tables

@Composable
private fun TablesEditor(viewModel: LinklyViewModel) {
    val config by viewModel.config.collectAsState()
    val tables = config.tables
    var editTable by remember { mutableStateOf<DiningTable?>(null) }
    var showBulk by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("عدد الطاولات: ${tables.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("الطاولات تظهر للنُّدل كشبكة ملوّنة حسب حالتها.", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editTable = DiningTable(id = Codec.newId(), name = "${tables.size + 1}") }) { Text("+ طاولة") }
                OutlinedButton(onClick = { showBulk = true }) { Text("إضافة عدة طاولات") }
            }
        }
        val zones = tables.groupBy { it.zone.ifBlank { "بدون منطقة" } }
        zones.forEach { (zone, list) ->
            item(key = "z_$zone") { Text(zone, fontWeight = FontWeight.Bold, color = LinklyIndigo, modifier = Modifier.padding(top = 6.dp)) }
            items(list, key = { it.id }) { t ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("طاولة ${t.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editTable = t }) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                        IconButton(onClick = { viewModel.saveTables(tables.filter { it.id != t.id }) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = LinklyRose)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    editTable?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        var zone by remember(t.id) { mutableStateOf(t.zone) }
        AlertDialog(
            onDismissRequest = { editTable = null },
            title = { Text("طاولة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(12) }, label = { Text("الاسم أو الرقم") }, singleLine = true)
                    OutlinedTextField(value = zone, onValueChange = { zone = it.take(20) }, label = { Text("المنطقة") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("داخل", "خارج", "VIP").forEach { z -> FilterChip(selected = zone == z, onClick = { zone = z }, label = { Text(z) }) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        val updated = t.copy(name = name.trim(), zone = zone.trim())
                        val exists = tables.any { it.id == t.id }
                        viewModel.saveTables(if (exists) tables.map { if (it.id == t.id) updated else it } else tables + updated)
                    }
                    editTable = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editTable = null }) { Text("إلغاء") } }
        )
    }

    if (showBulk) {
        var countText by remember { mutableStateOf("5") }
        var zone by remember { mutableStateOf("داخل") }
        AlertDialog(
            onDismissRequest = { showBulk = false },
            title = { Text("إضافة عدة طاولات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter(Char::isDigit).take(2) },
                        label = { Text("العدد") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(value = zone, onValueChange = { zone = it.take(20) }, label = { Text("المنطقة") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val n = (countText.toIntOrNull() ?: 0).coerceIn(0, 60)
                    val startNum = (tables.mapNotNull { it.name.toIntOrNull() }.maxOrNull() ?: 0) + 1
                    val added = (0 until n).map { DiningTable(id = Codec.newId(), name = "${startNum + it}", zone = zone.trim()) }
                    if (added.isNotEmpty()) viewModel.saveTables(tables + added)
                    showBulk = false
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { showBulk = false }) { Text("إلغاء") } }
        )
    }
}
