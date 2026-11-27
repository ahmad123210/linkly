package com.example.ui.phone

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BlockedDevice
import com.example.model.ManagedDevice
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Entry to the manager interface (waiter settings).
 *
 * The first time, the manager enters the password shown on the TV (or scans the TV's QR code).
 * After that, only this phone opens the manager interface, with no password.
 */
@Composable
fun ManagerAccessCard(viewModel: LinklyViewModel, onOpenManager: () -> Unit) {
    val isManager by viewModel.isManager.collectAsState()
    val managerMessage by viewModel.managerMessage.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val content = result.contents
        if (content != null) {
            if (!viewModel.loginWithQrContent(content)) scanError = "هذا ليس رمز المدير الخاص بـ Linkly"
        }
    }

    // Once the TV accepts the password, open the manager interface.
    LaunchedEffect(isManager) {
        if (isManager && showPasswordDialog) {
            showPasswordDialog = false
            passwordInput = ""
            onOpenManager()
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = null, tint = LinklyAmber)
                Text(text = "واجهة المدير", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isManager) "هذا الهاتف هو جهاز المدير." else "للمدير فقط. امسح رمز QR الظاهر على التلفاز (زر المدير)، أو أدخل كلمة السر، مرة واحدة فقط.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    viewModel.clearManagerMessage()
                    if (isManager) {
                        viewModel.refreshManagedDevices()
                        onOpenManager()
                    } else {
                        showPasswordDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("فتح واجهة المدير")
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("دخول المدير") },
            text = {
                Column {
                    OutlinedButton(
                        onClick = {
                            scanError = null
                            scanLauncher.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt("وجّه الكاميرا إلى رمز QR على التلفاز")
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("مسح رمز QR من التلفاز")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "أو أدخل كلمة سر المدير الظاهرة على التلفاز:",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { if (it.length <= 6) passwordInput = it.filter { ch -> ch.isDigit() } },
                        placeholder = { Text("6 أرقام") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val err = scanError ?: managerMessage
                    if (err != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = err, color = LinklyRose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (passwordInput.isNotBlank()) viewModel.managerLogin(passwordInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo)
                ) {
                    Text("دخول")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }
}

/** Manager: connected phones, names, block / unblock, and handing the manager role to another phone. */
@Composable
fun ManagerDevicesTab(viewModel: LinklyViewModel, onLeftManager: () -> Unit) {
    val devices by viewModel.managedDevices.collectAsState()
    val blocked by viewModel.blockedDevices.collectAsState()
    val managerMessage by viewModel.managerMessage.collectAsState()
    var confirmRelease by remember { mutableStateOf(false) }
    var deviceToBlock by remember { mutableStateOf<ManagedDevice?>(null) }
    var deviceToRename by remember { mutableStateOf<ManagedDevice?>(null) }
    var deviceToTransfer by remember { mutableStateOf<ManagedDevice?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshManagedDevices() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "الأجهزة المتصلة الآن (${devices.size})",
                    fontWeight = FontWeight.Bold,
                    color = LinklyEmerald,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.refreshManagedDevices() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "تحديث")
                }
            }
            managerMessage?.let { Text(it, color = LinklyAmber, fontSize = 12.sp) }
        }
        if (devices.isEmpty()) {
            item { Text("لا توجد أجهزة متصلة", color = Color.Gray, fontSize = 13.sp) }
        }
        items(devices, key = { it.clientId }) { device ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = LinklyIndigo)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.name.ifBlank { "جهاز بدون اسم" } + if (device.isManager) " (المدير)" else "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(device.ip, color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    if (!device.isManager && device.deviceId.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { deviceToRename = device }) { Text("تسمية") }
                            TextButton(onClick = { deviceToTransfer = device }) { Text("جعله المدير", color = LinklyAmber) }
                            TextButton(onClick = { deviceToBlock = device }) { Text("حظر", color = LinklyRose) }
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "الأجهزة المحظورة (${blocked.size})",
                fontWeight = FontWeight.Bold,
                color = LinklyRose,
                fontSize = 15.sp
            )
        }
        if (blocked.isEmpty()) {
            item { Text("لا توجد أجهزة محظورة", color = Color.Gray, fontSize = 13.sp) }
        }
        items(blocked, key = { "b_" + it.deviceId }) { b: BlockedDevice ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = LinklyRose)
                    Spacer(Modifier.width(10.dp))
                    Text(b.name.ifBlank { "جهاز" }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.unblockDevice(b.deviceId) }) { Text("إلغاء الحظر", color = LinklyEmerald) }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { confirmRelease = true }, modifier = Modifier.fillMaxWidth()) {
                Text("إلغاء تسجيل هذا الهاتف كمدير", color = LinklyRose)
            }
        }
    }

    deviceToBlock?.let { target ->
        AlertDialog(
            onDismissRequest = { deviceToBlock = null },
            title = { Text("حظر الجهاز") },
            text = { Text("سيُفصل \"${target.name.ifBlank { target.ip }}\" ولن يستطيع الاتصال بالتلفاز حتى تلغي الحظر.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.blockDevice(target)
                        deviceToBlock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyRose)
                ) { Text("حظر") }
            },
            dismissButton = { TextButton(onClick = { deviceToBlock = null }) { Text("إلغاء") } }
        )
    }

    deviceToRename?.let { target ->
        var nameInput by remember(target.clientId) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { deviceToRename = null },
            title = { Text("تسمية الهاتف") },
            text = {
                Column {
                    Text("يظهر هذا الاسم على الطلبات وفي هذه القائمة، ويتغيّر على الهاتف نفسه أيضاً.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it.take(30) }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (nameInput.isNotBlank()) viewModel.renameDevice(target, nameInput)
                    deviceToRename = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { deviceToRename = null }) { Text("إلغاء") } }
        )
    }

    deviceToTransfer?.let { target ->
        AlertDialog(
            onDismissRequest = { deviceToTransfer = null },
            title = { Text("نقل صلاحية المدير") },
            text = { Text("سيصبح \"${target.name.ifBlank { target.ip }}\" جهاز المدير، ويخرج هذا الهاتف من واجهة المدير.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.transferManager(target)
                        deviceToTransfer = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyAmber)
                ) { Text("نقل") }
            },
            dismissButton = { TextButton(onClick = { deviceToTransfer = null }) { Text("إلغاء") } }
        )
    }

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text("إلغاء تسجيل المدير") },
            text = { Text("ستحتاج كلمة سر جديدة من التلفاز لتسجيل مدير مرة أخرى.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.releaseManager()
                        confirmRelease = false
                        onLeftManager()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyRose)
                ) { Text("تأكيد") }
            },
            dismissButton = { TextButton(onClick = { confirmRelease = false }) { Text("رجوع") } }
        )
    }
}
