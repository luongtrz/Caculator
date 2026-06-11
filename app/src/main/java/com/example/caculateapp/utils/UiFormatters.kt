package com.example.caculateapp.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiFormatters {
    private val displayLocale = Locale.getDefault()
    private val vietnameseLocale = Locale("vi", "VN")

    private val cellWeightFormat = DecimalFormat(
        "0.##",
        DecimalFormatSymbols.getInstance(displayLocale)
    )
    private val totalWeightFormat = DecimalFormat(
        "0.0",
        DecimalFormatSymbols.getInstance(displayLocale)
    )
    private val moneyFormat: NumberFormat = NumberFormat.getIntegerInstance(vietnameseLocale)
    private val historyDateFormat = SimpleDateFormat("HH:mm • dd/MM/yyyy", displayLocale)
    private val exportDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", displayLocale)
    private val toolbarTimeFormat = SimpleDateFormat("HH:mm", displayLocale)
    private val shortDateFormat = SimpleDateFormat("dd/MM/yyyy", displayLocale)
    private val dayNames = arrayOf(
        "",
        "Chủ nhật",
        "Thứ 2",
        "Thứ 3",
        "Thứ 4",
        "Thứ 5",
        "Thứ 6",
        "Thứ 7"
    )

    fun weightCell(value: Double): String = when {
        value <= 0.0 -> ""
        value % 1.0 == 0.0 -> value.toInt().toString()
        else -> cellWeightFormat.format(value)
    }

    fun weightTotal(value: Double): String = "${totalWeightFormat.format(value)} kg"

    fun currency(value: Long): String = "${moneyFormat.format(value)} VNĐ"

    fun unitPrice(value: Long): String = "${moneyFormat.format(value)} VNĐ/kg"

    fun historyTimestamp(date: Date): String = historyDateFormat.format(date)

    fun exportTimestamp(date: Date): String = exportDateFormat.format(date)

    fun toolbarDate(date: Date): String = shortDateFormat.format(date)

    fun historyToolbarSubtitle(date: Date): String {
        val calendar = java.util.Calendar.getInstance().apply { time = date }
        val dayName = dayNames[calendar.get(java.util.Calendar.DAY_OF_WEEK)]
        return "${toolbarTimeFormat.format(date)}  $dayName, ${shortDateFormat.format(date)}"
    }
}
