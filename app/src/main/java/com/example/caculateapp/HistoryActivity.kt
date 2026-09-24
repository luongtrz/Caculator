package com.example.caculateapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.caculateapp.adapter.HistoryAdapter
import com.example.caculateapp.data.RiceRecord
import com.example.caculateapp.databinding.ActivityHistoryBinding
import com.example.caculateapp.databinding.DialogQrImportConfirmBinding
import com.example.caculateapp.databinding.DialogQrShareBinding
import com.example.caculateapp.utils.ExportManager
import com.example.caculateapp.utils.QrTransferManager
import com.example.caculateapp.viewmodel.HistoryViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * History Activity - Main launcher screen (100% Offline)
 * Displays list of all saved rice weighing sessions
 */
class HistoryActivity : AppCompatActivity() {

    companion object {
        private const val BAGS_PER_COLUMN = 5
    }

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter
    
    private val newSessionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* list auto-updates via Room Flow */ }

    private val editSessionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* list auto-updates via Room Flow */ }

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScannedQr(result.contents)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply top inset to toolbar so it clears the status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootHistory) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = sys.top)
            binding.rootHistory.updatePadding(bottom = sys.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(false)

        setupDateTime()
        setupRecyclerView()
        setupFAB()
        setupSearch()
        setupInfoButton()
        setupQrAndSelectionActions()
        setupBackPressHandler()
        observeRecords()

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val urlString = uri.toString()
        if (urlString.contains("canlua.app/import") || urlString.contains("canlua://import")) {
            handleScannedQr(urlString)
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::adapter.isInitialized && adapter.isSelectionMode) {
                    adapter.exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
    
    /**
     * Setup Info button click
     */
    private fun setupInfoButton() {
        binding.btnSettings.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cân Lúa (100% Offline)")
            .setMessage(
                "• Ứng dụng hoạt động hoàn toàn ngoại tuyến, không cần internet.\n" +
                "• Không yêu cầu đăng nhập tài khoản, không cần bất kỳ API key nào.\n" +
                "• Dữ liệu lưu trữ an toàn trực tiếp trên điện thoại.\n" +
                "• Chia sẻ và nhận đợt cân nhanh chóng giữa các điện thoại qua mã QR.\n" +
                "• Hỗ trợ xuất phiếu dạng ảnh và PDF chuyên nghiệp."
            )
            .setPositiveButton("Đã hiểu", null)
            .show()
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

            binding.toolbar.subtitle = "$time  $dayOfWeek, $date"

            timeHandler.postDelayed(this, 1000)
        }
    }

    private fun setupDateTime() {
        timeHandler.post(updateTimeRunnable)
    }

    override fun onResume() {
        super.onResume()
        binding.fabAddSession.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        timeHandler.removeCallbacks(updateTimeRunnable)
        searchHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Setup RecyclerView with adapter
     */
    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { record ->
                openEditSession(record.id)
            },
            onDelete = { record ->
                showDeleteConfirmation(record)
            },
            onExport = { record ->
                exportRecord(record)
            },
            onShareQr = { record ->
                shareRecordsAsQr(listOf(record))
            },
            onSelectionModeChanged = { inSelectionMode ->
                updateSelectionModeUI(inSelectionMode)
            },
            onSelectionCountChanged = { count ->
                updateSelectionCountUI(count)
            }
        )
        
        binding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
            setHasFixedSize(true)
        }
    }

    /**
     * Setup QR and Selection Toolbar & Bottom buttons
     */
    private fun setupQrAndSelectionActions() {
        // Toolbar: Scan QR
        binding.btnScanQr.setOnClickListener {
            startQrScan()
        }

        // Toolbar: Enter multi-selection mode
        binding.btnEnterSelect.setOnClickListener {
            adapter.enterSelectionMode()
        }

        // Toolbar: Cancel selection
        binding.btnCancelSelection.setOnClickListener {
            adapter.exitSelectionMode()
        }

        // Toolbar: Select all records
        binding.btnSelectAll.setOnClickListener {
            adapter.selectAll()
        }

        // Bottom: Share selected records via QR
        binding.btnShareSelectedQr.setOnClickListener {
            val selected = adapter.getSelectedRecords()
            if (selected.isEmpty()) {
                android.widget.Toast.makeText(this, "Vui lòng tick chọn ít nhất 1 đợt cân", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            shareRecordsAsQr(selected)
        }
    }

    private fun updateSelectionModeUI(inSelectionMode: Boolean) {
        if (inSelectionMode) {
            binding.layoutNormalActions.visibility = android.view.View.GONE
            binding.layoutSelectionActions.visibility = android.view.View.VISIBLE
            binding.fabAddSession.visibility = android.view.View.GONE
            binding.btnShareSelectedQr.visibility = android.view.View.VISIBLE
            binding.btnShareSelectedQr.isEnabled = false
            supportActionBar?.title = "Đã chọn: 0"
        } else {
            binding.layoutNormalActions.visibility = android.view.View.VISIBLE
            binding.layoutSelectionActions.visibility = android.view.View.GONE
            binding.fabAddSession.visibility = android.view.View.VISIBLE
            binding.btnShareSelectedQr.visibility = android.view.View.GONE
            supportActionBar?.title = "Lịch sử"
        }
    }

    private fun updateSelectionCountUI(count: Int) {
        supportActionBar?.title = "Đã chọn: $count"
        binding.btnShareSelectedQr.text = "Tạo mã QR chia sẻ ($count)"
        binding.btnShareSelectedQr.isEnabled = count > 0
    }

    /**
     * Start QR scanner using ZXing (Offline)
     */
    private fun startQrScan() {
        val options = ScanOptions().apply {
            setPrompt("Hướng camera vào mã QR để nhận đợt cân")
            setBeepEnabled(true)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        }
        qrScanLauncher.launch(options)
    }

    /**
     * Handle scanned QR code content
     */
    private fun handleScannedQr(contents: String) {
        val parseResult = QrTransferManager.deserializeRecords(contents)
        parseResult.onSuccess { records ->
            showImportConfirmDialog(records)
        }.onFailure { error ->
            AlertDialog.Builder(this)
                .setTitle("Mã QR không hợp lệ")
                .setMessage(error.message ?: "Không thể đọc dữ liệu từ mã QR này.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /**
     * Show confirmation dialog when importing records from QR
     */
    private fun showImportConfirmDialog(records: List<RiceRecord>) {
        val dialog = android.app.Dialog(this)
        val dialogBinding = DialogQrImportConfirmBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialogBinding.tvImportCount.text = "Tìm thấy ${records.size} đợt cân hợp lệ:"

        val container = dialogBinding.layoutImportItemsContainer
        container.removeAllViews()

        val moneyFormat = java.text.NumberFormat.getInstance(java.util.Locale("vi", "VN"))
        val weightFormat = java.text.NumberFormat.getInstance(java.util.Locale.getDefault()).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }

        for ((index, rec) in records.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_history, container, false)
            itemView.findViewById<android.view.View>(R.id.btn_menu)?.visibility = android.view.View.GONE
            itemView.findViewById<android.widget.TextView>(R.id.tv_customer_name)?.text =
                "${index + 1}. ${rec.customerName.ifBlank { "Khách hàng" }}"
            itemView.findViewById<android.widget.TextView>(R.id.tv_date_time)?.text =
                "${rec.weightList.size} bao"
            itemView.findViewById<android.widget.TextView>(R.id.tv_grand_total)?.text =
                "${weightFormat.format(rec.grandTotal)} kg"
            itemView.findViewById<android.widget.TextView>(R.id.tv_total_money)?.text =
                "${moneyFormat.format(rec.totalMoney)} VNĐ"
            container.addView(itemView)
        }

        dialogBinding.btnCancelImport.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmImport.setOnClickListener {
            dialog.dismiss()
            viewModel.importRecords(records) { success, failed ->
                if (success > 0) {
                    android.widget.Toast.makeText(
                        this,
                        "Đã nhận thành công $success đợt cân!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        this,
                        "Lỗi khi lưu đợt cân: $failed thất bại",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialog.show()
    }

    /**
     * Display QR Code dialog to share one or multiple records
     */
    private fun shareRecordsAsQr(records: List<RiceRecord>) {
        if (records.isEmpty()) return

        lifecycleScope.launch {
            try {
                val appLinkData = QrTransferManager.serializeToDeepLink(records)
                val textReceiptData = QrTransferManager.formatTextReceipt(records)

                val dialog = android.app.Dialog(this@HistoryActivity)
                val dialogBinding = DialogQrShareBinding.inflate(layoutInflater)
                dialog.setContentView(dialogBinding.root)
                dialog.window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.92).toInt(),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )

                val moneyFormat = java.text.NumberFormat.getInstance(java.util.Locale("vi", "VN"))
                val totalKg = records.sumOf { it.grandTotal }
                val totalMoney = records.sumOf { it.totalMoney }

                val summaryText = if (records.size == 1) {
                    val r = records[0]
                    "${r.customerName.ifBlank { "Khách hàng" }} • %.1f kg • %s VNĐ".format(
                        r.grandTotal,
                        moneyFormat.format(r.totalMoney)
                    )
                } else {
                    "${records.size} đợt cân • %.1f kg • %s VNĐ".format(
                        totalKg,
                        moneyFormat.format(totalMoney)
                    )
                }

                dialogBinding.tvQrSummary.text = summaryText

                var cachedAppLinkBitmap: android.graphics.Bitmap? = null
                var cachedReceiptBitmap: android.graphics.Bitmap? = null

                fun renderQr(isReceipt: Boolean) {
                    if (isReceipt) {
                        if (cachedReceiptBitmap == null) {
                            cachedReceiptBitmap = QrTransferManager.generateQrBitmap(textReceiptData, 800)
                        }
                        dialogBinding.imgQrCode.setImageBitmap(cachedReceiptBitmap)
                        dialogBinding.tvQrInstruction.text = "Bất kỳ máy nào dùng Zalo hoặc Camera chụp ảnh quét mã là đọc được ngay toàn bộ phiếu cân (không cần cài app)."
                    } else {
                        if (cachedAppLinkBitmap == null) {
                            cachedAppLinkBitmap = QrTransferManager.generateQrBitmap(appLinkData, 800)
                        }
                        dialogBinding.imgQrCode.setImageBitmap(cachedAppLinkBitmap)
                        dialogBinding.tvQrInstruction.text = "Đưa Camera điện thoại thường quét mã -> Bấm 'Mở bằng Cân Lúa' để nạp tự động, hoặc mở app khác quét."
                    }
                }

                // Default to App Link mode
                renderQr(false)

                dialogBinding.toggleQrMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (isChecked) {
                        when (checkedId) {
                            R.id.btn_mode_text_receipt -> renderQr(true)
                            R.id.btn_mode_app_link -> renderQr(false)
                        }
                    }
                }

                dialogBinding.btnCloseQr.setOnClickListener {
                    dialog.dismiss()
                }

                dialog.show()
            } catch (e: Exception) {
                AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("Lỗi tạo mã QR")
                    .setMessage(e.message ?: "Không thể tạo mã QR cho các đợt cân này.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    /**
     * Setup FAB to create new session
     */
    private fun setupFAB() {
        binding.fabAddSession.setOnClickListener {
            binding.fabAddSession.isEnabled = false
            newSessionLauncher.launch(Intent(this, MainActivity::class.java))
        }
    }
    
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
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allRecords.collectLatest { records ->
                    adapter.submitList(records)
                    binding.tvEmptyState.visibility =
                        if (records.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                }
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
    private fun openEditSession(recordId: Long) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra("EXTRA_RECORD_ID", recordId)
        editSessionLauncher.launch(intent)
    }
    
    /**
     * Show delete confirmation dialog
     */
    private fun showDeleteConfirmation(record: RiceRecord) {
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
    private fun exportRecord(record: RiceRecord) {
        showExportPreviewForRecord(record)
    }
    
    /**
     * Show export preview for a specific record
     */
    private fun showExportPreviewForRecord(record: RiceRecord) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogBinding = com.example.caculateapp.databinding.DialogExportPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        // Convert flat weightList to columns
        val columns = mutableListOf<List<Double>>()
        val weights = record.weightList
        for (i in weights.indices step BAGS_PER_COLUMN) {
            val col = weights.subList(i, minOf(i + BAGS_PER_COLUMN, weights.size)).toMutableList()
            while (col.size < BAGS_PER_COLUMN) col.add(0.0)
            columns.add(col)
        }
        
        val pagesContainer = dialogBinding.layoutPagesContainer
        
        val columnChunks = columns.chunked(10)
        columnChunks.forEachIndexed { pageIndex, pageColumns ->
            val inflater = android.view.LayoutInflater.from(this)
            val pageView = inflater.inflate(R.layout.layout_export_page, null)
            
            // Populate header
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_customer_name).text = record.customerName
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_date).text = 
                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(if (record.updatedAt > 0L) record.updatedAt else record.createdAt))
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_unit_price).text = 
                String.format("%,d VNĐ/kg", record.unitPrice)
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_title).text = 
                "PHIẾU CÂN - Trang ${pageIndex + 1}/${columnChunks.size}"
            
            val topRow = pageView.findViewById<android.widget.LinearLayout>(R.id.layout_top_row)
            val bottomRow = pageView.findViewById<android.widget.LinearLayout>(R.id.layout_bottom_row)
            
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
            
            val layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = 16
            pageView.layoutParams = layoutParams
            
            pagesContainer.addView(pageView)
        }
        
        dialogBinding.btnClosePreview.setOnClickListener {
            dialog.dismiss()
        }
        
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
        record: RiceRecord,
        columns: List<List<Double>>
    ) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val bottomSheetBinding = com.example.caculateapp.databinding.BottomSheetExportFormatBinding.inflate(layoutInflater)
        bottomSheet.setContentView(bottomSheetBinding.root)
        
        val exportManager = ExportManager(this)
        
        // Export as Images (multi-page)
        bottomSheetBinding.btnExportImage.setOnClickListener {
            bottomSheet.dismiss()
            
            lifecycleScope.launch {
                runCatching {
                    exportManager.exportToMultipleImages(
                        columns,
                        record.customerName,
                        record.unitPrice,
                        record.grandTotal,
                        record.totalMoney,
                        showToast = false
                    )
                }.onSuccess { uris ->
                    if (uris.isNotEmpty()) {
                        android.widget.Toast.makeText(
                            this@HistoryActivity,
                            "Đã xuất ${uris.size} ảnh vào thư mục Pictures/RiceManager",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            this@HistoryActivity,
                            "Không thể xuất ảnh",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { error ->
                    error.printStackTrace()
                    android.widget.Toast.makeText(
                        this@HistoryActivity,
                        "Lỗi: ${error.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        
        // Export as PDF (multi-page)
        bottomSheetBinding.btnExportPdf.setOnClickListener {
            bottomSheet.dismiss()
            
            lifecycleScope.launch {
                runCatching {
                    exportManager.exportToMultiPagePDF(
                        columns,
                        record.customerName,
                        record.unitPrice,
                        record.grandTotal,
                        record.totalMoney,
                        showToast = false
                    )
                }.onSuccess { uri ->
                    if (uri != null) {
                        android.widget.Toast.makeText(
                            this@HistoryActivity,
                            "Đã xuất PDF vào thư mục Downloads/RiceManager",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            this@HistoryActivity,
                            "Không thể xuất PDF",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { error ->
                    error.printStackTrace()
                    android.widget.Toast.makeText(
                        this@HistoryActivity,
                        "Lỗi: ${error.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
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
        
        val header = android.widget.TextView(this).apply {
            text = "Cột $columnNumber"
            setTextColor(android.graphics.Color.BLACK)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        columnLayout.addView(header)
        
        weights.forEach { weight ->
            val weightText = android.widget.TextView(this).apply {
                text = if (weight > 0.0) {
                    if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString()
                } else {
                    ""
                }
                setTextColor(android.graphics.Color.BLACK)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 4, 0, 4)
                minHeight = (24 * resources.displayMetrics.density).toInt()
            }
            columnLayout.addView(weightText)
        }
        
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
}
