package com.example.caculateapp

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.caculateapp.adapter.ColumnAdapter
import com.example.caculateapp.databinding.ActivityMainBinding
import com.example.caculateapp.databinding.BottomSheetExportFormatBinding
import com.example.caculateapp.databinding.DialogExportPreviewBinding
import com.example.caculateapp.utils.ExportManager
import com.example.caculateapp.utils.ExportPayload
import com.example.caculateapp.utils.ExportPreviewRenderer
import com.example.caculateapp.utils.UiFormatters
import com.example.caculateapp.viewmodel.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var columnAdapter: ColumnAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootMain) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerContainer.updatePadding(top = systemBars.top)
            binding.rootMain.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        val recordId = intent.getStringExtra("EXTRA_RECORD_ID")
        if (recordId != null) {
            viewModel.loadExistingRecord(recordId)
            binding.toolbar.subtitle = "Chỉnh sửa phiếu cân"
            binding.tvQuickHint.text = "Chạm vào từng ô để chỉnh số kg và lưu lại khi hoàn tất."
        } else {
            updateToolbarDate()
        }

        setupRecyclerView()
        setupQuickInput()
        setupInputListeners()
        setupObservers()
        setupButtons()
        setupBackPressHandler()

        if (recordId == null) {
            binding.etQuickInput.postDelayed({
                binding.etQuickInput.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.etQuickInput, InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
    }

    private fun updateToolbarDate() {
        binding.toolbar.subtitle = "Hôm nay • ${UiFormatters.toolbarDate(Date())}"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        for (index in 0 until menu.size()) {
            menu.getItem(index).icon?.setTint(getColor(R.color.md_theme_onPrimary))
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            R.id.action_history -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        columnAdapter = ColumnAdapter { columnIndex, bagIndex, weight ->
            viewModel.updateWeight(columnIndex, bagIndex, weight)
        }

        binding.recyclerWeights.apply {
            adapter = columnAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                this@MainActivity,
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                false
            )
            setHasFixedSize(true)
            itemAnimator = null
        }

        viewModel.columns.observe(this) { columns ->
            columnAdapter.updateColumns(columns)
        }
    }

    private fun setupQuickInput() {
        binding.etQuickInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                processQuickInput()
                true
            } else {
                false
            }
        }
        binding.btnConfirm.setOnClickListener { processQuickInput() }
    }

    private fun processQuickInput() {
        val weight = binding.etQuickInput.text.toString().toDoubleOrNull()
        if (weight == null || weight <= 0.0) {
            Toast.makeText(this, "Vui lòng nhập số kg hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        val flatIndex = viewModel.addQuickWeight(weight)
        if (flatIndex >= 0) {
            binding.etQuickInput.text?.clear()
            val columnIndex = flatIndex / 5
            val bagIndex = flatIndex % 5
            binding.recyclerWeights.post {
                binding.recyclerWeights.smoothScrollToPosition(columnIndex)
            }
            binding.tvQuickHint.text = "Đã thêm ${UiFormatters.weightCell(weight)} kg vào cột ${columnIndex + 1}, bao ${bagIndex + 1}."
            binding.etQuickInput.requestFocus()
        } else {
            Toast.makeText(this, "Không thể thêm dữ liệu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupInputListeners() {
        viewModel.customerName.observe(this) { name ->
            if (binding.editCustomerName.text.toString() != name) {
                binding.editCustomerName.setText(name)
            }
        }

        viewModel.unitPrice.observe(this) { price ->
            val expected = if (price > 0L) price.toString() else ""
            if (binding.editUnitPrice.text.toString() != expected) {
                binding.editUnitPrice.setText(expected)
            }
        }

        binding.editCustomerName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val name = s?.toString() ?: ""
                if (viewModel.customerName.value != name) {
                    viewModel.setCustomerName(name)
                }
            }
        })

        binding.editUnitPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val price = s?.toString()?.toLongOrNull() ?: 0L
                if (viewModel.unitPrice.value != price) {
                    viewModel.setUnitPrice(price)
                }
            }
        })
    }

    private fun setupObservers() {
        viewModel.sessionOverview.observe(this) { overview ->
            binding.tvSessionMeta.text =
                "${overview.filledBags}/${overview.totalSlots} bao đã nhập • ${overview.columnCount} cột"
        }

        viewModel.grandTotal.observe(this) { total ->
            binding.tvGrandTotal.text = UiFormatters.weightTotal(total)
        }

        viewModel.totalMoney.observe(this) { money ->
            binding.tvTotalMoney.text = UiFormatters.currency(money)
        }

        viewModel.saveStatus.observe(this) { status ->
            status?.let {
                when {
                    it.startsWith("Đã lưu thành công") -> {
                        Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }

                    it.isNotEmpty() -> {
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Thông báo")
                            .setMessage(it)
                            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                            .show()
                    }
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { viewModel.saveSession() }
        binding.btnShare.setOnClickListener { showExportPreviewDialog() }
        binding.btnAddColumn.setOnClickListener {
            viewModel.addColumn()
            val targetPosition = (viewModel.sessionOverview.value?.columnCount ?: 1) - 1
            binding.recyclerWeights.post {
                binding.recyclerWeights.smoothScrollToPosition(targetPosition.coerceAtLeast(0))
            }
            binding.tvQuickHint.text = "Đã thêm một cột mới để tiếp tục nhập."
        }
    }

    private fun showExportPreviewDialog() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogBinding = DialogExportPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val payload = ExportPayload(
            customerName = viewModel.customerName.value?.ifBlank { "Khách hàng" } ?: "Khách hàng",
            unitPrice = viewModel.unitPrice.value ?: 0L,
            grandTotal = viewModel.grandTotal.value ?: 0.0,
            totalMoney = viewModel.totalMoney.value ?: 0L,
            columns = viewModel.getColumns(),
            createdAt = Date()
        )

        dialogBinding.layoutPagesContainer.removeAllViews()
        ExportPreviewRenderer
            .createPageViews(this, layoutInflater, payload)
            .forEach(dialogBinding.layoutPagesContainer::addView)

        dialogBinding.btnClosePreview.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmExport.setOnClickListener {
            dialog.dismiss()
            showFormatSelectionBottomSheet(payload)
        }
        dialog.show()
    }

    private fun showFormatSelectionBottomSheet(payload: ExportPayload) {
        val bottomSheet = BottomSheetDialog(this)
        val bsBinding = BottomSheetExportFormatBinding.inflate(layoutInflater)
        bottomSheet.setContentView(bsBinding.root)

        val exportManager = ExportManager(this)

        bsBinding.btnExportImage.setOnClickListener {
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
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "Lỗi: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        bsBinding.btnExportPdf.setOnClickListener {
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
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "Lỗi: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        bottomSheet.show()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!viewModel.hasUnsavedChanges()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    return
                }

                val dialogView = layoutInflater.inflate(R.layout.dialog_unsaved_warning, null)
                val dialog = Dialog(this@MainActivity)
                dialog.setContentView(dialogView)
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.setCancelable(true)

                dialogView.findViewById<android.widget.Button>(R.id.btn_exit_anyway).setOnClickListener {
                    isEnabled = false
                    dialog.dismiss()
                    onBackPressedDispatcher.onBackPressed()
                }
                dialogView.findViewById<android.widget.Button>(R.id.btn_cancel).setOnClickListener {
                    dialog.dismiss()
                }
                dialog.show()
            }
        })
    }
}
