package com.example.caculateapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caculateapp.R
import com.example.caculateapp.data.RiceRecord
import com.example.caculateapp.databinding.ItemHistoryBinding
import com.example.caculateapp.utils.UiFormatters
import java.util.Date

class HistoryAdapter(
    private val onItemClick: (RiceRecord) -> Unit,
    private val onDelete: (RiceRecord) -> Unit,
    private val onExport: (RiceRecord) -> Unit
) : ListAdapter<RiceRecord, HistoryAdapter.HistoryViewHolder>(DiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id?.hashCode()?.toLong() ?: RecyclerView.NO_ID
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: RiceRecord) {
            val displayName = record.customerName.ifBlank { "Khách hàng" }
            binding.tvCustomerName.text = displayName
            binding.tvCustomerInitial.text = displayName.firstOrNull()?.uppercase() ?: "K"
            binding.tvDateTime.text = UiFormatters.historyTimestamp(record.createdAt ?: Date())
            binding.tvGrandTotal.text = UiFormatters.weightTotal(record.grandTotal)
            binding.tvTotalMoney.text = UiFormatters.currency(record.totalMoney)
            binding.tvBagCount.text = "${record.weightList.count { it > 0.0 }} bao"

            binding.cardItem.setOnClickListener {
                onItemClick(record)
            }

            binding.btnMenu.setOnClickListener { view ->
                showPopupMenu(view, record)
            }
        }

        private fun showPopupMenu(view: View, record: RiceRecord) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_history_item, popup.menu)

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_export -> {
                        onExport(record)
                        true
                    }

                    R.id.menu_delete -> {
                        onDelete(record)
                        true
                    }

                    else -> false
                }
            }

            popup.show()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RiceRecord>() {
        override fun areItemsTheSame(oldItem: RiceRecord, newItem: RiceRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RiceRecord, newItem: RiceRecord): Boolean {
            return oldItem == newItem
        }
    }
}
