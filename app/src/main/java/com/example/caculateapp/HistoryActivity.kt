package com.example.caculateapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.caculateapp.adapter.HistoryAdapter
import com.example.caculateapp.data.RiceRecord
import com.example.caculateapp.databinding.ActivityHistoryBinding
import com.example.caculateapp.databinding.BottomSheetExportFormatBinding
import com.example.caculateapp.databinding.DialogExportPreviewBinding
import com.example.caculateapp.utils.ExportManager
import com.example.caculateapp.utils.ExportPayload
import com.example.caculateapp.utils.ExportPreviewRenderer
import com.example.caculateapp.utils.UiFormatters
import com.example.caculateapp.viewmodel.HistoryViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

    companion object {
        const val REQUEST_CODE_NEW_SESSION = 100
        const val REQUEST_CODE_EDIT_SESSION = 101
    }

    private val timeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            binding.toolbar.subtitle = UiFormatters.historyToolbarSubtitle(Date())
            timeHandler.postDelayed(this, 30_000)
        }
    }

    private var lastClickTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootHistory) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.heroContainer.updatePadding(top = systemBars.top)
            binding.rootHistory.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        setupDateTime()
        setupRecyclerView()
        setupFab()
        setupSearch()
        setupSettings()
        observeSyncStatus()
        observeRecords()
        observeSummary()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkSyncStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        timeHandler.removeCallbacks(updateTimeRunnable)
    }

    private fun setupDateTime() {
        updateTimeRunnable.run()
    }

    private fun setupSettings() {
        binding.btnSettings.setOnClickListener {
            showSettingsBottomSheet()
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { record -> openEditSession(record.id) },
            onDelete = { record -> showDeleteConfirmation(record) },
            onExport = { record -> exportRecord(record) }
        )

        binding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupFab() {
        binding.fabAddSession.setOnClickListener {
            if (System.currentTimeMillis() - lastClickTime < 300) return@setOnClickListener
            lastClickTime = System.currentTimeMillis()

            val intent = Intent(this, MainActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_NEW_SESSION)
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString().orEmpty())
            }
        })
    }

    private fun observeRecords() {
        lifecycleScope.launch {
            viewModel.visibleRecords.collectLatest { records ->
                adapter.submitList(records)
                binding.tvEmptyState.visibility =
                    if (records.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun observeSummary() {
        lifecycleScope.launch {
            viewModel.historySummary.collectLatest { summary ->
                binding.tvStatSessions.text = summary.recordCount.toString()
                binding.tvStatWeight.text = UiFormatters.weightTotal(summary.totalWeight)
                binding.tvStatValue.text = UiFormatters.currency(summary.totalMoney)
            }
        }
    }

    private fun openEditSession(recordId: String?) {
        if (recordId == null) return
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("EXTRA_RECORD_ID", recordId)
        startActivityForResult(intent, REQUEST_CODE_EDIT_SESSION)
    }

    private fun showDeleteConfirmation(record: RiceRecord) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc muốn xóa đợt cân của ${record.customerName}?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteRecord(record) { success ->
                    if (!success) {
                        Toast.makeText(this, "Lỗi khi xóa", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun exportRecord(record: RiceRecord) {
        showExportPreviewForRecord(record)
    }

    private fun showExportPreviewForRecord(record: RiceRecord) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogBinding = DialogExportPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val payload = ExportPayload(
            customerName = record.customerName.ifBlank { "Khách hàng" },
            unitPrice = record.unitPrice,
            grandTotal = record.grandTotal,
            totalMoney = record.totalMoney,
            columns = ExportPreviewRenderer.toColumns(record.weightList),
            createdAt = record.createdAt ?: Date()
        )

        dialogBinding.layoutPagesContainer.removeAllViews()
        ExportPreviewRenderer
            .createPageViews(this, layoutInflater, payload)
            .forEach(dialogBinding.layoutPagesContainer::addView)

        dialogBinding.btnClosePreview.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmExport.setOnClickListener {
            dialog.dismiss()
            showFormatSelectionForRecord(payload)
        }

        dialog.show()
    }

    private fun showFormatSelectionForRecord(payload: ExportPayload) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetExportFormatBinding.inflate(layoutInflater)
        bottomSheet.setContentView(bottomSheetBinding.root)

        val exportManager = ExportManager(this)

        bottomSheetBinding.btnExportImage.setOnClickListener {
            bottomSheet.dismiss()
            lifecycleScope.launch {
                runCatching {
                    exportManager.exportToMultipleImages(
                        columns = payload.columns,
                        customerName = payload.customerName,
                        unitPrice = payload.unitPrice,
                        grandTotal = payload.grandTotal,
                        totalMoney = payload.totalMoney,
                        showToast = false,
                        createdAt = payload.createdAt
                    )
                }.onSuccess { uris ->
                    val message = if (uris.isNotEmpty()) {
                        "Đã xuất ${uris.size} ảnh vào Pictures/RiceManager"
                    } else {
                        "Không thể xuất ảnh"
                    }
                    Toast.makeText(this@HistoryActivity, message, Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(this@HistoryActivity, "Lỗi: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        bottomSheetBinding.btnExportPdf.setOnClickListener {
            bottomSheet.dismiss()
            lifecycleScope.launch {
                runCatching {
                    exportManager.exportToMultiPagePDF(
                        columns = payload.columns,
                        customerName = payload.customerName,
                        unitPrice = payload.unitPrice,
                        grandTotal = payload.grandTotal,
                        totalMoney = payload.totalMoney,
                        showToast = false,
                        createdAt = payload.createdAt
                    )
                }.onSuccess { uri ->
                    val message = if (uri != null) {
                        "Đã xuất PDF vào Downloads/RiceManager"
                    } else {
                        "Không thể xuất PDF"
                    }
                    Toast.makeText(this@HistoryActivity, message, Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(this@HistoryActivity, "Lỗi: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        bottomSheet.show()
    }

    private fun observeSyncStatus() {
        lifecycleScope.launch {
            viewModel.isCloudSynced.collectLatest { isSynced ->
                updateSyncBadge(isSynced)
            }
        }
    }

    private fun updateSyncBadge(isSynced: Boolean) {
        val color = if (isSynced) {
            getColor(R.color.color_weight_positive)
        } else {
            getColor(R.color.color_sync_pending)
        }
        binding.syncBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        binding.tvSyncState.text = if (isSynced) "Cloud đã đồng bộ" else "Đang chờ đồng bộ"
    }

    private fun showSettingsBottomSheet() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        bottomSheet.setContentView(sheetView)

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        sheetView.findViewById<android.widget.TextView>(R.id.tv_user_name).text =
            currentUser?.displayName ?: "User"
        sheetView.findViewById<android.widget.TextView>(R.id.tv_user_email).text =
            currentUser?.email ?: "No email"

        val syncStatusBadge = sheetView.findViewById<android.view.View>(R.id.sync_status_badge)
        val syncStatusText = sheetView.findViewById<android.widget.TextView>(R.id.tv_sync_status)
        val syncDetailText = sheetView.findViewById<android.widget.TextView>(R.id.tv_sync_detail)

        val job = lifecycleScope.launch {
            viewModel.isCloudSynced.collectLatest { isSynced ->
                val color = if (isSynced) getColor(R.color.color_weight_positive) else getColor(R.color.color_sync_pending)
                syncStatusBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                if (isSynced) {
                    syncStatusText.text = "Đã đồng bộ"
                    syncDetailText.text = "Tất cả dữ liệu đã lưu an toàn trên cloud."
                } else {
                    syncStatusText.text = "Đang đồng bộ"
                    syncDetailText.text = "Còn dữ liệu cục bộ đang chờ gửi lên cloud."
                }
            }
        }

        bottomSheet.setOnDismissListener { job.cancel() }

        sheetView.findViewById<android.widget.Button>(R.id.btn_sign_out_settings).setOnClickListener {
            bottomSheet.dismiss()
            showSignOutConfirmation()
        }

        bottomSheet.show()
    }

    private fun showSignOutConfirmation() {
        lifecycleScope.launch {
            try {
                val firestore = Firebase.firestore
                val hasPendingWrites = withTimeoutOrNull(5_000) {
                    try {
                        firestore.waitForPendingWrites().await()
                        false
                    } catch (_: Exception) {
                        true
                    }
                } ?: true

                if (hasPendingWrites) {
                    showPendingWritesWarning()
                } else {
                    showNormalSignOutDialog()
                }
            } catch (_: Exception) {
                showPendingWritesWarning()
            }
        }
    }

    private fun showPendingWritesWarning() {
        AlertDialog.Builder(this)
            .setTitle("Cảnh báo")
            .setMessage(
                "Vẫn còn dữ liệu chưa đồng bộ lên cloud.\n\n" +
                    "Nếu đăng xuất ngay, bạn có thể mất dữ liệu vừa nhập.\n\n" +
                    "Khuyến nghị:\n" +
                    "• Kết nối Wi‑Fi/4G\n" +
                    "• Đợi vài giây để app sync xong\n" +
                    "• Hoặc hủy để tiếp tục làm việc"
            )
            .setPositiveButton("Vẫn đăng xuất") { _, _ -> performSignOut() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showNormalSignOutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc muốn đăng xuất?\n\nDữ liệu hiện đã được lưu an toàn trên cloud.")
            .setPositiveButton("Đăng xuất") { _, _ -> performSignOut() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun performSignOut() {
        lifecycleScope.launch {
            try {
                val authManager = com.example.caculateapp.auth.AuthManager(this@HistoryActivity)
                authManager.signOut()

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            viewModel.checkSyncStatus()
        }
    }
}
