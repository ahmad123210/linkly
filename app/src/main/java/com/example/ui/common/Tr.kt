package com.example.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** True when this device shows the waiter / kitchen screens in English. */
val LocalEnglish = staticCompositionLocalOf { false }

/**
 * Translates an Arabic UI text to English when English is on.
 * The Arabic text is the key, so untranslated texts simply stay Arabic.
 */
@Composable
@ReadOnlyComposable
fun tr(ar: String): String = if (LocalEnglish.current) EnglishDictionary.map[ar] ?: ar else ar

object EnglishDictionary {
    val map: Map<String, String> = mapOf(
        "طلب جديد" to "New order"
    )
}
