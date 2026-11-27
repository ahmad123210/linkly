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
        "أدخل رمز الربط الظاهر أعلى شاشة التلفاز الرئيسية:" to "Enter the link code shown at the top of the main TV:",
        "أدخل رمز الربط الظاهر أعلى شاشة التلفاز:" to "Enter the link code shown at the top of the TV:",
        "إخفاء" to "Hide",
        "إخفاء الخيارات المتقدمة" to "Hide advanced options",
        "إرسال" to "Send",
        "إرسال طلب عاجل" to "Send urgent order",
        "إضافة" to "Add",
        "إغلاق" to "Close",
        "إلغاء" to "Cancel",
        "إلغاء الطلب" to "Cancel order",
        "إلغاء الطلب؟" to "Cancel this order?",
        "اتصال" to "Connect",
        "اتصال يدوي بعنوان IP" to "Connect by IP address",
        "اختر الطاولة (اختياري):" to "Pick a table (optional):",
        "استلمته" to "Picked up",
        "اسمك" to "Your name",
        "اكتب اسم الطلب" to "Type the item name",
        "الإعداد" to "Setup",
        "الإعدادات" to "Settings",
        "الاتصال" to "Connection",
        "السجل" to "History",
        "الطاولات" to "Tables",
        "الطلب" to "Order",
        "الكل" to "All",
        "اللغة" to "Language",
        "المدير" to "Manager",
        "المطلوب تحضيره" to "To prepare",
        "بانتظار التحضير" to "Waiting",
        "بدء التحضير ▶" to "Start ▶",
        "بدون" to "None",
        "تأكيد الإلغاء" to "Yes, cancel",
        "تراجع" to "Back",
        "تم إرسال النداء" to "Call sent",
        "تم التسليم" to "Delivered",
        "تم التسليم ✓" to "Delivered ✓",
        "تم ✓" to "Done ✓",
        "جاهز" to "Ready",
        "جاهز للاستلام" to "Ready for pickup",
        "جاهز ✓" to "Ready ✓",
        "جديد" to "New",
        "حفظ" to "Save",
        "حوّل هذا الجهاز إلى شاشة: شاشة الطلبات الرئيسية، أو شاشة إضافية للمطبخ أو البار، أو شاشة استلام للزبائن." to "Turn this device into a screen: the main orders screen, an extra kitchen or bar screen, or a customer pickup screen.",
        "خيارات متقدمة" to "Advanced options",
        "د" to "min",
        "ربط" to "Link",
        "رجوع" to "Back",
        "رجوع لوضع الهاتف" to "Back to phone mode",
        "رمز الربط" to "Link code",
        "سيظهر الطلب ملغياً على شاشة التلفاز." to "The order will show as cancelled on the TV.",
        "شاشة إضافية" to "Extra screen",
        "شاشة إضافية (مطبخ / بار / استلام)" to "Extra screen (kitchen / bar / pickup)",
        "شاشة الطلبات الرئيسية (التلفاز)" to "Main orders screen (TV)",
        "صنف" to "items",
        "صنف خاص" to "Custom item",
        "طاولة" to "Table",
        "طلب" to "Order",
        "طلب بدون طاولة (سفري / كاونتر)" to "Order without a table (takeaway / counter)",
        "طلب جديد" to "New order",
        "طلباتي" to "My orders",
        "عاجل" to "Urgent",
        "عرض" to "Show",
        "غير مرتبط بالشاشة بعد" to "Not linked to the screen yet",
        "فارغة" to "Free",
        "قيد التحضير" to "Preparing",
        "لا توجد أصناف في هذا القسم. يضيفها المدير من واجهة المدير." to "No items in this section. The manager adds them from the manager screen.",
        "لا توجد طاولات. يضيفها المدير من واجهة المدير ← القائمة ← الطاولات." to "No tables yet. The manager adds them in Manager → Menu → Tables.",
        "لم ترسل أي طلب اليوم" to "You have not sent any order today",
        "لم يُرسل بعد" to "Not sent yet",
        "ماذا تعرض هذه الشاشة؟" to "What does this screen show?",
        "مثال: بدون سكر" to "e.g. no sugar",
        "مرتبط بالشاشة ✓" to "Linked to the screen ✓",
        "مسح الكل" to "Clear all",
        "ملاحظة" to "Note",
        "ملاحظة على" to "Note for",
        "ملغي" to "Cancelled",
        "منذ" to "",
        "نداء" to "Call",
        "نداء سريع للمطبخ أو الصالة" to "Quick call to the kitchen or hall",
        "نفد" to "Sold out",
        "وضع الجهاز" to "Device mode",
        "أحتاج مساعدة" to "Need help",
        "الحساب" to "The bill",
        "تنظيف طاولة" to "Clean table",
        "بدون سكر" to "No sugar",
        "سكر قليل" to "Less sugar",
        "حار" to "Spicy",
        "بدون ثلج" to "No ice",
        "سفري" to "Takeaway",
        "بدون بصل" to "No onion",
        "🍳 المطبخ" to "🍳 Kitchen",
        "🍹 البار" to "🍹 Bar",
        "🔢 شاشة الاستلام للزبائن" to "🔢 Customer pickup screen"
    )
}
