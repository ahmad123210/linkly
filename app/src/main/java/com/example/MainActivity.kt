package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import android.content.Intent
import com.example.model.DeviceRole
import com.example.model.TvScreenMode
import com.example.service.LinklyBackgroundService
import com.example.ui.phone.PhoneMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.tv.TvMainScreen
import com.example.ui.tv.DisplayMainScreen
import com.example.viewmodel.LinklyViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LinklyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleNavigationIntent(intent)

        setContent {
            MyApplicationTheme {
                // Arabic RTL Layout Direction
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val deviceRole by viewModel.deviceRole.collectAsState()

                    // Ensure background service is running when in TV mode
                    LaunchedEffect(deviceRole) {
                        if (deviceRole == DeviceRole.TV) {
                            LinklyBackgroundService.start(applicationContext)
                        }
                        // Kitchen / hall screens must never go dark.
                        if (deviceRole == DeviceRole.PHONE) {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    // Request Runtime Permissions for Microphone & Notifications
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        // Permissions handled
                    }

                    LaunchedEffect(Unit) {
                        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        val ungranted = permissionsToRequest.filter {
                            ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                        }

                        if (ungranted.isNotEmpty()) {
                            permissionLauncher.launch(ungranted.toTypedArray())
                        }
                    }

                    Surface(modifier = Modifier.fillMaxSize()) {
                        Crossfade(targetState = deviceRole, label = "roleTransition") { role ->
                            when (role) {
                                DeviceRole.TV -> TvMainScreen(viewModel = viewModel)
                                DeviceRole.PHONE -> PhoneMainScreen(viewModel = viewModel)
                                DeviceRole.DISPLAY -> DisplayMainScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        // linkly://manager?code=123456 (the TV's manager QR code, opened from the phone camera)
        val data = intent?.data
        if (data != null && data.scheme == "linkly" && data.host == "manager") {
            viewModel.loginWithQrContent(data.toString())
            return
        }
        val dest = intent?.getStringExtra("EXTRA_NAVIGATE_TO") ?: return
        try {
            val mode = TvScreenMode.valueOf(dest)
            viewModel.switchTvScreen(mode)
        } catch (_: Exception) {
            viewModel.switchTvScreen(TvScreenMode.ORDERS)
        }
    }

    // Android TV Remote Key Handling (Mic button or Keycode)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_RECORD || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
            viewModel.togglePushToTalk()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyUp(keyCode, event)
    }
}
