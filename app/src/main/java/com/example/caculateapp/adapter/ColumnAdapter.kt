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

class ColumnAdapter(
    private val onWeightChanged: (columnIndex: Int, bagIndex: Int, weight: Double) -> Unit
) : ListAdapter<List<Double>, ColumnAdapter.ColumnViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<List<Double>>() {
            override fun areItemsTheSame(old: List<Double>, new: List<Double>) = old === new
            override fun areContentsTheSame(old: List<Double>, new: List<Double>) = old == new
        }
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
            // Create listeners once per ViewHolder — avoids re-allocation on every bind
            weightViews.forEachIndexed { bagIndex, textView ->
                textView.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                    val weight = getItem(pos).getOrElse(bagIndex) { 0.0 }
                    showEditDialog(textView, pos, bagIndex, weight)
                }
            }
        }

        fun bind(columnIndex: Int, weights: List<Double>) {
            binding.tvColumnNumber.text = "Cột ${columnIndex + 1}"

            var total = 0.0
            weightViews.forEachIndexed { i, tv ->
                val w = weights.getOrElse(i) { 0.0 }
                total += w
                val display = if (w > 0.0) formatWeight(w) else ""
                if (tv.text.toString() != display) tv.text = display
            }

            val totalText = "%.1f kg".format(total)
            if (binding.tvColumnTotal.text.toString() != totalText) {
                binding.tvColumnTotal.text = totalText
            }
        }

        private fun formatWeight(w: Double): String =
            if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

        private fun showEditDialog(
            targetView: TextView,
            columnIndex: Int,
            bagIndex: Int,
            currentWeight: Double
        ) {
            val context = targetView.context
            val dialog = android.app.AlertDialog.Builder(context).create()
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_weight, null)
            dialog.setView(dialogView)

            val etEdit = dialogView.findViewById<EditText>(R.id.et_edit_weight)
            val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btn_cancel)
            val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btn_save)

            if (currentWeight > 0) {
                etEdit.setText(formatWeight(currentWeight))
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
        holder.bind(position, getItem(position))
    }

    fun updateColumns(newColumns: List<List<Double>>) {
        submitList(newColumns.map { it.toList() })
    }
}
