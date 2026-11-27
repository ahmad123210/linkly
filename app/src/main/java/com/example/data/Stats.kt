package com.example.data

import com.example.model.OrderStatus
import com.example.model.QuickOrder

/** Numbers for the manager's "today" dashboard, worked out from the order history. */
data class DayStats(
    val totalOrders: Int,
    val cancelled: Int,
    val calls: Int,
    val activeNow: Int,
    val avgPrepMinutes: Double?,
    val topItems: List<Pair<String, Int>>,
    val waiters: List<WaiterStats>,
    val revenue: Double
)

data class WaiterStats(
    val name: String,
    val orders: Int,
    val avgPrepMinutes: Double?
)

object Stats {

    fun isRealOrder(o: QuickOrder) = !o.isCall

    fun prepMinutes(o: QuickOrder): Double? =
        if (o.readyAt > o.timestamp) (o.readyAt - o.timestamp) / 60000.0 else null

    fun forDay(all: List<QuickOrder>, day: String, prices: Map<String, Double> = emptyMap()): DayStats {
        val dayOrders = all.filter { it.dateStr == day }
        val real = dayOrders.filter { isRealOrder(it) }
        val done = real.filter { !it.isCancelled && it.status != OrderStatus.CANCELLED }
        val preps = done.mapNotNull { prepMinutes(it) }
        val itemCounts = HashMap<String, Int>()
        var revenue = 0.0
        done.forEach { o ->
            o.items.forEach { item ->
                itemCounts[item.name] = (itemCounts[item.name] ?: 0) + item.quantity
                revenue += (prices[item.menuItemId] ?: 0.0) * item.quantity
            }
        }
        val waiters = done.groupBy { it.senderName.ifBlank { "—" } }.map { (name, list) ->
            val p = list.mapNotNull { prepMinutes(it) }
            WaiterStats(name = name, orders = list.size, avgPrepMinutes = if (p.isEmpty()) null else p.average())
        }.sortedByDescending { it.orders }
        return DayStats(
            totalOrders = done.size,
            cancelled = real.size - done.size,
            calls = dayOrders.count { it.isCall },
            activeNow = done.count { !it.isHidden && (it.status == OrderStatus.PENDING || it.status == OrderStatus.PREPARING || it.status == OrderStatus.READY) },
            avgPrepMinutes = if (preps.isEmpty()) null else preps.average(),
            topItems = itemCounts.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
            waiters = waiters,
            revenue = revenue
        )
    }
}
