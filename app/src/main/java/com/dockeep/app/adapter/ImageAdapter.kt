package com.dockeep.app.adapter

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dockeep.app.R
import com.dockeep.app.database.DocumentImage
import com.google.android.material.button.MaterialButton
import java.io.File

class ImageAdapter(
    private val images: MutableList<DocumentImage>,
    private val onImageClick: (DocumentImage) -> Unit,
    private val onShareClick: (DocumentImage) -> Unit,
    private val onDownloadClick: (DocumentImage) -> Unit,
    private val onDeleteClick: (DocumentImage) -> Unit,
    private val onImageReordered: (List<DocumentImage>) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView as CardView
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val shareButton: MaterialButton = itemView.findViewById(R.id.shareButton)
        val downloadButton: MaterialButton = itemView.findViewById(R.id.downloadButton)
        val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)
    }
    
    // Flag to prevent UI refresh during drag operations
    private var isDragging = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        // Validate position
        if (position < 0 || position >= images.size) {
            return
        }
        
        val image = images[position]
        
        // Load image with optimization
        Glide.with(holder.imageView.context)
            .load(File(image.imagePath))
            .placeholder(R.drawable.ic_document_placeholder)
            .error(R.drawable.ic_document_placeholder)
            .override(400, 400) // Set a reasonable size to improve performance
            .centerCrop()
            .into(holder.imageView)
        
        // Add improved touch handling with better visual feedback
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
                    holder.cardView.cardElevation = 12f
                }
                MotionEvent.ACTION_UP, 
                MotionEvent.ACTION_CANCEL -> {
                    // Restore scale on touch release
                    holder.cardView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .setInterpolator(OvershootInterpolator(1.2f))
                        .start()
                    holder.cardView.cardElevation = 6f
                }
            }
            false
        }
        
        // Set click listeners with ultra-smooth animations
        holder.cardView.setOnClickListener {
            // Add an ultra-smooth click animation
            holder.cardView.animate()
                .translationZ(25f)
                .setDuration(80)
                .setInterpolator(OvershootInterpolator(1.2f))
                .withEndAction {
                    holder.cardView.animate()
                        .translationZ(0f)
                        .setDuration(120)
                        .setInterpolator(OvershootInterpolator(1.1f))
                        .start()
                }
                .start()
            onImageClick(image)
        }
        
        // Add long click listener for drag and drop with improved handling
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
        
        holder.shareButton.setOnClickListener {
            animateButtonPressUltraSmooth(it)
            onShareClick(image)
        }
        
        holder.downloadButton.setOnClickListener {
            animateButtonPressUltraSmooth(it)
            onDownloadClick(image)
        }
        
        holder.deleteButton.setOnClickListener {
            animateButtonPressUltraSmooth(it)
            onDeleteClick(image)
        }
    }

    private fun animateButtonPressUltraSmooth(view: View) {
        view.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(80)
            .setInterpolator(OvershootInterpolator(1.2f))
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(120)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .start()
            }
            .start()
    }

    override fun getItemCount() = images.size
    
    fun updateImages(newImages: List<DocumentImage>) {
        images.clear()
        // Sort images by order before adding them
        images.addAll(newImages.sortedBy { it.order })
        // Only notify if not dragging to prevent UI refresh during drag operations
        if (!isDragging) {
            notifyDataSetChanged()
        }
    }
    
    // Methods for drag and drop functionality
    fun moveItem(fromPosition: Int, toPosition: Int) {
        // Check if positions are valid
        if (fromPosition < 0 || toPosition < 0 || 
            fromPosition >= images.size || toPosition >= images.size) {
            return
        }
        
        // Don't perform move if positions are the same
        if (fromPosition == toPosition) {
            return
        }
        
        // Implement shifting behavior instead of swapping
        val item = images.removeAt(fromPosition)
        
        // Insert at the new position
        images.add(toPosition, item)
        
        // Update order values for all affected items
        for (i in minOf(fromPosition, toPosition)..maxOf(fromPosition, toPosition)) {
            if (i < images.size) {
                images[i] = images[i].copy(order = i)
            }
        }
        
        // Notify adapter about the move
        notifyItemMoved(fromPosition, toPosition)
        
        // Also notify about order changes in the affected range
        val start = minOf(fromPosition, toPosition)
        val count = kotlin.math.abs(toPosition - fromPosition) + 1
        notifyItemRangeChanged(start, count)
        
        // Pass a copy of the list to avoid ConcurrentModificationException
        onImageReordered(images.toList())
    }
    
    // Add a method to get the current list of images
    fun getCurrentImages(): List<DocumentImage> {
        return images.toList()
    }
    
    // Methods to control drag state
    fun setDragging(dragging: Boolean) {
        isDragging = dragging
    }
}