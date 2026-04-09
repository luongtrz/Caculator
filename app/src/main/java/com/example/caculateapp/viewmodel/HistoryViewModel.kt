package com.example.caculateapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.caculateapp.data.FirebaseService
import com.example.caculateapp.data.RiceRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * ViewModel for History Screen
 * Manages list of all saved rice weighing sessions
 * Now uses Firebase Firestore with realtime updates
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val firebaseService = FirebaseService()
    
    /**
     * Flow of all records sorted by date (newest first)
     * Realtime updates from Firestore
     */
    val allRecords: Flow<List<RiceRecord>> = firebaseService.getRecordsFlow()
    
    // Sync status (true = synced, false = pending)
    private val _isCloudSynced = MutableStateFlow(true)
    val isCloudSynced: StateFlow<Boolean> = _isCloudSynced.asStateFlow()
    
    private var syncListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    
    init {
        startSyncMonitoring()
    }
    
    /**
     * Search records by customer name
     * Note: Firestore search is limited, doing client-side filtering for now
     */
    fun searchRecords(query: String): Flow<List<RiceRecord>> {
        return if (query.isBlank()) {
            allRecords
        } else {
            // Client-side search - could be improved with Algolia or similar
            allRecords.map { records ->
                records.filter { record ->
                    record.customerName.contains(query, ignoreCase = true)
                }
            }
        }
    }
    
    /**
     * Delete a specific record
     */
    fun deleteRecord(record: RiceRecord, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val recordId = record.id ?: return@launch
            
            val result = firebaseService.deleteRecord(recordId)
            onComplete(result.isSuccess)
        }
    }
    
    /**
     * Delete record by ID
     */
    fun deleteRecordById(recordId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = firebaseService.deleteRecord(recordId)
            onComplete(result.isSuccess)
        }
    }
    
    /**
     * Start monitoring Firestore sync status
     * Persists across configuration changes/navigation unlike Activity-scoped listeners
     */
    /**
     * Start monitoring Firestore sync status
     * Persists across configuration changes/navigation unlike Activity-scoped listeners
     */
    private fun startSyncMonitoring() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        val firestore = com.google.firebase.ktx.Firebase.firestore
        
        // Remove existing listener if any
        syncListenerRegistration?.remove()
        
        // Listen to metadata changes on the records collection
        // Note: NO Activity scope passed here, so it survives navigation
        syncListenerRegistration = firestore.collection("users")
            .document(user.uid)
            .collection("records")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, e ->
                if (e != null) {
                    // Log error if needed: android.util.Log.e("Sync", "Listen failed", e)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) {
                    // Local changes not yet written to backend
                    _isCloudSynced.value = false
                } else {
                    // All synced (or no data)
                    _isCloudSynced.value = true
                }
            }
    }
    
    /**
     * Manual check for sync status (Failsafe)
     */
    fun checkSyncStatus() {
        viewModelScope.launch {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return@launch
            val firestore = com.google.firebase.ktx.Firebase.firestore
            
            try {
                // Quick check for pending writes
                val hasPending = withTimeoutOrNull(2000) {
                    try {
                        firestore.waitForPendingWrites().await()
                        false
                    } catch (e: Exception) {
                        true
                    }
                } ?: true
                
                // Only update if true (pending), otherwise let listener handle it
                // Or we can query metadata of a dummy query?
                // Actually waitForPendingWrites is blocking.
                // Alternative: Check if we can get a snapshot from cache and check metadata
                
                // Let's rely on the listener, but re-register it if needed?
                // Or just force a state update if we detect pending
                
                if (hasPending) {
                     _isCloudSynced.value = false
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        syncListenerRegistration?.remove()
    }
}
