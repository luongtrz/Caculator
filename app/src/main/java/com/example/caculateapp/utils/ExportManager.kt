package com.example.caculateapp.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

/**
 * ExportManager: Handles export to Image and PDF
 * Uses MediaStore API for modern Android compatibility
 */
class ExportManager(private val context: Context) {

    private fun showToastLong(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Create multiple page bitmaps (max 10 columns per page)
     */
    fun createMultiplePages(
        columns: List<List<Double>>,
        customerName: String,
        unitPrice: Long,
        grandTotal: Double,
        totalMoney: Long,
        createdAt: Date = Date()
    ): List<Bitmap> {
        val payload = ExportPayload(
            customerName = customerName,
            unitPrice = unitPrice,
            grandTotal = grandTotal,
            totalMoney = totalMoney,
            columns = columns,
            createdAt = createdAt
        )
        val inflater = android.view.LayoutInflater.from(context)

        return ExportPreviewRenderer
            .createPageViews(context, inflater, payload)
            .map { captureViewToBitmap(it) }
    }

    /**
     * Capture a View as Bitmap
     */
    fun captureViewToBitmap(view: View): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(
            view.measuredWidth,
            view.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        return bitmap
    }

    /**
     * Save Bitmap to Gallery using MediaStore API
     * Works on all Android versions without storage permissions
     */
    fun saveBitmapToGallery(
        bitmap: Bitmap,
        customFilename: String? = null,
        showToast: Boolean = true
    ): Uri? {
        val filename = customFilename ?: generateFilename("jpg")
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RiceManager")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            if (showToast) {
                showToastLong("Lỗi khi lưu ảnh: ${e.message}")
            }
            null
        }
    }

    /**
     * Generate PDF from View using PdfDocument
     * Saves to Downloads folder using MediaStore
     */
    fun generatePDF(view: View, customFilename: String? = null): Uri? {
        val filename = customFilename ?: generateFilename("pdf")
        return try {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(595, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            val canvas = page.canvas
            val scaleX = 595f / view.measuredWidth
            val scaleY = 842f / view.measuredHeight
            val scale = minOf(scaleX, scaleY)

            canvas.scale(scale, scale)
            view.draw(canvas)

            pdfDocument.finishPage(page)

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Documents/RiceManager")
                }

                context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "RiceManager"
                )
                if (!dir.exists()) dir.mkdirs()

                val file = File(dir, filename)
                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                pdfDocument.close()

                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                pdfDocument.close()
                showToastLong("Đã lưu PDF vào thư mục Documents/RiceManager")
                it
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToastLong("Lỗi khi tạo PDF: ${e.message}")
            null
        }
    }

    /**
     * Export to multiple images (one per page)
     */
    suspend fun exportToMultipleImages(
        columns: List<List<Double>>,
        customerName: String,
        unitPrice: Long,
        grandTotal: Double,
        totalMoney: Long,
        showToast: Boolean = true,
        createdAt: Date = Date()
    ): List<Uri> {
        val pages = createMultiplePages(
            columns = columns,
            customerName = customerName,
            unitPrice = unitPrice,
            grandTotal = grandTotal,
            totalMoney = totalMoney,
            createdAt = createdAt
        )
        val uris = withContext(Dispatchers.IO) {
            val pageUris = mutableListOf<Uri>()
            pages.forEachIndexed { index, bitmap ->
                val filename = generateFilename("jpg").replace(".jpg", "_page${index + 1}.jpg")
                saveBitmapToGallery(bitmap, filename, showToast)?.let(pageUris::add)
            }
            pageUris
        }

        if (showToast && uris.isNotEmpty()) {
            showToastLong("Đã xuất ${uris.size} ảnh vào thư mục Pictures/RiceManager")
        }

        return uris
    }

    /**
     * Export to multi-page PDF
     */
    suspend fun exportToMultiPagePDF(
        columns: List<List<Double>>,
        customerName: String,
        unitPrice: Long,
        grandTotal: Double,
        totalMoney: Long,
        showToast: Boolean = true,
        createdAt: Date = Date()
    ): Uri? {
        val pages = createMultiplePages(
            columns = columns,
            customerName = customerName,
            unitPrice = unitPrice,
            grandTotal = grandTotal,
            totalMoney = totalMoney,
            createdAt = createdAt
        )
        return withContext(Dispatchers.IO) {
            try {
                val pdfDocument = PdfDocument()

                pages.forEachIndexed { index, bitmap ->
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width,
                        bitmap.height,
                        index + 1
                    ).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                }

                val filename = generateFilename("pdf")
                val contentValues = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, filename)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                    put(
                        MediaStore.Files.FileColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/RiceManager"
                    )
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"),
                    contentValues
                )

                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                    pdfDocument.close()
                    if (showToast) {
                        showToastLong("Đã xuất PDF ${pages.size} trang vào thư mục Downloads/RiceManager")
                    }
                    it
                } ?: run {
                    pdfDocument.close()
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (showToast) {
                    showToastLong("Lỗi khi tạo PDF: ${e.message}")
                }
                null
            }
        }
    }

    /**
     * Share file via intent
     */
    fun shareFile(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ qua..."))
    }

    /**
     * Share multiple files
     */
    fun shareMultipleFiles(uris: List<Uri>, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ ${uris.size} ảnh qua..."))
    }

    /**
     * Generate filename with timestamp
     */
    private fun generateFilename(extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "PhieuCanLua_$timestamp.$extension"
    }

    /**
     * Generate custom filename with customer name and date
     */
    fun generateCustomFilename(customerName: String, extension: String): String {
        val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val safeName = customerName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "${safeName}_${date}.$extension"
    }
}
