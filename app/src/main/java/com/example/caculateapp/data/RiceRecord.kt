package com.example.caculateapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Entity representing a rice weighing session record
 */
@Entity(tableName = "rice_records")
data class RiceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val customerName: String,
    
    val unitPrice: Double, // VNĐ per kg
    
    val weightList: List<Double>, // List of all weight entries
    
    val grandTotal: Double, // Sum of all weights
    
    val totalMoney: Double, // totalWeight * unitPrice
    
    val createdAt: Long = System.currentTimeMillis() // Timestamp for sorting
)

/**
 * TypeConverter for List<Double> to store in Room database
 */
class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromDoubleList(value: List<Double>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toDoubleList(value: String): List<Double> {
        val listType = object : TypeToken<List<Double>>() {}.type
        return gson.fromJson(value, listType)
    }
}
