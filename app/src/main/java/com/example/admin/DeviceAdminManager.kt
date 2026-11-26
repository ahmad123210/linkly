package com.example.admin

import android.content.Context
import android.util.Log
import com.example.model.LinklyMessage
import com.example.network.NetworkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * TV side: the manager role.
 *
 * - The TV shows a random 6-digit manager password.
 * - The first phone that enters it becomes the manager. Only that one phone (by its device id)
 *   can open the manager screen afterwards; it keeps a token so it never needs the password again.
 * - The manager sees the connected phones and can block / unblock them.
 * - Resetting the manager on the TV forgets the manager phone and creates a new password.
 *
 * Normal pairing for waiters is unchanged and stays automatic.
 */
class DeviceAdminManager(
    context: Context,
    private val network: NetworkManager
) {
    companion object {
        private const val TAG = "DeviceAdmin"
        private const val PREFS = "linkly_admin_prefs"
        private const val KEY_PASSWORD = "manager_password"
        private const val KEY_MANAGER_DEVICE = "manager_device_id"
        private const val KEY_MANAGER_NAME = "manager_name"
        private const val KEY_MANAGER_TOKEN = "manager_token"
        private const val KEY_BLOCKED = "blocked_devices"
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_MS = 5 * 60 * 1000L

        // Protocol actions
        const val HELLO = "HELLO"
        const val MANAGER_LOGIN = "MANAGER_LOGIN"
        const val MANAGER_AUTH = "MANAGER_AUTH"
        const val MANAGER_LIST = "MANAGER_LIST"
        const val MANAGER_BLOCK = "MANAGER_BLOCK"
        const val MANAGER_UNBLOCK = "MANAGER_UNBLOCK"
        const val MANAGER_RELEASE = "MANAGER_RELEASE"
        const val MANAGER_RESULT = "MANAGER_RESULT"
        const val MANAGER_DEVICES = "MANAGER_DEVICES"
        const val DEVICE_BLOCKED = "DEVICE_BLOCKED"

        private val ADMIN_ACTIONS = setOf(
            MANAGER_LOGIN, MANAGER_AUTH, MANAGER_LIST, MANAGER_BLOCK, MANAGER_UNBLOCK, MANAGER_RELEASE
        )
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    private val _managerPassword = MutableStateFlow(
        prefs.getString(KEY_PASSWORD, null) ?: generatePassword().also { prefs.edit().putString(KEY_PASSWORD, it).apply() }
    )
    val managerPassword: StateFlow<String> = _managerPassword.asStateFlow()

    private val _managerName = MutableStateFlow(prefs.getString(KEY_MANAGER_NAME, "") ?: "")
    /** Name of the registered manager phone, empty when none is registered. */
    val managerName: StateFlow<String> = _managerName.asStateFlow()

    // deviceId -> last known name
    private val blocked: MutableMap<String, String> = loadBlocked()

    private var failedAttempts = 0
    private var lockedUntil = 0L

    init {
        network.serverMessageFilter = ::filter
        network.onClientsChanged = { pushDeviceListToManager() }
    }

    // ------------------------------------------------------------------ TV UI actions

    /** Forget the manager phone and create a new password. */
    @Synchronized
    fun resetManager() {
        prefs.edit()
            .remove(KEY_MANAGER_DEVICE)
            .remove(KEY_MANAGER_NAME)
            .remove(KEY_MANAGER_TOKEN)
            .apply()
        _managerName.value = ""
        val pwd = generatePassword()
        prefs.edit().putString(KEY_PASSWORD, pwd).apply()
        _managerPassword.value = pwd
        failedAttempts = 0
        lockedUntil = 0L
    }

    /** Clear every block (from the TV, in case the manager phone is lost). */
    @Synchronized
    fun clearAllBlocks() {
        blocked.clear()
        saveBlocked()
        pushDeviceListToManager()
    }

    // ------------------------------------------------------------------ message filter

    /** Returns false when the message must not reach the rest of the app. */
    private fun filter(msg: LinklyMessage): Boolean {
        val client = network.getClient(msg.clientId)

        // Learn who is on this connection.
        if (msg.action == HELLO || msg.action == "PAIR_REQUEST") {
            val obj = parse(msg.payload)
            val deviceId = obj.optString("deviceId")
            val name = obj.optString("name").ifBlank { obj.optString("waiterName") }
            if (deviceId.isNotBlank() || name.isNotBlank()) {
                network.setClientIdentity(msg.clientId, deviceId, name)
            }
            if (deviceId.isNotBlank() && isBlocked(deviceId)) {
                rejectBlocked(msg.clientId)
                return false
            }
            if (msg.action == HELLO) return false
        }

        val deviceId = network.getClient(msg.clientId)?.deviceId ?: client?.deviceId ?: ""
        if (deviceId.isNotBlank() && isBlocked(deviceId)) {
            rejectBlocked(msg.clientId)
            return false
        }

        if (msg.action in ADMIN_ACTIONS) {
            handleAdmin(msg)
            return false
        }
        return true
    }

    @Synchronized
    private fun isBlocked(deviceId: String) = blocked.containsKey(deviceId)

    private fun rejectBlocked(clientId: String) {
        network.sendTo(clientId, LinklyMessage(action = DEVICE_BLOCKED, senderRole = "TV", payload = ""))
        // Give the writer a moment to deliver the notice, then close.
        Thread {
            try { Thread.sleep(300) } catch (_: InterruptedException) {}
            network.disconnectClient(clientId)
        }.start()
    }

    // ------------------------------------------------------------------ manager commands

    @Synchronized
    private fun handleAdmin(msg: LinklyMessage) {
        val obj = parse(msg.payload)
        val senderDevice = obj.optString("deviceId").ifBlank { network.getClient(msg.clientId)?.deviceId ?: "" }
        if (senderDevice.isNotBlank()) {
            network.setClientIdentity(msg.clientId, senderDevice, obj.optString("name"))
        }
        val registeredDevice = prefs.getString(KEY_MANAGER_DEVICE, "") ?: ""
        val registeredToken = prefs.getString(KEY_MANAGER_TOKEN, "") ?: ""

        if (msg.action == MANAGER_LOGIN) {
            val now = System.currentTimeMillis()
            if (now < lockedUntil) {
                reply(msg.clientId, false, "محاولات كثيرة خاطئة. حاول بعد ${((lockedUntil - now) / 60000) + 1} دقائق")
                return
            }
            if (senderDevice.isBlank()) {
                reply(msg.clientId, false, "تعذر التعرف على هذا الجهاز")
                return
            }
            if (obj.optString("password").trim() != _managerPassword.value) {
                failedAttempts++
                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                    lockedUntil = now + LOCKOUT_MS
                    failedAttempts = 0
                }
                reply(msg.clientId, false, "كلمة سر المدير غير صحيحة")
                return
            }
            if (registeredDevice.isNotBlank() && registeredDevice != senderDevice) {
                reply(msg.clientId, false, "المدير مسجّل على جهاز آخر. أعد تعيين المدير من التلفاز أولاً")
                return
            }
            failedAttempts = 0
            val token = registeredToken.ifBlank { newToken() }
            val name = obj.optString("name").ifBlank { "المدير" }
            prefs.edit()
                .putString(KEY_MANAGER_DEVICE, senderDevice)
                .putString(KEY_MANAGER_TOKEN, token)
                .putString(KEY_MANAGER_NAME, name)
                .apply()
            _managerName.value = name
            reply(msg.clientId, true, "تم تسجيل هذا الجهاز كمدير", token)
            sendDeviceList(msg.clientId)
            return
        }

        // Every other command needs the registered manager device and its token.
        val token = obj.optString("token")
        val authorized = registeredDevice.isNotBlank() && token.isNotBlank() &&
            senderDevice == registeredDevice && token == registeredToken
        if (!authorized) {
            reply(msg.clientId, false, "هذا الجهاز ليس المدير المسجّل", revoked = true)
            return
        }

        when (msg.action) {
            MANAGER_AUTH -> {
                reply(msg.clientId, true, "", token)
                sendDeviceList(msg.clientId)
            }
            MANAGER_LIST -> sendDeviceList(msg.clientId)
            MANAGER_BLOCK -> {
                val target = obj.optString("targetDeviceId")
                if (target.isNotBlank() && target != registeredDevice) {
                    val name = network.connectedClients.value.firstOrNull { it.deviceId == target }?.name
                        ?: obj.optString("targetName")
                    blocked[target] = name.ifBlank { "جهاز" }
                    saveBlocked()
                    network.connectedClients.value
                        .filter { it.deviceId == target }
                        .forEach { rejectBlocked(it.clientId) }
                }
                sendDeviceList(msg.clientId)
            }
            MANAGER_UNBLOCK -> {
                blocked.remove(obj.optString("targetDeviceId"))
                saveBlocked()
                sendDeviceList(msg.clientId)
            }
            MANAGER_RELEASE -> {
                resetManager()
                reply(msg.clientId, false, "تم إلغاء تسجيل هذا الجهاز كمدير", revoked = true)
            }
        }
    }

    private fun reply(clientId: String, success: Boolean, message: String, token: String = "", revoked: Boolean = false) {
        val payload = JSONObject().apply {
            put("success", success)
            put("message", message)
            put("token", token)
            put("revoked", revoked)
        }.toString()
        network.sendTo(clientId, LinklyMessage(action = MANAGER_RESULT, senderRole = "TV", payload = payload))
    }

    private fun managerClientIds(): List<String> {
        val managerDevice = prefs.getString(KEY_MANAGER_DEVICE, "") ?: ""
        if (managerDevice.isBlank()) return emptyList()
        return network.connectedClients.value.filter { it.deviceId == managerDevice }.map { it.clientId }
    }

    private fun pushDeviceListToManager() {
        managerClientIds().forEach { sendDeviceList(it) }
    }

    @Synchronized
    private fun sendDeviceList(clientId: String) {
        val managerDevice = prefs.getString(KEY_MANAGER_DEVICE, "") ?: ""
        val devices = JSONArray()
        network.connectedClients.value.forEach { c ->
            devices.put(JSONObject().apply {
                put("clientId", c.clientId)
                put("deviceId", c.deviceId)
                put("name", c.name)
                put("ip", c.ip)
                put("connectedAt", c.connectedAt)
                put("isManager", c.deviceId.isNotBlank() && c.deviceId == managerDevice)
            })
        }
        val blockedArr = JSONArray()
        blocked.forEach { (id, name) ->
            blockedArr.put(JSONObject().apply {
                put("deviceId", id)
                put("name", name)
            })
        }
        val payload = JSONObject().apply {
            put("devices", devices)
            put("blocked", blockedArr)
        }.toString()
        network.sendTo(clientId, LinklyMessage(action = MANAGER_DEVICES, senderRole = "TV", payload = payload))
    }

    // ------------------------------------------------------------------ storage helpers

    private fun generatePassword(): String = (100000 + random.nextInt(900000)).toString()

    private fun newToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun loadBlocked(): MutableMap<String, String> {
        val map = LinkedHashMap<String, String>()
        try {
            val arr = JSONArray(prefs.getString(KEY_BLOCKED, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                map[o.getString("deviceId")] = o.optString("name")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading blocked devices", e)
        }
        return map
    }

    private fun saveBlocked() {
        val arr = JSONArray()
        blocked.forEach { (id, name) ->
            arr.put(JSONObject().apply { put("deviceId", id); put("name", name) })
        }
        prefs.edit().putString(KEY_BLOCKED, arr.toString()).apply()
    }

    private fun parse(payload: String): JSONObject = try {
        JSONObject(payload)
    } catch (_: Exception) {
        JSONObject()
    }
}
