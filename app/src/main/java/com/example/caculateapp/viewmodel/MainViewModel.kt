package com.example.caculateapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.caculateapp.data.AppDatabase
import com.example.caculateapp.data.RiceRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel for MainActivity
 * Manages rice weighing session data with real-time calculations
 * Handles both new session creation and existing session editing (100% Offline Room Database)
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val INITIAL_COLUMNS = 1
        private const val BAGS_PER_COLUMN = 5
        private const val INITIAL_CELL_COUNT = INITIAL_COLUMNS * BAGS_PER_COLUMN
    }
    
    private val dao = AppDatabase.getDatabase(application).riceDao()
    
    // Current record ID for edit mode (null/0 = new session, non-null = editing)
    private var currentRecordId: Long? = null
    
    // Original state for change tracking
    private var originalRecord: RiceRecord? = null
    
    // Customer information
    private val _customerName = MutableLiveData<String>("")
    val customerName: LiveData<String> = _customerName
    
    private val _unitPrice = MutableLiveData<Long>(0L)
    val unitPrice: LiveData<Long> = _unitPrice
    
    // Weight data
    private val _weightList = MutableLiveData<MutableList<Double>>(mutableListOf())
    val weightList: LiveData<MutableList<Double>> = _weightList
    
    // Calculated totals
    private val _columnTotals = MutableLiveData<List<Double>>(emptyList())
    val columnTotals: LiveData<List<Double>> = _columnTotals
    
    private val _grandTotal = MutableLiveData<Double>(0.0)
    val grandTotal: LiveData<Double> = _grandTotal
    
    private val _totalMoney = MutableLiveData<Long>(0L)
    val totalMoney: LiveData<Long> = _totalMoney
    
    // UI state
    private val _saveStatus = MutableLiveData<String>()
    val saveStatus: LiveData<String> = _saveStatus
    
    private val _isLocked = MutableLiveData<Boolean>(false)
    val isLocked: LiveData<Boolean> = _isLocked
    
    init {
        initializeEmptySession()
    }
    
    /**
     * Initialize with empty cells for new session
     */
    private fun initializeEmptySession() {
        val initialWeights = MutableList(INITIAL_CELL_COUNT) { 0.0 }
        _weightList.value = initialWeights
        calculateTotals()
        
        // Initialize original record as empty
        originalRecord = RiceRecord(
            customerName = "",
            unitPrice = 0L,
            weightList = initialWeights.toList(),
            grandTotal = 0.0,
            totalMoney = 0L
        )
    }
    
    /**
     * Load existing record for editing
     */
    fun loadExistingRecord(recordId: Long) {
        viewModelScope.launch {
            try {
                val record = dao.getRecordById(recordId)
                if (record != null) {
                    currentRecordId = record.id
                    _customerName.value = record.customerName
                    _unitPrice.value = record.unitPrice
                    _weightList.value = record.weightList.toMutableList()
                    calculateTotals()
                    
                    // Save original state for change tracking
                    originalRecord = record.copy()
                } else {
                    _saveStatus.value = "Lỗi khi tải dữ liệu: Không tìm thấy"
                }
            } catch (e: Exception) {
                _saveStatus.value = "Lỗi khi tải dữ liệu: ${e.message}"
            }
        }
    }
    
    /**
     * Update customer name
     */
    fun setCustomerName(name: String) {
        _customerName.value = name
    }
    
    /**
     * Update unit price
     */
    fun setUnitPrice(price: Long) {
        _unitPrice.value = price
        calculateTotals() // Recalculate total money
    }
    
    /**
     * Add a new column (5 bags)
     */
    fun addColumn() {
        val currentList = _weightList.value ?: mutableListOf()
        repeat(BAGS_PER_COLUMN) {
            currentList.add(0.0)
        }
        _weightList.value = currentList
        calculateTotals()
    }
    
    /**
     * Add quick weight to next empty slot
     */
    fun addQuickWeight(weight: Double): Int {
        val currentList = _weightList.value?.toMutableList() ?: mutableListOf()
        
        // Find first empty slot (0.0)
        var emptyIndex = currentList.indexOfFirst { it == 0.0 }
        
        if (emptyIndex == -1) {
            repeat(BAGS_PER_COLUMN) { currentList.add(0.0) }
            emptyIndex = currentList.size - BAGS_PER_COLUMN
        }
        
        // Set the weight
        currentList[emptyIndex] = weight
        _weightList.value = currentList
        
        calculateTotals()
        return emptyIndex
    }
    
    /**
     * Get columns structure for ColumnAdapter
     * Converts flat list to list of columns (each column = 5 weights)
     */
    fun getColumns(): List<List<Double>> {
        val weights = _weightList.value ?: return listOf()
        val columns = mutableListOf<List<Double>>()
        
        for (i in weights.indices step BAGS_PER_COLUMN) {
            val columnWeights = weights.subList(i, minOf(i + BAGS_PER_COLUMN, weights.size)).toMutableList()
            while (columnWeights.size < BAGS_PER_COLUMN) {
                columnWeights.add(0.0)
            }
            columns.add(columnWeights)
        }

        if (columns.isEmpty()) {
            columns.add(List(BAGS_PER_COLUMN) { 0.0 })
        }
        
        return columns
    }
    
    /**
     * Update weight at specific position
     */
    fun updateWeight(columnIndex: Int, bagIndex: Int, weight: Double) {
        // Validate indices
        if (columnIndex < 0 || bagIndex < 0 || bagIndex >= BAGS_PER_COLUMN) {
            return
        }

        val flatIndex = columnIndex * BAGS_PER_COLUMN + bagIndex
        val currentList = _weightList.value?.toMutableList() ?: mutableListOf()
        
        // Ensure list is large enough
        while (currentList.size <= flatIndex) {
            currentList.add(0.0)
        }
        
        currentList[flatIndex] = weight
        _weightList.value = currentList
        calculateTotals()
    }
    
    /**
     * Calculate column totals, grand total, and total money
     * Called automatically when weights or unit price change
     */
    private fun calculateTotals() {
        val weights = _weightList.value ?: mutableListOf()
        val columns = mutableListOf<Double>()
        
        var index = 0
        while (index < weights.size) {
            var columnSum = 0.0
            repeat(BAGS_PER_COLUMN) {
                if (index < weights.size) {
                    columnSum += weights[index]
                    index++
                }
            }
            columns.add(columnSum)
        }
        
        _columnTotals.value = columns
        
        // Calculate grand total
        val total = weights.sum()
        _grandTotal.value = total
        
        // Calculate total money (convert to Long)
        val price = _unitPrice.value ?: 0L
        _totalMoney.value = (total * price).toLong()
    }
    
    private val saveMutex = Mutex()

    /**
     * Check if the session has any real entered data
     */
    fun hasAnyData(): Boolean {
        val name = _customerName.value?.trim() ?: ""
        val price = _unitPrice.value ?: 0L
        val weights = _weightList.value ?: emptyList()
        val hasWeights = weights.any { it > 0.0 }
        return name.isNotEmpty() || price > 0L || hasWeights
    }

    /**
     * Internal save helper. Performs insert or update in Room SQLite.
     * Sets updatedAt to current time.
     */
    private suspend fun saveInternal(nameOverride: String? = null): RiceRecord {
        val rawName = nameOverride ?: _customerName.value?.trim() ?: ""
        val finalName = if (rawName.isBlank()) "Khách hàng" else rawName
        val price = _unitPrice.value ?: 0L
        val weights = (_weightList.value ?: mutableListOf()).toList()
        val total = _grandTotal.value ?: 0.0
        val money = _totalMoney.value ?: 0L
        val now = System.currentTimeMillis()

        val record = RiceRecord(
            id = currentRecordId ?: 0L,
            customerName = finalName,
            unitPrice = price,
            weightList = weights,
            grandTotal = total,
            totalMoney = money,
            createdAt = originalRecord?.createdAt?.takeIf { it > 0L } ?: now,
            updatedAt = now
        )

        if (currentRecordId != null && currentRecordId != 0L) {
            dao.update(record)
            originalRecord = record.copy()
        } else {
            val newId = dao.insert(record)
            currentRecordId = newId
            originalRecord = record.copy(id = newId)
        }

        return originalRecord!!
    }

    /**
     * Auto-save current session silently without prompting.
     * Returns true if saved, false if skipped.
     */
    suspend fun autoSave(): Boolean {
        return saveMutex.withLock {
            if (!hasUnsavedChanges()) return@withLock false
            if (currentRecordId == null && !hasAnyData()) return@withLock false

            try {
                saveInternal()
                true
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Auto save failed: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Background trigger for auto-save (e.g. onPause / onStop)
     */
    fun autoSaveInBackground() {
        if (!hasUnsavedChanges()) return
        if (currentRecordId == null && !hasAnyData()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                autoSave()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Background auto save failed: ${e.message}", e)
            }
        }
    }

    /**
     * Manual save current session to SQLite
     */
    fun saveSession() {
        viewModelScope.launch {
            saveMutex.withLock {
                try {
                    val name = _customerName.value?.trim() ?: ""
                    val weights = _weightList.value ?: emptyList()
                    val hasWeights = weights.any { it > 0.0 }
                    val price = _unitPrice.value ?: 0L

                    if (name.isBlank() && !hasWeights && price <= 0L) {
                        _saveStatus.value = "Chưa có dữ liệu để lưu"
                        return@withLock
                    }

                    saveInternal()
                    _saveStatus.value = "Đã lưu thành công!"
                } catch (e: Exception) {
                    _saveStatus.value = "Lỗi khi lưu: ${e.message}"
                }
            }
        }
    }

    /**
     * Check if current state differs from original/saved state
     */
    fun hasUnsavedChanges(): Boolean {
        val currentName = _customerName.value?.trim() ?: ""
        val currentPrice = _unitPrice.value ?: 0L
        val currentWeights = _weightList.value ?: emptyList()

        val original = originalRecord ?: return (currentName.isNotEmpty() || currentPrice > 0L || currentWeights.any { it > 0.0 })

        val originalName = original.customerName.trim()
        val nameChanged = if (currentName.isEmpty() && (originalName.isEmpty() || originalName == "Khách hàng")) {
            false
        } else {
            currentName != originalName
        }
        if (nameChanged) return true
        if (currentPrice != original.unitPrice) return true

        val originalWeights = original.weightList
        if (currentWeights.size != originalWeights.size) return true

        for (i in currentWeights.indices) {
            if (currentWeights[i] != originalWeights[i]) return true
        }

        return false
    }    
    /**
     * Generate shareable text for Zalo/social media
     */
    fun generateShareText(): String {
        val name = _customerName.value ?: "Không rõ"
        val price = _unitPrice.value ?: 0L
        val weights = _weightList.value ?: mutableListOf()
        val columns = _columnTotals.value ?: emptyList()
        val total = _grandTotal.value ?: 0.0
        val money = _totalMoney.value ?: 0L
        
        val sb = StringBuilder()
        sb.append("PHIẾU CÂN\n")
        sb.append("━━━━━━━━━━━━━━━━━━\n\n")
        sb.append("Khách hàng: $name\n")
        sb.append("Đơn giá: ${String.format("%,d", price)} VNĐ/kg\n\n")
        sb.append("CHI TIẾT CÂN:\n")
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        
        var index = 0
        var columnNumber = 1
        while (index < weights.size) {
            sb.append("\nCột $columnNumber:\n")
            val columnWeights = mutableListOf<Double>()
            repeat(BAGS_PER_COLUMN) {
                if (index < weights.size) {
                    val weight = weights[index]
                    if (weight > 0) {
                        sb.append("  • ${String.format("%.2f", weight)} kg\n")
                    }
                    columnWeights.add(weight)
                    index++
                }
            }
            val columnTotal = columnWeights.sum()
            if (columnTotal > 0) {
                sb.append("  Tổng cột: ${String.format("%.2f", columnTotal)} kg\n")
            }
            columnNumber++
        }
        
        sb.append("\n━━━━━━━━━━━━━━━━━━\n")
        sb.append("📦 TỔNG KHỐI LƯỢNG: ${String.format("%.2f", total)} kg\n")
        sb.append("💵 THÀNH TIỀN: ${String.format("%,d", money)} VNĐ\n")
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        
        return sb.toString()
    }
    
    /**
     * Toggle lock mode (chốt sổ / mở khóa)
     */
    fun toggleLock() {
        _isLocked.value = !(_isLocked.value ?: false)
    }
    
    /**
     * Lock the data (chốt sổ) to prevent accidental edits
     */
    fun lockData() {
        _isLocked.value = true
    }
    
    /**
     * Unlock the data (mở khóa) to allow editing
     */
    fun unlockData() {
        _isLocked.value = false
    }
    
    /**
     * Clear current session (reset all fields)
     */
    fun clearSession() {
        _customerName.value = ""
        _unitPrice.value = 0L
        _weightList.value = MutableList(INITIAL_CELL_COUNT) { 0.0 }
        calculateTotals()
    }
}
