package com.example.caculateapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.caculateapp.data.FirebaseService
import com.example.caculateapp.data.RiceRecord
import kotlinx.coroutines.launch

/**
 * ViewModel for MainActivity
 * Manages rice weighing session data with real-time calculations
 * Handles both new session creation and existing session editing
 * Now uses Firebase Firestore instead of Room Database
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val INITIAL_COLUMNS = 3
        private const val BAGS_PER_COLUMN = 5
        private const val INITIAL_CELL_COUNT = INITIAL_COLUMNS * BAGS_PER_COLUMN
    }
    
    private val firebaseService = FirebaseService()
    
    // Current record ID for edit mode (null = new session, non-null = editing)
    private var currentRecordId: String? = null
    
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
    fun loadExistingRecord(recordId: String) {
        viewModelScope.launch {
            try {
                val result = firebaseService.getRecord(recordId)
                val record = result.getOrNull()
                
                if (record != null) {
                    currentRecordId = record.id
                    _customerName.value = record.customerName
                    _unitPrice.value = record.unitPrice
                    _weightList.value = record.weightList.toMutableList()
                    calculateTotals()
                    
                    // Save original state for change tracking
                    originalRecord = record.copy()
                } else {
                    _saveStatus.value = "Lỗi khi tải dữ liệu: ${result.exceptionOrNull()?.message}"
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
     * Update weight at specific position
     */
    fun updateWeight(position: Int, weight: Double) {
        val currentList = _weightList.value ?: mutableListOf()
        if (position in currentList.indices) {
            currentList[position] = weight
            _weightList.value = currentList
            calculateTotals()
        }
    }
    
    /**
     * Add a new column (5 bags)
     */
    fun addColumn() {
        val currentList = _weightList.value ?: mutableListOf()
        // Add 5 new entries
        repeat(5) {
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
        
        // If no empty slot found, add new column (5 new slots)
        if (emptyIndex == -1) {
            repeat(5) { currentList.add(0.0) }
            emptyIndex = currentList.size - 5
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
        
        for (i in weights.indices step 5) {
            val columnWeights = weights.subList(i, minOf(i + 5, weights.size)).toMutableList()
            // Pad with zeros if less than 5
            while (columnWeights.size < 5) {
                columnWeights.add(0.0)
            }
            columns.add(columnWeights)
        }
        
        // If no columns, add one empty column
        if (columns.isEmpty()) {
            columns.add(listOf(0.0, 0.0, 0.0, 0.0, 0.0))
        }
        
        return columns
    }
    
    /**
     * Update weight at specific position
     */
    fun updateWeight(columnIndex: Int, bagIndex: Int, weight: Double) {
        // Validate indices
        if (columnIndex < 0 || bagIndex < 0 || bagIndex >= 5) {
            return
        }
        
        val flatIndex = columnIndex * 5 + bagIndex
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
        
        // Calculate column totals (each column has 5 bags)
        var index = 0
        while (index < weights.size) {
            var columnSum = 0.0
            repeat(5) {
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
    
    /**
     * Save current session to Firestore
     */
    fun saveSession() {
        viewModelScope.launch {
            try {
                val name = _customerName.value ?: ""
                if (name.isBlank()) {
                    _saveStatus.value = "Vui lòng nhập tên khách hàng"
                    return@launch
                }
                
                val price = _unitPrice.value ?: 0L
                if (price <= 0L) {
                    _saveStatus.value = "Vui lòng nhập đơn giá hợp lệ"
                    return@launch
                }
                
                val weights = _weightList.value ?: mutableListOf()
                val total = _grandTotal.value ?: 0.0
                val money = _totalMoney.value ?: 0L
                
                val record = RiceRecord(
                    id = currentRecordId,
                    customerName = name,
                    unitPrice = price,
                    weightList = weights.toList(),
                    grandTotal = total,
                    totalMoney = money
                )
                
                val isSuccess: Boolean
                val errorMessage: String?
                val savedId: String?
                
                if (currentRecordId != null) {
                    // Update existing record
                    val result = firebaseService.updateRecord(record)
                    isSuccess = result.isSuccess
                    errorMessage = result.exceptionOrNull()?.message
                    savedId = currentRecordId
                } else {
                    // Create new record
                    val result = firebaseService.saveRecord(record)
                    isSuccess = result.isSuccess
                    errorMessage = result.exceptionOrNull()?.message
                    savedId = result.getOrNull()
                }
                
                if (isSuccess) {
                    _saveStatus.value = "Đã lưu thành công!"
                    // Update original record tracking
                    if (savedId != null) {
                        originalRecord = record.copy(id = savedId)
                        currentRecordId = savedId
                    }
                } else {
                    _saveStatus.value = "Lỗi khi lưu: $errorMessage"
                }
            } catch (e: Exception) {
                _saveStatus.value = "Lỗi khi lưu: ${e.message}"
            }
        }
    }
    
    /**
     * Check if current state differs from original/saved state
     */
    fun hasUnsavedChanges(): Boolean {
        val currentName = _customerName.value ?: ""
        val currentPrice = _unitPrice.value ?: 0L
        val currentWeights = _weightList.value ?: emptyList()
        
        // If we have an original record, compare against it
        val original = originalRecord ?: return (currentName.isNotEmpty() || currentPrice > 0L || currentWeights.any { it > 0.0 })
        
        if (currentName != original.customerName) return true
        if (currentPrice != original.unitPrice) return true
        
        // Compare weights (values only)
        val originalWeights = original.weightList
        if (currentWeights.size != originalWeights.size) return true
        
        for (i in currentWeights.indices) {
            // Compare as Double roughly or exact
            // Since we load and save doubles, exact match should usually work if no math changed them
            // But let's be safe with strict equality for now as they are direct values
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
        
        // Group weights by columns (5 bags each)
        var index = 0
        var columnNumber = 1
        while (index < weights.size) {
            sb.append("\nCột $columnNumber:\n")
            val columnWeights = mutableListOf<Double>()
            repeat(5) {
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
        _weightList.value = MutableList(15) { 0.0 }
        calculateTotals()
    }
}
