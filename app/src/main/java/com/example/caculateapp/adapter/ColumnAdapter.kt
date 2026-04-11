package com.example.caculateapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.caculateapp.R
import com.example.caculateapp.databinding.ItemColumnBinding

/**
 * ColumnAdapter: Displays rice weight data in column format
 * Each column contains 5 bags + column total (like traditional paper)
 */
class ColumnAdapter(
    private val onWeightChanged: (columnIndex: Int, bagIndex: Int, weight: Double) -> Unit
) : RecyclerView.Adapter<ColumnAdapter.ColumnViewHolder>() {
    
    // List of columns, each column has 5 weights
    private var columns = mutableListOf<MutableList<Double>>()
    
    inner class ColumnViewHolder(val binding: ItemColumnBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        private val weightViews = listOf(
            binding.editWeight1,
            binding.editWeight2,
            binding.editWeight3,
            binding.editWeight4,
            binding.editWeight5
        )
        
        fun bind(columnIndex: Int, weights: List<Double>, columnTotal: Double) {
            // Set column number
            binding.tvColumnNumber.text = "Cột ${columnIndex + 1}"

            // Bind each weight with CLICK TO EDIT dialog
            weightViews.forEachIndexed { bagIndex, textView ->
                val weight = weights.getOrNull(bagIndex) ?: 0.0

                // Display current value
                val displayText = if (weight > 0.0) weight.toString() else ""
                if (textView.text.toString() != displayText) {
                    textView.text = displayText
                }

                // Click listener to show dialog
                textView.setOnClickListener {
                    showEditDialog(textView, bagIndex, weight)
                }
            }

            // Set column total
            binding.tvColumnTotal.text = String.format("%.1f kg", columnTotal)
        }
        
        /**
         * Show dialog to edit weight value
         */
        private fun showEditDialog(targetView: TextView, bagIndex: Int, currentWeight: Double) {
            val context = targetView.context
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
                    val newText = if (newWeight > 0.0) newWeight.toString() else ""
                    if (targetView.text.toString() != newText) {
                        targetView.text = newText
                    }
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
     * Update columns data with targeted notifications (avoids full rebind)
     */
    fun updateColumns(newColumns: List<List<Double>>) {
        val oldColumns = columns.toList()
        val newColumnsList = newColumns.map { it.toMutableList() }.toMutableList()
        columns = newColumnsList

        val oldSize = oldColumns.size
        val newSize = newColumnsList.size
        val minSize = minOf(oldSize, newSize)

        // Notify only changed columns
        for (i in 0 until minSize) {
            if (oldColumns[i] != newColumnsList[i]) {
                notifyItemChanged(i)
            }
        }

        // Handle added/removed columns
        if (newSize > oldSize) {
            notifyItemRangeInserted(oldSize, newSize - oldSize)
        } else if (oldSize > newSize) {
            notifyItemRangeRemoved(newSize, oldSize - newSize)
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
