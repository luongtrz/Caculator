package com.example.caculateapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.caculateapp.data.AppDatabase
import com.example.caculateapp.data.RiceRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * ViewModel for History Screen
 * Manages list of all saved rice weighing sessions
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val dao = AppDatabase.getDatabase(application).riceDao()
    
    /**
     * Flow of all records sorted by date (newest first)
     */
    val allRecords: Flow<List<RiceRecord>> = dao.getAllRecordsSortedByDate()
    
    /**
     * Search records by customer name
     */
    fun searchRecords(query: String): Flow<List<RiceRecord>> {
        return if (query.isBlank()) {
            allRecords
        } else {
            dao.searchRecords(query)
        }
    }
    
    /**
     * Delete a specific record
     */
    fun deleteRecord(record: RiceRecord) {
        viewModelScope.launch {
            dao.delete(record)
        }
    }
    
    /**
     * Delete record by ID
     */
    fun deleteRecordById(recordId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            dao.getRecordById(recordId)?.let { record ->
                dao.delete(record)
                onComplete()
            }
        }
    }
}
