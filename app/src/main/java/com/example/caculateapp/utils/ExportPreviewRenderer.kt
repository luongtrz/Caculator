package com.example.caculateapp.utils

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.caculateapp.R
import com.example.caculateapp.databinding.LayoutExportPageBinding
import java.util.Date

data class ExportPayload(
    val customerName: String,
    val unitPrice: Long,
    val grandTotal: Double,
    val totalMoney: Long,
    val columns: List<List<Double>>,
    val createdAt: Date
)

object ExportPreviewRenderer {
    private const val BAGS_PER_COLUMN = 5
    private const val COLUMNS_PER_PAGE = 10

    fun toColumns(weights: List<Double>): List<List<Double>> {
        if (weights.isEmpty()) {
            return listOf(List(BAGS_PER_COLUMN) { 0.0 })
        }

        return weights.chunked(BAGS_PER_COLUMN).map { chunk ->
            buildList(BAGS_PER_COLUMN) {
                addAll(chunk)
                repeat(BAGS_PER_COLUMN - chunk.size) {
                    add(0.0)
                }
            }
        }
    }

    fun createPageViews(
        context: Context,
        inflater: LayoutInflater,
        payload: ExportPayload
    ): List<View> {
        val columnChunks = payload.columns.chunked(COLUMNS_PER_PAGE)
        return columnChunks.mapIndexed { pageIndex, pageColumns ->
            val binding = LayoutExportPageBinding.inflate(inflater)
            val pageTitle = "PHIẾU CÂN • Trang ${pageIndex + 1}/${columnChunks.size}"

            binding.tvPageCustomerName.text = payload.customerName
            binding.tvPageDate.text = UiFormatters.exportTimestamp(payload.createdAt)
            binding.tvPageUnitPrice.text = UiFormatters.unitPrice(payload.unitPrice)
            binding.tvPageTitle.text = pageTitle

            var pageTotal = 0.0
            pageColumns.forEachIndexed { columnOffset, weights ->
                val columnNumber = pageIndex * COLUMNS_PER_PAGE + columnOffset + 1
                val columnView = createColumnView(context, columnNumber, weights)
                pageTotal += weights.sum()

                if (columnOffset < COLUMNS_PER_PAGE / 2) {
                    binding.layoutTopRow.addView(columnView)
                } else {
                    binding.layoutBottomRow.addView(columnView)
                }
            }

            binding.tvPageTotal.text = UiFormatters.weightTotal(pageTotal)
            binding.tvGrandTotal.text = UiFormatters.weightTotal(payload.grandTotal)
            binding.tvTotalMoney.text = UiFormatters.currency(payload.totalMoney)

            binding.root.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = context.resources.getDimensionPixelSize(R.dimen.spacing_md)
            }

            binding.root
        }
    }

    private fun createColumnView(
        context: Context,
        columnNumber: Int,
        weights: List<Double>
    ): View {
        val horizontalPadding = context.resources.getDimensionPixelSize(R.dimen.export_column_padding)
        val rowHeight = context.resources.getDimensionPixelSize(R.dimen.export_column_row_height)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(horizontalPadding, 0, horizontalPadding, 0)

            addView(TextView(context).apply {
                text = "Cột $columnNumber"
                setTextColor(ContextCompat.getColor(context, R.color.export_text_primary))
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, horizontalPadding)
            })

            weights.forEach { weight ->
                addView(TextView(context).apply {
                    text = UiFormatters.weightCell(weight)
                    setTextColor(ContextCompat.getColor(context, R.color.export_text_primary))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    minHeight = rowHeight
                })
            }

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.resources.getDimensionPixelSize(R.dimen.export_divider_height)
                ).apply {
                    topMargin = horizontalPadding
                    bottomMargin = horizontalPadding
                }
                setBackgroundColor(ContextCompat.getColor(context, R.color.export_text_primary))
            })

            addView(TextView(context).apply {
                text = UiFormatters.weightTotal(weights.sum())
                setTextColor(ContextCompat.getColor(context, R.color.export_text_primary))
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
    }
}
