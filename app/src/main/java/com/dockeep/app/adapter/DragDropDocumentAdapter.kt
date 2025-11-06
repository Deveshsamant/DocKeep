package com.dockeep.app.adapter

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.R
import com.dockeep.app.database.Document
import com.dockeep.app.utils.ColorUtils
import com.google.android.material.card.MaterialCardView
import java.util.Collections

class DragDropDocumentAdapter(
    private var documents: MutableList<Document>,
    private val imageMap: Map<Long, String?>? = null,
    private val imageCountMap: Map<Long, Int>? = null,
    private val onDocumentClick: (Document) -> Unit,
    private val onDocumentMoved: (List<Document>) -> Unit
) : RecyclerView.Adapter<DragDropDocumentAdapter.DocumentViewHolder>() {

    // Add debounce mechanism to prevent multiple rapid clicks
    private var lastClickTime: Long = 0
    private val CLICK_DELAY: Long = 300 // Reduced delay for more responsive feel
    
    // Flag to prevent UI refresh during drag operations
    private var isDragging = false

    class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val placeholderCircle: TextView = itemView.findViewById(R.id.placeholderCircle)
        val titleText: TextView = itemView.findViewById(R.id.titleText)
        val imageContainerCard: MaterialCardView = itemView.findViewById(R.id.imageContainerCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document, parent, false)
        return DocumentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        // Check if position is valid
        if (position < 0 || position >= documents.size) {
            return
        }
        
        val document = documents[position]
        
        // Set document title in all caps
        holder.titleText.text = document.name.uppercase()
        
        // Set placeholder circle with first letter
        val firstLetter = if (document.name.isNotEmpty()) {
            document.name.first().uppercaseChar().toString()
        } else {
            holder.itemView.context.getString(R.string.placeholder_default_letter)
        }
        holder.placeholderCircle.text = firstLetter
        
        // Set consistent background and text color for placeholder based on document name
        val colorScheme = ColorUtils.getPlaceholderColorScheme(holder.itemView.context, document.name)
        holder.imageContainerCard.setCardBackgroundColor(colorScheme.backgroundColor)
        holder.placeholderCircle.setTextColor(colorScheme.textColor)
        
        // Set touch listener for better touch handling
        holder.cardView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Immediate visual feedback on touch
                    holder.cardView.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .setDuration(50)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Restore scale on touch release
                    holder.cardView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .setInterpolator(OvershootInterpolator(1.2f))
                        .start()
                }
            }
            false // Don't consume the touch event
        }
        
        // Set click listeners with ultra-smooth animations
        holder.cardView.setOnClickListener {
            // Debounce clicks to prevent multiple rapid openings
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > CLICK_DELAY) {
                lastClickTime = currentTime
                
                // Add an ultra-smooth click animation
                ObjectAnimator.ofFloat(holder.cardView, "translationZ", 45f).apply {
                    duration = 80
                    interpolator = OvershootInterpolator(1.2f)
                    start()
                }
                ObjectAnimator.ofFloat(holder.cardView, "translationZ", 0f).apply {
                    duration = 120
                    interpolator = OvershootInterpolator(1.1f)
                    startDelay = 80
                    start()
                }
                onDocumentClick(document)
            }
        }
        
        // Set long click listener for drag and drop with improved handling
        holder.cardView.setOnLongClickListener {
            // Add visual feedback for long press
            holder.cardView.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(100)
                .setInterpolator(OvershootInterpolator(1.1f))
                .withEndAction {
                    holder.cardView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .setInterpolator(OvershootInterpolator(1.1f))
                        .start()
                }
                .start()
            // Return false to indicate we're not consuming the event here
            // The ItemTouchHelper will handle the drag operation
            false
        }
    }

    override fun getItemCount() = documents.size

    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        // Check bounds to prevent IndexOutOfBoundsException
        if (fromPosition < 0 || toPosition < 0 || 
            fromPosition >= documents.size || toPosition >= documents.size) {
            return false
        }
        
        // Don't perform move if positions are the same
        if (fromPosition == toPosition) {
            return false
        }
        
        try {
            // Implement shifting behavior instead of swapping
            val document = documents.removeAt(fromPosition)
            
            // Insert at the new position
            documents.add(toPosition, document)
            
            // Update order values for all affected documents
            for (i in minOf(fromPosition, toPosition)..maxOf(fromPosition, toPosition)) {
                if (i < documents.size) {
                    documents[i] = documents[i].copy(order = i)
                }
            }
            
            notifyItemMoved(fromPosition, toPosition)
            
            // Also notify about order changes in the affected range
            val start = minOf(fromPosition, toPosition)
            val count = kotlin.math.abs(toPosition - fromPosition) + 1
            notifyItemRangeChanged(start, count)
            
            // Pass a copy of the list to avoid ConcurrentModificationException
            onDocumentMoved(documents.toList())
            return true
        } catch (e: Exception) {
            // Log the exception or handle it appropriately
            return false
        }
    }

    fun updateDocuments(newDocuments: List<Document>) {
        // Create a new list to avoid reference issues
        documents = newDocuments.toMutableList()
        // Only notify if not dragging to prevent UI refresh during drag operations
        if (!isDragging) {
            notifyDataSetChanged()
        }
    }
    
    // Add a method to get the current list of documents
    fun getCurrentDocuments(): List<Document> {
        return documents.toList()
    }
    
    // Methods to control drag state
    fun setDragging(dragging: Boolean) {
        isDragging = dragging
    }
}