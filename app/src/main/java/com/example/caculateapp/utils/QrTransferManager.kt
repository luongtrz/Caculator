package com.example.caculateapp.utils

import android.graphics.Bitmap
import android.util.Base64
import com.example.caculateapp.data.RiceRecord
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.EnumMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * QrTransferManager handles offline serialization, compression, QR generation,
 * and deserialization of RiceRecord objects for peer-to-peer device sharing.
 */
object QrTransferManager {

    private const val QR_PREFIX = "RICEQR:1:"

    /**
     * Serializes a list of RiceRecords into a compact, GZIP-compressed, Base64-encoded string.
     */
    fun serializeRecords(records: List<RiceRecord>): String {
        val root = JSONObject()
        root.put("v", 1)

        val recordsArray = JSONArray()
        for (record in records) {
            val obj = JSONObject()
            obj.put("c", record.customerName)
            obj.put("p", record.unitPrice)
            obj.put("gt", record.grandTotal)
            obj.put("tm", record.totalMoney)
            obj.put("t", record.createdAt)

            val weightsArray = JSONArray()
            for (w in record.weightList) {
                weightsArray.put(w)
            }
            obj.put("w", weightsArray)

            recordsArray.put(obj)
        }
        root.put("r", recordsArray)

        val jsonString = root.toString()

        // Compress using GZIP
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { gzip ->
            gzip.write(jsonString.toByteArray(Charsets.UTF_8))
        }
        val compressedBytes = byteStream.toByteArray()
        val base64Data = base64Encode(compressedBytes)

        return "$QR_PREFIX$base64Data"
    }

    /**
     * Deserializes a scanned QR code content string back into a list of RiceRecord objects.
     */
    fun deserializeRecords(qrContent: String): Result<List<RiceRecord>> {
        return try {
            val jsonString = if (qrContent.startsWith(QR_PREFIX)) {
                val base64Data = qrContent.removePrefix(QR_PREFIX)
                val compressedBytes = base64Decode(base64Data)
                GZIPInputStream(ByteArrayInputStream(compressedBytes))
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            } else if (qrContent.startsWith("{") && qrContent.endsWith("}")) {
                // Fallback for uncompressed JSON
                qrContent
            } else {
                return Result.failure(IllegalArgumentException("Mã QR không đúng định dạng của ứng dụng Cân Lúa."))
            }

            val root = JSONObject(jsonString)
            val recordsArray = root.optJSONArray("r") 
                ?: return Result.failure(IllegalArgumentException("Không tìm thấy danh sách đợt cân trong mã QR."))

            val resultList = mutableListOf<RiceRecord>()
            for (i in 0 until recordsArray.length()) {
                val obj = recordsArray.getJSONObject(i)
                val customerName = obj.optString("c", "")
                val unitPrice = obj.optLong("p", 0L)
                val grandTotal = obj.optDouble("gt", 0.0)
                val totalMoney = obj.optLong("tm", 0L)
                val timestamp = obj.optLong("t", System.currentTimeMillis())

                val weightsArray = obj.optJSONArray("w")
                val weightList = mutableListOf<Double>()
                if (weightsArray != null) {
                    for (j in 0 until weightsArray.length()) {
                        weightList.add(weightsArray.getDouble(j))
                    }
                }

                resultList.add(
                    RiceRecord(
                        id = 0L,
                        customerName = customerName,
                        unitPrice = unitPrice,
                        weightList = weightList,
                        grandTotal = grandTotal,
                        totalMoney = totalMoney,
                        createdAt = timestamp
                    )
                )
            }

            if (resultList.isEmpty()) {
                Result.failure(IllegalArgumentException("Mã QR không chứa đợt cân nào."))
            } else {
                Result.success(resultList)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi đọc dữ liệu từ mã QR: ${e.message}", e))
        }
    }

    /**
     * Generates a high-quality Bitmap for the given QR string content.
     */
    fun generateQrBitmap(content: String, size: Int = 800): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1) // minimal border to maximize QR area
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
        }
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        return BarcodeEncoder().createBitmap(bitMatrix)
    }

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun base64Decode(str: String): ByteArray {
        return try {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }
}
