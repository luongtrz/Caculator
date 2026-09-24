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
            obj.put("u", record.updatedAt)

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
     * Serializes records into a standard Deep Link URL.
     * When scanned with standard phone Camera (Google Lens, Samsung/Xiaomi Camera),
     * the system recognizes the URL and offers to "Open in CaculateApp".
     */
    fun serializeToDeepLink(records: List<RiceRecord>): String {
        val rawData = serializeRecords(records)
        val encoded = java.net.URLEncoder.encode(rawData, "UTF-8")
        return "https://canlua.app/import?d=$encoded"
    }

    /**
     * Formats records into a beautiful, human-readable text receipt.
     * When scanned by Zalo or standard Camera, it displays cleanly on the user's screen
     * WITHOUT needing any app installed.
     * It also embeds a compact machine-readable tag so CaculateApp can parse it with full fidelity.
     */
    fun formatTextReceipt(records: List<RiceRecord>): String {
        val sb = StringBuilder()
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        val moneyFormat = java.text.NumberFormat.getInstance(java.util.Locale("vi", "VN"))

        if (records.size == 1) {
            val r = records[0]
            val name = r.customerName.ifBlank { "Khách lẻ" }
            val dateStr = dateFormat.format(java.util.Date(if (r.updatedAt > 0L) r.updatedAt else r.createdAt))
            val bags = r.weightList.filter { it > 0.0 }

            sb.append("🌾 PHIẾU CÂN LÚA\n")
            sb.append("👤 Khách: $name\n")
            sb.append("📅 Ngày: $dateStr\n")
            if (r.unitPrice > 0L) {
                sb.append("💰 Đơn giá: ${moneyFormat.format(r.unitPrice)} đ/kg\n")
            }
            sb.append("───────────────\n")
            sb.append("Chi tiết cân (kg):\n")

            val chunks = bags.chunked(5)
            chunks.forEachIndexed { i, col ->
                val colTotal = col.sum()
                val colWeights = col.joinToString("  ") { "%.1f".format(it) }
                sb.append("Cột ${i + 1}: $colWeights (${"%.1f".format(colTotal)} kg)\n")
            }

            sb.append("───────────────\n")
            sb.append("📦 Tổng: ${bags.size} bao | ${"%.1f".format(r.grandTotal)} kg\n")
            if (r.totalMoney > 0L) {
                sb.append("💵 THÀNH TIỀN: ${moneyFormat.format(r.totalMoney)} đ\n")
            }
            sb.append("───────────────\n")
            val compressedTag = serializeRecords(records)
            sb.append("[$compressedTag]")
        } else {
            sb.append("🌾 TỔNG HỢP ${records.size} ĐỢT CÂN\n")
            val dateStr = dateFormat.format(java.util.Date())
            sb.append("📅 Ngày: $dateStr\n")
            sb.append("───────────────\n")

            var totalBags = 0
            var totalGrand = 0.0
            var totalMoney = 0L

            records.forEachIndexed { index, r ->
                val name = r.customerName.ifBlank { "Đợt ${index + 1}" }
                val bagCount = r.weightList.count { it > 0.0 }
                totalBags += bagCount
                totalGrand += r.grandTotal
                totalMoney += r.totalMoney

                sb.append("${index + 1}. $name: $bagCount bao | ${"%.1f".format(r.grandTotal)} kg")
                if (r.totalMoney > 0L) {
                    sb.append(" | ${moneyFormat.format(r.totalMoney)} đ")
                }
                sb.append("\n")
            }

            sb.append("───────────────\n")
            sb.append("📦 TỔNG CỘNG: $totalBags bao | ${"%.1f".format(totalGrand)} kg\n")
            if (totalMoney > 0L) {
                sb.append("💵 THÀNH TIỀN: ${moneyFormat.format(totalMoney)} đ\n")
            }
            sb.append("───────────────\n")
            val compressedTag = serializeRecords(records)
            sb.append("[$compressedTag]")
        }
        return sb.toString()
    }

    /**
     * Deserializes a scanned QR code content string back into a list of RiceRecord objects.
     * Supports:
     * 1. Deep Link URLs (https://canlua.app/import?d=... or canlua://import?d=...)
     * 2. Embedded machine tags in text receipts ([RICEQR:1:...])
     * 3. Direct RICEQR:1:... strings
     * 4. Plain text receipt fallback
     */
    fun deserializeRecords(qrContent: String): Result<List<RiceRecord>> {
        val trimmed = qrContent.trim()

        // 1. Check for embedded [RICEQR:1:...] tag in text receipt
        val tagStart = trimmed.indexOf("[RICEQR:1:")
        if (tagStart != -1) {
            val tagEnd = trimmed.indexOf("]", tagStart)
            if (tagEnd != -1) {
                val innerTag = trimmed.substring(tagStart + 1, tagEnd)
                return deserializeRecords(innerTag)
            }
        }

        // 2. Check for Deep Link URL
        if (trimmed.startsWith("https://canlua.app/import") || trimmed.startsWith("canlua://import")) {
            val dParam = if (trimmed.contains("?d=")) {
                trimmed.substringAfter("?d=").substringBefore("&")
            } else if (trimmed.contains("&d=")) {
                trimmed.substringAfter("&d=").substringBefore("&")
            } else {
                ""
            }
            if (dParam.isNotEmpty()) {
                val decoded = java.net.URLDecoder.decode(dParam, "UTF-8")
                return deserializeRecords(decoded)
            }
        }

        // 3. Decompress RICEQR:1:... format
        return try {
            val jsonString = if (trimmed.startsWith(QR_PREFIX)) {
                val base64Data = trimmed.removePrefix(QR_PREFIX)
                val compressedBytes = base64Decode(base64Data)
                GZIPInputStream(ByteArrayInputStream(compressedBytes))
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                // Fallback for uncompressed JSON
                trimmed
            } else if (trimmed.contains("PHIẾU CÂN LÚA")) {
                // Fallback for plain text receipt without machine tag
                return parsePlainTextReceipt(trimmed)
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
                val updatedAt = obj.optLong("u", timestamp)

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
                        createdAt = timestamp,
                        updatedAt = updatedAt
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
     * Fallback parser for plain text receipts
     */
    private fun parsePlainTextReceipt(text: String): Result<List<RiceRecord>> {
        return try {
            val lines = text.lines()
            var customerName = "Khách hàng"
            var unitPrice = 0L
            val weightList = mutableListOf<Double>()
            var grandTotal = 0.0
            var totalMoney = 0L

            for (line in lines) {
                val lineTrimmed = line.trim()
                if (lineTrimmed.startsWith("👤 Khách:") || lineTrimmed.startsWith("Khách hàng:")) {
                    customerName = lineTrimmed.substringAfter(":").trim()
                } else if (lineTrimmed.contains("Đơn giá:") || lineTrimmed.contains("Giá:")) {
                    val pricePart = lineTrimmed.substringAfter(":").replace(Regex("[^0-9]"), "")
                    unitPrice = pricePart.toLongOrNull() ?: 0L
                } else if (lineTrimmed.contains("Tổng:") || lineTrimmed.contains("TỔNG KHỐI LƯỢNG:")) {
                    val totalMatch = Regex("([0-9]+[.,]?[0-9]*)\\s*kg").find(lineTrimmed)
                    if (totalMatch != null) {
                        val numStr = totalMatch.groupValues[1].replace(",", ".")
                        grandTotal = numStr.toDoubleOrNull() ?: 0.0
                    }
                } else if (lineTrimmed.contains("THÀNH TIỀN:")) {
                    val moneyPart = lineTrimmed.substringAfter("THÀNH TIỀN:").replace(Regex("[^0-9]"), "")
                    totalMoney = moneyPart.toLongOrNull() ?: 0L
                } else if (lineTrimmed.startsWith("Cột") || lineTrimmed.startsWith("•") || (lineTrimmed.contains(",") && lineTrimmed.any { it.isDigit() })) {
                    // Strip parenthesized column total like "(151.0 kg)" so it isn't parsed as a bag
                    val cleanLine = lineTrimmed.replace(Regex("\\(.*?\\)"), "")
                    val numbers = Regex("[0-9]+[.,][0-9]+").findAll(cleanLine)
                    for (match in numbers) {
                        val w = match.value.replace(",", ".").toDoubleOrNull()
                        if (w != null && w in 10.0..200.0) {
                            weightList.add(w)
                        }
                    }
                }
            }

            if (weightList.isEmpty() && grandTotal <= 0.0) {
                return Result.failure(IllegalArgumentException("Không tìm thấy dữ liệu đợt cân hợp lệ trong nội dung."))
            }

            if (grandTotal == 0.0 && weightList.isNotEmpty()) {
                grandTotal = weightList.sum()
            }
            if (totalMoney == 0L && unitPrice > 0L) {
                totalMoney = (grandTotal * unitPrice).toLong()
            }

            val record = RiceRecord(
                id = 0L,
                customerName = customerName,
                unitPrice = unitPrice,
                weightList = weightList,
                grandTotal = grandTotal,
                totalMoney = totalMoney,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            Result.success(listOf(record))
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi phân tích phiếu cân: ${e.message}", e))
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
