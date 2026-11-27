package com.example.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Waiter phone only: tells the waiter that one of *their* orders is ready.
 * Vibration + short sound + a notification. The notification is mirrored by the phone
 * to a paired smart watch, so the watch vibrates too.
 * (The TV itself stays sound-only, as decided.)
 */
class WaiterAlerts(private val context: Context) {

    companion object {
        private const val TAG = "WaiterAlerts"
        private const val CHANNEL_READY = "linkly_order_ready"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_READY, "الطلبات الجاهزة", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيه عندما يصبح طلبك جاهزاً"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                )
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun orderReady(orderId: String, title: String, details: String) {
        vibrate(longArrayOf(0, 300, 150, 300))
        notify(orderId, "✅ $title جاهز", details)
    }

    fun orderCancelled(orderId: String, title: String) {
        vibrate(longArrayOf(0, 600))
        notify(orderId, "❌ تم إلغاء $title", "")
    }

    private fun vibrate(pattern: LongArray) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() != true) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate failed", e)
        }
    }

    private fun notify(orderId: String, title: String, text: String) {
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canPost || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            // No notification allowed: still play the short sound ourselves.
            playSound()
            return
        }
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(context, CHANNEL_READY)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            NotificationManagerCompat.from(context).notify(orderId.hashCode(), n)
        } catch (e: SecurityException) {
            playSound()
        } catch (e: Exception) {
            Log.e(TAG, "Notify failed", e)
        }
    }

    private fun playSound() {
        try {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        } catch (_: Exception) {}
    }
}
