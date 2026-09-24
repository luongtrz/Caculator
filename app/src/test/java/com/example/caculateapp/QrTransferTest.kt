package com.example.caculateapp

import com.example.caculateapp.data.RiceRecord
import com.example.caculateapp.utils.QrTransferManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTransferTest {

    @Test
    fun testSerializationAndDeserialization() {
        val originalRecords = listOf(
            RiceRecord(
                id = 123L,
                customerName = "Nguyễn Văn A",
                unitPrice = 8500L,
                weightList = listOf(50.2, 51.0, 49.8, 52.3, 50.5),
                grandTotal = 253.8,
                totalMoney = 2157300L,
                createdAt = 1710000000000L,
                updatedAt = 1710002000000L
            ),
            RiceRecord(
                id = 456L,
                customerName = "Trần Thị B",
                unitPrice = 9000L,
                weightList = listOf(60.0, 61.2, 59.5),
                grandTotal = 180.7,
                totalMoney = 1626300L,
                createdAt = 1710005000000L,
                updatedAt = 1710006000000L
            )
        )

        // 1. Serialize
        val qrContent = QrTransferManager.serializeRecords(originalRecords)
        assertTrue(qrContent.startsWith("RICEQR:1:"))
        assertTrue("QR content length was ${qrContent.length}", qrContent.length < 500)

        // 2. Deserialize
        val result = QrTransferManager.deserializeRecords(qrContent)
        assertTrue(result.isSuccess)

        val deserializedRecords = result.getOrThrow()
        assertEquals(2, deserializedRecords.size)

        val r1 = deserializedRecords[0]
        assertEquals("Nguyễn Văn A", r1.customerName)
        assertEquals(8500L, r1.unitPrice)
        assertEquals(5, r1.weightList.size)
        assertEquals(50.2, r1.weightList[0], 0.001)
        assertEquals(253.8, r1.grandTotal, 0.001)
        assertEquals(2157300L, r1.totalMoney)
        assertEquals(1710000000000L, r1.createdAt)
        assertEquals(1710002000000L, r1.updatedAt)

        val r2 = deserializedRecords[1]
        assertEquals("Trần Thị B", r2.customerName)
        assertEquals(9000L, r2.unitPrice)
        assertEquals(3, r2.weightList.size)
        assertEquals(180.7, r2.grandTotal, 0.001)
        assertEquals(1626300L, r2.totalMoney)
        assertEquals(1710005000000L, r2.createdAt)
        assertEquals(1710006000000L, r2.updatedAt)
    }

    @Test
    fun testInvalidQrContentFailsGracefully() {
        val result = QrTransferManager.deserializeRecords("https://example.com/invalid")
        assertTrue(result.isFailure)
    }
}
