package com.example.caculateapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.caculateapp.adapter.HistoryAdapter
import com.example.caculateapp.databinding.ActivityHistoryBinding
import com.example.caculateapp.utils.ExportManager
import com.example.caculateapp.viewmodel.HistoryViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * History Activity - Main launcher screen
 * Displays list of all saved rice weighing sessions
 */
class HistoryActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter
    
    companion object {
        const val REQUEST_CODE_NEW_SESSION = 100
        const val REQUEST_CODE_EDIT_SESSION = 101
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        setupFAB()
        setupSearch()
        observeRecords()
    }
    
    /**
     * Setup RecyclerView with adapter
     */
    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { record ->
                // Click on card -> open MainActivity to view/edit this record
                openEditSession(record.id)
            },
            onDelete = { record ->
                showDeleteConfirmation(record)
            },
            onExport = { record ->
                exportRecord(record)
            }
        )
        
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter
    }
    
    /**
     * Setup FAB to create new session
     */
    private fun setupFAB() {
        binding.fabAddSession.setOnClickListener {
            // Open MainActivity for new session
            val intent = Intent(this, MainActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_NEW_SESSION)
        }
    }
    
    /**
     * Setup search functionality
     */
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                searchRecords(query)
            }
        })
    }
    
    /**
     * Observe records from ViewModel
     */
    private fun observeRecords() {
        lifecycleScope.launch {
            viewModel.allRecords.collectLatest { records ->
                adapter.submitList(records)
            }
        }
    }
    
    /**
     * Search records by query
     */
    private fun searchRecords(query: String) {
        lifecycleScope.launch {
            viewModel.searchRecords(query).collectLatest { records ->
                adapter.submitList(records)
            }
        }
    }
    
    /**
     * Open MainActivity to edit existing session
     */
    private fun openEditSession(recordId: Long) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("EXTRA_RECORD_ID", recordId)
        startActivityForResult(intent, REQUEST_CODE_EDIT_SESSION)
    }
    
    /**
     * Show delete confirmation dialog
     */
    private fun showDeleteConfirmation(record: com.example.caculateapp.data.RiceRecord) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc muốn xóa đợt cân của ${record.customerName}?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteRecord(record)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    /**
     * Export a specific record with preview
     */
    private fun exportRecord(record: com.example.caculateapp.data.RiceRecord) {
        // Show preview dialog first, then format selection
        showExportPreviewForRecord(record)
    }
    
    /**
     * Show export preview for a specific record
     */
    private fun showExportPreviewForRecord(record: com.example.caculateapp.data.RiceRecord) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogBinding = com.example.caculateapp.databinding.DialogExportPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        val exportManager = ExportManager(this)
        
        // Convert flat weightList to columns
        val columns = mutableListOf<List<Double>>()
        val weights = record.weightList
        for (i in weights.indices step 5) {
            val columnWeights = weights.subList(i, minOf(i + 5, weights.size)).toMutableList()
            while (columnWeights.size < 5) {
                columnWeights.add(0.0)
            }
            columns.add(columnWeights)
        }
        
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
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_customer_name).text = record.customerName
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_date).text = 
                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(record.createdAt))
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_unit_price).text = 
                String.format("%,.0f VNĐ/kg", record.unitPrice)
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
                String.format("%.1f kg", record.grandTotal)
            pageView.findViewById<android.widget.TextView>(R.id.tv_total_money).text = 
                String.format("%,.0f VNĐ", record.totalMoney)
            
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
        
        // Confirm export button -> show format selection
        dialogBinding.btnConfirmExport.setOnClickListener {
            dialog.dismiss()
            showFormatSelectionForRecord(record, columns)
        }
        
        dialog.show()
    }
    
    /**
     * Show format selection for exporting a record
     */
    private fun showFormatSelectionForRecord(
        record: com.example.caculateapp.data.RiceRecord,
        columns: List<List<Double>>
    ) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val bottomSheetBinding = com.example.caculateapp.databinding.BottomSheetExportFormatBinding.inflate(layoutInflater)
        bottomSheet.setContentView(bottomSheetBinding.root)
        
        val exportManager = ExportManager(this)
        
        // Export as Images (multi-page)
        bottomSheetBinding.btnExportImage.setOnClickListener {
            bottomSheet.dismiss()
            
            try {
                exportManager.exportToMultipleImages(
                    columns,
                    record.customerName,
                    record.unitPrice,
                    record.grandTotal,
                    record.totalMoney
                )
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(this, "Lỗi: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
        // Export as PDF (multi-page)
        bottomSheetBinding.btnExportPdf.setOnClickListener {
            bottomSheet.dismiss()
            
            try {
                exportManager.exportToMultiPagePDF(
                    columns,
                    record.customerName,
                    record.unitPrice,
                    record.grandTotal,
                    record.totalMoney
                )
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(this, "Lỗi: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
        bottomSheet.show()
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
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Refresh list when returning from MainActivity
        if (resultCode == RESULT_OK) {
            // RecyclerView will auto-update via Flow
        }
    }
}
