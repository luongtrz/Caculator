package com.example.caculateapp.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.caculateapp.R
import com.example.caculateapp.databinding.ItemColumnBinding
import android.widget.EditText

/**
 * ColumnAdapter: Displays rice weight data in column format
 * Each column contains 5 bags + column total (like traditional paper)
 */
class ColumnAdapter(
    private val onWeightChanged: (columnIndex: Int, bagIndex: Int, weight: Double) -> Unit
) : RecyclerView.Adapter<ColumnAdapter.ColumnViewHolder>() {
    
    // List of columns, each column has 5 weights
    private var columns = mutableListOf<MutableList<Double>>()
    private val textWatchers = mutableMapOf<Int, MutableList<TextWatcher>>()
    
    inner class ColumnViewHolder(val binding: ItemColumnBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        val editTexts = listOf(
            binding.editWeight1,
            binding.editWeight2,
            binding.editWeight3,
            binding.editWeight4,
            binding.editWeight5
        )
        
        fun bind(columnIndex: Int, weights: List<Double>, columnTotal: Double) {
            // Set column number
            binding.tvColumnNumber.text = "Cột ${columnIndex + 1}"
            
            // COMPLETELY remove ALL old watchers for this ViewHolder
            editTexts.forEach { editText ->
                // Remove all watchers (not just from map)
                val watchersList = textWatchers[adapterPosition]
                watchersList?.forEach { watcher ->
                    editText.removeTextChangedListener(watcher)
                }
            }
            textWatchers.remove(adapterPosition)
            
            // Create new watchers list
            val watchers = mutableListOf<TextWatcher>()
            
            // Bind each weight with CLICK TO EDIT dialog
            editTexts.forEachIndexed { bagIndex, editText ->
                val weight = weights.getOrNull(bagIndex) ?: 0.0
                
                // Display current value
                editText.setText(if (weight > 0) weight.toString() else "")
                
                // Make EditText non-editable but clickable
                editText.isFocusable = false
                editText.isFocusableInTouchMode = false
                editText.isClickable = true
                
                // Click listener to show dialog
                editText.setOnClickListener {
                    showEditDialog(editText, columnIndex, bagIndex, weight)
                }
                
                // Create text watcher for any programmatic changes
                val watcher = object : TextWatcher {
                    private var isUpdating = false
                    
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (isUpdating) return
                        
                        val currentPosition = adapterPosition
                        if (currentPosition == RecyclerView.NO_POSITION) return
                        
                        val newWeight = s?.toString()?.toDoubleOrNull() ?: 0.0
                        onWeightChanged(currentPosition, bagIndex, newWeight)
                    }
                    
                    fun setTextWithoutTrigger(text: String) {
                        isUpdating = true
                        editText.setText(text)
                        isUpdating = false
                    }
                }
                
                editText.addTextChangedListener(watcher)
                watchers.add(watcher)
            }
            
            // Store watchers using adapterPosition
            textWatchers[adapterPosition] = watchers
            
            // Set column total
            binding.tvColumnTotal.text = String.format("%.1f kg", columnTotal)
        }
        
        /**
         * Show dialog to edit weight value
         */
        private fun showEditDialog(editText: EditText, columnIndex: Int, bagIndex: Int, currentWeight: Double) {
            val context = editText.context
            val dialog = android.app.AlertDialog.Builder(context).create()
            val dialogView = android.view.LayoutInflater.from(context)
                .inflate(R.layout.dialog_edit_weight, null)
            
            dialog.setView(dialogView)
            
            val etEditWeight = dialogView.findViewById<EditText>(R.id.et_edit_weight)
            val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btn_cancel)
            val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btn_save)
            
            // Set current value
            if (currentWeight > 0) {
                etEditWeight.setText(currentWeight.toString())
            }
            
            // Auto-select text and show keyboard
            etEditWeight.requestFocus()
            etEditWeight.selectAll()
            etEditWeight.postDelayed({
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) 
                    as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etEditWeight, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 100)
            
            // Cancel button
            btnCancel.setOnClickListener {
                dialog.dismiss()
            }
            
            // Save button
            btnSave.setOnClickListener {
                val newWeight = etEditWeight.text.toString().toDoubleOrNull() ?: 0.0
                
                // Update the value
                val currentPosition = adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    editText.setText(if (newWeight > 0) newWeight.toString() else "")
                    onWeightChanged(currentPosition, bagIndex, newWeight)
                }
                
                dialog.dismiss()
            }
            
            dialog.show()
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColumnViewHolder {
        val binding = ItemColumnBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ColumnViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ColumnViewHolder, position: Int) {
        val weights = columns[position]
        val columnTotal = weights.sum()
        holder.bind(position, weights, columnTotal)
    }
    
    override fun getItemCount() = columns.size
    
    /**
     * Update columns data safely (post to avoid layout conflicts)
     */
    fun updateColumns(newColumns: List<List<Double>>) {
        val oldSize = columns.size
        
        // Create NEW list instead of clearing (prevents race conditions)
        val newColumnsList = newColumns.map { it.toMutableList() }.toMutableList()
        columns = newColumnsList
        
        // Post to handler to avoid "Cannot call this method while RecyclerView is computing a layout"
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                // Always use notifyDataSetChanged for simplicity and reliability
                notifyDataSetChanged()
            } catch (e: Exception) {
                // Log error but don't crash
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Get current columns data
     */
    fun getColumns(): List<List<Double>> {
        return columns.map { it.toList() }
    }
    
    /**
     * Add a new empty column
     */
    fun addColumn() {
        columns.add(mutableListOf(0.0, 0.0, 0.0, 0.0, 0.0))
        notifyItemInserted(columns.size - 1)
    }
    
    /**
     * Update single weight value
     */
    fun updateWeight(columnIndex: Int, bagIndex: Int, weight: Double) {
        if (columnIndex < columns.size && bagIndex < 5) {
            columns[columnIndex][bagIndex] = weight
            notifyItemChanged(columnIndex)
        }
    }
}
