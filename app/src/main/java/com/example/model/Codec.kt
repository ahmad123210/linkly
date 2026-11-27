package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** JSON encoding of everything sent between the TV and the phones, in one place. */
object Codec {

    // ------------------------------------------------------------------ orders

    fun orderToJson(order: QuickOrder): JSONObject = JSONObject().apply {
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
        put("tableId", order.tableId)
        put("tableName", order.tableName)
        put("isCall", order.isCall)
        put("preparingAt", order.preparingAt)
        put("readyAt", order.readyAt)
        put("deliveredAt", order.deliveredAt)
        put("readyStations", JSONArray(order.readyStations))
        val itemsArr = JSONArray()
        order.items.forEach { item ->
            itemsArr.put(JSONObject().apply {
                put("name", item.name)
                put("quantity", item.quantity)
                put("emoji", item.emoji)
                put("note", item.note)
                put("menuItemId", item.menuItemId)
                put("categoryId", item.categoryId)
            })
        }
        put("items", itemsArr)
    }

    fun parseOrder(obj: JSONObject): QuickOrder {
        val items = mutableListOf<OrderItem>()
        val itemsArr = obj.optJSONArray("items")
        if (itemsArr != null) {
            for (j in 0 until itemsArr.length()) {
                val o = itemsArr.getJSONObject(j)
                items.add(
                    OrderItem(
                        name = o.optString("name"),
                        quantity = o.optInt("quantity", 1),
                        emoji = o.optString("emoji", "🍵"),
                        note = o.optString("note"),
                        menuItemId = o.optString("menuItemId"),
                        categoryId = o.optString("categoryId")
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
            status = parseStatus(obj.optString("status", "PENDING")),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            dateStr = dateStr,
            ttsSpeechText = obj.optString("ttsSpeechText"),
            useNotificationSoundOnly = obj.optBoolean("useNotificationSoundOnly", false),
            senderName = obj.optString("senderName", "نادل 1"),
            senderDeviceId = obj.optString("senderDeviceId", ""),
            isOfflinePending = obj.optBoolean("isOfflinePending", false),
            tableId = obj.optString("tableId"),
            tableName = obj.optString("tableName"),
            isCall = obj.optBoolean("isCall", false),
            preparingAt = obj.optLong("preparingAt", 0L),
            readyAt = obj.optLong("readyAt", 0L),
            deliveredAt = obj.optLong("deliveredAt", 0L),
            readyStations = obj.optJSONArray("readyStations")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList(),
            items = items
        )
    }

    fun parseStatus(raw: String): OrderStatus =
        try { OrderStatus.valueOf(raw) } catch (_: Exception) { OrderStatus.PENDING }

    fun ordersToJson(list: List<QuickOrder>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(orderToJson(it)) }
        return arr
    }

    fun parseOrders(raw: String): List<QuickOrder> {
        val arr = JSONArray(raw)
        val out = ArrayList<QuickOrder>(arr.length())
        for (i in 0 until arr.length()) out.add(parseOrder(arr.getJSONObject(i)))
        return out
    }

    // ------------------------------------------------------------------ config (menu, tables, settings)

    fun configToJson(c: LinklyConfig): JSONObject = JSONObject().apply {
        put("version", c.version)
        put("categories", JSONArray().apply {
            c.categories.forEach { cat ->
                put(JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("emoji", cat.emoji)
                    put("station", cat.station)
                })
            }
        })
        put("items", JSONArray().apply {
            c.items.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("name", m.name)
                    put("emoji", m.emoji)
                    put("categoryId", m.categoryId)
                    put("price", m.price)
                    put("available", m.available)
                    put("imageB64", m.imageB64)
                })
            }
        })
        put("tables", JSONArray().apply {
            c.tables.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("zone", t.zone)
                })
            }
        })
        put("settings", settingsToJson(c.settings))
    }

    fun settingsToJson(s: LinklySettings): JSONObject = JSONObject().apply {
        put("warnMinutes", s.warnMinutes)
        put("lateMinutes", s.lateMinutes)
        put("idleEnabled", s.idleEnabled)
        put("idleDelaySec", s.idleDelaySec)
        put("currency", s.currency)
    }

    fun parseSettings(o: JSONObject?): LinklySettings {
        if (o == null) return LinklySettings()
        val warn = o.optInt("warnMinutes", 10).coerceIn(1, 240)
        return LinklySettings(
            warnMinutes = warn,
            lateMinutes = o.optInt("lateMinutes", 20).coerceIn(warn + 1, 480),
            idleEnabled = o.optBoolean("idleEnabled", true),
            idleDelaySec = o.optInt("idleDelaySec", 60).coerceIn(10, 3600),
            currency = o.optString("currency")
        )
    }

    fun parseConfig(o: JSONObject): LinklyConfig {
        val cats = mutableListOf<MenuCategory>()
        o.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val name = c.optString("name").trim()
                if (name.isBlank()) continue
                cats.add(MenuCategory(id = c.optString("id").ifBlank { newId() }, name = name, emoji = c.optString("emoji", "🍽️"), station = c.optString("station")))
            }
        }
        val items = mutableListOf<MenuItem>()
        o.optJSONArray("items")?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val name = m.optString("name").trim()
                if (name.isBlank()) continue
                items.add(
                    MenuItem(
                        id = m.optString("id").ifBlank { newId() },
                        name = name,
                        emoji = m.optString("emoji", "🍽️"),
                        categoryId = m.optString("categoryId"),
                        price = m.optDouble("price", 0.0).let { if (it.isNaN() || it < 0) 0.0 else it },
                        available = m.optBoolean("available", true),
                        imageB64 = m.optString("imageB64")
                    )
                )
            }
        }
        val tables = mutableListOf<DiningTable>()
        o.optJSONArray("tables")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                val name = t.optString("name").trim()
                if (name.isBlank()) continue
                tables.add(DiningTable(id = t.optString("id").ifBlank { newId() }, name = name, zone = t.optString("zone", "داخل")))
            }
        }
        return LinklyConfig(
            categories = cats,
            items = items,
            tables = tables,
            settings = parseSettings(o.optJSONObject("settings")),
            version = o.optLong("version", 0L)
        )
    }

    fun newId(): String = java.util.UUID.randomUUID().toString().take(8)

    /** First-run menu: the old fixed list, now in editable sections. */
    fun defaultConfig(): LinklyConfig {
        val hot = MenuCategory(id = "hot", name = "مشروبات ساخنة", emoji = "☕", station = "bar")
        val cold = MenuCategory(id = "cold", name = "مشروبات باردة", emoji = "🍹", station = "bar")
        val food = MenuCategory(id = "food", name = "وجبات", emoji = "🍽️", station = "kitchen")
        val sweets = MenuCategory(id = "sweets", name = "حلويات", emoji = "🍰", station = "kitchen")
        val items = listOf(
            MenuItem(id = "tea", name = "شاي بالنعناع", emoji = "🍵", categoryId = hot.id),
            MenuItem(id = "coffee", name = "قهوة", emoji = "☕", categoryId = hot.id),
            MenuItem(id = "water", name = "قارورة ماء", emoji = "💧", categoryId = cold.id),
            MenuItem(id = "juice", name = "عصير طازج", emoji = "🍹", categoryId = cold.id),
            MenuItem(id = "meal", name = "وجبة طعام", emoji = "🍽️", categoryId = food.id),
            MenuItem(id = "dessert", name = "طبق حلوى", emoji = "🍰", categoryId = sweets.id)
        )
        val tables = (1..10).map { DiningTable(id = "t$it", name = "$it", zone = "داخل") }
        return LinklyConfig(categories = listOf(hot, cold, food, sweets), items = items, tables = tables, version = 1L)
    }
}
