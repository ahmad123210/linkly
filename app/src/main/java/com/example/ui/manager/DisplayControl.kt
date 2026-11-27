package com.example.ui.manager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TvScreenMode
import com.example.ui.phone.PhoneImagesScreen
import com.example.ui.phone.PhoneTextScreen
import com.example.ui.phone.PhoneVoiceScreen
import com.example.ui.theme.LinklyCyan
import com.example.ui.theme.LinklyEmerald
import com.example.ui.theme.LinklyIndigo
import com.example.ui.theme.LinklyRose
import com.example.viewmodel.LinklyViewModel

/** Manager only: everything that changes what the TV shows (moved here from the waiter phone). */
@Composable
fun DisplayControlTab(viewModel: LinklyViewModel, onOpenWhiteboard: () -> Unit) {
    val activeTvScreen by viewModel.tvScreenMode.collectAsState()
    var section by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // What the TV shows now
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الشاشة تعرض الآن:", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { viewModel.wakeUpTv() }) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = LinklyCyan)
                        Spacer(Modifier.width(4.dp))
                        Text("استدعاء الشاشة")
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val modes = listOf(
                        TvScreenMode.ORDERS to "الطلبات",
                        TvScreenMode.TEXT to "النص",
                        TvScreenMode.IMAGE to "الصور",
                        TvScreenMode.WHITEBOARD to "الرسم"
                    )
                    items(modes) { (mode, label) ->
                        FilterChip(
                            selected = activeTvScreen == mode,
                            onClick = { viewModel.switchTvScreen(mode) },
                            label = { Text(label, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }

        // Tool sections
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val sections = listOf("نص على الشاشة", "صور", "الرسم والشرح", "الموسيقى", "الصوت المباشر")
            items(sections.size) { i ->
                FilterChip(selected = section == i, onClick = { section = i }, label = { Text(sections[i]) })
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (section) {
                0 -> PhoneTextScreen(viewModel = viewModel)
                1 -> PhoneImagesScreen(viewModel = viewModel)
                2 -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Draw, contentDescription = null, tint = LinklyIndigo, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("ارسم أو اشرح على الشاشة مباشرة من هذا الهاتف.", color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenWhiteboard) { Text("فتح لوحة الرسم") }
                }
                3 -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) { AudioControlCard(viewModel) }
                else -> PhoneVoiceScreen(viewModel = viewModel)
            }
        }
    }
}

/** Background music on the TV, controlled from the manager phone (never plays on the phone). */
@Composable
fun AudioControlCard(viewModel: LinklyViewModel) {
    val context = LocalContext.current
    val audioPlaylist by viewModel.audioPlaylist.collectAsState()
    val audioPlaybackState by viewModel.audioPlaybackState.collectAsState()

    val audioFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                var fileName = "track_${System.currentTimeMillis()}.mp3"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) fileName = cursor.getString(nameIndex)
                }
                if (bytes != null && bytes.isNotEmpty()) viewModel.uploadAudioFile(fileName, bytes)
            } catch (e: Exception) {
                android.util.Log.e("AudioControlCard", "Error picking audio file", e)
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = LinklyCyan)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = "موسيقى في الخلفية على التلفاز", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = if (audioPlaybackState.isPlaying) "قيد التشغيل: ${audioPlaybackState.currentTrackTitle}" else "متوقف حالياً",
                        color = if (audioPlaybackState.isPlaying) LinklyEmerald else Color.Gray,
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = { viewModel.toggleAudioPlayback() },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (audioPlaybackState.isPlaying) LinklyRose else LinklyEmerald)
                ) {
                    Icon(
                        imageVector = if (audioPlaybackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (audioPlaybackState.isPlaying) "إيقاف مؤقت" else "تشغيل",
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { audioFilePicker.launch("audio/*") },
                colors = ButtonDefaults.buttonColors(containerColor = LinklyIndigo),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "رفع ملف صوتي إلى التلفاز", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "قائمة التشغيل على التلفاز:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            audioPlaylist.forEach { track ->
                val isCurrent = audioPlaybackState.currentTrackTitle == track.title && audioPlaybackState.isPlaying
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) LinklyCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { viewModel.playAudioTrack(track.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.title,
                        fontSize = 13.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) LinklyCyan else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCurrent) Text(text = "▶ يعمل", color = LinklyCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
