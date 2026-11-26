package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.example.model.ConnectedClient
import com.example.model.ConnectionStatus
import com.example.model.LinklyMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Line-delimited JSON transport between phones (clients) and the TV (server).
 *
 * Each connection has its own outgoing queue and writer, so one slow or dead phone
 * can never delay the others. A heartbeat (PING every 15 s) plus a read timeout
 * removes dead connections that TCP alone would keep open for hours.
 */
class NetworkManager(private val context: Context) {

    companion object {
        const val TAG = "LinklyNetwork"
        const val SERVICE_TYPE = "_linkly._tcp."
        const val DEFAULT_PORT = 8888
        const val ACTION_PING = "PING"

        private const val HEARTBEAT_MS = 15_000L
        private const val READ_TIMEOUT_MS = 45_000
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val QUEUE_CAPACITY = 512
        // Gallery images travel as one base64 line; anything bigger than this is rejected.
        private const val MAX_LINE_CHARS = 12 * 1024 * 1024
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectedDeviceIp = MutableStateFlow("")
    val connectedDeviceIp: StateFlow<String> = _connectedDeviceIp.asStateFlow()

    private val _connectedClientsCount = MutableStateFlow(0)
    val connectedClientsCount: StateFlow<Int> = _connectedClientsCount.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<LinklyMessage>(extraBufferCapacity = 256)
    val incomingMessages: SharedFlow<LinklyMessage> = _incomingMessages.asSharedFlow()

    private val _discoveredServices = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val discoveredServices: StateFlow<List<NsdServiceInfo>> = _discoveredServices.asStateFlow()

    /**
     * TV side: runs on every incoming message before it is published.
     * Return false to swallow the message (used by the device admin for blocking and manager commands).
     */
    @Volatile
    var serverMessageFilter: ((LinklyMessage) -> Boolean)? = null

    /** Called on the TV whenever the set of connected phones changes. */
    @Volatile
    var onClientsChanged: (() -> Unit)? = null

    /** Called on the phone right after each successful connection to the TV. */
    @Volatile
    var onConnectedToServer: (() -> Unit)? = null

    private class Connection(
        val id: String,
        val socket: Socket,
        val queue: Channel<String> = Channel(QUEUE_CAPACITY)
    )

    // Server components (TV)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val clients = ConcurrentHashMap<String, Connection>()
    private val clientInfo = ConcurrentHashMap<String, ConnectedClient>()
    private var registrationListener: NsdManager.RegistrationListener? = null

    // Client components (Phone)
    @Volatile
    private var serverConnection: Connection? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var autoReconnectJob: Job? = null
    private var targetHost: String = ""
    private var targetPort: Int = DEFAULT_PORT

    /** Phone side: delay before the next reconnect attempt (raised when the TV blocks this phone). */
    @Volatile
    var reconnectDelayMs: Long = 3_000L

    init {
        scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                sendMessage(LinklyMessage(action = ACTION_PING, senderRole = "", payload = ""))
            }
        }
    }

    // ---------------------------------------------------------------- server (TV)

    fun startServer(port: Int = DEFAULT_PORT) {
        if (serverSocket != null && serverSocket?.isClosed == false) {
            Log.d(TAG, "Server already running on port $port")
            return
        }
        stopAll()
        serverJob = scope.launch {
            try {
                val ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                serverSocket = ss
                _connectionStatus.value = ConnectionStatus.LISTENING
                Log.d(TAG, "Server listening on port $port")
                registerNsdService(port)

                while (isActive && !ss.isClosed) {
                    try {
                        val socket = ss.accept()
                        acceptClient(socket)
                    } catch (e: Exception) {
                        if (!isActive || ss.isClosed) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    private fun acceptClient(socket: Socket) {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.soTimeout = READ_TIMEOUT_MS
        val conn = Connection(UUID.randomUUID().toString().take(8), socket)
        val ip = socket.inetAddress?.hostAddress ?: ""
        clients[conn.id] = conn
        clientInfo[conn.id] = ConnectedClient(clientId = conn.id, ip = ip, connectedAt = System.currentTimeMillis())
        publishClients()
        _connectionStatus.value = ConnectionStatus.CONNECTED
        _connectedDeviceIp.value = ip
        Log.d(TAG, "Client connected: $ip (${conn.id}), total clients: ${clients.size}")

        startWriter(conn) { dropClient(conn) }
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                while (isActive && !socket.isClosed) {
                    val line = readLineLimited(reader) ?: break
                    if (line.isNotBlank()) parseAndEmit(line, conn.id)
                }
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "Client ${conn.id} timed out (no heartbeat)")
            } catch (e: Exception) {
                Log.d(TAG, "Client socket closed: ${e.message}")
            } finally {
                dropClient(conn)
            }
        }
    }

    private fun dropClient(conn: Connection) {
        if (clients.remove(conn.id) == null) return
        clientInfo.remove(conn.id)
        conn.queue.close()
        try { conn.socket.close() } catch (_: Exception) {}
        publishClients()
        if (clients.isEmpty() && serverSocket?.isClosed == false) {
            _connectionStatus.value = ConnectionStatus.LISTENING
            _connectedDeviceIp.value = ""
        }
    }

    private fun publishClients() {
        _connectedClientsCount.value = clients.size
        _connectedClients.value = clientInfo.values.sortedBy { it.connectedAt }
        try { onClientsChanged?.invoke() } catch (e: Exception) { Log.e(TAG, "onClientsChanged failed", e) }
    }

    /** TV side: remember who a connection belongs to (sent by the phone in HELLO / PAIR_REQUEST). */
    fun setClientIdentity(clientId: String, deviceId: String, name: String) {
        val current = clientInfo[clientId] ?: return
        val updated = current.copy(
            deviceId = deviceId.ifBlank { current.deviceId },
            name = name.ifBlank { current.name }
        )
        if (updated != current) {
            clientInfo[clientId] = updated
            publishClients()
        }
    }

    fun getClient(clientId: String): ConnectedClient? = clientInfo[clientId]

    /** TV side: close one phone's connection. */
    fun disconnectClient(clientId: String) {
        clients[clientId]?.let { dropClient(it) }
    }

    // ---------------------------------------------------------------- client (phone)

    fun startClient(host: String = "", port: Int = DEFAULT_PORT, autoDiscover: Boolean = true) {
        stopAll()
        targetPort = port
        if (host.isNotBlank()) {
            targetHost = host
            connectToHost(host, port)
        } else if (autoDiscover) {
            _connectionStatus.value = ConnectionStatus.SEARCHING
            startDiscovery()
        }
    }

    fun connectToHost(host: String, port: Int = DEFAULT_PORT) {
        targetHost = host
        targetPort = port
        autoReconnectJob?.cancel()
        // Unblock the previous attempt's blocking read so it ends right away.
        serverConnection?.let { try { it.socket.close() } catch (_: Exception) {} }
        autoReconnectJob = scope.launch {
            while (isActive) {
                var conn: Connection? = null
                try {
                    _connectionStatus.value = ConnectionStatus.SEARCHING
                    Log.d(TAG, "Connecting to TV: $host:$port...")
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    socket.soTimeout = READ_TIMEOUT_MS
                    val c = Connection("server", socket)
                    conn = c
                    serverConnection = c
                    startWriter(c) { try { socket.close() } catch (_: Exception) {} }
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    _connectedDeviceIp.value = host
                    reconnectDelayMs = 3_000L
                    Log.d(TAG, "Connected to TV successfully!")
                    try { onConnectedToServer?.invoke() } catch (e: Exception) { Log.e(TAG, "onConnected failed", e) }

                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    while (isActive && !socket.isClosed) {
                        val line = readLineLimited(reader) ?: break
                        if (line.isNotBlank()) parseAndEmit(line, "")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Socket error / disconnect: ${e.message}")
                } finally {
                    conn?.queue?.close()
                    try { conn?.socket?.close() } catch (_: Exception) {}
                    if (serverConnection === conn) serverConnection = null
                    if (isActive) {
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                        _connectedDeviceIp.value = ""
                    }
                }
                delay(reconnectDelayMs)
            }
        }
    }

    // ---------------------------------------------------------------- sending

    private fun startWriter(conn: Connection, onFailure: () -> Unit) {
        scope.launch {
            try {
                val out = conn.socket.getOutputStream()
                for (line in conn.queue) {
                    out.write(line.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Writer for ${conn.id} stopped: ${e.message}")
                onFailure()
            }
        }
    }

    private fun encode(msg: LinklyMessage): String? = try {
        JSONObject().apply {
            put("action", msg.action)
            put("senderRole", msg.senderRole)
            put("payload", msg.payload)
        }.toString() + "\n"
    } catch (e: Exception) {
        Log.e(TAG, "Error encoding message", e)
        null
    }

    private fun enqueue(conn: Connection, line: String) {
        val result = conn.queue.trySend(line)
        if (result.isFailure && !result.isClosed) {
            // Queue full: this peer is not reading. Drop it rather than silently losing orders.
            Log.w(TAG, "Outgoing queue full for ${conn.id}, closing connection")
            if (conn.id == "server") {
                try { conn.socket.close() } catch (_: Exception) {}
            } else {
                dropClient(conn)
            }
        }
    }

    /** Sends to the TV (phone mode) or to every connected phone (TV mode). */
    fun sendMessage(msg: LinklyMessage) {
        val line = encode(msg) ?: return
        clients.values.forEach { enqueue(it, line) }
        serverConnection?.let { enqueue(it, line) }
    }

    /** TV side: send to one phone only. */
    fun sendTo(clientId: String, msg: LinklyMessage) {
        val line = encode(msg) ?: return
        clients[clientId]?.let { enqueue(it, line) }
    }

    /** TV side: send to every phone except one (used to relay a phone's change to the others). */
    fun sendToAllExcept(clientId: String, msg: LinklyMessage) {
        val line = encode(msg) ?: return
        clients.values.forEach { if (it.id != clientId) enqueue(it, line) }
    }

    // ---------------------------------------------------------------- receiving

    private fun readLineLimited(reader: BufferedReader): String? {
        val sb = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > MAX_LINE_CHARS) throw IOException("Incoming message too large")
        }
    }

    private fun parseAndEmit(line: String, clientId: String) {
        try {
            val obj = JSONObject(line)
            val msg = LinklyMessage(
                action = obj.optString("action"),
                senderRole = obj.optString("senderRole", "PHONE"),
                payload = obj.optString("payload", ""),
                clientId = clientId
            )
            if (msg.action == ACTION_PING) return
            if (clientId.isNotEmpty()) {
                val allowed = try { serverMessageFilter?.invoke(msg) ?: true } catch (e: Exception) {
                    Log.e(TAG, "Message filter failed", e); true
                }
                if (!allowed) return
            }
            _incomingMessages.tryEmit(msg)
        } catch (e: Throwable) {
            Log.e(TAG, "Parse error on packet: ${line.take(40)}", e)
        }
    }

    // ---------------------------------------------------------------- discovery (NSD)

    private fun registerNsdService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Linkly-TV-${Build.MODEL.take(6)}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "NSD registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD register failed: $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD register error", e)
        }
    }

    private fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NSD discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("linkly")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager?.stopServiceDiscovery(this) } catch (_: Exception) {}
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD discovery error", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                val host = resolvedService.host?.hostAddress
                if (!host.isNullOrBlank()) {
                    val currentList = _discoveredServices.value.toMutableList()
                    currentList.removeAll { it.serviceName == resolvedService.serviceName }
                    currentList.add(resolvedService)
                    _discoveredServices.value = currentList

                    if (_connectionStatus.value != ConnectionStatus.CONNECTED &&
                        !(host == targetHost && autoReconnectJob?.isActive == true)
                    ) {
                        connectToHost(host, resolvedService.port)
                    }
                }
            }
        }
        try {
            nsdManager?.resolveService(serviceInfo, resolver)
        } catch (e: Exception) {
            Log.e(TAG, "Resolve error", e)
        }
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun stopAll() {
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (_: Exception) {}
        registrationListener = null
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {}
        discoveryListener = null
        serverConnection?.let {
            it.queue.close()
            try { it.socket.close() } catch (_: Exception) {}
        }
        serverConnection = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        clients.values.toList().forEach { dropClient(it) }
        clients.clear()
        clientInfo.clear()
        _connectedClientsCount.value = 0
        _connectedClients.value = emptyList()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
}
