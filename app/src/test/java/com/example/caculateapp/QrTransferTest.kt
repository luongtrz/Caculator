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
    fun testDeepLinkSerializationAndDeserialization() {
        val originalRecords = listOf(
            RiceRecord(
                id = 101L,
                customerName = "Chú Năm Lúa",
                unitPrice = 8200L,
                weightList = listOf(50.5, 51.2, 49.9),
                grandTotal = 151.6,
                totalMoney = 1243120L,
                createdAt = 1710001111000L,
                updatedAt = 1710002222000L
            )
        )

        // 1. Serialize to Deep Link
        val deepLinkUrl = QrTransferManager.serializeToDeepLink(originalRecords)
        assertTrue(deepLinkUrl.startsWith("https://canlua.app/import?d="))

        // 2. Deserialize Deep Link
        val result = QrTransferManager.deserializeRecords(deepLinkUrl)
        assertTrue(result.isSuccess)

        val deserialized = result.getOrThrow()
        assertEquals(1, deserialized.size)
        assertEquals("Chú Năm Lúa", deserialized[0].customerName)
        assertEquals(8200L, deserialized[0].unitPrice)
        assertEquals(3, deserialized[0].weightList.size)
        assertEquals(151.6, deserialized[0].grandTotal, 0.001)
        assertEquals(1243120L, deserialized[0].totalMoney)
        assertEquals(1710001111000L, deserialized[0].createdAt)
        assertEquals(1710002222000L, deserialized[0].updatedAt)
    }

    @Test
    fun testTextReceiptFormattingAndDeserialization() {
        val originalRecords = listOf(
            RiceRecord(
                id = 202L,
                customerName = "Anh Ba Ruộng",
                unitPrice = 8600L,
                weightList = listOf(50.0, 52.0, 51.5, 49.5, 50.5),
                grandTotal = 253.5,
                totalMoney = 2180100L,
                createdAt = 1710003333000L,
                updatedAt = 1710004444000L
            )
        )

        // 1. Format text receipt
        val textReceipt = QrTransferManager.formatTextReceipt(originalRecords)
        assertTrue(textReceipt.contains("🌾 PHIẾU CÂN LÚA"))
        assertTrue(textReceipt.contains("Anh Ba Ruộng"))
        assertTrue(textReceipt.contains("253.5 kg"))
        assertTrue(textReceipt.contains("[RICEQR:1:"))

        // 2. Deserialize text receipt (using embedded tag)
        val result = QrTransferManager.deserializeRecords(textReceipt)
        assertTrue(result.isSuccess)

        val deserialized = result.getOrThrow()
        assertEquals(1, deserialized.size)
        assertEquals("Anh Ba Ruộng", deserialized[0].customerName)
        assertEquals(8600L, deserialized[0].unitPrice)
        assertEquals(5, deserialized[0].weightList.size)
        assertEquals(253.5, deserialized[0].grandTotal, 0.001)
        assertEquals(2180100L, deserialized[0].totalMoney)
    }

    @Test
    fun testPlainTextReceiptFallbackWithoutEmbeddedTag() {
        val plainText = """
            🌾 PHIẾU CÂN LÚA
            👤 Khách: Chú Bảy Cò
            📅 Ngày: 24/09/2026 21:00
            💰 Đơn giá: 8,500 đ/kg
            ───────────────
            Chi tiết cân (kg):
            Cột 1: 50.2  51.0  49.8 (151.0 kg)
            ───────────────
            📦 Tổng: 3 bao | 151.0 kg
            💵 THÀNH TIỀN: 1,283,500 đ
        """.trimIndent()

        val result = QrTransferManager.deserializeRecords(plainText)
        assertTrue(result.isSuccess)

        val deserialized = result.getOrThrow()
        assertEquals(1, deserialized.size)
        assertEquals("Chú Bảy Cò", deserialized[0].customerName)
        assertEquals(8500L, deserialized[0].unitPrice)
        assertEquals(3, deserialized[0].weightList.size)
        assertEquals(50.2, deserialized[0].weightList[0], 0.001)
        assertEquals(151.0, deserialized[0].grandTotal, 0.001)
        assertEquals(1283500L, deserialized[0].totalMoney)
    }

    @Test
    fun testInvalidQrContentFailsGracefully() {
        val result = QrTransferManager.deserializeRecords("https://example.com/invalid")
        assertTrue(result.isFailure)
    }
}
