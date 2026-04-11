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
import java.util.*

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
     * Create a simple column view for export (vertical numbers only)
     */
    private fun createColumnView(columnNumber: Int, weights: List<Double>): View {
        val columnLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(8, 0, 8, 0)
        }
        
        // Column header
        val header = android.widget.TextView(context).apply {
            text = "Cột $columnNumber"
            setTextColor(android.graphics.Color.BLACK)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        columnLayout.addView(header)
        
        // Weight values (only non-zero)
        weights.filter { it > 0.0 }.forEach { weight ->
            val weightText = android.widget.TextView(context).apply {
                text = if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString()
                setTextColor(android.graphics.Color.BLACK)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 4, 0, 4)
            }
            columnLayout.addView(weightText)
        }
        
        // Divider
        val divider = android.view.View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        columnLayout.addView(divider)
        
        // Column total
        val total = weights.sum()
        val totalText = android.widget.TextView(context).apply {
            text = String.format("%.1f kg", total)
            setTextColor(android.graphics.Color.BLACK)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        columnLayout.addView(totalText)
        
        return columnLayout
    }
    
    /**
     * Create multiple page bitmaps (max 10 columns per page)
     */
    fun createMultiplePages(
        columns: List<List<Double>>,
        customerName: String,
        unitPrice: Long,
        grandTotal: Double,
        totalMoney: Long
    ): List<Bitmap> {
        val pages = mutableListOf<Bitmap>()
        val columnChunks = columns.chunked(10)
        
        columnChunks.forEachIndexed { pageIndex, pageColumns ->
            val inflater = android.view.LayoutInflater.from(context)
            val pageView = inflater.inflate(
                context.resources.getIdentifier("layout_export_page", "layout", context.packageName),
                null
            )
            
            // Populate header
            pageView.findViewById<android.widget.TextView>(
                context.resources.getIdentifier("tv_page_customer_name", "id", context.packageName)
            ).text = customerName
            
            pageView.findViewById<android.widget.TextView>(
                context.resources.getIdentifier("tv_page_date", "id", context.packageName)
            ).text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            
            pageView.findViewById<android.widget.TextView>(
                context.resources.getIdentifier("tv_page_unit_price", "id", context.packageName)
            ).text = String.format("%,d VNĐ/kg", unitPrice)
            
            pageView.findViewById<android.widget.TextView>(
                context.resources.getIdentifier("tv_page_title", "id", context.packageName)
            ).text = "PHIẾU CÂN - Trang ${pageIndex + 1}/${columnChunks.size}"
            
            // Get row containers
            val topRow = pageView.findViewById<android.widget.LinearLayout>(
                context.resources.getIdentifier("layout_top_row", "id", context.packageName)
            )
            val bottomRow = pageView.findViewById<android.widget.LinearLayout>(
                context.resources.getIdentifier("layout_bottom_row", "id", context.packageName)
            )
            
            // Add columns (first 5 to top, next 5 to bottom)
            var pageTotal = 0.0
            pageColumns.forEachIndexed { colIndex, colWeights ->
                val globalColumnNumber = pageIndex * 10 + colIndex + 1
                val columnView = createColumnView(globalColumnNumber, colWeights)
                pageTotal += colWeights.sum()
                
                if (colIndex < 5) {
                    topRow.addView(columnView)
                } else {
                    bottomRow.addView(columnView)
                }
            }
            
            // Populate totals
            pageView.findViewById<android.widget.TextView>(
                context.resources.getIdentifier("tv_page_total", "id", context.packageName)
            ).text = String.format("%.1f kg", pageTotal)
            
            pageView.findViewById<android.widget.TextView>(
                context.resources.getIdentifier("tv_grand_total", "id", context.packageName)
            ).text = String.format("%.1f kg", grandTotal)
            
            pageView.findViewById<android.widget.TextView>(
                context.resources.getIdentifier("tv_total_money", "id", context.packageName)
            ).text = String.format("%,d VNĐ", totalMoney)
            
            // Capture page as bitmap
            val bitmap = captureViewToBitmap(pageView)
            pages.add(bitmap)
        }
        
        return pages
    }
    
    /**
     * Capture a View as Bitmap
     */
    fun captureViewToBitmap(view: View): Bitmap {
        // Measure and layout the view
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        
        // Create bitmap and draw view on it
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
                // Toast removed to avoid duplication with exportToMultipleImages summary
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
            // Measure and layout view
            view.measure(
                View.MeasureSpec.makeMeasureSpec(595, View.MeasureSpec.EXACTLY), // A4 width in points
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            
            // Create PDF document
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            val page = pdfDocument.startPage(pageInfo)
            
            // Scale view to fit A4
            val canvas = page.canvas
            val scaleX = 595f / view.measuredWidth
            val scaleY = 842f / view.measuredHeight
            val scale = minOf(scaleX, scaleY)
            
            canvas.scale(scale, scale)
            view.draw(canvas)
            
            pdfDocument.finishPage(page)
            
            // Save PDF using MediaStore
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: Use MediaStore
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
                // Android 9 and below: Use external storage
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
                
                // Return file URI using FileProvider
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            
            // Write PDF for Android 10+
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
        showToast: Boolean = true
    ): List<Uri> {
        val pages = createMultiplePages(columns, customerName, unitPrice, grandTotal, totalMoney)
        val uris = withContext(Dispatchers.IO) {
            val pageUris = mutableListOf<Uri>()
            pages.forEachIndexed { index, bitmap ->
                val filename = generateFilename("jpg").replace(".jpg", "_page${index + 1}.jpg")
                saveBitmapToGallery(bitmap, filename, showToast)?.let { uri ->
                    pageUris.add(uri)
                }
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
        showToast: Boolean = true
    ): Uri? {
        val pages = createMultiplePages(columns, customerName, unitPrice, grandTotal, totalMoney)
        return withContext(Dispatchers.IO) {
            try {
                val pdfDocument = PdfDocument()
                
                pages.forEachIndexed { index, bitmap ->
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    
                    val canvas = page.canvas
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    
                    pdfDocument.finishPage(page)
                }
                
                // Save PDF
                val filename = generateFilename("pdf")
                val contentValues = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, filename)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RiceManager")
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
