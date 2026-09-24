package com.example.caculateapp

import com.example.caculateapp.data.Converters
import com.example.caculateapp.data.RiceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiceRecordTest {

    private val converters = Converters()

    @Test
    fun testRiceRecordDefaultValues() {
        val before = System.currentTimeMillis()
        val record = RiceRecord(
            customerName = "Nguyễn Văn C",
            unitPrice = 7500L,
            grandTotal = 150.0,
            totalMoney = 1125000L
        )
        val after = System.currentTimeMillis()

        assertEquals(0L, record.id)
        assertEquals("Nguyễn Văn C", record.customerName)
        assertEquals(7500L, record.unitPrice)
        assertEquals(150.0, record.grandTotal, 0.001)
        assertEquals(1125000L, record.totalMoney)
        assertTrue(record.createdAt in before..after)
        assertTrue(record.updatedAt in before..after)
    }

    @Test
    fun testConvertersWithValidList() {
        val weights = listOf(50.1, 52.3, 49.8)
        val stringRepresentation = converters.fromDoubleList(weights)
        assertEquals("50.1,52.3,49.8", stringRepresentation)

        val restored = converters.toDoubleList(stringRepresentation)
        assertEquals(3, restored.size)
        assertEquals(50.1, restored[0], 0.001)
        assertEquals(52.3, restored[1], 0.001)
        assertEquals(49.8, restored[2], 0.001)
    }

    @Test
    fun testConvertersWithNullOrEmpty() {
        assertEquals("", converters.fromDoubleList(null))
        assertEquals("", converters.fromDoubleList(emptyList()))

        assertTrue(converters.toDoubleList(null).isEmpty())
        assertTrue(converters.toDoubleList("").isEmpty())
    }

    @Test
    fun testConvertersWithCorruptedString() {
        val corrupted = "50.1,invalid,52.0,,abc"
        val restored = converters.toDoubleList(corrupted)
        assertEquals(2, restored.size)
        assertEquals(50.1, restored[0], 0.001)
        assertEquals(52.0, restored[1], 0.001)
    }
}
