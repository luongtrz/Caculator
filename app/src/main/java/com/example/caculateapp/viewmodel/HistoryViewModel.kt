package com.example.caculateapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.caculateapp.data.AppDatabase
import com.example.caculateapp.data.RiceRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * ViewModel for History Screen (100% Offline Room Database)
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val dao = AppDatabase.getDatabase(application).riceDao()
    
    val allRecords: Flow<List<RiceRecord>> = dao.getAllRecords()
    
    fun searchRecords(query: String): Flow<List<RiceRecord>> {
        return if (query.isBlank()) {
            allRecords
        } else {
            dao.searchRecords(query)
        }
    }
    
    fun deleteRecord(record: RiceRecord, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                dao.delete(record)
                onComplete(true)
            } catch (_: Exception) {
                onComplete(false)
            }
        }
    }
    
    fun importRecords(records: List<RiceRecord>, onComplete: (Int, Int) -> Unit) {
        viewModelScope.launch {
            var success = 0
            var failed = 0
            for (record in records) {
                try {
                    dao.insert(record.copy(id = 0L))
                    success++
                } catch (_: Exception) {
                    failed++
                }
            }
            onComplete(success, failed)
        }
    }
}
