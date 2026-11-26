package com.example.model

enum class DeviceRole {
    PHONE,
    TV
}

enum class TvScreenMode {
    TEXT,
    WHITEBOARD,
    IMAGE,
    ORDERS
}

enum class ConnectionStatus {
    DISCONNECTED,
    SEARCHING,
    LISTENING,
    CONNECTED
}

enum class ToolType {
    PEN,
    RECTANGLE,
    CIRCLE,
    ERASER,
    POINTER
}

enum class PointerShape {
    DOT,
    LASER,
    ARROW,
    STAR
}

data class DrawPoint(
    val x: Float = 0f,
    val y: Float = 0f
)

data class DrawStroke(
    val id: String = java.util.UUID.randomUUID().toString(),
    val toolType: ToolType = ToolType.PEN,
    val points: List<DrawPoint> = emptyList(),
    val colorHex: String = "#FFFFFF",
    val strokeWidth: Float = 6f,
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 0f,
    val endY: Float = 0f
)

data class PointerState(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val isVisible: Boolean = false,
    val shape: PointerShape = PointerShape.LASER,
    val colorHex: String = "#EF4444"
)

data class TextDisplayData(
    val text: String = "أهلاً بكم في شاشة Linkly الذكية",
    val fontSizeSp: Float = 56f,
    val textColorHex: String = "#FFFFFF",
    val bgColorHex: String = "#0A0E17",
    val alignment: String = "CENTER", // "START", "CENTER", "END"
    val isTicker: Boolean = false,
    val showClock: Boolean = false,
    val is12HourClock: Boolean = true
)

enum class OrderStatus(val titleAr: String, val colorHex: String) {
    PENDING("جاري التحضير", "#F59E0B"),
    READY("جاهز للاستلام", "#10B981"),
    DELIVERED("تم التسليم", "#64748B"),
    CANCELLED("ملغي", "#DC2626")
}

data class OrderItem(
    val name: String,
    val quantity: Int = 1,
    val emoji: String = "🍵"
)

data class QuickOrder(
    val id: String = java.util.UUID.randomUUID().toString().take(6).uppercase(),
    val title: String = "",
    val items: List<OrderItem> = emptyList(),
    val ticketNumber: String = "",
    val isUrgent: Boolean = false,
    val isHidden: Boolean = false,
    val isCancelled: Boolean = false,
    val status: OrderStatus = OrderStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val dateStr: String = "", // e.g. "2026-10-23"
    val ttsSpeechText: String = "",
    val useNotificationSoundOnly: Boolean = false,
    val senderName: String = "نادل 1",
    val senderDeviceId: String = "",
    val isOfflinePending: Boolean = false
)

data class DailyOrdersSummary(
    val dateStr: String,
    val formattedDateAr: String,
    val orders: List<QuickOrder>
)

data class AudioTrackItem(
    val id: String,
    val title: String,
    val fileName: String,
    val durationMs: Long = 0,
    val isBuiltIn: Boolean = false
)

data class AudioPlaybackState(
    val isPlaying: Boolean = false,
    val currentTrackTitle: String = "أجواء موسيقية هادئة",
    val volume: Float = 0.7f
)

/**
 * Protocol Message exchanged over WebSocket / TCP socket
 */
data class LinklyMessage(
    val action: String,
    val senderRole: String = "PHONE",
    val payload: String = "",
    // Local-only: id of the socket the message arrived on (TV side). Never sent over the wire.
    val clientId: String = ""
)

/** A phone currently connected to the TV, as seen by the TV. */
data class ConnectedClient(
    val clientId: String,
    val ip: String,
    val connectedAt: Long,
    val deviceId: String = "",
    val name: String = ""
)

/** Device entry shown on the manager screen (phone side). */
data class ManagedDevice(
    val clientId: String,
    val deviceId: String,
    val name: String,
    val ip: String,
    val connectedAt: Long,
    val isManager: Boolean
)

data class BlockedDevice(
    val deviceId: String,
    val name: String
)
