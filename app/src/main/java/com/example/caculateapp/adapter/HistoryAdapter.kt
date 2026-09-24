package com.example.caculateapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caculateapp.R
import com.example.caculateapp.data.RiceRecord
import com.google.android.material.checkbox.MaterialCheckBox
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for History screen RecyclerView
 * Displays list of saved weighing sessions with click, multi-selection, and popup menu actions
 */
class HistoryAdapter(
    private val onItemClick: (RiceRecord) -> Unit,
    private val onDelete: (RiceRecord) -> Unit,
    private val onExport: (RiceRecord) -> Unit,
    private val onShareQr: (RiceRecord) -> Unit,
    private val onSelectionModeChanged: (Boolean) -> Unit,
    private val onSelectionCountChanged: (Int) -> Unit
) : ListAdapter<RiceRecord, HistoryAdapter.HistoryViewHolder>(DiffCallback()) {
    
    companion object {
        private val dateFormat = SimpleDateFormat("hh:mm a dd/MM/yyyy", Locale.getDefault())
        private val weightFormat = NumberFormat.getInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        private val moneyFormat = NumberFormat.getInstance(Locale("vi", "VN"))
    }

    var isSelectionMode: Boolean = false
        private set
    private val selectedIds = mutableSetOf<Long>()

    fun enterSelectionMode(initialSelectedRecord: RiceRecord? = null) {
        isSelectionMode = true
        selectedIds.clear()
        initialSelectedRecord?.id?.let { selectedIds.add(it) }
        notifyDataSetChanged()
        onSelectionModeChanged(true)
        onSelectionCountChanged(selectedIds.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionModeChanged(false)
        onSelectionCountChanged(0)
    }

    fun toggleSelection(record: RiceRecord) {
        val id = record.id
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        notifyDataSetChanged()
        onSelectionCountChanged(selectedIds.size)
    }

    fun selectAll() {
        selectedIds.clear()
        currentList.forEach { record ->
            selectedIds.add(record.id)
        }
        notifyDataSetChanged()
        onSelectionCountChanged(selectedIds.size)
    }

    fun getSelectedRecords(): List<RiceRecord> {
        return currentList.filter { selectedIds.contains(it.id) }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardItem: View = itemView.findViewById(R.id.card_item)
        private val cbSelect: MaterialCheckBox = itemView.findViewById(R.id.cb_select)
        private val tvCustomerName: TextView = itemView.findViewById(R.id.tv_customer_name)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tv_date_time)
        private val tvGrandTotal: TextView = itemView.findViewById(R.id.tv_grand_total)
        private val tvTotalMoney: TextView = itemView.findViewById(R.id.tv_total_money)
        private val btnMenu: ImageButton = itemView.findViewById(R.id.btn_menu)
        
        fun bind(record: RiceRecord) {
            tvCustomerName.text = record.customerName.ifBlank { "Khách hàng" }
            val displayTime = if (record.updatedAt > 0L) record.updatedAt else record.createdAt
            tvDateTime.text = dateFormat.format(Date(displayTime))
            tvGrandTotal.text = "${weightFormat.format(record.grandTotal)} kg"
            tvTotalMoney.text = "${moneyFormat.format(record.totalMoney)} VNĐ"
            
            val isSelected = selectedIds.contains(record.id)

            if (isSelectionMode) {
                cbSelect.visibility = View.VISIBLE
                cbSelect.isChecked = isSelected
                btnMenu.visibility = View.GONE
                
                cardItem.setOnClickListener {
                    toggleSelection(record)
                }
                cbSelect.setOnClickListener {
                    toggleSelection(record)
                }
                cardItem.setOnLongClickListener(null)
            } else {
                cbSelect.visibility = View.GONE
                btnMenu.visibility = View.VISIBLE
                
                cardItem.setOnClickListener {
                    onItemClick(record)
                }
                
                cardItem.setOnLongClickListener {
                    enterSelectionMode(record)
                    true
                }
                
                btnMenu.setOnClickListener { view ->
                    showPopupMenu(view, record)
                }
            }
        }
        
        private fun showPopupMenu(view: View, record: RiceRecord) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_history_item, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_share_qr -> {
                        onShareQr(record)
                        true
                    }
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
