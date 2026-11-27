package com.example.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/** Decodes a Base64 JPEG thumbnail once per value. */
@Composable
fun rememberB64Image(b64: String): ImageBitmap? = remember(b64) {
    if (b64.isBlank()) null else try {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/** Shrinks a picked photo to a small square-ish thumbnail for the menu (keeps messages small). */
fun bitmapToThumbB64(bmp: Bitmap, maxSide: Int = 256): String {
    val longSide = maxOf(bmp.width, bmp.height).coerceAtLeast(1)
    val scaled = if (longSide <= maxSide) bmp else {
        val r = maxSide.toFloat() / longSide
        Bitmap.createScaledBitmap(bmp, (bmp.width * r).toInt().coerceAtLeast(1), (bmp.height * r).toInt().coerceAtLeast(1), true)
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

fun formatPrice(price: Double, currency: String): String {
    if (price <= 0.0) return ""
    val num = if (price % 1.0 == 0.0) price.toLong().toString() else String.format(java.util.Locale.US, "%.2f", price)
    return if (currency.isBlank()) num else "$num $currency"
}

fun minutesSince(ts: Long, now: Long = System.currentTimeMillis()): Long = ((now - ts) / 60000L).coerceAtLeast(0L)

fun stationLabel(station: String): String = when (station) {
    "kitchen" -> "المطبخ"
    "bar" -> "البار"
    else -> "الشاشة الرئيسية"
}
