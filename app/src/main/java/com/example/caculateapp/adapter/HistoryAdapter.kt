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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for History screen RecyclerView
 * Displays list of saved weighing sessions with click and menu actions
 */
class HistoryAdapter(
    private val onItemClick: (RiceRecord) -> Unit,
    private val onDelete: (RiceRecord) -> Unit,
    private val onExport: (RiceRecord) -> Unit
) : ListAdapter<RiceRecord, HistoryAdapter.HistoryViewHolder>(DiffCallback()) {
    
    companion object {
        private const val DATE_FORMAT_PATTERN = "dd/MM/yyyy HH:mm"
        private val dateFormat = SimpleDateFormat("hh:mm a dd/MM/yyyy", Locale.getDefault())
        private val weightFormat = NumberFormat.getInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        private val moneyFormat = NumberFormat.getInstance(Locale("vi", "VN"))
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
        private val tvCustomerName: TextView = itemView.findViewById(R.id.tv_customer_name)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tv_date_time)
        private val tvGrandTotal: TextView = itemView.findViewById(R.id.tv_grand_total)
        private val tvTotalMoney: TextView = itemView.findViewById(R.id.tv_total_money)
        private val btnMenu: ImageButton = itemView.findViewById(R.id.btn_menu)
        
        fun bind(record: RiceRecord) {
            // Customer name (fallback to default if empty)
            tvCustomerName.text = record.customerName.ifBlank { "Khách hàng" }
            
            tvDateTime.text = dateFormat.format(record.createdAt ?: Date())
            tvGrandTotal.text = "${weightFormat.format(record.grandTotal)} kg"
            tvTotalMoney.text = "${moneyFormat.format(record.totalMoney.toLong())} VNĐ"
            
            // Card click -> navigate to edit screen
            cardItem.setOnClickListener {
                onItemClick(record)
            }
            
            // Menu button click -> show popup
            btnMenu.setOnClickListener { view ->
                showPopupMenu(view, record)
            }
        }
        
        /**
         * Show popup menu with Export and Delete options
         */
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
    
    /**
     * DiffUtil callback for efficient list updates
     */
    class DiffCallback : DiffUtil.ItemCallback<RiceRecord>() {
        override fun areItemsTheSame(oldItem: RiceRecord, newItem: RiceRecord): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: RiceRecord, newItem: RiceRecord): Boolean {
            return oldItem == newItem
        }
    }
}
