package com.example.ui.manager

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Reports
import com.example.data.Stats
import com.example.model.LinklySettings
import com.example.model.QuickOrder
import com.example.ui.common.formatPrice
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Orders known to the manager: the TV's full history when received, plus anything newer seen live. */
@Composable
private fun rememberAllOrders(viewModel: LinklyViewModel): List<QuickOrder> {
    val history by viewModel.managerHistory.collectAsState()
    val live by viewModel.orders.collectAsState()
    return remember(history, live) {
        val byId = LinkedHashMap<String, QuickOrder>()
        history.forEach { byId[it.id] = it }
        live.forEach { byId[it.id] = it }
        byId.values.toList()
    }
}

private fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun fmt1(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it) } ?: "—"

// ---------------------------------------------------------------------------------- dashboard

@Composable
fun DashboardTab(viewModel: LinklyViewModel) {
    val config by viewModel.config.collectAsState()
    val all = rememberAllOrders(viewModel)
    LaunchedEffect(Unit) { viewModel.requestHistory() }

    val days = remember(all) { all.map { it.dateStr }.distinct().sortedDescending() }
    var day by remember { mutableStateOf(todayStr()) }
    val prices = remember(config) { config.items.associate { it.id to it.price } }
    val stats = remember(all, day, prices) { Stats.forDay(all, day, prices) }

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("لوحة اليوم", fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.requestHistory() }) { Icon(Icons.Default.Refresh, contentDescription = "تحديث") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val list = (listOf(todayStr()) + days).distinct().take(14)
                items(list) { d ->
                    FilterChip(selected = d == day, onClick = { day = d }, label = { Text(if (d == todayStr()) "اليوم" else d.drop(5)) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("الطلبات", "${stats.totalOrders}", LinklyIndigo, Modifier.weight(1f))
                StatTile("متوسط التحضير", "${fmt1(stats.avgPrepMinutes)} د", LinklyCyan, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("على الشاشة الآن", "${stats.activeNow}", LinklyAmber, Modifier.weight(1f))
                StatTile("ملغاة", "${stats.cancelled}", LinklyRose, Modifier.weight(1f))
                StatTile("نداءات", "${stats.calls}", LinklyEmerald, Modifier.weight(1f))
            }
        }
        if (stats.revenue > 0) {
            item { StatTile("المبيعات التقريبية", formatPrice(stats.revenue, config.settings.currency), LinklyEmerald, Modifier.fillMaxWidth()) }
        }
        item {
            SectionCard("الأكثر طلباً") {
                if (stats.topItems.isEmpty()) Text("لا توجد طلبات بعد", color = Color.Gray)
                stats.topItems.forEachIndexed { i, (name, count) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("${i + 1}. $name", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text("$count", fontWeight = FontWeight.Black, color = LinklyIndigo)
                    }
                }
            }
        }
        item {
            SectionCard("أداء النُّدل") {
                if (stats.waiters.isEmpty()) Text("لا توجد طلبات بعد", color = Color.Gray)
                stats.waiters.forEach { w ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("👤 ${w.name}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text("${w.orders} طلب", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text("${fmt1(w.avgPrepMinutes)} د", color = Color.Gray)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 24.sp, color = color)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------------------------- more

@Composable
fun MoreTab(viewModel: LinklyViewModel) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val managerMessage by viewModel.managerMessage.collectAsState()
    val backupData by viewModel.backupData.collectAsState()
    val all = rememberAllOrders(viewModel)
    LaunchedEffect(Unit) { viewModel.requestHistory() }

    var exportDay by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun ordersFor(day: String?): List<QuickOrder> = if (day == null) all else all.filter { it.dateStr == day }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            info = try {
                context.contentResolver.openOutputStream(uri)?.use { Reports.writeCsv(ordersFor(exportDay), it) }
                "تم حفظ ملف Excel (CSV)"
            } catch (e: Exception) {
                "تعذر حفظ الملف"
            }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) {
            info = try {
                val title = "تقرير طلبات Linkly" + (exportDay?.let { " - $it" } ?: "")
                context.contentResolver.openOutputStream(uri)?.use { Reports.writePdf(ordersFor(exportDay), title, it) }
                "تم حفظ ملف PDF"
            } catch (e: Exception) {
                "تعذر حفظ الملف"
            }
        }
    }
    val backupSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val data = backupData
        if (uri != null && data != null) {
            info = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray(Charsets.UTF_8)) }
                "تم حفظ النسخة الاحتياطية"
            } catch (e: Exception) {
                "تعذر حفظ النسخة الاحتياطية"
            }
        }
        viewModel.clearBackupData()
    }
    val backupOpenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                viewModel.restoreBackup(text)
                info = "جارٍ الاستعادة على التلفاز..."
            } catch (e: Exception) {
                info = "تعذر قراءة الملف"
            }
        }
    }

    // When the TV answers the backup request, ask where to save it.
    LaunchedEffect(backupData) {
        if (backupData != null) backupSaveLauncher.launch("linkly-backup-${todayStr()}.json")
    }

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val msg = info ?: managerMessage
        if (msg != null) {
            item { Text(msg, color = LinklyAmber, fontWeight = FontWeight.Bold) }
        }
        item { SettingsEditor(settings = config.settings, onSave = { viewModel.saveSettings(it) }) }

        item {
            SectionCard("السجل والتقارير") {
                Text("احفظ طلبات يوم أو كل الطلبات كملف Excel أو PDF.", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportDay = null; csvLauncher.launch("linkly-orders-all.csv") }) { Text("الكل Excel") }
                    OutlinedButton(onClick = { exportDay = null; pdfLauncher.launch("linkly-orders-all.pdf") }) { Text("الكل PDF") }
                }
            }
        }
        val days = all.groupBy { it.dateStr }.toList().sortedByDescending { it.first }
        items(days, key = { it.first }) { (day, list) ->
            val s = Stats.forDay(list, day)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(day, fontWeight = FontWeight.Bold)
                    Text("${s.totalOrders} طلب • ${s.cancelled} ملغي • متوسط ${fmt1(s.avgPrepMinutes)} د", fontSize = 12.sp, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        androidx.compose.material3.TextButton(onClick = { exportDay = day; csvLauncher.launch("linkly-orders-$day.csv") }) { Text("Excel") }
                        androidx.compose.material3.TextButton(onClick = { exportDay = day; pdfLauncher.launch("linkly-orders-$day.pdf") }) { Text("PDF") }
                        androidx.compose.material3.TextButton(onClick = { viewModel.restoreDailyOrders(day) }) { Text("إعادتها للشاشة") }
                    }
                }
            }
        }

        item {
            SectionCard("النسخ الاحتياطي") {
                Text("احفظ القائمة والطاولات والإعدادات في ملف، واستعدها عند تغيير التلفاز أو إعادة تثبيت التطبيق.", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { info = null; viewModel.requestBackup() }) { Text("حفظ نسخة") }
                    OutlinedButton(onClick = { info = null; backupOpenLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("استعادة نسخة") }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun SettingsEditor(settings: LinklySettings, onSave: (LinklySettings) -> Unit) {
    var warn by remember(settings) { mutableStateOf(settings.warnMinutes.toString()) }
    var late by remember(settings) { mutableStateOf(settings.lateMinutes.toString()) }
    var idle by remember(settings) { mutableStateOf(settings.idleEnabled) }
    var idleDelay by remember(settings) { mutableStateOf(settings.idleDelaySec.toString()) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }

    SectionCard("إعدادات الشاشة والطلبات") {
        Text("لون المؤقت على بطاقة الطلب في التلفاز:", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = warn,
                onValueChange = { warn = it.filter(Char::isDigit).take(3) },
                label = { Text("برتقالي بعد (د)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = late,
                onValueChange = { late = it.filter(Char::isDigit).take(3) },
                label = { Text("أحمر بعد (د)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text("وضع الاستراحة", fontWeight = FontWeight.Bold)
                Text("عند عدم وجود طلبات يعرض التلفاز الصور أو النص أو الساعة، ويعود للطلبات مع أول طلب.", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(checked = idle, onCheckedChange = { idle = it })
        }
        if (idle) {
            OutlinedTextField(
                value = idleDelay,
                onValueChange = { idleDelay = it.filter(Char::isDigit).take(4) },
                label = { Text("بعد كم ثانية بلا طلبات") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it.take(6) },
            label = { Text("العملة (اختياري)") },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val w = (warn.toIntOrNull() ?: 10).coerceIn(1, 240)
                val l = (late.toIntOrNull() ?: 20).coerceIn(w + 1, 480)
                onSave(
                    settings.copy(
                        warnMinutes = w,
                        lateMinutes = l,
                        idleEnabled = idle,
                        idleDelaySec = (idleDelay.toIntOrNull() ?: 60).coerceIn(10, 3600),
                        currency = currency.trim()
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
            modifier = Modifier.fillMaxWidth()
        ) { Text("حفظ الإعدادات") }
    }
}
