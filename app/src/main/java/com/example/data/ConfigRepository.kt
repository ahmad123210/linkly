package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.Codec
import com.example.model.LinklyConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Menu, tables and manager settings.
 *
 * The TV owns the real copy and sends it to every phone (CONFIG_SYNC).
 * Phones keep the last copy they received, so the menu still works while offline.
 */
class ConfigRepository(context: Context) {

    companion object {
        private const val TAG = "ConfigRepository"
        private const val PREFS = "linkly_config"
        private const val KEY_CONFIG = "config_json"
        const val CONFIG_SYNC = "CONFIG_SYNC"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<LinklyConfig> = _config.asStateFlow()

    private fun load(): LinklyConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return Codec.defaultConfig()
        return try {
            Codec.parseConfig(JSONObject(raw))
        } catch (e: Exception) {
            Log.e(TAG, "Bad saved config, using defaults", e)
            Codec.defaultConfig()
        }
    }

    /** TV: the manager changed something. Bumps the version and saves. */
    @Synchronized
    fun update(transform: (LinklyConfig) -> LinklyConfig): LinklyConfig {
        val next = transform(_config.value).copy(version = System.currentTimeMillis())
        save(next)
        return next
    }

    /** Phone: store the copy the TV just sent. */
    @Synchronized
    fun applyRemote(json: JSONObject) {
        try {
            save(Codec.parseConfig(json))
        } catch (e: Exception) {
            Log.e(TAG, "Bad config from TV", e)
        }
    }

    @Synchronized
    fun replace(config: LinklyConfig) = save(config.copy(version = System.currentTimeMillis()))

    private fun save(c: LinklyConfig) {
        _config.value = c
        prefs.edit().putString(KEY_CONFIG, Codec.configToJson(c).toString()).apply()
    }

    fun toJsonString(): String = Codec.configToJson(_config.value).toString()
}
