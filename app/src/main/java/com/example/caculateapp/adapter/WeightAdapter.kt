package com.example.caculateapp.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.RecyclerView
import com.example.caculateapp.databinding.ItemWeightBinding

/**
 * RecyclerView Adapter for weight input cells
 * Handles EditText focus, TextWatcher management, and prevents data loss on scroll
 */
class WeightAdapter(
    private val weights: MutableList<Double>,
    private val onWeightChanged: (position: Int, weight: Double) -> Unit
) : RecyclerView.Adapter<WeightAdapter.WeightViewHolder>() {
    
    private var isLocked: Boolean = false
    
    inner class WeightViewHolder(
        private val binding: ItemWeightBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var textWatcher: TextWatcher? = null
        
        fun bind(position: Int, weight: Double) {
            // Remove previous TextWatcher to prevent duplicate listeners
            textWatcher?.let {
                binding.editWeight.removeTextChangedListener(it)
            }
            
            // Set the weight value
            val currentText = binding.editWeight.text.toString()
            val weightText = if (weight > 0.0) String.format("%.2f", weight) else ""
            
            // Only update if text is different to prevent cursor jumping
            if (currentText != weightText) {
                binding.editWeight.setText(weightText)
            }
            
            // Set enabled state based on lock mode
            binding.editWeight.isEnabled = !isLocked
            
            // Create new TextWatcher
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString() ?: ""
                    val newWeight = text.toDoubleOrNull() ?: 0.0
                    
                    // Update the backing list
                    if (position in weights.indices) {
                        weights[position] = newWeight
                    }
                    
                    // Notify ViewModel about the change
                    onWeightChanged(position, newWeight)
                }
            }
            
            // Add the new TextWatcher
            binding.editWeight.addTextChangedListener(textWatcher)
            
            // Handle IME action "Next" to move to next cell
            binding.editWeight.setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || 
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    
                    // Calculate next position
                    // Since we have 5 rows and horizontal scrolling,
                    // next item is position + 1
                    val nextPosition = position + 1
                    
                    if (nextPosition < itemCount) {
                        // Find the RecyclerView and scroll to next item
                        val recyclerView = binding.root.parent as? RecyclerView
                        recyclerView?.smoothScrollToPosition(nextPosition)
                        
                        // Request focus on next item after a short delay
                        binding.root.postDelayed({
                            recyclerView?.findViewHolderForAdapterPosition(nextPosition)?.let { holder ->
                                (holder as? WeightViewHolder)?.binding?.editWeight?.requestFocus()
                            }
                        }, 100)
                    }
                    true
                } else {
                    false
                }
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeightViewHolder {
        val binding = ItemWeightBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WeightViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: WeightViewHolder, position: Int) {
        holder.bind(position, weights[position])
    }
    
    override fun getItemCount(): Int = weights.size
    
    /**
     * Update the entire weight list
     * Called when ViewModel updates the data
     */
    fun updateWeights(newWeights: List<Double>) {
        weights.clear()
        weights.addAll(newWeights)
        notifyDataSetChanged()
    }
    
    /**
     * Set lock mode to prevent/allow editing
     */
    fun setLocked(locked: Boolean) {
        isLocked = locked
        notifyDataSetChanged() // Refresh all views to update enabled state
    }
}
