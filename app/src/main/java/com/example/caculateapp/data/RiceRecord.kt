package com.example.caculateapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * Room Entity representing a rice weighing session record (100% offline)
 */
@Entity(tableName = "rice_records")
data class RiceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    
    val customerName: String = "",
    
    val unitPrice: Long = 0L, // VNĐ per kg (integer only)
    
    val weightList: List<Double> = emptyList(), // List of all weight entries
    
    val grandTotal: Double = 0.0, // Sum of all weights
    
    val totalMoney: Long = 0L, // grandTotal * unitPrice (integer only)
    
    val createdAt: Long = System.currentTimeMillis() // Local timestamp (epoch millis)
)

/**
 * Room TypeConverter for storing List<Double> in SQLite as comma-separated values
 */
class Converters {
    @TypeConverter
    fun fromDoubleList(list: List<Double>?): String {
        return list?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toDoubleList(data: String?): List<Double> {
        if (data.isNullOrEmpty()) return emptyList()
        return data.split(",").mapNotNull { it.toDoubleOrNull() }
    }
}
