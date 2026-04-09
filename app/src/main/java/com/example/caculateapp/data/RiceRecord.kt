package com.example.caculateapp.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Entity representing a rice weighing session record
 * Firestore-only data model (Room Database removed)
 */
data class RiceRecord(
    @DocumentId
    val id: String? = null, // Firestore document ID (nullable for new records)
    
    val customerName: String = "",
    
    val unitPrice: Long = 0L, // VNĐ per kg (integer only)
    
    val weightList: List<Double> = emptyList(), // List of all weight entries
    
    val grandTotal: Double = 0.0, // Sum of all weights
    
    val totalMoney: Long = 0L, // grandTotal * unitPrice (integer only)
    
    @ServerTimestamp
    val createdAt: Date? = null, // Firestore server timestamp
    
    val updatedAt: Long = System.currentTimeMillis() // Local timestamp
) {
    // No-argument constructor for Firestore
    constructor() : this(
        id = null,
        customerName = "",
        unitPrice = 0L,
        weightList = emptyList(),
        grandTotal = 0.0,
        totalMoney = 0L,
        createdAt = null,
        updatedAt = System.currentTimeMillis()
    )
}
