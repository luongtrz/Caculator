package com.example.caculateapp.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.example.caculateapp.R

class ExportManager(context: Context) {

    private val context = context.applicationContext

    private val viLocale = Locale("vi", "VN")
    private val moneyFormat = NumberFormat.getInstance(viLocale)

    private fun showToastLong(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun createColumnView(columnNumber: Int, weights: List<Double>): View {
        val columnLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            setPadding(8, 0, 8, 0)
        }
        columnLayout.addView(android.widget.TextView(context).apply {
            text = "Cột $columnNumber"
            setTextColor(android.graphics.Color.BLACK)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })
        weights.filter { it > 0.0 }.forEach { weight ->
            columnLayout.addView(android.widget.TextView(context).apply {
                text = if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString()
                setTextColor(android.graphics.Color.BLACK)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 4, 0, 4)
            })
        }
        columnLayout.addView(android.view.View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2
            ).apply { setMargins(0, 8, 0, 8) }
            setBackgroundColor(android.graphics.Color.BLACK)
        })
        columnLayout.addView(android.widget.TextView(context).apply {
            text = String.format("%.1f kg", weights.sum())
            setTextColor(android.graphics.Color.BLACK)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        })
        return columnLayout
    }

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
            val pageView = android.view.LayoutInflater.from(context)
                .inflate(R.layout.layout_export_page, null)

            pageView.findViewById<android.widget.TextView>(R.id.tv_page_customer_name).text = customerName
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_date).text =
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_unit_price).text =
                "${moneyFormat.format(unitPrice)} VNĐ/kg"
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_title).text =
                "PHIẾU CÂN - Trang ${pageIndex + 1}/${columnChunks.size}"

            val topRow = pageView.findViewById<android.widget.LinearLayout>(R.id.layout_top_row)
            val bottomRow = pageView.findViewById<android.widget.LinearLayout>(R.id.layout_bottom_row)

            var pageTotal = 0.0
            pageColumns.forEachIndexed { colIndex, colWeights ->
                val colView = createColumnView(pageIndex * 10 + colIndex + 1, colWeights)
                pageTotal += colWeights.sum()
                if (colIndex < 5) topRow.addView(colView) else bottomRow.addView(colView)
            }

            pageView.findViewById<android.widget.TextView>(R.id.tv_page_total).text =
                String.format("%.1f kg", pageTotal)
            pageView.findViewById<android.widget.TextView>(R.id.tv_grand_total).text =
                String.format("%.1f kg", grandTotal)
            pageView.findViewById<android.widget.TextView>(R.id.tv_total_money).text =
                "${moneyFormat.format(totalMoney)} VNĐ"

            pages.add(captureViewToBitmap(pageView))
        }
        return pages
    }

    fun captureViewToBitmap(view: View): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    fun saveBitmapToGallery(bitmap: Bitmap, customFilename: String? = null, showToast: Boolean = true): Uri? {
        val filename = customFilename ?: generateFilename("jpg")
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RiceManager")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            }
            uri
        } catch (e: Exception) {
            if (showToast) showToastLong("Lỗi khi lưu ảnh: ${e.message}")
            null
        }
    }

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
            pages.mapIndexedNotNull { index, bitmap ->
                val filename = generateFilename("jpg").replace(".jpg", "_page${index + 1}.jpg")
                saveBitmapToGallery(bitmap, filename, showToast).also { bitmap.recycle() }
            }
        }
        if (showToast && uris.isNotEmpty()) {
            showToastLong("Đã xuất ${uris.size} ảnh vào thư mục Pictures/RiceManager")
        }
        return uris
    }

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
            val pdfDocument = PdfDocument()
            try {
                pages.forEachIndexed { index, bitmap ->
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                    bitmap.recycle()
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, generateFilename("pdf"))
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RiceManager")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), contentValues
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out -> pdfDocument.writeTo(out) }
                    if (showToast) showToastLong("Đã xuất PDF ${pages.size} trang vào Downloads/RiceManager")
                }
                uri
            } catch (e: Exception) {
                if (showToast) showToastLong("Lỗi khi tạo PDF: ${e.message}")
                null
            } finally {
                pdfDocument.close()
            }
        }
    }

    fun shareFile(uri: Uri, mimeType: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Chia sẻ qua..."))
    }

    fun shareMultipleFiles(uris: List<Uri>, mimeType: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Chia sẻ ${uris.size} ảnh qua..."))
    }

    private fun generateFilename(extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "PhieuCanLua_$timestamp.$extension"
    }

    fun generateCustomFilename(customerName: String, extension: String): String {
        val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val safeName = customerName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "${safeName}_${date}.$extension"
    }
}
