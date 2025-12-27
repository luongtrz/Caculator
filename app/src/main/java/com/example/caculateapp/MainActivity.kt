package com.example.caculateapp

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.caculateapp.adapter.ColumnAdapter
import com.example.caculateapp.databinding.ActivityMainBinding
import com.example.caculateapp.databinding.DialogExportPreviewBinding
import com.example.caculateapp.databinding.BottomSheetExportFormatBinding
import com.example.caculateapp.databinding.LayoutExportTemplateBinding
import com.example.caculateapp.utils.ExportManager
import com.example.caculateapp.viewmodel.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main Activity for Rice Weighing Manager
 * Connects UI with ViewModel and handles user interactions
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide action bar/title bar
        supportActionBar?.hide()
        
        // Setup ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Check if editing existing record
        val recordId = intent.getStringExtra("EXTRA_RECORD_ID")
        if (recordId != null) {
            // Load existing record for editing
            viewModel.loadExistingRecord(recordId)
        }
        
        setupDateTime()
        setupRecyclerView()
        setupQuickInput()
        setupInputListeners()
        setupObservers()
        setupButtons()
        setupBackPressHandler()
        
        // Auto-focus on quick input and show keyboard (only for new sessions)
        if (recordId == null) {
            binding.etQuickInput.postDelayed({
                binding.etQuickInput.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.etQuickInput, InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
    }
    
    /**
     * Setup date and time display (auto-update every second)
     */
    private fun setupDateTime() {
        val handler = android.os.Handler(mainLooper)
        val updateTime = object : Runnable {
            override fun run() {
                val calendar = java.util.Calendar.getInstance()
                
                // Format date: "Thứ 5, ngày 26 tháng 12 năm 2024"
                val dayOfWeek = when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
                    java.util.Calendar.SUNDAY -> "Chủ nhật"
                    java.util.Calendar.MONDAY -> "Thứ 2"
                    java.util.Calendar.TUESDAY -> "Thứ 3"
                    java.util.Calendar.WEDNESDAY -> "Thứ 4"
                    java.util.Calendar.THURSDAY -> "Thứ 5"
                    java.util.Calendar.FRIDAY -> "Thứ 6"
                    java.util.Calendar.SATURDAY -> "Thứ 7"
                    else -> ""
                }
                
                val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                val month = calendar.get(java.util.Calendar.MONTH) + 1
                val year = calendar.get(java.util.Calendar.YEAR)
                
                binding.tvDate.text = "$dayOfWeek, ngày $day tháng $month năm $year"
                
                // Lunar calendar (placeholder - can integrate library later)
                val lunarDay = day - 1  // Simple approximation
                val lunarMonth = if (month > 1) month - 1 else 12
                val lunarYear = when(year) {
                    2024 -> "Giáp Thìn"
                    2025 -> "Ất Tỵ"
                    else -> "Ất Tỵ"
                }
                binding.tvLunarDate.text = "(Âm lịch: ngày $lunarDay tháng $lunarMonth năm $lunarYear)"
                
                // Format time: "11:13:26 PM"
                val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.US)
                binding.tvTime.text = timeFormat.format(calendar.time)
                
                // Update every second
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateTime)
    }
    
    /**
     * Setup RecyclerView with LinearLayoutManager (Horizontal)
     * Column-based layout like traditional paper sheet
     */
    private fun setupRecyclerView() {        // Initialize ColumnAdapter with callback
        val columnAdapter = ColumnAdapter { columnIndex, bagIndex, weight ->
            viewModel.updateWeight(columnIndex, bagIndex, weight)
        }
        
        // Setup LinearLayoutManager (HORIZONTAL for columns)
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this,
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false
        )
        
        binding.recyclerWeights.apply {
            adapter = columnAdapter
            this.layoutManager = layoutManager
        }
        
        // Load initial columns immediately
        val initialColumns = viewModel.getColumns()
        columnAdapter.updateColumns(initialColumns)
        
        // Observe weight changes and update adapter
        viewModel.weightList.observe(this) {
            val columns = viewModel.getColumns()
            columnAdapter.updateColumns(columns)
        }
    }
    
    /**
     * Setup Quick Input for continuous data entry
     */
    private fun setupQuickInput() {
        // Handle Enter key press
        binding.etQuickInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                processQuickInput()
                true
            } else {
                false
            }
        }
        
        // Handle Confirm button click
        binding.btnConfirm.setOnClickListener {
            processQuickInput()
        }
    }
    
    /**
     * Process quick input and add to grid
     */
    private fun processQuickInput() {
        val inputText = binding.etQuickInput.text.toString()
        val weight = inputText.toDoubleOrNull()
        
        if (weight == null || weight <= 0) {
            Toast.makeText(this, "Vui lòng nhập số hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Add weight (returns flat index)
        val flatIndex = viewModel.addQuickWeight(weight)
        
        if (flatIndex >= 0) {
            // Calculate column index from flat index
            val columnIndex = flatIndex / 5
            
            // Clear input
            binding.etQuickInput.text?.clear()
            
            // Scroll to column containing the new weight
            binding.recyclerWeights.postDelayed({
                binding.recyclerWeights.smoothScrollToPosition(columnIndex)
            }, 100)
            
            // Keep focus on quick input
            binding.etQuickInput.requestFocus()
            
        } else {
            Toast.makeText(this, "Lỗi khi thêm dữ liệu", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Setup listeners for customer name and unit price inputs
     */
    private fun setupInputListeners() {
        // Two-way binding: ViewModel -> EditText (when loading existing record)
        viewModel.customerName.observe(this) { name ->
            if (binding.editCustomerName.text.toString() != name) {
                binding.editCustomerName.setText(name)
            }
        }
        
        viewModel.unitPrice.observe(this) { price ->
            val expectedText = if (price > 0L) price.toString() else ""
            if (binding.editUnitPrice.text.toString() != expectedText) {
                binding.editUnitPrice.setText(expectedText)
            }
        }
        
        // Two-way binding: EditText -> ViewModel (when user types)
        binding.editCustomerName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val name = s?.toString() ?: ""
                if (viewModel.customerName.value != name) {
                    viewModel.setCustomerName(name)
                }
            }
        })
        
        binding.editUnitPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val priceText = s?.toString() ?: ""
                val price = priceText.toLongOrNull() ?: 0L
                if (viewModel.unitPrice.value != price) {
                    viewModel.setUnitPrice(price)
                }
            }
        })
    }
    
    /**
     * Setup observers for ViewModel LiveData
     */
    private fun setupObservers() {
        // Observe grand total
        viewModel.grandTotal.observe(this) { total ->
            binding.tvGrandTotal.text = String.format("%.1f kg", total)
        }
        
        // Observe total money
        viewModel.totalMoney.observe(this) { money ->
            binding.tvTotalMoney.text = String.format("%,d VNĐ", money)
        }
        
        // Observe save status - handle validation and success
        viewModel.saveStatus.observe(this) { status ->
            status?.let {
                when {
                    it.startsWith("Đã lưu thành công") -> {
                        // Success case - show toast and return to history
                        Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                    it.isNotEmpty() -> {
                        // Error/validation case - show AlertDialog to ensure user sees it
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Thông báo")
                            .setMessage(it)
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }
                }
            }
        }
    }
    
    /**
     * Setup button click listeners
     */
    private fun setupButtons() {
        // Save button - validate then save and return to history
        binding.btnSave.setOnClickListener {
            // First validate by attempting to save
            viewModel.saveSession()
            // Don't finish here - wait for save status observer
        }
        
        // Share button - Show multi-page preview first
        binding.btnShare.setOnClickListener {
            showExportPreviewDialog()
        }
    }
    
    /**
     * Share text via Intent (Zalo or any messaging app)
     */
    private fun shareToZalo(text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        
        val shareIntent = Intent.createChooser(sendIntent, "Chia sẻ phiếu cân qua")
        startActivity(shareIntent)
    }
    
    /**
     * Show Export Preview Dialog with multi-page scroll
     */
    private fun showExportPreviewDialog() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogBinding = DialogExportPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        val exportManager = ExportManager(this)
        
        // Get data from ViewModel
        val customerName = viewModel.customerName.value?.ifBlank { "Khách hàng" } ?: "Khách hàng"
        val unitPrice = viewModel.unitPrice.value ?: 0L
        val grandTotal = viewModel.grandTotal.value ?: 0.0
        val totalMoney = viewModel.totalMoney.value ?: 0L
        val columns = viewModel.getColumns()
        
        // Get pages container
        val pagesContainer = dialogBinding.root.findViewById<android.widget.LinearLayout>(
            resources.getIdentifier("layout_pages_container", "id", packageName)
        )
        
        // Generate all pages and add to container
        val columnChunks = columns.chunked(10)
        columnChunks.forEachIndexed { pageIndex, pageColumns ->
            val inflater = android.view.LayoutInflater.from(this)
            val pageView = inflater.inflate(R.layout.layout_export_page, null)
            
            // Populate header
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_customer_name).text = customerName
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_date).text = 
                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_unit_price).text = 
                String.format("%,d VNĐ/kg", unitPrice)
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_title).text = 
                "PHIẾU CÂN - Trang ${pageIndex + 1}/${columnChunks.size}"
            
            // Get row containers
            val topRow = pageView.findViewById<android.widget.LinearLayout>(R.id.layout_top_row)
            val bottomRow = pageView.findViewById<android.widget.LinearLayout>(R.id.layout_bottom_row)
            
            // Add columns (first 5 to top, next 5 to bottom)
            var pageTotal = 0.0
            pageColumns.forEachIndexed { colIndex, colWeights ->
                val globalColumnNumber = pageIndex * 10 + colIndex + 1
                val columnView = createPreviewColumnView(globalColumnNumber, colWeights)
                pageTotal += colWeights.sum()
                
                if (colIndex < 5) {
                    topRow.addView(columnView)
                } else {
                    bottomRow.addView(columnView)
                }
            }
            
            // Populate totals
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_total).text = 
                String.format("%.1f kg", pageTotal)
            pageView.findViewById<android.widget.TextView>(R.id.tv_grand_total).text = 
                String.format("%.1f kg", grandTotal)
            pageView.findViewById<android.widget.TextView>(R.id.tv_total_money).text = 
                String.format("%,d VNĐ", totalMoney)
            
            // Add page to container with some margin
            val layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = 16
            pageView.layoutParams = layoutParams
            
            pagesContainer.addView(pageView)
        }
        
        // Close button
        dialogBinding.btnClosePreview.setOnClickListener {
            dialog.dismiss()
        }
        
        // Confirm export button
        dialogBinding.btnConfirmExport.setOnClickListener {
            dialog.dismiss()
            showFormatSelectionBottomSheet()
        }
        
        dialog.show()
    }
    
    /**
     * Create a simple column view for preview
     */
    private fun createPreviewColumnView(columnNumber: Int, weights: List<Double>): android.view.View {
        val columnLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(8, 0, 8, 0)
        }
        
        // Column header
        val header = android.widget.TextView(this).apply {
            text = "Cột $columnNumber"
            setTextColor(android.graphics.Color.BLACK)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        columnLayout.addView(header)
        
        // Weight values (only non-zero)
        weights.filter { it > 0.0 }.forEach { weight ->
            val weightText = android.widget.TextView(this).apply {
                text = if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString()
                setTextColor(android.graphics.Color.BLACK)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 4, 0, 4)
            }
            columnLayout.addView(weightText)
        }
        
        // Divider
        val divider = android.view.View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        columnLayout.addView(divider)
        
        // Column total
        val total = weights.sum()
        val totalText = android.widget.TextView(this).apply {
            text = String.format("%.1f kg", total)
            setTextColor(android.graphics.Color.BLACK)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        columnLayout.addView(totalText)
        
        return columnLayout
    }
    
    /**
     * Populate Export Template with current data
     */
    private fun populateExportTemplate(templateBinding: LayoutExportTemplateBinding) {
        // Customer name
        val customerName = viewModel.customerName.value ?: "Khách hàng"
        templateBinding.tvExportCustomerName.text = customerName
        
        // Date
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        templateBinding.tvExportDate.text = "Ngày: $currentDate"
        
        // Unit price
        val unitPrice = viewModel.unitPrice.value ?: 0L
        val unitPriceStr = String.format("%,.0f", unitPrice)
        templateBinding.tvExportUnitPrice.text = "Đơn giá: $unitPriceStr VNĐ/kg"
        
        // Grand total
        val grandTotal = viewModel.grandTotal.value ?: 0.0
        templateBinding.tvExportGrandTotal.text = String.format("%.2f kg", grandTotal)
        
        // Total money
        val totalMoney = viewModel.totalMoney.value ?: 0.0
        val totalMoneyStr = String.format("%,.0f", totalMoney)
        templateBinding.tvExportTotalMoney.text = "$totalMoneyStr VNĐ"
        
        // Populate weight grid
        populateWeightGrid(templateBinding.layoutExportGridContainer)
    }
    
    /**
     * Populate weight grid dynamically based on data
     */
    private fun populateWeightGrid(container: LinearLayout) {
        container.removeAllViews()
        
        val weights = viewModel.weightList.value ?: return
        val columnTotals = viewModel.columnTotals.value ?: listOf()
        
        if (weights.isEmpty()) return
        
        // Calculate number of columns (5 bags per column)
        val numColumns = (weights.size + 4) / 5
        
        // Limit columns per page (max 8 columns to fit nicely)
        val columnsToShow = minOf(numColumns, 8)
        
        // Create grid with columns
        for (colIndex in 0 until columnsToShow) {
            // Create column layout with fixed width
            val columnLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(android.R.dimen.app_icon_size), // ~72dp
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(2, 0, 2, 0)
                }
            }
            
            // Header
            val headerText = TextView(this).apply {
                text = "C${colIndex + 1}"
                textSize = 10f
                setTextColor(resources.getColor(android.R.color.black, null))
                setPadding(4, 4, 4, 4)
                setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
                gravity = android.view.Gravity.CENTER
            }
            columnLayout.addView(headerText)
            
            // 5 weight cells
            for (row in 0 until 5) {
                val index = colIndex * 5 + row
                val weight = if (index < weights.size) weights[index] else 0.0
                
                val cellText = TextView(this).apply {
                    text = if (weight > 0) String.format("%.1f", weight) else ""
                    textSize = 11f
                    setTextColor(resources.getColor(android.R.color.black, null))
                    setPadding(4, 8, 4, 8)
                    setBackgroundResource(android.R.drawable.edit_text)
                    gravity = android.view.Gravity.CENTER
                    minWidth = 60
                }
                columnLayout.addView(cellText)
            }
            
            // Column total
            val total = if (colIndex < columnTotals.size) columnTotals[colIndex] else 0.0
            val totalText = TextView(this).apply {
                text = String.format("%.1f", total)
                textSize = 10f
                setTextColor(resources.getColor(android.R.color.black, null))
                setPadding(4, 4, 4, 4)
                setBackgroundColor(resources.getColor(android.R.color.holo_blue_light, null))
                gravity = android.view.Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            columnLayout.addView(totalText)
            
            container.addView(columnLayout)
        }
        
        // If more than 8 columns, show warning
        if (numColumns > columnsToShow) {
            val warningText = TextView(this).apply {
                text = "... +${numColumns - columnsToShow} cột"
                textSize = 10f
                setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                setPadding(8, 20, 8, 8)
                gravity = android.view.Gravity.CENTER
            }
            container.addView(warningText)
        }
    }
    
    /**
     * Show Format Selection Bottom Sheet
     */
    private fun showFormatSelectionBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetExportFormatBinding.inflate(layoutInflater)
        bottomSheet.setContentView(bottomSheetBinding.root)
        
        val exportManager = ExportManager(this)
        
        // Get data from ViewModel
        val customerName = viewModel.customerName.value?.ifBlank { "Khách hàng" } ?: "Khách hàng"
        val unitPrice = viewModel.unitPrice.value ?: 0L
        val grandTotal = viewModel.grandTotal.value ?: 0.0
        val totalMoney = viewModel.totalMoney.value ?: 0L
        val columns = viewModel.getColumns()
        
        // Export as Images (multi-page)
        bottomSheetBinding.btnExportImage.setOnClickListener {
            bottomSheet.dismiss()
            
            try {
                val uris = exportManager.exportToMultipleImages(
                    columns,
                    customerName,
                    unitPrice,
                    grandTotal,
                    totalMoney
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        // Export as PDF (multi-page)
        bottomSheetBinding.btnExportPdf.setOnClickListener {
            bottomSheet.dismiss()
            
            try {
                exportManager.exportToMultiPagePDF(
                    columns,
                    customerName,
                    unitPrice,
                    grandTotal,
                    totalMoney
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        bottomSheet.show()
    }
    
    /**
     * Setup back button handler with confirmation dialog
     * Uses modern OnBackPressedDispatcher instead of deprecated onBackPressed
     */
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Check if there's unsaved data
                val hasUnsavedData = hasUnsavedData()
                
                if (!hasUnsavedData) {
                    // No unsaved data, exit normally
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    return
                }
                
                // Show custom confirmation dialog
                val dialogView = layoutInflater.inflate(R.layout.dialog_unsaved_warning, null)
                val dialog = Dialog(this@MainActivity)
                dialog.setContentView(dialogView)
                dialog.window?.setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame)
                dialog.setCancelable(true)
                
                // Setup buttons
                val btnExitAnyway = dialogView.findViewById<android.widget.Button>(R.id.btn_exit_anyway)
                val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btn_cancel)
                
                btnExitAnyway.setOnClickListener {
                    // User accepts the risk, exit
                    isEnabled = false
                    dialog.dismiss()
                    onBackPressedDispatcher.onBackPressed()
                }
                
                btnCancel.setOnClickListener {
                    // User cancels, stay on the screen
                    dialog.dismiss()
                }
                
                dialog.show()
            }
        })
    }
    
    /**
     * Check if there's any unsaved data
     */
    private fun hasUnsavedData(): Boolean {
        return viewModel.hasUnsavedChanges()
    }
}
