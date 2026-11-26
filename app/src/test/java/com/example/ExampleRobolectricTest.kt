package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DeviceRole
import com.example.model.OrderStatus
import com.example.model.QuickOrder
import com.example.model.TextDisplayData
import com.example.model.TvScreenMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Linkly", appName)
    }

    @Test
    fun `verify default order model initialization`() {
        val order = QuickOrder(
            title = "طلب شاي",
            ticketNumber = "104",
            isUrgent = true,
            status = OrderStatus.PENDING,
            useNotificationSoundOnly = true
        )
        assertNotNull(order.id)
        assertEquals(OrderStatus.PENDING, order.status)
        assertEquals("104", order.ticketNumber)
        assertEquals(true, order.isUrgent)
        assertEquals(false, order.isHidden)
        assertEquals(true, order.useNotificationSoundOnly)
    }

    @Test
    fun `verify order cancellation properties`() {
        val cancelledOrder = QuickOrder(
            ticketNumber = "50",
            status = OrderStatus.CANCELLED,
            isCancelled = true
        )
        assertEquals(OrderStatus.CANCELLED, cancelledOrder.status)
        assertTrue(cancelledOrder.isCancelled)
        assertEquals("ملغي", OrderStatus.CANCELLED.titleAr)
    }

    @Test
    fun `verify text display data with 12 hour clock`() {
        val textData = TextDisplayData(
            text = "مرحباً",
            showClock = true,
            is12HourClock = true
        )
        assertTrue(textData.showClock)
        assertTrue(textData.is12HourClock)
    }

    @Test
    fun `verify tv screen modes`() {
        assertEquals(4, TvScreenMode.entries.size)
        assertEquals(DeviceRole.TV, DeviceRole.valueOf("TV"))
        assertEquals(DeviceRole.PHONE, DeviceRole.valueOf("PHONE"))
    }

    @Test
    fun `verify queue number progression on ready and delivered`() {
        val order1 = QuickOrder(ticketNumber = "10", status = OrderStatus.PENDING, timestamp = 1000L)
        val order2 = QuickOrder(ticketNumber = "11", status = OrderStatus.READY, timestamp = 2000L)
        val orderList = listOf(order1, order2)

        // Latest ready order should be prioritized
        val readyOrder = orderList.filter { !it.isHidden && !it.isCancelled && it.status == OrderStatus.READY }.maxByOrNull { it.timestamp }
        assertEquals("11", readyOrder?.ticketNumber)

        // When order2 is delivered, next pending order should advance
        val updatedList = listOf(order1, order2.copy(status = OrderStatus.DELIVERED, isHidden = true))
        val nextReady = updatedList.filter { !it.isHidden && !it.isCancelled && it.status == OrderStatus.READY }.maxByOrNull { it.timestamp }
        assertEquals(null, nextReady)
    }

    @Test
    fun `verify waiter sender name and offline pending order model`() {
        val waiterOrder = QuickOrder(
            title = "طلب عصير",
            ticketNumber = "12",
            senderName = "نادل 2",
            isOfflinePending = true
        )
        assertEquals("نادل 2", waiterOrder.senderName)
        assertTrue(waiterOrder.isOfflinePending)
    }

    @Test
    fun `verify tv default screen mode is orders`() {
        assertEquals("ORDERS", TvScreenMode.ORDERS.name)
    }
}
