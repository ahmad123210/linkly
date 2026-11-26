package com.example.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.BlockedDevice
import com.example.model.ManagedDevice
import com.example.ui.theme.LinklyAmber
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel

/**
 * Settings card with the "manager screen" button.
 * First use: asks for the password shown on the TV (المدير button on the TV orders screen).
 * After that, only this phone opens the manager screen, with no password.
 */
@Composable
fun ManagerAccessCard(viewModel: LinklyViewModel) {
    val isManager by viewModel.isManager.collectAsState()
    val managerMessage by viewModel.managerMessage.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showManagerScreen by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }

    // Once the TV accepts the password, move straight to the manager screen.
    LaunchedEffect(isManager) {
        if (isManager && showPasswordDialog) {
            showPasswordDialog = false
            passwordInput = ""
            showManagerScreen = true
        }
        if (!isManager) showManagerScreen = false
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
                text = if (isManager) "هذا الهاتف هو جهاز المدير." else "للمدير فقط. تحتاج كلمة السر الظاهرة على التلفاز (زر المدير) مرة واحدة.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    viewModel.clearManagerMessage()
                    if (isManager) {
                        viewModel.refreshManagedDevices()
                        showManagerScreen = true
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
                    Text(
                        text = "أدخل كلمة سر المدير الظاهرة على التلفاز:",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { if (it.length <= 6) passwordInput = it.filter { ch -> ch.isDigit() } },
                        placeholder = { Text("6 أرقام") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (managerMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = managerMessage ?: "", color = LinklyRose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

    if (showManagerScreen) {
        ManagerScreenDialog(viewModel = viewModel, onClose = { showManagerScreen = false })
    }
}

@Composable
private fun ManagerScreenDialog(viewModel: LinklyViewModel, onClose: () -> Unit) {
    val devices by viewModel.managedDevices.collectAsState()
    val blocked by viewModel.blockedDevices.collectAsState()
    var confirmRelease by remember { mutableStateOf(false) }
    var deviceToBlock by remember { mutableStateOf<ManagedDevice?>(null) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("إدارة الأجهزة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { viewModel.refreshManagedDevices() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "تحديث")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "الأجهزة المتصلة الآن (${devices.size})",
                            fontWeight = FontWeight.Bold,
                            color = LinklyEmerald,
                            fontSize = 14.sp
                        )
                    }
                    if (devices.isEmpty()) {
                        item { Text("لا توجد أجهزة متصلة", color = Color.Gray, fontSize = 13.sp) }
                    }
                    items(devices, key = { it.clientId }) { device ->
                        DeviceRow(
                            title = device.name.ifBlank { "جهاز بدون اسم" } + if (device.isManager) " (المدير)" else "",
                            subtitle = device.ip,
                            actionLabel = if (device.isManager || device.deviceId.isBlank()) null else "حظر",
                            onAction = { deviceToBlock = device }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "الأجهزة المحظورة (${blocked.size})",
                            fontWeight = FontWeight.Bold,
                            color = LinklyRose,
                            fontSize = 14.sp
                        )
                    }
                    if (blocked.isEmpty()) {
                        item { Text("لا توجد أجهزة محظورة", color = Color.Gray, fontSize = 13.sp) }
                    }
                    items(blocked, key = { "b_" + it.deviceId }) { b: BlockedDevice ->
                        DeviceRow(
                            title = b.name.ifBlank { "جهاز" },
                            subtitle = "محظور",
                            actionLabel = "إلغاء الحظر",
                            onAction = { viewModel.unblockDevice(b.deviceId) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { confirmRelease = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إلغاء تسجيل هذا الهاتف كمدير", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق")
                }
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
            dismissButton = {
                TextButton(onClick = { deviceToBlock = null }) { Text("إلغاء") }
            }
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
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinklyRose)
                ) { Text("تأكيد") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRelease = false }) { Text("رجوع") }
            }
        )
    }
}

@Composable
private fun DeviceRow(title: String, subtitle: String, actionLabel: String?, onAction: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (actionLabel == "إلغاء الحظر") Icons.Default.Block else Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = LinklyIndigo
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            if (actionLabel != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = if (actionLabel == "حظر") LinklyRose else LinklyEmerald)
                }
            }
        }
    }
}
