package com.example.caculateapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/**
 * FirebaseService - Manages Firestore operations
 * All data is scoped to current user's ID
 */
class FirebaseService {
    
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    /**
     * Get current user's records collection reference
     */
    private fun getUserRecordsCollection() = firestore
        .collection("users")
        .document(getCurrentUserId())
        .collection("records")
    
    /**
     * Get current user ID (must be signed in)
     */
    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid 
            ?: throw IllegalStateException("User must be signed in")
    }
    
    /**
     * Save a new record to Firestore
     * @return Document ID of the saved record
     */
    suspend fun saveRecord(record: RiceRecord): Result<String> {
        return try {
            val docRef = getUserRecordsCollection().document()
            val recordWithId = record.copy(id = docRef.id)
            
            try {
                // Wait usually means Server ACK. If offline, this hangs.
                // We wait max 2s, if timeout, we assume Offline Success.
                withTimeout(2000) {
                    docRef.set(recordWithId).await()
                }
            } catch (e: TimeoutCancellationException) {
                // Offline mode: Task is pending, but local cache is updated
            }
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update an existing record
     */
    suspend fun updateRecord(record: RiceRecord): Result<Unit> {
        return try {
            if (record.id == null) {
                return Result.failure(IllegalArgumentException("Record ID is null"))
            }
            
            val task = getUserRecordsCollection()
                .document(record.id)
                .set(record)
                
            try {
                withTimeout(2000) {
                    task.await()
                }
            } catch (e: TimeoutCancellationException) {
                // Offline success
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all records for current user (one-time read)
     */
    suspend fun getRecords(): Result<List<RiceRecord>> {
        return try {
            val snapshot = getUserRecordsCollection()
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val records = snapshot.documents.mapNotNull { doc ->
                doc.toObject(RiceRecord::class.java)
            }
            
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get records as Flow for real-time updates (cost-efficient for current user)
     * Use this for HistoryActivity
     */
    fun getRecordsFlow(): Flow<List<RiceRecord>> = callbackFlow {
        val listenerRegistration = getUserRecordsCollection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val records = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(RiceRecord::class.java)
                } ?: emptyList()
                
                trySend(records)
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Get single record by ID
     */
    suspend fun getRecord(recordId: String): Result<RiceRecord> {
        return try {
            val doc = getUserRecordsCollection()
                .document(recordId)
                .get()
                .await()
            
            val record = doc.toObject(RiceRecord::class.java)
            
            if (record != null) {
                Result.success(record)
            } else {
                Result.failure(Exception("Record not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a record
     */
    suspend fun deleteRecord(recordId: String): Result<Unit> {
        return try {
            getUserRecordsCollection()
                .document(recordId)
                .delete()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Search records by customer name
     */
    suspend fun searchRecords(query: String): Result<List<RiceRecord>> {
        return try {
            val snapshot = getUserRecordsCollection()
                .orderBy("customerName")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .get()
                .await()
            
            val records = snapshot.documents.mapNotNull { doc ->
                doc.toObject(RiceRecord::class.java)
            }
            
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
