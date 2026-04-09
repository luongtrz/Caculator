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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

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
        
        // Hide action bar/title bar
        supportActionBar?.hide()
        
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupDateTime()
        setupRecyclerView()
        setupFAB()
        setupSearch()
        setupSettings()
        observeSyncStatus()
        observeRecords()
    }
    
    override fun onResume() {
        super.onResume()
        // Force check status when returning (e.g. from Add Session)
        viewModel.checkSyncStatus()
    }
    
    /**
     * Setup Settings icon click
     */
    private fun setupSettings() {
        binding.btnSettings.setOnClickListener {
            showSettingsBottomSheet()
        }
    }

    private val timeFormat = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.US)
    private val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
    private val timeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val dayNames = arrayOf("", "Chủ nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7")

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            val calendar = java.util.Calendar.getInstance()
            val time = timeFormat.format(calendar.time)
            val dayOfWeek = dayNames[calendar.get(java.util.Calendar.DAY_OF_WEEK)]
            val date = dateFormat.format(calendar.time)

            binding.tvDateTime.text = "$time, $dayOfWeek, $date"

            timeHandler.postDelayed(this, 1000)
        }
    }

    private fun setupDateTime() {
        timeHandler.post(updateTimeRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        timeHandler.removeCallbacks(updateTimeRunnable)
    }

    
    /**
     * Show sign out confirmation dialog
     */
    private fun showSignOutConfirmation() {
        // First, check for pending Firestore writes
        lifecycleScope.launch {
            try {
                val firestore = Firebase.firestore
                
                // Try to wait for pending writes with timeout
                val hasPendingWrites = withTimeoutOrNull(5000) {
                    try {
                        firestore.waitForPendingWrites().await()
                        false // No pending writes
                    } catch (e: Exception) {
                        true // Has pending writes or error
                    }
                } ?: true // Timeout = assume has pending writes
                
                if (hasPendingWrites) {
                    showPendingWritesWarning()
                } else {
                    showNormalSignOutDialog()
                }
            } catch (e: Exception) {
                // If check fails, show warning to be safe
                showPendingWritesWarning()
            }
        }
    }
    
    /**
     * Show warning when there are pending writes
     */
    private fun showPendingWritesWarning() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Cảnh báo")
            .setMessage(
                "Có dữ liệu chưa được đồng bộ lên cloud!\n\n" +
                "Nếu đăng xuất ngay, bạn có thể MẤT DỮ LIỆU vừa nhập.\n\n" +
                "Khuyến nghị:\n" +
                "• Kết nối WiFi/4G trước\n" +
                "• Đợi ít nhất 5-10 giây cho app sync\n" +
                "• Hoặc hủy và tiếp tục làm việc"
            )
            .setPositiveButton("Vẫn đăng xuất (RỦI RO)") { _, _ ->
                performSignOut()
            }
            .setNegativeButton("Hủy - Giữ an toàn", null)
            .setCancelable(true)
            .show()
    }
    
    /**
     * Show normal sign out dialog (when all data is synced)
     */
    private fun showNormalSignOutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc muốn đăng xuất?\n\nDữ liệu đã được lưu an toàn trên cloud.")
            .setPositiveButton("Đăng xuất") { _, _ ->
                performSignOut()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    /**
     * Perform sign out and navigate to LoginActivity
     */
    private fun performSignOut() {
        lifecycleScope.launch {
            try {
                val authManager = com.example.caculateapp.auth.AuthManager(this@HistoryActivity)
                authManager.signOut()
                
                // Navigate to LoginActivity
                val intent = Intent(this@HistoryActivity, com.example.caculateapp.auth.LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("Lỗi")
                    .setMessage("Không thể đăng xuất: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
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
        
        binding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
            setHasFixedSize(true)
        }
    }
    
    /**
     * Setup FAB to create new session
     */
    private fun setupFAB() {
        binding.fabAddSession.setOnClickListener {
            // Prevent double click (debounce 300ms)
            if (System.currentTimeMillis() - lastClickTime < 300) return@setOnClickListener
            lastClickTime = System.currentTimeMillis()
            
            // Open MainActivity for new session
            val intent = Intent(this, MainActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_NEW_SESSION)
        }
    }
    
    // Add debounce variable
    private var lastClickTime: Long = 0
    
    private var searchJob: kotlinx.coroutines.Job? = null
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Setup search functionality with debounce
     */
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchHandler.removeCallbacksAndMessages(null)
                searchHandler.postDelayed({
                    val query = s?.toString() ?: ""
                    searchRecords(query)
                }, 300)
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
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            viewModel.searchRecords(query).collectLatest { records ->
                adapter.submitList(records)
            }
        }
    }
    
    /**
     * Open MainActivity to edit existing session
     */
    private fun openEditSession(recordId: String?) {
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
                viewModel.deleteRecord(record) { success ->
                    if (!success) {
                        android.widget.Toast.makeText(this, "Lỗi khi xóa", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
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
                    .format(record.createdAt ?: java.util.Date())
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_unit_price).text = 
                String.format("%,d VNĐ/kg", record.unitPrice)
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
                String.format("%,d VNĐ", record.totalMoney)
            
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
        
        // Weight values (always show 5 cells)
        weights.forEach { weight ->
            val weightText = android.widget.TextView(this).apply {
                text = if (weight > 0.0) {
                    if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString()
                } else {
                    "" // Empty string for zero values
                }
                setTextColor(android.graphics.Color.BLACK)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 4, 0, 4)
                
                // Ensure it has height even if empty
                minHeight = (24 * resources.displayMetrics.density).toInt()
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
     * Start monitoring Firestore sync status
     */
    private fun observeSyncStatus() {
        lifecycleScope.launch {
            viewModel.isCloudSynced.collectLatest { isSynced ->
                updateSyncBadge(isSynced)
            }
        }
    }
    
    /**
     * Update sync status badge
     */
    /**
     * Update sync status badge
     */
    private fun updateSyncBadge(isSynced: Boolean) {
        binding.syncBadge.setBackgroundColor(
            if (isSynced) {
                androidx.core.content.ContextCompat.getColor(this, R.color.color_weight_positive) // Green
            } else {
                androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_secondary) // Orange
            }
        )
    }
    
    /**
     * Show settings bottom sheet
     */
    private fun showSettingsBottomSheet() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        bottomSheet.setContentView(sheetView)
        
        // Get user info
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        sheetView.findViewById<android.widget.TextView>(R.id.tv_user_name).text = 
            currentUser?.displayName ?: "User"
        sheetView.findViewById<android.widget.TextView>(R.id.tv_user_email).text = 
            currentUser?.email ?: "No email"
        
        // Check sync status using ViewModel state (Realtime)
        val syncStatusBadge = sheetView.findViewById<android.view.View>(R.id.sync_status_badge)
        val syncStatusText = sheetView.findViewById<android.widget.TextView>(R.id.tv_sync_status)
        val syncDetailText = sheetView.findViewById<android.widget.TextView>(R.id.tv_sync_detail)
        
        // Launch a job to collect sync status while the bottom sheet is open
        // We use sheetView attached to window or dialog lifecycle?
        // Using activity lifecycleScope is fine, but we should probably cancel it when dialog closes.
        // Easier: Just launch in lifecycleScope and let it update the detached view (no harm)
        // Or better: Observe until dialog is dismissed.
        
        val job = lifecycleScope.launch {
            viewModel.isCloudSynced.collectLatest { isSynced ->
                if (isSynced) {
                    // All synced
                    syncStatusBadge.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.color_weight_positive)) // Green
                    syncStatusText.text = "Đã đồng bộ"
                    syncDetailText.text = "Tất cả dữ liệu đã lưu trên cloud"
                } else {
                    // Has pending
                    syncStatusBadge.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_secondary)) // Orange
                    syncStatusText.text = "Đang đồng bộ..."
                    syncDetailText.text = "Có dữ liệu chưa sync lên cloud"
                }
            }
        }
        
        bottomSheet.setOnDismissListener {
            job.cancel()
        }
        
        // Setup sign out button
        sheetView.findViewById<android.widget.Button>(R.id.btn_sign_out_settings).setOnClickListener {
            bottomSheet.dismiss()
            showSignOutConfirmation()
        }
        
        bottomSheet.show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Refresh list when returning from MainActivity
        if (resultCode == RESULT_OK) {
            // RecyclerView will auto-update via Flow
        }
    }
}

