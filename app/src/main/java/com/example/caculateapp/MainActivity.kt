package com.example.caculateapp

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
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
import com.example.caculateapp.databinding.LayoutExportTemplateBinding
import com.example.caculateapp.utils.ExportManager
import com.example.caculateapp.viewmodel.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var columnAdapter: ColumnAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply window insets: toolbar gets status bar top padding, bottom card gets nav bar padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootMain) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = sys.top)
            binding.rootMain.updatePadding(bottom = sys.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        val recordId = intent.getStringExtra("EXTRA_RECORD_ID")
        if (recordId != null) {
            viewModel.loadExistingRecord(recordId)
            binding.toolbar.subtitle = "Chỉnh sửa"
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

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private fun updateToolbarDate() {
        binding.toolbar.subtitle = dateFormat.format(Date())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Tint menu icons to match toolbar text color
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.setTint(
                androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_onPrimary)
            )
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

        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this,
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false
        )

        binding.recyclerWeights.apply {
            adapter = columnAdapter
            this.layoutManager = layoutManager
            setHasFixedSize(true)
            itemAnimator = null
        }

        // Single observer drives all updates — no pre-load needed
        viewModel.weightList.observe(this) {
            columnAdapter.updateColumns(viewModel.getColumns())
        }
    }

    private fun setupQuickInput() {
        binding.etQuickInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                processQuickInput()
                true
            } else false
        }
        binding.btnConfirm.setOnClickListener { processQuickInput() }
    }

    private fun processQuickInput() {
        val weight = binding.etQuickInput.text.toString().toDoubleOrNull()
        if (weight == null || weight <= 0) {
            Toast.makeText(this, "Vui lòng nhập số hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        val flatIndex = viewModel.addQuickWeight(weight)
        if (flatIndex >= 0) {
            binding.etQuickInput.text?.clear()
            val columnIndex = flatIndex / 5
            binding.recyclerWeights.post {
                binding.recyclerWeights.scrollToPosition(columnIndex)
            }
            binding.etQuickInput.requestFocus()
        } else {
            Toast.makeText(this, "Lỗi khi thêm dữ liệu", Toast.LENGTH_SHORT).show()
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val name = s?.toString() ?: ""
                if (viewModel.customerName.value != name) viewModel.setCustomerName(name)
            }
        })

        binding.editUnitPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val price = s?.toString()?.toLongOrNull() ?: 0L
                if (viewModel.unitPrice.value != price) viewModel.setUnitPrice(price)
            }
        })
    }

    private fun setupObservers() {
        viewModel.grandTotal.observe(this) { total ->
            binding.tvGrandTotal.text = "%.1f kg".format(total)
        }

        viewModel.totalMoney.observe(this) { money ->
            binding.tvTotalMoney.text = "%,d VNĐ".format(money)
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
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .show()
                    }
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { viewModel.saveSession() }
        binding.btnShare.setOnClickListener { showExportPreviewDialog() }
    }

    private fun showExportPreviewDialog() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogBinding = DialogExportPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val customerName = viewModel.customerName.value?.ifBlank { "Khách hàng" } ?: "Khách hàng"
        val unitPrice = viewModel.unitPrice.value ?: 0L
        val grandTotal = viewModel.grandTotal.value ?: 0.0
        val totalMoney = viewModel.totalMoney.value ?: 0L
        val columns = viewModel.getColumns()

        val pagesContainer = dialogBinding.root.findViewById<android.widget.LinearLayout>(
            resources.getIdentifier("layout_pages_container", "id", packageName)
        )

        val columnChunks = columns.chunked(10)
        columnChunks.forEachIndexed { pageIndex, pageColumns ->
            val pageView = layoutInflater.inflate(R.layout.layout_export_page, null)

            pageView.findViewById<android.widget.TextView>(R.id.tv_page_customer_name).text = customerName
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_date).text =
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_unit_price).text =
                "%,d VNĐ/kg".format(unitPrice)
            pageView.findViewById<android.widget.TextView>(R.id.tv_page_title).text =
                "PHIẾU CÂN - Trang ${pageIndex + 1}/${columnChunks.size}"

            val topRow = pageView.findViewById<android.widget.LinearLayout>(R.id.layout_top_row)
            val bottomRow = pageView.findViewById<android.widget.LinearLayout>(R.id.layout_bottom_row)

            var pageTotal = 0.0
            pageColumns.forEachIndexed { colIndex, colWeights ->
                val globalNum = pageIndex * 10 + colIndex + 1
                val colView = createPreviewColumnView(globalNum, colWeights)
                pageTotal += colWeights.sum()
                if (colIndex < 5) topRow.addView(colView) else bottomRow.addView(colView)
            }

            pageView.findViewById<android.widget.TextView>(R.id.tv_page_total).text = "%.1f kg".format(pageTotal)
            pageView.findViewById<android.widget.TextView>(R.id.tv_grand_total).text = "%.1f kg".format(grandTotal)
            pageView.findViewById<android.widget.TextView>(R.id.tv_total_money).text = "%,d VNĐ".format(totalMoney)

            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            pageView.layoutParams = lp
            pagesContainer.addView(pageView)
        }

        dialogBinding.btnClosePreview.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmExport.setOnClickListener {
            dialog.dismiss()
            showFormatSelectionBottomSheet()
        }
        dialog.show()
    }

    private fun createPreviewColumnView(columnNumber: Int, weights: List<Double>): android.view.View {
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(8, 0, 8, 0)
        }
        col.addView(android.widget.TextView(this).apply {
            text = "Cột $columnNumber"
            setTextColor(android.graphics.Color.BLACK)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })
        weights.forEach { w ->
            col.addView(android.widget.TextView(this).apply {
                text = if (w > 0.0) (if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()) else ""
                setTextColor(android.graphics.Color.BLACK)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 4, 0, 4)
                minHeight = (24 * resources.displayMetrics.density).toInt()
            })
        }
        col.addView(android.view.View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2
            ).apply { setMargins(0, 8, 0, 8) }
            setBackgroundColor(android.graphics.Color.BLACK)
        })
        col.addView(android.widget.TextView(this).apply {
            text = "%.1f kg".format(weights.sum())
            setTextColor(android.graphics.Color.BLACK)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        })
        return col
    }

    private fun populateExportTemplate(templateBinding: LayoutExportTemplateBinding) {
        templateBinding.tvExportCustomerName.text = viewModel.customerName.value ?: "Khách hàng"
        templateBinding.tvExportDate.text = "Ngày: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val price = viewModel.unitPrice.value ?: 0L
        templateBinding.tvExportUnitPrice.text = "Đơn giá: %,.0f VNĐ/kg".format(price.toDouble())
        templateBinding.tvExportGrandTotal.text = "%.2f kg".format(viewModel.grandTotal.value ?: 0.0)
        templateBinding.tvExportTotalMoney.text = "%,.0f VNĐ".format((viewModel.totalMoney.value ?: 0L).toDouble())
        populateWeightGrid(templateBinding.layoutExportGridContainer)
    }

    private fun populateWeightGrid(container: LinearLayout) {
        container.removeAllViews()
        val weights = viewModel.weightList.value ?: return
        val columnTotals = viewModel.columnTotals.value ?: listOf()
        if (weights.isEmpty()) return
        val numColumns = minOf((weights.size + 4) / 5, 8)
        for (colIndex in 0 until numColumns) {
            val colLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(android.R.dimen.app_icon_size),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(2, 0, 2, 0) }
            }
            colLayout.addView(TextView(this).apply {
                text = "C${colIndex + 1}"
                textSize = 10f
                setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.md_theme_onPrimaryContainer))
                setPadding(4, 4, 4, 4)
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.md_theme_primaryContainer))
                gravity = android.view.Gravity.CENTER
            })
            for (row in 0 until 5) {
                val idx = colIndex * 5 + row
                val w = if (idx < weights.size) weights[idx] else 0.0
                colLayout.addView(TextView(this).apply {
                    text = if (w > 0) "%.1f".format(w) else ""
                    textSize = 11f
                    setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.md_theme_onSurface))
                    setPadding(4, 8, 4, 8)
                    setBackgroundResource(R.drawable.bg_weight_cell)
                    gravity = android.view.Gravity.CENTER
                    minWidth = 60
                })
            }
            val total = if (colIndex < columnTotals.size) columnTotals[colIndex] else 0.0
            colLayout.addView(TextView(this).apply {
                text = "%.1f".format(total)
                textSize = 10f
                setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.md_theme_onPrimary))
                setPadding(4, 4, 4, 4)
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.md_theme_primary))
                gravity = android.view.Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            container.addView(colLayout)
        }
    }

    private fun showFormatSelectionBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val bsBinding = BottomSheetExportFormatBinding.inflate(layoutInflater)
        bottomSheet.setContentView(bsBinding.root)

        val exportManager = ExportManager(this)
        val customerName = viewModel.customerName.value?.ifBlank { "Khách hàng" } ?: "Khách hàng"
        val unitPrice = viewModel.unitPrice.value ?: 0L
        val grandTotal = viewModel.grandTotal.value ?: 0.0
        val totalMoney = viewModel.totalMoney.value ?: 0L
        val columns = viewModel.getColumns()

        bsBinding.btnExportImage.setOnClickListener {
            bottomSheet.dismiss()
            lifecycleScope.launch {
                runCatching {
                    exportManager.exportToMultipleImages(columns, customerName, unitPrice, grandTotal, totalMoney, showToast = false)
                }.onSuccess { uris ->
                    val msg = if (uris.isNotEmpty()) "Đã xuất ${uris.size} ảnh vào Pictures/RiceManager"
                              else "Không thể xuất ảnh"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }.onFailure { e ->
                    Toast.makeText(this@MainActivity, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        bsBinding.btnExportPdf.setOnClickListener {
            bottomSheet.dismiss()
            lifecycleScope.launch {
                runCatching {
                    exportManager.exportToMultiPagePDF(columns, customerName, unitPrice, grandTotal, totalMoney, showToast = false)
                }.onSuccess { uri ->
                    val msg = if (uri != null) "Đã xuất PDF vào Downloads/RiceManager" else "Không thể xuất PDF"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }.onFailure { e ->
                    Toast.makeText(this@MainActivity, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
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
                dialog.window?.setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame)
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
