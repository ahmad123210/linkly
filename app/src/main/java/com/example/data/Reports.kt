package com.example.data

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.model.OrderStatus
import com.example.model.QuickOrder
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Order reports the manager can save: a CSV file (opens in Excel) and a PDF. */
object Reports {

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

    private fun statusAr(o: QuickOrder): String = when {
        o.isCancelled || o.status == OrderStatus.CANCELLED -> "ملغي"
        else -> o.status.titleAr
    }

    private fun itemsText(o: QuickOrder): String =
        o.items.joinToString(" + ") { "${it.quantity}× ${it.name}" + if (it.note.isNotBlank()) " (${it.note})" else "" }

    private fun csvCell(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""

    fun writeCsv(orders: List<QuickOrder>, out: OutputStream) {
        val sb = StringBuilder()
        sb.append('﻿') // BOM so Excel shows Arabic correctly
        sb.append(listOf("التاريخ", "الوقت", "رقم الطلب", "الطاولة", "النادل", "الأصناف", "عاجل", "نداء", "الحالة", "دقائق التحضير")
            .joinToString(",") { csvCell(it) }).append("\r\n")
        orders.sortedBy { it.timestamp }.forEach { o ->
            val prep = Stats.prepMinutes(o)?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            sb.append(
                listOf(
                    o.dateStr,
                    timeFmt.format(Date(o.timestamp)),
                    o.ticketNumber.trimStart('#'),
                    o.tableName,
                    o.senderName,
                    itemsText(o),
                    if (o.isUrgent) "نعم" else "",
                    if (o.isCall) "نعم" else "",
                    statusAr(o),
                    prep
                ).joinToString(",") { csvCell(it) }
            ).append("\r\n")
        }
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** A4 PDF: summary on top, then one line per order. Arabic is shaped by StaticLayout. */
    fun writePdf(orders: List<QuickOrder>, title: String, out: OutputStream) {
        val pageW = 595
        val pageH = 842
        val margin = 36
        val doc = PdfDocument()
        val titlePaint = TextPaint().apply { textSize = 18f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val bodyPaint = TextPaint().apply { textSize = 11f; isAntiAlias = true; color = Color.DKGRAY }
        val boldPaint = TextPaint().apply { textSize = 12f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }

        val days = orders.map { it.dateStr }.distinct()
        val summaryLines = mutableListOf<String>()
        days.sortedDescending().forEach { d ->
            val s = Stats.forDay(orders, d)
            summaryLines += "$d: ${s.totalOrders} طلب، ${s.cancelled} ملغي" +
                (s.avgPrepMinutes?.let { "، متوسط التحضير ${String.format(Locale.US, "%.1f", it)} د" } ?: "")
            if (s.topItems.isNotEmpty()) summaryLines += "الأكثر طلباً: " + s.topItems.joinToString("، ") { "${it.first} (${it.second})" }
        }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = margin.toFloat()
        }
        fun draw(text: String, paint: TextPaint, gap: Float = 4f) {
            val width = pageW - margin * 2
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
            }
            if (y + layout.height > pageH - margin) newPage()
            val canvas = page!!.canvas
            canvas.save()
            canvas.translate(margin.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + gap
        }

        newPage()
        draw(title, titlePaint, 10f)
        summaryLines.forEach { draw(it, boldPaint) }
        y += 8f
        orders.sortedBy { it.timestamp }.forEach { o ->
            val head = buildString {
                append(o.dateStr).append(" ").append(timeFmt.format(Date(o.timestamp)))
                if (o.ticketNumber.isNotBlank()) append("  •  #").append(o.ticketNumber.trimStart('#'))
                if (o.tableName.isNotBlank()) append("  •  طاولة ").append(o.tableName)
                append("  •  ").append(o.senderName)
                append("  •  ").append(statusAr(o))
                if (o.isUrgent) append("  •  عاجل")
            }
            draw(head, boldPaint, 1f)
            draw(itemsText(o), bodyPaint, 8f)
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
        out.flush()
    }
}
