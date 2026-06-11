package com.example.caculateapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caculateapp.R
import com.example.caculateapp.databinding.ItemColumnBinding
import com.example.caculateapp.utils.UiFormatters
import com.example.caculateapp.viewmodel.WeightColumn
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ColumnAdapter(
    private val onWeightChanged: (columnIndex: Int, bagIndex: Int, weight: Double) -> Unit
) : ListAdapter<WeightColumn, ColumnAdapter.ColumnViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<WeightColumn>() {
            override fun areItemsTheSame(old: WeightColumn, new: WeightColumn): Boolean {
                return old.index == new.index
            }

            override fun areContentsTheSame(old: WeightColumn, new: WeightColumn): Boolean {
                return old == new
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    inner class ColumnViewHolder(val binding: ItemColumnBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val weightViews = listOf(
            binding.editWeight1,
            binding.editWeight2,
            binding.editWeight3,
            binding.editWeight4,
            binding.editWeight5
        )

        init {
            weightViews.forEachIndexed { bagIndex, textView ->
                textView.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                    val weight = getItem(position).weights.getOrElse(bagIndex) { 0.0 }
                    showEditDialog(textView, position, bagIndex, weight)
                }
            }
        }

        fun bind(column: WeightColumn) {
            binding.tvColumnNumber.text = "Cột ${column.index + 1}"

            weightViews.forEachIndexed { index, textView ->
                val weight = column.weights.getOrElse(index) { 0.0 }
                val display = UiFormatters.weightCell(weight)

                if (textView.text.toString() != display) {
                    textView.text = display
                }
                textView.alpha = if (weight > 0.0) 1f else 0.65f
            }

            val totalText = UiFormatters.weightTotal(column.total)
            if (binding.tvColumnTotal.text.toString() != totalText) {
                binding.tvColumnTotal.text = totalText
            }
        }

        private fun showEditDialog(
            targetView: TextView,
            columnIndex: Int,
            bagIndex: Int,
            currentWeight: Double
        ) {
            val context = targetView.context
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_weight, null)
            val dialog = MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .create()

            val etEdit = dialogView.findViewById<EditText>(R.id.et_edit_weight)
            val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btn_cancel)
            val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btn_save)

            if (currentWeight > 0.0) {
                etEdit.setText(UiFormatters.weightCell(currentWeight))
            }

            etEdit.requestFocus()
            etEdit.selectAll()
            etEdit.postDelayed({
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 100)

            btnCancel.setOnClickListener { dialog.dismiss() }
            btnSave.setOnClickListener {
                val newWeight = etEdit.text.toString().toDoubleOrNull() ?: 0.0
                onWeightChanged(columnIndex, bagIndex, newWeight)
                dialog.dismiss()
            }

            dialog.show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColumnViewHolder {
        val binding = ItemColumnBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColumnViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColumnViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).index.toLong()
    }

    fun updateColumns(newColumns: List<WeightColumn>) {
        submitList(newColumns.map { it.copy(weights = it.weights.toList()) })
    }
}
