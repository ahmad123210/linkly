package com.example

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.example.admin.DeviceAdminManager
import com.example.audio.AudioStreamManager
import com.example.data.ConfigRepository
import com.example.data.OrdersRepository
import com.example.audio.TvAudioPlayer
import com.example.model.DeviceRole
import com.example.network.NetworkManager
import com.example.tts.ArabicTtsManager
import java.util.UUID

class LinklyApplication : Application() {

    companion object {
        lateinit var instance: LinklyApplication
            private set

        const val ROLE_PREFS = "linkly_prefs"
        const val KEY_DEVICE_ROLE = "device_role"
        private const val KEY_DEVICE_ID = "device_id"
    }

    val networkManager: NetworkManager by lazy { NetworkManager(this) }
    val audioStreamManager: AudioStreamManager by lazy { AudioStreamManager(this, networkManager) }
    val tvAudioPlayer: TvAudioPlayer by lazy { TvAudioPlayer(this) }
    val arabicTtsManager: ArabicTtsManager by lazy { ArabicTtsManager(this) }
    val configRepo: ConfigRepository by lazy { ConfigRepository(this) }
    val ordersRepo: OrdersRepository by lazy { OrdersRepository(this) }
    val deviceAdmin: DeviceAdminManager by lazy { DeviceAdminManager(this, networkManager, configRepo) }

    /** Stable random id for this install, used by the TV to recognise phones (manager, blocking). */
    val deviceId: String by lazy {
        val prefs = getSharedPreferences(ROLE_PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Install the TV-side message filter (manager role + blocked devices) before any traffic.
        deviceAdmin
    }

    /** The saved role, or a guess from the hardware on first run. */
    fun currentRole(): DeviceRole {
        val saved = getSharedPreferences(ROLE_PREFS, Context.MODE_PRIVATE).getString(KEY_DEVICE_ROLE, null)
        if (saved != null) {
            return try { DeviceRole.valueOf(saved) } catch (_: Exception) { detectRole() }
        }
        return detectRole()
    }

    fun saveRole(role: DeviceRole) {
        getSharedPreferences(ROLE_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DEVICE_ROLE, role.name).apply()
    }

    private fun detectRole(): DeviceRole {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val pm = packageManager
        val isTv = isTvMode || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        return if (isTv) DeviceRole.TV else DeviceRole.PHONE
    }
}
