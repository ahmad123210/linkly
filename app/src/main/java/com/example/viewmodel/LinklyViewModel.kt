package com.example.viewmodel

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioStreamManager
import com.example.audio.TvAudioPlayer
import com.example.model.AudioPlaybackState
import com.example.model.AudioTrackItem
import com.example.model.ConnectionStatus
import com.example.model.DailyOrdersSummary
import com.example.model.DeviceRole
import com.example.model.DrawPoint
import com.example.model.DrawStroke
import com.example.model.LinklyMessage
import com.example.model.OrderItem
import com.example.model.OrderStatus
import com.example.model.PointerShape
import com.example.model.PointerState
import com.example.model.QuickOrder
import com.example.model.TextDisplayData
import com.example.model.ToolType
import com.example.model.TvScreenMode
import com.example.network.NetworkManager
import com.example.service.LinklyBackgroundService
import com.example.tts.ArabicTtsManager
import com.example.LinklyApplication
import com.example.admin.DeviceAdminManager
import com.example.model.BlockedDevice
import com.example.model.ManagedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LinklyViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LinklyViewModel"
        private const val MAX_TV_IMAGE_SIDE = 1920

        private val RELAYED_ACTIONS = setOf(
            "ORDER_STATUS", "ORDER_CANCEL", "ORDER_HIDE", "ORDER_RESTORE", "ORDER_DELETE",
            "SET_ACTIVE_ORDER_NUMBER", "ORDERS_RESTORE_DAY"
        )
    }

    val networkManager = (application as? com.example.LinklyApplication)?.networkManager ?: NetworkManager(application)
    val audioStreamManager = (application as? com.example.LinklyApplication)?.audioStreamManager ?: AudioStreamManager(application, networkManager)
    val arabicTtsManager = (application as? com.example.LinklyApplication)?.arabicTtsManager ?: ArabicTtsManager(application)
    val tvAudioPlayer = (application as? com.example.LinklyApplication)?.tvAudioPlayer ?: TvAudioPlayer(application)

    // Device role
    private val app = application as LinklyApplication
    val deviceAdmin: DeviceAdminManager = app.deviceAdmin
    private val ioMutex = Mutex()

    private val _deviceRole = MutableStateFlow(app.currentRole())
    val deviceRole: StateFlow<DeviceRole> = _deviceRole.asStateFlow()

    // Active TV Screen Mode - Default is ORDERS as requested
    private val _tvScreenMode = MutableStateFlow(TvScreenMode.ORDERS)
    val tvScreenMode: StateFlow<TvScreenMode> = _tvScreenMode.asStateFlow()

    // Text Display State
    private val _textData = MutableStateFlow(TextDisplayData())
    val textData: StateFlow<TextDisplayData> = _textData.asStateFlow()

    // Whiteboard State
    private val _strokes = MutableStateFlow<List<DrawStroke>>(emptyList())
    val strokes: StateFlow<List<DrawStroke>> = _strokes.asStateFlow()

    private val _currentStroke = MutableStateFlow<DrawStroke?>(null)
    val currentStroke: StateFlow<DrawStroke?> = _currentStroke.asStateFlow()

    private val _activeTool = MutableStateFlow(ToolType.PEN)
    val activeTool: StateFlow<ToolType> = _activeTool.asStateFlow()

    private val _selectedColorHex = MutableStateFlow("#38BDF8")
    val selectedColorHex: StateFlow<String> = _selectedColorHex.asStateFlow()

    private val _selectedStrokeWidth = MutableStateFlow(6f)
    val selectedStrokeWidth: StateFlow<Float> = _selectedStrokeWidth.asStateFlow()

    private val _pointerState = MutableStateFlow(PointerState())
    val pointerState: StateFlow<PointerState> = _pointerState.asStateFlow()

    private val _canvasBackgroundImage = MutableStateFlow<Bitmap?>(null)
    val canvasBackgroundImage: StateFlow<Bitmap?> = _canvasBackgroundImage.asStateFlow()

    // Multi-Image Gallery State (Persisted on TV)
    private val _galleryImages = MutableStateFlow<List<Bitmap>>(emptyList())
    val galleryImages: StateFlow<List<Bitmap>> = _galleryImages.asStateFlow()

    private val _currentGalleryIndex = MutableStateFlow(0)
    val currentGalleryIndex: StateFlow<Int> = _currentGalleryIndex.asStateFlow()

    // Auto-Slideshow State
    private val _isSlideshowRunning = MutableStateFlow(false)
    val isSlideshowRunning: StateFlow<Boolean> = _isSlideshowRunning.asStateFlow()

    private val _slideshowIntervalSec = MutableStateFlow(5)
    val slideshowIntervalSec: StateFlow<Int> = _slideshowIntervalSec.asStateFlow()
    private var slideshowJob: Job? = null

    // Quick Orders State
    private val _orders = MutableStateFlow<List<QuickOrder>>(emptyList())
    val orders: StateFlow<List<QuickOrder>> = _orders.asStateFlow()

    // Auto-calculated next available ticket number (highest existing ticket + 1)
    // Numbering restarts every day.
    val nextTicketNumber: StateFlow<Int> = _orders.map { orderList ->
        maxTicketForDay(orderList, todayStr()) + 1
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    // Daily Orders History
    private val _dailyHistory = MutableStateFlow<List<DailyOrdersSummary>>(emptyList())
    val dailyHistory: StateFlow<List<DailyOrdersSummary>> = _dailyHistory.asStateFlow()

    // Active unserved order number displayed at the top of TV
    private val _activeUnservedOrderNumber = MutableStateFlow("")
    val activeUnservedOrderNumber: StateFlow<String> = _activeUnservedOrderNumber.asStateFlow()

    // Show archived/hidden orders toggle for TV
    private val _showArchivedOrders = MutableStateFlow(false)
    val showArchivedOrders: StateFlow<Boolean> = _showArchivedOrders.asStateFlow()

    // Global Order Sound Alert (Arabic TTS vs Chime Notification)
    private val appPrefs by lazy { application.getSharedPreferences("linkly_app_prefs", Context.MODE_PRIVATE) }
    private val _useTtsForOrders = MutableStateFlow(true)
    val useTtsForOrders: StateFlow<Boolean> = _useTtsForOrders.asStateFlow()

    // Security PIN Pairing (TV PIN default 1234, persisted)
    private val _tvPairingPin = MutableStateFlow(appPrefs.getString("tv_pairing_pin", "1234") ?: "1234")
    val tvPairingPin: StateFlow<String> = _tvPairingPin.asStateFlow()

    // Waiter Phone Identity (e.g. "نادل 1", "نادل 2")
    private val _waiterName = MutableStateFlow(appPrefs.getString("waiter_name", "نادل 1") ?: "نادل 1")
    val waiterName: StateFlow<String> = _waiterName.asStateFlow()

    // Phone Pairing Status
    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _pairingError = MutableStateFlow<String?>(null)
    val pairingError: StateFlow<String?> = _pairingError.asStateFlow()

    // Connected Clients (Waitstaff phones) Count
    val connectedClientsCount: StateFlow<Int> = networkManager.connectedClientsCount

    // Offline Pending Orders Queue
    private val _offlinePendingOrders = MutableStateFlow<List<QuickOrder>>(emptyList())
    val offlinePendingOrders: StateFlow<List<QuickOrder>> = _offlinePendingOrders.asStateFlow()

    // Instant Order Notification Alert on TV Screen
    private val _tvNewOrderAlert = MutableStateFlow<QuickOrder?>(null)
    val tvNewOrderAlert: StateFlow<QuickOrder?> = _tvNewOrderAlert.asStateFlow()
    private var dismissAlertJob: Job? = null

    // Status alert for phone
    private val _phoneOrderStatusAlert = MutableStateFlow<String?>(null)
    val phoneOrderStatusAlert: StateFlow<String?> = _phoneOrderStatusAlert.asStateFlow()

    // Wake Up Alert banner
    private val _showWakeBanner = MutableStateFlow(false)
    val showWakeBanner: StateFlow<Boolean> = _showWakeBanner.asStateFlow()

    // --- Manager role (phone side) ---
    private val _managerToken = MutableStateFlow(appPrefs.getString("manager_token", "") ?: "")
    /** True when this phone is the registered manager. */
    val isManager: StateFlow<Boolean> = _managerToken.map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _managerToken.value.isNotBlank())

    private val _managedDevices = MutableStateFlow<List<ManagedDevice>>(emptyList())
    val managedDevices: StateFlow<List<ManagedDevice>> = _managedDevices.asStateFlow()

    private val _blockedDevices = MutableStateFlow<List<BlockedDevice>>(emptyList())
    val blockedDevices: StateFlow<List<BlockedDevice>> = _blockedDevices.asStateFlow()

    private val _managerMessage = MutableStateFlow<String?>(null)
    val managerMessage: StateFlow<String?> = _managerMessage.asStateFlow()

    private val _isBlockedByTv = MutableStateFlow(false)
    val isBlockedByTv: StateFlow<Boolean> = _isBlockedByTv.asStateFlow()

    // --- Manager role (TV side) ---
    val tvManagerPassword: StateFlow<String> = deviceAdmin.managerPassword
    val tvManagerName: StateFlow<String> = deviceAdmin.managerName

    // Connection Info
    val connectionStatus: StateFlow<ConnectionStatus> = networkManager.connectionStatus
    val connectedDeviceIp: StateFlow<String> = networkManager.connectedDeviceIp

    // Walkie Talkie States
    val isTransmitting: StateFlow<Boolean> = audioStreamManager.isTransmitting
    val isReceiving: StateFlow<Boolean> = audioStreamManager.isReceiving
    val currentAmplitude: StateFlow<Float> = audioStreamManager.currentAmplitude

    // Audio Player States (Synced between TV and Phone)
    private val _syncedPlaylist = MutableStateFlow<List<AudioTrackItem>>(tvAudioPlayer.playlist.value)
    val audioPlaylist: StateFlow<List<AudioTrackItem>> = _syncedPlaylist.asStateFlow()

    private val _syncedAudioPlaybackState = MutableStateFlow(tvAudioPlayer.playbackState.value)
    val audioPlaybackState: StateFlow<AudioPlaybackState> = _syncedAudioPlaybackState.asStateFlow()

    init {
        try {
            LinklyBackgroundService.start(application)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting LinklyBackgroundService", e)
        }

        if (_deviceRole.value == DeviceRole.TV) {
            _isPaired.value = true
        }

        // Phone: introduce itself on every (re)connection, so the TV knows who is who.
        networkManager.onConnectedToServer = {
            if (_deviceRole.value == DeviceRole.PHONE) {
                sendHello()
                if (_managerToken.value.isNotBlank()) sendManagerCommand(DeviceAdminManager.MANAGER_AUTH)
            }
        }

        initializeNetworkRole(_deviceRole.value)

        // TV audio playback state and playlist synchronization observer
        viewModelScope.launch {
            tvAudioPlayer.playbackState.collect { state ->
                _syncedAudioPlaybackState.value = state
                if (_deviceRole.value == DeviceRole.TV) {
                    val json = JSONObject().apply {
                        put("isPlaying", state.isPlaying)
                        put("currentTrackTitle", state.currentTrackTitle)
                        put("volume", state.volume.toDouble())
                    }.toString()
                    networkManager.sendMessage(
                        LinklyMessage(action = "AUDIO_STATE", senderRole = DeviceRole.TV.name, payload = json)
                    )
                }
            }
        }
        viewModelScope.launch {
            tvAudioPlayer.playlist.collect { list ->
                _syncedPlaylist.value = list
                if (_deviceRole.value == DeviceRole.TV) {
                    broadcastAudioPlaylist(list)
                }
            }
        }

        // Load persisted TV cache, orders history, sound alert preference, and offline orders
        _useTtsForOrders.value = appPrefs.getBoolean("use_tts_for_orders", true)
        loadCachedGalleryImages()
        loadOrdersHistory()
        loadOfflineOrders()

        // Auto-reconnection & auto-pairing observer
        viewModelScope.launch {
            networkManager.connectionStatus.collect { status ->
                if (status == ConnectionStatus.CONNECTED && _deviceRole.value == DeviceRole.PHONE) {
                    val savedPin = appPrefs.getString("phone_saved_pin", "") ?: ""
                    if (savedPin.isNotBlank()) {
                        submitPairingPin(savedPin)
                    }
                } else if (status == ConnectionStatus.DISCONNECTED && _deviceRole.value == DeviceRole.PHONE) {
                    _isPaired.value = false
                }
            }
        }

        // Auto-sync offline orders when paired and connected
        viewModelScope.launch {
            _isPaired.collect { paired ->
                if (paired && _deviceRole.value == DeviceRole.PHONE && _offlinePendingOrders.value.isNotEmpty()) {
                    syncPendingOfflineOrders()
                }
            }
        }

        viewModelScope.launch {
            networkManager.incomingMessages.collect { msg ->
                handleIncomingMessage(msg)
            }
        }
    }

    fun setDeviceRole(role: DeviceRole) {
        app.saveRole(role)
        if (_deviceRole.value != role) {
            _deviceRole.value = role
            _isPaired.value = role == DeviceRole.TV
            if (role == DeviceRole.TV) {
                LinklyBackgroundService.start(getApplication())
            } else {
                LinklyBackgroundService.stop(getApplication())
            }
            initializeNetworkRole(role)
        }
    }

    private fun initializeNetworkRole(role: DeviceRole) {
        if (role == DeviceRole.TV) {
            networkManager.startServer()
        } else {
            networkManager.startClient()
        }
    }

    fun dismissWakeBanner() {
        _showWakeBanner.value = false
    }

    // --- Screen Switching ---
    fun switchTvScreen(mode: TvScreenMode) {
        _tvScreenMode.value = mode
        networkManager.sendMessage(
            LinklyMessage(
                action = "SCREEN_SWITCH",
                senderRole = _deviceRole.value.name,
                payload = mode.name
            )
        )
    }

    // --- Text Display ---
    fun updateTextData(data: TextDisplayData) {
        _textData.value = data
        val json = JSONObject().apply {
            put("text", data.text)
            put("fontSizeSp", data.fontSizeSp)
            put("textColorHex", data.textColorHex)
            put("bgColorHex", data.bgColorHex)
            put("alignment", data.alignment)
            put("isTicker", data.isTicker)
            put("showClock", data.showClock)
            put("is12HourClock", data.is12HourClock)
        }.toString()

        networkManager.sendMessage(
            LinklyMessage(
                action = "TEXT_UPDATE",
                senderRole = _deviceRole.value.name,
                payload = json
            )
        )
        switchTvScreen(TvScreenMode.TEXT)
        wakeUpTv("TEXT")
    }

    fun toggleClock(show: Boolean, is12h: Boolean = true) {
        val updated = _textData.value.copy(showClock = show, is12HourClock = is12h)
        updateTextData(updated)
    }

    // --- Whiteboard Actions ---
    fun setTool(tool: ToolType) {
        _activeTool.value = tool
    }

    fun setColorHex(color: String) {
        _selectedColorHex.value = color
    }

    fun setStrokeWidth(width: Float) {
        _selectedStrokeWidth.value = width
    }

    fun onDrawStart(x: Float, y: Float) {
        if (_tvScreenMode.value != TvScreenMode.WHITEBOARD) {
            switchTvScreen(TvScreenMode.WHITEBOARD)
            wakeUpTv("WHITEBOARD")
        }
        if (_activeTool.value == ToolType.POINTER) {
            updatePointer(x, y, true)
            return
        }

        val stroke = DrawStroke(
            toolType = _activeTool.value,
            points = listOf(DrawPoint(x, y)),
            colorHex = _selectedColorHex.value,
            strokeWidth = _selectedStrokeWidth.value,
            startX = x,
            startY = y,
            endX = x,
            endY = y
        )
        _currentStroke.value = stroke
    }

    fun onDrawMove(x: Float, y: Float) {
        if (_activeTool.value == ToolType.POINTER) {
            updatePointer(x, y, true)
            return
        }

        val current = _currentStroke.value ?: return
        when (current.toolType) {
            ToolType.PEN, ToolType.ERASER -> {
                val updatedPoints = current.points + DrawPoint(x, y)
                _currentStroke.value = current.copy(points = updatedPoints)
            }
            ToolType.RECTANGLE, ToolType.CIRCLE -> {
                _currentStroke.value = current.copy(endX = x, endY = y)
            }
            else -> {}
        }
    }

    fun onDrawEnd() {
        if (_activeTool.value == ToolType.POINTER) {
            updatePointer(0.5f, 0.5f, false)
            return
        }

        val completed = _currentStroke.value ?: return
        _strokes.value = _strokes.value + completed
        _currentStroke.value = null

        sendStrokeToNetwork(completed)
    }

    private fun sendStrokeToNetwork(stroke: DrawStroke) {
        val json = JSONObject().apply {
            put("id", stroke.id)
            put("toolType", stroke.toolType.name)
            put("colorHex", stroke.colorHex)
            put("strokeWidth", stroke.strokeWidth)
            put("startX", stroke.startX)
            put("startY", stroke.startY)
            put("endX", stroke.endX)
            put("endY", stroke.endY)
            val ptsArray = JSONArray()
            stroke.points.forEach { pt ->
                ptsArray.put(JSONObject().apply {
                    put("x", pt.x)
                    put("y", pt.y)
                })
            }
            put("points", ptsArray)
        }.toString()

        networkManager.sendMessage(
            LinklyMessage(
                action = "DRAW_STROKE",
                senderRole = _deviceRole.value.name,
                payload = json
            )
        )
    }

    fun updatePointer(x: Float, y: Float, isVisible: Boolean, shape: PointerShape = PointerShape.LASER) {
        val state = PointerState(
            x = x.coerceIn(0f, 1f),
            y = y.coerceIn(0f, 1f),
            isVisible = isVisible,
            shape = shape,
            colorHex = _selectedColorHex.value
        )
        _pointerState.value = state

        val json = JSONObject().apply {
            put("x", state.x)
            put("y", state.y)
            put("isVisible", state.isVisible)
            put("shape", state.shape.name)
            put("colorHex", state.colorHex)
        }.toString()

        networkManager.sendMessage(
            LinklyMessage(
                action = "DRAW_POINTER",
                senderRole = _deviceRole.value.name,
                payload = json
            )
        )
    }

    fun undoStroke() {
        if (_strokes.value.isNotEmpty()) {
            _strokes.value = _strokes.value.dropLast(1)
            networkManager.sendMessage(
                LinklyMessage(
                    action = "DRAW_UNDO",
                    senderRole = _deviceRole.value.name
                )
            )
        }
    }

    fun clearCanvas() {
        _strokes.value = emptyList()
        _currentStroke.value = null
        networkManager.sendMessage(
            LinklyMessage(
                action = "DRAW_CLEAR",
                senderRole = _deviceRole.value.name
            )
        )
    }

    fun setCanvasBackgroundBitmap(bitmap: Bitmap?) {
        _canvasBackgroundImage.value = bitmap
        if (bitmap == null) {
            networkManager.sendMessage(
                LinklyMessage(
                    action = "DRAW_BG",
                    senderRole = _deviceRole.value.name,
                    payload = ""
                )
            )
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val stream = ByteArrayOutputStream()
                    scaleForTv(bitmap).compress(Bitmap.CompressFormat.JPEG, 70, stream)
                    val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    networkManager.sendMessage(
                        LinklyMessage(
                            action = "DRAW_BG",
                            senderRole = _deviceRole.value.name,
                            payload = base64
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error compressing background bitmap", e)
                }
            }
        }
    }

    // --- Multi-Image Gallery & Persistent Storage on TV ---
    fun setGalleryImages(bitmaps: List<Bitmap>) {
        _galleryImages.value = bitmaps
        _currentGalleryIndex.value = 0
        saveGalleryImagesToDisk(bitmaps)

        viewModelScope.launch(Dispatchers.IO) {
            networkManager.sendMessage(
                LinklyMessage(action = "GALLERY_CLEAR", senderRole = _deviceRole.value.name)
            )
            bitmaps.forEachIndexed { index, bmp ->
                try {
                    val stream = ByteArrayOutputStream()
                    scaleForTv(bmp).compress(Bitmap.CompressFormat.JPEG, 70, stream)
                    val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    val payload = JSONObject().apply {
                        put("index", index)
                        put("base64", base64)
                    }.toString()
                    networkManager.sendMessage(
                        LinklyMessage(action = "GALLERY_ADD", senderRole = _deviceRole.value.name, payload = payload)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending gallery image", e)
                }
            }
            switchTvScreen(TvScreenMode.IMAGE)
        }
    }

    fun setGalleryIndex(index: Int) {
        val total = _galleryImages.value.size
        if (total == 0) return
        val safeIndex = index.coerceIn(0, total - 1)
        _currentGalleryIndex.value = safeIndex
        networkManager.sendMessage(
            LinklyMessage(
                action = "GALLERY_INDEX",
                senderRole = _deviceRole.value.name,
                payload = safeIndex.toString()
            )
        )
    }

    fun nextGalleryImage() {
        val total = _galleryImages.value.size
        if (total > 1) {
            val next = (_currentGalleryIndex.value + 1) % total
            setGalleryIndex(next)
        }
    }

    fun prevGalleryImage() {
        val total = _galleryImages.value.size
        if (total > 1) {
            val prev = if (_currentGalleryIndex.value - 1 < 0) total - 1 else _currentGalleryIndex.value - 1
            setGalleryIndex(prev)
        }
    }

    // --- Auto Slideshow Controls ---
    fun toggleSlideshow() {
        val newRunning = !_isSlideshowRunning.value
        _isSlideshowRunning.value = newRunning
        networkManager.sendMessage(
            LinklyMessage(
                action = "SLIDESHOW_CONFIG",
                senderRole = _deviceRole.value.name,
                payload = JSONObject().apply {
                    put("isRunning", newRunning)
                    put("intervalSec", _slideshowIntervalSec.value)
                }.toString()
            )
        )
        if (newRunning) {
            startSlideshowLoop()
        } else {
            slideshowJob?.cancel()
            slideshowJob = null
        }
    }

    fun setSlideshowInterval(seconds: Int) {
        val safeSec = seconds.coerceIn(2, 60)
        _slideshowIntervalSec.value = safeSec
        networkManager.sendMessage(
            LinklyMessage(
                action = "SLIDESHOW_CONFIG",
                senderRole = _deviceRole.value.name,
                payload = JSONObject().apply {
                    put("isRunning", _isSlideshowRunning.value)
                    put("intervalSec", safeSec)
                }.toString()
            )
        )
        if (_isSlideshowRunning.value) {
            startSlideshowLoop()
        }
    }

    private fun startSlideshowLoop() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (isActive && _isSlideshowRunning.value) {
                delay(_slideshowIntervalSec.value * 1000L)
                nextGalleryImage()
            }
        }
    }

    private fun saveGalleryImagesToDisk(bitmaps: List<Bitmap>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(getApplication<Application>().filesDir, "tv_gallery_cache")
                if (!dir.exists()) dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }

                bitmaps.forEachIndexed { idx, bmp ->
                    val file = File(dir, "img_$idx.jpg")
                    FileOutputStream(file).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving gallery to disk", e)
            }
        }
    }

    private fun loadCachedGalleryImages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(getApplication<Application>().filesDir, "tv_gallery_cache")
                if (dir.exists()) {
                    val files = dir.listFiles()?.filter { it.extension == "jpg" }?.sortedBy { it.name } ?: emptyList()
                    val bitmaps = files.mapNotNull { BitmapFactory.decodeFile(it.absolutePath) }
                    if (bitmaps.isNotEmpty()) {
                        _galleryImages.value = bitmaps
                        _currentGalleryIndex.value = 0
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cached gallery", e)
            }
        }
    }

    // --- Walkie-Talkie ---
    fun togglePushToTalk() {
        audioStreamManager.togglePushToTalk(_deviceRole.value.name)
    }

    fun stopPushToTalk() {
        audioStreamManager.stopPushToTalk(_deviceRole.value.name)
    }

    fun toggleArchivedOrders() {
        _showArchivedOrders.value = !_showArchivedOrders.value
    }

    fun dismissPhoneOrderStatusAlert() {
        _phoneOrderStatusAlert.value = null
    }

    fun setUseTtsForOrders(useTts: Boolean) {
        _useTtsForOrders.value = useTts
        appPrefs.edit().putBoolean("use_tts_for_orders", useTts).apply()
        networkManager.sendMessage(
            LinklyMessage(
                action = "SET_TTS_SETTING",
                senderRole = _deviceRole.value.name,
                payload = useTts.toString()
            )
        )
    }

    fun playPhoneNotificationSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(getApplication<Application>(), uri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing phone notification sound", e)
        }
    }

    // --- Active Order Number Control & Queue Progression ---
    fun setActiveUnservedOrderNumber(numberStr: String) {
        _activeUnservedOrderNumber.value = numberStr
        networkManager.sendMessage(
            LinklyMessage(
                action = "SET_ACTIVE_ORDER_NUMBER",
                senderRole = _deviceRole.value.name,
                payload = numberStr
            )
        )
    }

    fun recalculateQueueNumber(ordersList: List<QuickOrder>) {
        // Priority 1: The latest prepared order (READY) that is not hidden and not cancelled
        val readyOrder = ordersList
            .filter { !it.isHidden && !it.isCancelled && it.status == OrderStatus.READY }
            .maxByOrNull { it.timestamp }
        if (readyOrder != null && readyOrder.ticketNumber.isNotBlank()) {
            _activeUnservedOrderNumber.value = readyOrder.ticketNumber
            return
        }
        // Priority 2: Next pending order in sequence
        val pendingOrder = ordersList
            .filter { !it.isHidden && !it.isCancelled && it.status == OrderStatus.PENDING }
            .minByOrNull { it.timestamp }
        if (pendingOrder != null && pendingOrder.ticketNumber.isNotBlank()) {
            _activeUnservedOrderNumber.value = pendingOrder.ticketNumber
            return
        }
    }

    // --- PIN Security & Waiter Methods ---
    fun setTvPairingPin(newPin: String) {
        val clean = newPin.trim().filter { it.isDigit() }.take(6)
        if (clean.length in 4..6) {
            _tvPairingPin.value = clean
            appPrefs.edit().putString("tv_pairing_pin", clean).apply()
        }
    }

    fun setWaiterName(name: String) {
        val clean = name.trim().ifBlank { "نادل 1" }
        _waiterName.value = clean
        appPrefs.edit().putString("waiter_name", clean).apply()
        if (_deviceRole.value == DeviceRole.PHONE) sendHello()
    }

    fun submitPairingPin(pin: String) {
        _pairingError.value = null
        val payloadJson = JSONObject().apply {
            put("pin", pin.trim())
            put("waiterName", _waiterName.value)
            put("deviceId", app.deviceId)
        }.toString()
        networkManager.sendMessage(
            LinklyMessage(
                action = "PAIR_REQUEST",
                senderRole = _deviceRole.value.name,
                payload = payloadJson
            )
        )
    }

    fun clearSavedPinAndUnpair() {
        _isPaired.value = false
        _pairingError.value = null
        appPrefs.edit().remove("phone_saved_pin").apply()
    }

    fun dismissTvNewOrderAlert() {
        _tvNewOrderAlert.value = null
        dismissAlertJob?.cancel()
    }

    // --- Offline Orders Storage & Sync ---
    private fun saveOfflineOrders() {
        val snapshot = _offlinePendingOrders.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(getApplication<Application>().filesDir, "pending_offline_orders.json")
                val rootArr = JSONArray()
                snapshot.forEach { order ->
                    rootArr.put(orderToJson(order))
                }
                ioMutex.withLock {
                    FileOutputStream(file).use { it.write(rootArr.toString().toByteArray()) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving offline orders", e)
            }
        }
    }

    private fun loadOfflineOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(getApplication<Application>().filesDir, "pending_offline_orders.json")
                if (!file.exists()) return@launch
                val jsonStr = file.readText()
                val rootArr = JSONArray(jsonStr)
                val loaded = mutableListOf<QuickOrder>()
                for (i in 0 until rootArr.length()) {
                    loaded.add(parseOrderFromJson(rootArr.getJSONObject(i)))
                }
                _offlinePendingOrders.value = loaded
            } catch (e: Exception) {
                Log.e(TAG, "Error loading offline orders", e)
            }
        }
    }

    fun syncPendingOfflineOrders() {
        val pending = _offlinePendingOrders.value
        if (pending.isEmpty() || networkManager.connectionStatus.value != ConnectionStatus.CONNECTED || !_isPaired.value) {
            return
        }

        viewModelScope.launch {
            pending.forEach { order ->
                val synced = order.copy(isOfflinePending = false)
                transmitOrderJson(synced)
                delay(120L)
            }
            _offlinePendingOrders.value = emptyList()
            saveOfflineOrders()
            // Mark orders as no longer pending locally
            _orders.value = _orders.value.map { it.copy(isOfflinePending = false) }
            saveOrdersHistory()
        }
    }

    private fun transmitOrderJson(order: QuickOrder) {
        val json = orderToJson(order).toString()
        networkManager.sendMessage(
            LinklyMessage(
                action = "ORDER_NEW",
                senderRole = _deviceRole.value.name,
                payload = json
            )
        )
    }

    fun sendOrdersSnapshotToClients() {
        val rootArr = JSONArray()
        _orders.value.forEach { order ->
            rootArr.put(orderToJson(order))
        }
        networkManager.sendMessage(
            LinklyMessage(
                action = "ORDERS_SNAPSHOT",
                senderRole = DeviceRole.TV.name,
                payload = rootArr.toString()
            )
        )
    }

    private fun orderToJson(order: QuickOrder): JSONObject {
        return JSONObject().apply {
            put("id", order.id)
            put("title", order.title)
            put("ticketNumber", order.ticketNumber)
            put("isUrgent", order.isUrgent)
            put("isHidden", order.isHidden)
            put("isCancelled", order.isCancelled)
            put("status", order.status.name)
            put("timestamp", order.timestamp)
            put("dateStr", order.dateStr)
            put("ttsSpeechText", order.ttsSpeechText)
            put("useNotificationSoundOnly", order.useNotificationSoundOnly)
            put("senderName", order.senderName)
            put("senderDeviceId", order.senderDeviceId)
            put("isOfflinePending", order.isOfflinePending)
            val itemsArr = JSONArray()
            order.items.forEach { item ->
                itemsArr.put(JSONObject().apply {
                    put("name", item.name)
                    put("quantity", item.quantity)
                    put("emoji", item.emoji)
                })
            }
            put("items", itemsArr)
        }
    }

    private fun parseOrderFromJson(obj: JSONObject): QuickOrder {
        val items = mutableListOf<OrderItem>()
        val itemsArr = obj.optJSONArray("items")
        if (itemsArr != null) {
            for (j in 0 until itemsArr.length()) {
                val itmObj = itemsArr.getJSONObject(j)
                items.add(
                    OrderItem(
                        name = itmObj.optString("name"),
                        quantity = itmObj.optInt("quantity", 1),
                        emoji = itmObj.optString("emoji", "🍵")
                    )
                )
            }
        }
        val dateStr = obj.optString("dateStr").ifBlank {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(obj.optLong("timestamp", System.currentTimeMillis())))
        }

        return QuickOrder(
            id = obj.optString("id"),
            title = obj.optString("title"),
            ticketNumber = obj.optString("ticketNumber"),
            isUrgent = obj.optBoolean("isUrgent", false),
            isHidden = obj.optBoolean("isHidden", false),
            isCancelled = obj.optBoolean("isCancelled", false),
            status = try { OrderStatus.valueOf(obj.optString("status", "PENDING")) } catch (_: Exception) { OrderStatus.PENDING },
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            dateStr = dateStr,
            ttsSpeechText = obj.optString("ttsSpeechText"),
            useNotificationSoundOnly = obj.optBoolean("useNotificationSoundOnly", false),
            senderName = obj.optString("senderName", "نادل 1"),
            senderDeviceId = obj.optString("senderDeviceId", ""),
            isOfflinePending = obj.optBoolean("isOfflinePending", false),
            items = items
        )
    }

    // --- Quick Orders Handling & Daily History ---
    fun sendQuickOrder(order: QuickOrder) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val orderWithDate = if (order.dateStr.isBlank()) order.copy(dateStr = todayStr) else order
        val sender = if (order.senderName.isNotBlank() && order.senderName != "نادل 1") order.senderName else _waiterName.value
        val isConnectedAndPaired = connectionStatus.value == ConnectionStatus.CONNECTED && _isPaired.value

        val finalOrder = orderWithDate.copy(
            useNotificationSoundOnly = !_useTtsForOrders.value,
            senderName = sender,
            isOfflinePending = !isConnectedAndPaired
        )

        val current = _orders.value.toMutableList()
        current.removeAll { it.id == finalOrder.id }
        current.add(0, finalOrder)
        _orders.value = current

        // Recalculate queue number
        recalculateQueueNumber(current)

        saveOrdersHistory()

        if (isConnectedAndPaired) {
            // The TV only plays a sound for a new order (no pop-up, no forced screen change).
            transmitOrderJson(finalOrder)
        } else {
            // Queue for auto-sync when connection/pairing returns
            val pending = _offlinePendingOrders.value.toMutableList()
            pending.removeAll { it.id == finalOrder.id }
            pending.add(0, finalOrder)
            _offlinePendingOrders.value = pending
            saveOfflineOrders()
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: OrderStatus, hideOrder: Boolean = false) {
        val updated = _orders.value.map {
            if (it.id == orderId) it.copy(status = newStatus, isHidden = if (hideOrder) true else it.isHidden) else it
        }
        _orders.value = updated
        // Advance queue number: when READY, queue number updates to this order; when DELIVERED, it moves to the next ready/pending order!
        recalculateQueueNumber(updated)
        saveOrdersHistory()

        val json = JSONObject().apply {
            put("id", orderId)
            put("status", newStatus.name)
            put("isHidden", hideOrder)
            put("activeQueueNumber", _activeUnservedOrderNumber.value)
        }.toString()

        networkManager.sendMessage(
            LinklyMessage(
                action = "ORDER_STATUS",
                senderRole = _deviceRole.value.name,
                payload = json
            )
        )
    }

    fun cancelOrder(orderId: String) {
        val updated = _orders.value.map {
            if (it.id == orderId) it.copy(status = OrderStatus.CANCELLED, isCancelled = true) else it
        }
        _orders.value = updated
        recalculateQueueNumber(updated)
        saveOrdersHistory()

        val json = JSONObject().apply {
            put("id", orderId)
            put("activeQueueNumber", _activeUnservedOrderNumber.value)
        }.toString()

        networkManager.sendMessage(
            LinklyMessage(
                action = "ORDER_CANCEL",
                senderRole = _deviceRole.value.name,
                payload = json
            )
        )

        // Also trigger cancel alert locally if in TV mode
        if (_deviceRole.value == DeviceRole.TV) {
            tvAudioPlayer.playOrderCancelAlert()
        }
    }

    fun hideOrder(orderId: String) {
        val updated = _orders.value.map {
            if (it.id == orderId) it.copy(isHidden = true) else it
        }
        _orders.value = updated
        recalculateQueueNumber(updated)
        saveOrdersHistory()

        val json = JSONObject().apply {
            put("id", orderId)
            put("isHidden", true)
            put("activeQueueNumber", _activeUnservedOrderNumber.value)
        }.toString()

        networkManager.sendMessage(
            LinklyMessage(
                action = "ORDER_HIDE",
                senderRole = _deviceRole.value.name,
                payload = json
            )
        )
    }

    fun restoreOrder(orderId: String) {
        val updated = _orders.value.map {
            if (it.id == orderId) it.copy(isHidden = false, isCancelled = false, status = OrderStatus.PENDING) else it
        }
        _orders.value = updated
        recalculateQueueNumber(updated)
        saveOrdersHistory()

        val json = JSONObject().apply {
            put("id", orderId)
            put("activeQueueNumber", _activeUnservedOrderNumber.value)
        }.toString()

        networkManager.sendMessage(
            LinklyMessage(
                action = "ORDER_RESTORE",
                senderRole = _deviceRole.value.name,
                payload = json
            )
        )
    }

    fun deleteOrderPermanently(orderId: String) {
        val updated = _orders.value.filter { it.id != orderId }
        _orders.value = updated
        recalculateQueueNumber(updated)
        saveOrdersHistory()

        val json = JSONObject().apply {
            put("id", orderId)
            put("activeQueueNumber", _activeUnservedOrderNumber.value)
        }.toString()

        networkManager.sendMessage(
            LinklyMessage(
                action = "ORDER_DELETE",
                senderRole = _deviceRole.value.name,
                payload = json
            )
        )
    }

    fun restoreDailyOrders(dateStr: String) {
        applyRestoreDay(dateStr)
        networkManager.sendMessage(
            LinklyMessage(
                action = "ORDERS_RESTORE_DAY",
                senderRole = _deviceRole.value.name,
                payload = JSONObject().apply { put("dateStr", dateStr) }.toString()
            )
        )
        switchTvScreen(TvScreenMode.ORDERS)
    }

    /**
     * Brings a day's orders back on screen. Other days stay in the history
     * (the old version replaced the whole history with that one day).
     */
    private fun applyRestoreDay(dateStr: String) {
        if (_orders.value.none { it.dateStr == dateStr }) return
        _orders.value = _orders.value.map { if (it.dateStr == dateStr) it.copy(isHidden = false) else it }
        val lastTicket = maxTicketForDay(_orders.value, dateStr)
        if (lastTicket > 0) {
            _activeUnservedOrderNumber.value = "$lastTicket"
        }
        saveOrdersHistory()
    }

    private fun saveOrdersHistory() {
        val snapshot = _orders.value
        updateDailyHistoryGrouping(snapshot)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rootArr = JSONArray()
                snapshot.forEach { order ->
                    rootArr.put(orderToJson(order))
                }
                ioMutex.withLock {
                    // Write to a temp file then rename, so a crash mid-write never corrupts the history.
                    val dir = getApplication<Application>().filesDir
                    val tmp = File(dir, "orders_history.json.tmp")
                    FileOutputStream(tmp).use { it.write(rootArr.toString().toByteArray()) }
                    tmp.renameTo(File(dir, "orders_history.json"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving orders history", e)
            }
        }
    }

    private fun loadOrdersHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(getApplication<Application>().filesDir, "orders_history.json")
                if (!file.exists()) return@launch

                val jsonStr = file.readText()
                val rootArr = JSONArray(jsonStr)
                val loaded = mutableListOf<QuickOrder>()

                for (i in 0 until rootArr.length()) {
                    loaded.add(parseOrderFromJson(rootArr.getJSONObject(i)))
                }

                _orders.value = loaded
                updateDailyHistoryGrouping(loaded)

                // Set initial active unserved order number from latest pending ticket
                val latestPending = loaded.find { !it.isHidden && it.status == OrderStatus.PENDING && !it.isCancelled }
                if (latestPending != null && latestPending.ticketNumber.isNotBlank()) {
                    _activeUnservedOrderNumber.value = latestPending.ticketNumber
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading orders history", e)
            }
        }
    }

    private fun updateDailyHistoryGrouping(ordersList: List<QuickOrder>) {
        val grouped = ordersList.groupBy { it.dateStr }
        val summaries = grouped.map { (dateStr, list) ->
            val sampleTime = list.firstOrNull()?.timestamp ?: System.currentTimeMillis()
            val formatted = try {
                SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(Date(sampleTime))
            } catch (_: Exception) {
                dateStr
            }
            DailyOrdersSummary(
                dateStr = dateStr,
                formattedDateAr = formatted,
                orders = list
            )
        }.sortedByDescending { it.dateStr }
        _dailyHistory.value = summaries
    }

    // --- Background Audio Remote Controls (Plays strictly on TV, controlled from Phone) ---
    fun broadcastAudioPlaylist(list: List<AudioTrackItem> = tvAudioPlayer.playlist.value) {
        try {
            val arr = JSONArray()
            list.forEach { item ->
                arr.put(JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("fileName", item.fileName)
                })
            }
            networkManager.sendMessage(
                LinklyMessage(action = "AUDIO_PLAYLIST_SYNC", senderRole = DeviceRole.TV.name, payload = arr.toString())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting audio playlist", e)
        }
    }

    fun toggleAudioPlayback() {
        if (_deviceRole.value == DeviceRole.TV) {
            tvAudioPlayer.togglePlayPause()
        } else {
            // When on Phone: send toggle command to TV, never play on phone
            networkManager.sendMessage(
                LinklyMessage(
                    action = "AUDIO_TOGGLE",
                    senderRole = _deviceRole.value.name
                )
            )
        }
    }

    fun playAudioTrack(trackId: String) {
        if (_deviceRole.value == DeviceRole.TV) {
            tvAudioPlayer.playTrack(trackId)
        } else {
            // When on Phone: command the TV to play this track, never play on phone
            networkManager.sendMessage(
                LinklyMessage(
                    action = "AUDIO_TRACK",
                    senderRole = _deviceRole.value.name,
                    payload = trackId
                )
            )
        }
    }

    fun uploadAudioFile(fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            try {
                val cleanName = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                if (_deviceRole.value == DeviceRole.TV) {
                    val targetFile = File(getApplication<Application>().filesDir, "linkly_audio/$cleanName")
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { it.write(bytes) }
                    tvAudioPlayer.refreshPlaylist()
                    broadcastAudioPlaylist()
                    return@launch
                }

                // If on Phone, transmit in reliable 32KB chunks so large audio files transfer safely
                val chunkSize = 32 * 1024
                val totalChunks = (bytes.size + chunkSize - 1) / chunkSize

                val startPayload = JSONObject().apply {
                    put("fileName", cleanName)
                    put("totalChunks", totalChunks)
                    put("totalBytes", bytes.size)
                }.toString()

                networkManager.sendMessage(
                    LinklyMessage(action = "AUDIO_UPLOAD_START", senderRole = _deviceRole.value.name, payload = startPayload)
                )

                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = (start + chunkSize).coerceAtMost(bytes.size)
                    val chunk = bytes.copyOfRange(start, end)
                    val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)

                    val chunkPayload = JSONObject().apply {
                        put("fileName", cleanName)
                        put("chunkIndex", i)
                        put("data", b64)
                    }.toString()

                    networkManager.sendMessage(
                        LinklyMessage(action = "AUDIO_UPLOAD_CHUNK", senderRole = _deviceRole.value.name, payload = chunkPayload)
                    )
                    delay(30L)
                }

                val finishPayload = JSONObject().apply {
                    put("fileName", cleanName)
                }.toString()

                networkManager.sendMessage(
                    LinklyMessage(action = "AUDIO_UPLOAD_FINISH", senderRole = _deviceRole.value.name, payload = finishPayload)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading audio file", e)
            }
        }
    }

    fun wakeUpTv(destination: String = "ORDERS") {
        networkManager.sendMessage(
            LinklyMessage(
                action = "WAKE_UP",
                senderRole = _deviceRole.value.name,
                payload = destination
            )
        )
    }

    // --- Message Dispatcher ---
    private fun handleIncomingMessage(msg: LinklyMessage) {
        // TV: a change made on one phone is passed on to the other phones, so all lists match.
        if (_deviceRole.value == DeviceRole.TV && msg.clientId.isNotEmpty() && msg.action in RELAYED_ACTIONS) {
            networkManager.sendToAllExcept(
                msg.clientId,
                LinklyMessage(action = msg.action, senderRole = DeviceRole.TV.name, payload = msg.payload)
            )
        }
        when (msg.action) {
            DeviceAdminManager.MANAGER_RESULT -> if (_deviceRole.value == DeviceRole.PHONE) handleManagerResult(msg.payload)
            DeviceAdminManager.MANAGER_DEVICES -> if (_deviceRole.value == DeviceRole.PHONE) handleManagerDevices(msg.payload)
            DeviceAdminManager.DEVICE_BLOCKED -> if (_deviceRole.value == DeviceRole.PHONE) {
                _isBlockedByTv.value = true
                _isPaired.value = false
                _pairingError.value = "تم حظر هذا الجهاز من قبل المدير"
                networkManager.reconnectDelayMs = 30_000L
            }
            "SCREEN_SWITCH" -> {
                try {
                    _tvScreenMode.value = TvScreenMode.valueOf(msg.payload)
                } catch (_: Exception) {}
            }
            "TEXT_UPDATE" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    _textData.value = TextDisplayData(
                        text = obj.optString("text"),
                        fontSizeSp = obj.optDouble("fontSizeSp", 56.0).toFloat(),
                        textColorHex = obj.optString("textColorHex", "#FFFFFF"),
                        bgColorHex = obj.optString("bgColorHex", "#0A0E17"),
                        alignment = obj.optString("alignment", "CENTER"),
                        isTicker = obj.optBoolean("isTicker", false),
                        showClock = obj.optBoolean("showClock", false),
                        is12HourClock = obj.optBoolean("is12HourClock", true)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing text update", e)
                }
            }
            "DRAW_STROKE" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    val pts = mutableListOf<DrawPoint>()
                    val ptsArr = obj.optJSONArray("points")
                    if (ptsArr != null) {
                        for (i in 0 until ptsArr.length()) {
                            val ptObj = ptsArr.getJSONObject(i)
                            pts.add(DrawPoint(ptObj.getDouble("x").toFloat(), ptObj.getDouble("y").toFloat()))
                        }
                    }
                    val stroke = DrawStroke(
                        id = obj.optString("id"),
                        toolType = ToolType.valueOf(obj.optString("toolType", "PEN")),
                        colorHex = obj.optString("colorHex", "#FFFFFF"),
                        strokeWidth = obj.optDouble("strokeWidth", 6.0).toFloat(),
                        startX = obj.optDouble("startX", 0.0).toFloat(),
                        startY = obj.optDouble("startY", 0.0).toFloat(),
                        endX = obj.optDouble("endX", 0.0).toFloat(),
                        endY = obj.optDouble("endY", 0.0).toFloat(),
                        points = pts
                    )
                    _strokes.value = _strokes.value + stroke
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing draw stroke", e)
                }
            }
            "DRAW_POINTER" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    _pointerState.value = PointerState(
                        x = obj.optDouble("x", 0.5).toFloat(),
                        y = obj.optDouble("y", 0.5).toFloat(),
                        isVisible = obj.optBoolean("isVisible", false),
                        shape = PointerShape.valueOf(obj.optString("shape", "LASER")),
                        colorHex = obj.optString("colorHex", "#EF4444")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing pointer state", e)
                }
            }
            "DRAW_CLEAR" -> {
                _strokes.value = emptyList()
                _currentStroke.value = null
            }
            "DRAW_UNDO" -> {
                if (_strokes.value.isNotEmpty()) {
                    _strokes.value = _strokes.value.dropLast(1)
                }
            }
            "DRAW_BG" -> {
                if (msg.payload.isBlank()) {
                    _canvasBackgroundImage.value = null
                } else {
                    try {
                        val bytes = Base64.decode(msg.payload, Base64.NO_WRAP)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        _canvasBackgroundImage.value = bmp
                    } catch (e: Exception) {
                        Log.e(TAG, "Error decoding background image", e)
                    }
                }
            }
            "GALLERY_CLEAR" -> {
                _galleryImages.value = emptyList()
                _currentGalleryIndex.value = 0
            }
            "GALLERY_ADD" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    val b64 = obj.optString("base64")
                    if (b64.isNotBlank()) {
                        val bytes = Base64.decode(b64, Base64.NO_WRAP)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            val updated = _galleryImages.value + bmp
                            _galleryImages.value = updated
                            // Save latest images persistently on TV!
                            if (_deviceRole.value == DeviceRole.TV) {
                                saveGalleryImagesToDisk(updated)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding gallery image", e)
                }
            }
            "GALLERY_INDEX" -> {
                try {
                    val idx = msg.payload.toIntOrNull() ?: 0
                    _currentGalleryIndex.value = idx
                } catch (_: Exception) {}
            }
            "SLIDESHOW_CONFIG" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    val isRunning = obj.optBoolean("isRunning", false)
                    val interval = obj.optInt("intervalSec", 5)
                    _isSlideshowRunning.value = isRunning
                    _slideshowIntervalSec.value = interval
                    if (isRunning) {
                        startSlideshowLoop()
                    } else {
                        slideshowJob?.cancel()
                        slideshowJob = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing slideshow config", e)
                }
            }
            // On the TV the background service plays live voice; handling it here too played every chunk twice.
            "VOICE_PTT" -> {
                if (_deviceRole.value == DeviceRole.PHONE) audioStreamManager.handlePttState(msg.payload)
            }
            "VOICE_CHUNK" -> {
                if (_deviceRole.value == DeviceRole.PHONE) audioStreamManager.handleIncomingAudioChunk(msg.payload)
            }
            "PAIR_REQUEST" -> {
                if (_deviceRole.value == DeviceRole.TV) {
                    try {
                        val obj = JSONObject(msg.payload)
                        val submittedPin = obj.optString("pin").trim()
                        val isSuccess = submittedPin == _tvPairingPin.value
                        val resultJson = JSONObject().apply {
                            put("success", isSuccess)
                            put("pin", submittedPin)
                            put("message", if (isSuccess) "تم الاقتران بنجاح مع الشاشة" else "رمز الاقتران (PIN) غير صحيح!")
                        }.toString()

                        // Reply only to the phone that asked (it used to go to every phone, PIN included).
                        val pairResult = LinklyMessage(
                            action = "PAIR_RESULT",
                            senderRole = DeviceRole.TV.name,
                            payload = resultJson
                        )
                        if (msg.clientId.isNotEmpty()) {
                            networkManager.sendTo(msg.clientId, pairResult)
                        } else {
                            networkManager.sendMessage(pairResult)
                        }

                        if (isSuccess) {
                            sendOrdersSnapshotToClients()
                            broadcastAudioPlaylist()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling pair request", e)
                    }
                }
            }
            "PAIR_RESULT" -> {
                if (_deviceRole.value == DeviceRole.PHONE) {
                    try {
                        val obj = JSONObject(msg.payload)
                        val success = obj.optBoolean("success", false)
                        val pin = obj.optString("pin")
                        if (success) {
                            _isPaired.value = true
                            _isBlockedByTv.value = false
                            _pairingError.value = null
                            appPrefs.edit().putString("phone_saved_pin", pin).apply()
                            syncPendingOfflineOrders()
                        } else {
                            _isPaired.value = false
                            _pairingError.value = obj.optString("message", "رمز الاقتران غير صحيح")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling pair result", e)
                    }
                }
            }
            "ORDERS_SNAPSHOT" -> {
                if (_deviceRole.value == DeviceRole.PHONE) {
                    try {
                        val rootArr = JSONArray(msg.payload)
                        val loaded = mutableListOf<QuickOrder>()
                        for (i in 0 until rootArr.length()) {
                            loaded.add(parseOrderFromJson(rootArr.getJSONObject(i)))
                        }
                        val offlinePending = _offlinePendingOrders.value
                        val combined = (offlinePending + loaded).distinctBy { it.id }
                        _orders.value = combined
                        recalculateQueueNumber(combined)
                        saveOrdersHistory()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing orders snapshot", e)
                    }
                }
            }
            "ORDER_NEW" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    var order = parseOrderFromJson(obj)
                    var corrected = false

                    // The TV is the single source of ticket numbers: if two waiters picked the same
                    // number for today, the later order gets the next free one.
                    if (_deviceRole.value == DeviceRole.TV) {
                        val day = order.dateStr.ifBlank { todayStr() }
                        val clash = order.ticketNumber.isBlank() || _orders.value.any {
                            it.id != order.id && it.dateStr == day && !it.isCancelled &&
                                it.ticketNumber.trimStart('#') == order.ticketNumber.trimStart('#')
                        }
                        if (clash) {
                            val next = maxTicketForDay(_orders.value.filter { it.id != order.id }, day) + 1
                            order = order.copy(ticketNumber = next.toString(), dateStr = day)
                            corrected = true
                        }
                    }

                    val current = _orders.value.toMutableList()
                    current.removeAll { it.id == order.id }
                    current.add(0, order)
                    _orders.value = current

                    // Update active unserved order number
                    recalculateQueueNumber(_orders.value)
                    saveOrdersHistory()

                    // TV: the background service plays the chime (sound only, no pop-up).
                    // Here we only send the (possibly renumbered) order to every phone, sender included.
                    if (_deviceRole.value == DeviceRole.TV) {
                        networkManager.sendMessage(
                            LinklyMessage(
                                action = "ORDER_NEW",
                                senderRole = DeviceRole.TV.name,
                                payload = if (corrected) orderToJson(order).toString() else msg.payload
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing new order", e)
                }
            }
            "ORDER_STATUS" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    val id = obj.optString("id")
                    val status = OrderStatus.valueOf(obj.optString("status"))
                    val isHidden = obj.optBoolean("isHidden", false)
                    val syncQueue = obj.optString("activeQueueNumber")
                    _orders.value = _orders.value.map {
                        if (it.id == id) it.copy(status = status, isHidden = if (isHidden) true else it.isHidden) else it
                    }
                    if (syncQueue.isNotBlank()) {
                        _activeUnservedOrderNumber.value = syncQueue
                    } else {
                        recalculateQueueNumber(_orders.value)
                    }
                    saveOrdersHistory()

                    val matched = _orders.value.find { it.id == id }
                    val orderLabel = if (matched?.ticketNumber?.isNotBlank() == true) "طلب دور #${matched.ticketNumber}" else (matched?.title ?: "طلبك")
                    _phoneOrderStatusAlert.value = "تحديث: $orderLabel أصبح (${status.titleAr})!"

                    // Play audible notification on Phone!
                    if (_deviceRole.value == DeviceRole.PHONE) {
                        playPhoneNotificationSound()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating order status", e)
                }
            }
            "ORDER_CANCEL" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    val id = obj.optString("id")
                    val syncQueue = obj.optString("activeQueueNumber")
                    _orders.value = _orders.value.map {
                        if (it.id == id) it.copy(status = OrderStatus.CANCELLED, isCancelled = true) else it
                    }
                    if (syncQueue.isNotBlank()) {
                        _activeUnservedOrderNumber.value = syncQueue
                    } else {
                        recalculateQueueNumber(_orders.value)
                    }
                    saveOrdersHistory()

                    // Trigger distinct cancel warning tone on TV, or phone notification
                    if (_deviceRole.value == DeviceRole.TV) {
                        tvAudioPlayer.playOrderCancelAlert()
                    } else if (_deviceRole.value == DeviceRole.PHONE) {
                        val matched = _orders.value.find { it.id == id }
                        val orderLabel = if (matched?.ticketNumber?.isNotBlank() == true) "طلب دور #${matched.ticketNumber}" else (matched?.title ?: "الطلب")
                        _phoneOrderStatusAlert.value = "تنبيه: تم إلغاء $orderLabel!"
                        playPhoneNotificationSound()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing cancel order", e)
                }
            }
            "ORDER_HIDE" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    val id = obj.optString("id")
                    val syncQueue = obj.optString("activeQueueNumber")
                    _orders.value = _orders.value.map {
                        if (it.id == id) it.copy(isHidden = true) else it
                    }
                    if (syncQueue.isNotBlank()) {
                        _activeUnservedOrderNumber.value = syncQueue
                    } else {
                        recalculateQueueNumber(_orders.value)
                    }
                    saveOrdersHistory()
                } catch (_: Exception) {}
            }
            "ORDER_RESTORE" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    val id = obj.optString("id")
                    val syncQueue = obj.optString("activeQueueNumber")
                    _orders.value = _orders.value.map {
                        if (it.id == id) it.copy(isHidden = false, isCancelled = false, status = OrderStatus.PENDING) else it
                    }
                    if (syncQueue.isNotBlank()) {
                        _activeUnservedOrderNumber.value = syncQueue
                    } else {
                        recalculateQueueNumber(_orders.value)
                    }
                    saveOrdersHistory()
                } catch (_: Exception) {}
            }
            "ORDER_DELETE" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    val id = obj.optString("id")
                    val syncQueue = obj.optString("activeQueueNumber")
                    _orders.value = _orders.value.filter { it.id != id }
                    if (syncQueue.isNotBlank()) {
                        _activeUnservedOrderNumber.value = syncQueue
                    } else {
                        recalculateQueueNumber(_orders.value)
                    }
                    saveOrdersHistory()
                } catch (_: Exception) {}
            }
            "SET_TTS_SETTING" -> {
                val enabled = msg.payload.toBooleanStrictOrNull() ?: true
                _useTtsForOrders.value = enabled
                appPrefs.edit().putBoolean("use_tts_for_orders", enabled).apply()
            }
            "SET_ACTIVE_ORDER_NUMBER" -> {
                _activeUnservedOrderNumber.value = msg.payload
            }
            "ORDERS_RESTORE_DAY" -> {
                try {
                    val obj = JSONObject(msg.payload)
                    val dateStr = obj.optString("dateStr")
                    // Apply only. Re-broadcasting here made the TV and phone bounce the message forever.
                    applyRestoreDay(dateStr)
                } catch (_: Exception) {}
            }
            "AUDIO_TOGGLE" -> {
                if (_deviceRole.value == DeviceRole.TV) {
                    tvAudioPlayer.togglePlayPause()
                }
            }
            "AUDIO_PLAY" -> {
                if (_deviceRole.value == DeviceRole.TV) {
                    tvAudioPlayer.playlist.value.firstOrNull()?.let {
                        tvAudioPlayer.playTrack(it.id)
                    }
                }
            }
            "AUDIO_PAUSE" -> {
                if (_deviceRole.value == DeviceRole.TV) {
                    tvAudioPlayer.stopCurrentPlayback()
                }
            }
            "AUDIO_TRACK" -> {
                if (_deviceRole.value == DeviceRole.TV) {
                    tvAudioPlayer.playTrack(msg.payload)
                }
            }
            "AUDIO_UPLOAD_START" -> {
                if (_deviceRole.value == DeviceRole.TV) {
                    try {
                        val obj = JSONObject(msg.payload)
                        val fn = safeAudioFileName(obj.getString("fileName")) ?: return
                        val tempFile = File(getApplication<Application>().filesDir, "linkly_audio/$fn.tmp")
                        tempFile.parentFile?.mkdirs()
                        if (tempFile.exists()) tempFile.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error on AUDIO_UPLOAD_START", e)
                    }
                }
            }
            "AUDIO_UPLOAD_CHUNK" -> {
                if (_deviceRole.value == DeviceRole.TV) {
                    try {
                        val obj = JSONObject(msg.payload)
                        val fn = safeAudioFileName(obj.getString("fileName")) ?: return
                        val b64 = obj.getString("data")
                        val bytes = Base64.decode(b64, Base64.NO_WRAP)
                        val tempFile = File(getApplication<Application>().filesDir, "linkly_audio/$fn.tmp")
                        FileOutputStream(tempFile, true).use { it.write(bytes) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error on AUDIO_UPLOAD_CHUNK", e)
                    }
                }
            }
            "AUDIO_UPLOAD_FINISH" -> {
                if (_deviceRole.value == DeviceRole.TV) {
                    try {
                        val obj = JSONObject(msg.payload)
                        val fn = safeAudioFileName(obj.getString("fileName")) ?: return
                        val tempFile = File(getApplication<Application>().filesDir, "linkly_audio/$fn.tmp")
                        val destFile = File(getApplication<Application>().filesDir, "linkly_audio/$fn")
                        if (tempFile.exists()) {
                            if (destFile.exists()) destFile.delete()
                            tempFile.renameTo(destFile)
                        }
                        tvAudioPlayer.refreshPlaylist()
                        broadcastAudioPlaylist()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error on AUDIO_UPLOAD_FINISH", e)
                    }
                }
            }
            "AUDIO_STATE" -> {
                if (_deviceRole.value == DeviceRole.PHONE) {
                    try {
                        val obj = JSONObject(msg.payload)
                        _syncedAudioPlaybackState.value = AudioPlaybackState(
                            isPlaying = obj.optBoolean("isPlaying", false),
                            currentTrackTitle = obj.optString("currentTrackTitle", ""),
                            volume = obj.optDouble("volume", 0.7).toFloat()
                        )
                    } catch (_: Exception) {}
                }
            }
            "AUDIO_PLAYLIST_SYNC" -> {
                if (_deviceRole.value == DeviceRole.PHONE) {
                    try {
                        val arr = JSONArray(msg.payload)
                        val list = mutableListOf<AudioTrackItem>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(AudioTrackItem(id = o.getString("id"), title = o.getString("title"), fileName = o.getString("fileName")))
                        }
                        _syncedPlaylist.value = list
                    } catch (_: Exception) {}
                }
            }
            "WAKE_UP" -> {
                try {
                    val destMode = if (msg.payload in listOf("ORDERS", "TEXT", "WHITEBOARD", "IMAGE")) {
                        try { TvScreenMode.valueOf(msg.payload) } catch (_: Exception) { TvScreenMode.ORDERS }
                    } else {
                        TvScreenMode.ORDERS
                    }
                    // Only switch what is shown. The background service brings the screen
                    // to the front and plays the sound, so nothing happens twice.
                    if (_deviceRole.value == DeviceRole.TV) {
                        _tvScreenMode.value = destMode
                    }
                    _showWakeBanner.value = false
                } catch (e: Exception) {
                    Log.e(TAG, "Error on wake up event", e)
                }
            }
        }
    }

    // --- Helpers ---
    private fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun maxTicketForDay(list: List<QuickOrder>, day: String): Int =
        list.filter { it.dateStr == day }
            .mapNotNull { it.ticketNumber.trimStart('#').toIntOrNull() }
            .maxOrNull() ?: 0

    /** Phone photos can be 12+ MP; the TV never needs more than 1920 px on the long side. */
    private fun scaleForTv(bmp: Bitmap): Bitmap {
        val longSide = maxOf(bmp.width, bmp.height)
        if (longSide <= MAX_TV_IMAGE_SIDE) return bmp
        val ratio = MAX_TV_IMAGE_SIDE.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * ratio).toInt().coerceAtLeast(1),
            (bmp.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    /** Keeps uploaded audio inside linkly_audio/: strips any folder part such as "../". */
    private fun safeAudioFileName(raw: String): String? {
        val name = File(raw.replace('\\', '/')).name
            .replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .trim()
        if (name.isBlank() || name == "." || name == ".." || name.length > 120) return null
        return name
    }

    // --- Manager role (phone side) ---
    private fun sendHello() {
        val payload = JSONObject().apply {
            put("deviceId", app.deviceId)
            put("name", _waiterName.value)
        }.toString()
        networkManager.sendMessage(LinklyMessage(action = DeviceAdminManager.HELLO, senderRole = DeviceRole.PHONE.name, payload = payload))
    }

    private fun sendManagerCommand(action: String, extra: JSONObject.() -> Unit = {}) {
        val payload = JSONObject().apply {
            put("deviceId", app.deviceId)
            put("name", _waiterName.value)
            put("token", _managerToken.value)
            extra()
        }.toString()
        networkManager.sendMessage(LinklyMessage(action = action, senderRole = DeviceRole.PHONE.name, payload = payload))
    }

    /** First time only: register this phone as the manager with the password shown on the TV. */
    fun managerLogin(password: String) {
        _managerMessage.value = null
        if (connectionStatus.value != ConnectionStatus.CONNECTED) {
            _managerMessage.value = "الهاتف غير متصل بالتلفاز"
            return
        }
        sendManagerCommand(DeviceAdminManager.MANAGER_LOGIN) { put("password", password.trim()) }
    }

    fun refreshManagedDevices() {
        if (_managerToken.value.isNotBlank()) sendManagerCommand(DeviceAdminManager.MANAGER_LIST)
    }

    fun blockDevice(device: ManagedDevice) {
        sendManagerCommand(DeviceAdminManager.MANAGER_BLOCK) {
            put("targetDeviceId", device.deviceId)
            put("targetName", device.name)
        }
    }

    fun unblockDevice(deviceId: String) {
        sendManagerCommand(DeviceAdminManager.MANAGER_UNBLOCK) { put("targetDeviceId", deviceId) }
    }

    fun releaseManager() {
        sendManagerCommand(DeviceAdminManager.MANAGER_RELEASE)
        clearManagerToken()
    }

    fun clearManagerMessage() {
        _managerMessage.value = null
    }

    private fun clearManagerToken() {
        _managerToken.value = ""
        appPrefs.edit().remove("manager_token").apply()
        _managedDevices.value = emptyList()
        _blockedDevices.value = emptyList()
    }

    private fun handleManagerResult(payload: String) {
        try {
            val obj = JSONObject(payload)
            val success = obj.optBoolean("success", false)
            val token = obj.optString("token")
            val message = obj.optString("message")
            if (success && token.isNotBlank()) {
                _managerToken.value = token
                appPrefs.edit().putString("manager_token", token).apply()
            } else if (obj.optBoolean("revoked", false)) {
                clearManagerToken()
            }
            _managerMessage.value = message.ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing manager result", e)
        }
    }

    private fun handleManagerDevices(payload: String) {
        try {
            val obj = JSONObject(payload)
            val devices = mutableListOf<ManagedDevice>()
            val arr = obj.optJSONArray("devices") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                devices.add(
                    ManagedDevice(
                        clientId = o.optString("clientId"),
                        deviceId = o.optString("deviceId"),
                        name = o.optString("name"),
                        ip = o.optString("ip"),
                        connectedAt = o.optLong("connectedAt"),
                        isManager = o.optBoolean("isManager", false)
                    )
                )
            }
            val blocked = mutableListOf<BlockedDevice>()
            val barr = obj.optJSONArray("blocked") ?: JSONArray()
            for (i in 0 until barr.length()) {
                val o = barr.getJSONObject(i)
                blocked.add(BlockedDevice(deviceId = o.optString("deviceId"), name = o.optString("name")))
            }
            _managedDevices.value = devices
            _blockedDevices.value = blocked
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing device list", e)
        }
    }

    // --- Manager role (TV side) ---
    fun resetTvManager() = deviceAdmin.resetManager()

    fun clearAllBlockedDevices() = deviceAdmin.clearAllBlocks()

    override fun onCleared() {
        super.onCleared()
        slideshowJob?.cancel()
        dismissAlertJob?.cancel()
        // Note: Do NOT call networkManager.stopAll() or release singletons here,
        // because LinklyBackgroundService maintains the persistent connection and audio streams
        // in the background even if MainActivity is stopped/recreated.
    }
}
