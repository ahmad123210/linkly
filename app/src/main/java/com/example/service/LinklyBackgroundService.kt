package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.RingtoneManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.LinklyApplication
import com.example.MainActivity
import com.example.R
import com.example.model.DeviceRole
import com.example.network.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Foreground Service for Android TV Background Reliability:
 * - Holds Partial WakeLock and High-Performance WifiLock so the CPU & Wi-Fi never sleep in standby.
 * - Hosts the WebSocket Server and NSD registration continuously in the background, even when YouTube or other apps are active.
 * - Streams direct walkie-talkie audio to TV speakers in the background without interrupting YouTube.
 * - Wakes up and brings MainActivity to the front immediately upon summon or new order.
 */
class LinklyBackgroundService : Service() {

    companion object {
        const val CHANNEL_ID = "linkly_service_channel"
        const val WAKE_CHANNEL_ID = "linkly_wake_channel"
        const val NOTIFICATION_ID = 1001
        const val WAKE_NOTIFICATION_ID = 1002
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_WAKE_UP = "ACTION_WAKE_UP"

        fun start(context: Context) {
            // The persistent server only belongs on the TV. Phones never run this service.
            if (LinklyApplication.instance.currentRole() != DeviceRole.TV) return
            try {
                val intent = Intent(context, LinklyBackgroundService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("LinklyService", "Error in startService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LinklyBackgroundService::class.java))
            } catch (e: Exception) {
                android.util.Log.e("LinklyService", "Error stopping service", e)
            }
        }

        fun isOverlayPermissionGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun isBatteryOptimizationIgnored(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else {
                true
            }
        }

        fun openOverlayPermissionSettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { context.startActivity(fallback) } catch (_: Exception) {}
                }
            }
        }

        fun openBatteryOptimizationSettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { context.startActivity(fallback) } catch (_: Exception) {}
                }
            }
        }

        /**
         * Brings the Linkly screen to the front (used only by an explicit "summon screen" from a phone).
         * No notification text is shown on the TV; the caller plays the sound.
         * Works over other apps when the "display over other apps" permission is granted.
         */
        fun wakeUpActivity(context: Context, destination: String = "ORDERS") {
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    putExtra("EXTRA_NAVIGATE_TO", destination)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                android.util.Log.e("LinklyService", "Error bringing screen to front", e)
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Partial WakeLock held for the whole life of the service (released in onDestroy),
        // so the CPU keeps serving sockets in standby. The old 24 h timeout silently expired.
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Linkly:BackgroundWakeLock")?.apply {
            setReferenceCounted(false)
        }
        try {
            @Suppress("WakelockTimeout")
            wakeLock?.acquire()
        } catch (_: Exception) {}

        // Acquire High-Performance WifiLock so Wi-Fi card does not sleep when screen is off/standby
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Linkly:WifiLock")
        try {
            wifiLock?.acquire()
        } catch (_: Exception) {}

        // Start TV Network Server and incoming packet observer
        startBackgroundNetworkEngine()
    }

    private var engineStarted = false

    private fun startBackgroundNetworkEngine() {
        if (engineStarted) return
        try {
            val app = LinklyApplication.instance
            if (app.currentRole() != DeviceRole.TV) return
            engineStarted = true
            val networkManager = app.networkManager
            android.util.Log.d("LinklyService", "Hosting persistent server & NSD on TV...")
            networkManager.startServer(NetworkManager.DEFAULT_PORT)

            // The service is the ONLY place that plays sounds, live voice and summons the screen on the TV.
            // The screen (ViewModel) only updates what is displayed, so nothing is handled twice.
            serviceScope.launch {
                networkManager.incomingMessages.collect { msg ->
                    when (msg.action) {
                        "WAKE_UP" -> {
                            val dest = if (msg.payload in listOf("ORDERS", "TEXT", "WHITEBOARD", "IMAGE")) msg.payload else "ORDERS"
                            wakeUpActivity(this@LinklyBackgroundService, dest)
                            app.tvAudioPlayer.playOrderChime()
                        }
                        "ORDER_NEW" -> {
                            // Sound only: no text notification and no forced screen change.
                            // A different sound for urgent orders and for waiter calls.
                            val obj = try { org.json.JSONObject(msg.payload) } catch (_: Exception) { null }
                            app.tvAudioPlayer.playForNewOrder(
                                isUrgent = obj?.optBoolean("isUrgent", false) ?: false,
                                isCall = obj?.optBoolean("isCall", false) ?: false
                            )
                        }
                        "VOICE_PTT" -> app.audioStreamManager.handlePttState(msg.payload)
                        "VOICE_CHUNK" -> app.audioStreamManager.handleIncomingAudioChunk(msg.payload)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LinklyService", "Error starting background network engine", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_WAKE_UP -> {
                wakeUpActivity(this, "ORDERS")
            }
            else -> {
                if (LinklyApplication.instance.currentRole() != DeviceRole.TV) {
                    // Must still enter the foreground before stopping, or Android crashes the app.
                    try {
                        startForeground(NOTIFICATION_ID, buildNotification("Linkly", ""))
                    } catch (_: Exception) {}
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    val notification = buildNotification("Linkly قيد العمل في الخلفية", "متصل دائماً وجاهز لاستقبال الأوامر والصوت المباشر")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // connectedDevice only: dataSync is limited to 6 h/day on Android 15 and cannot start at boot.
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LinklyService", "Failed to start foreground service", e)
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(title: String, message: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "خدمة Linkly في الخلفية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "تحافظ على اتصال الصوت والمزامنة اللحظية بين الهاتف والتلفاز"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            // The old pop-up notification channel is no longer used (sound only on the TV).
            manager?.deleteNotificationChannel(WAKE_CHANNEL_ID)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
