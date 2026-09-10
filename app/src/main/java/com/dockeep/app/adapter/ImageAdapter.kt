package com.dockeep.app.adapter

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dockeep.app.R
import com.dockeep.app.database.DocumentImage
import java.io.File
import java.util.Locale

class ImageAdapter(
    private val images: MutableList<DocumentImage>,
    private val onImageClick: (DocumentImage) -> Unit,
    private val onShareClick: (DocumentImage) -> Unit,
    private val onDownloadClick: (DocumentImage) -> Unit,
    private val onDeleteClick: (DocumentImage) -> Unit,
    private val onImageReordered: (List<DocumentImage>) -> Unit,
    private val onEditClick: (DocumentImage) -> Unit = {},
    private val onPdfClick: (DocumentImage) -> Unit = {},
    private val onSharePdfClick: (DocumentImage) -> Unit = {},
    private val onReadTextClick: (DocumentImage) -> Unit = {}
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: View = itemView
        val imageView: ImageView? = itemView.findViewById(R.id.imageView)
        val textBody: TextView? = itemView.findViewById(R.id.textBlockBody)
        val captionText: TextView = itemView.findViewById(R.id.captionText)
        val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
    }

    override fun getItemViewType(position: Int): Int =
        if (images.getOrNull(position)?.isText == true) TYPE_TEXT else TYPE_IMAGE
    
    // Flag to prevent UI refresh during drag operations
    private var isDragging = false

    private companion object {
        const val TYPE_IMAGE = 0
        const val TYPE_TEXT = 1

        const val MENU_EDIT = 0
        const val MENU_PDF = 4
        const val MENU_SHARE_PDF = 5
        const val MENU_READ_TEXT = 6
        const val MENU_SHARE = 1
        const val MENU_DOWNLOAD = 2
        const val MENU_DELETE = 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val layout = if (viewType == TYPE_TEXT) R.layout.item_text_block else R.layout.item_image
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        // Validate position
        if (position < 0 || position >= images.size) {
            return
        }
        
        val image = images[position]
        
        if (image.isText) {
            holder.textBody?.text = image.text.orEmpty()
        } else {
            holder.imageView?.let { view ->
                Glide.with(view.context)
                    .load(File(image.imagePath))
                    .placeholder(R.drawable.ic_document_placeholder)
                    .error(R.drawable.ic_document_placeholder)
                    .override(400, 400)
                    // The file is rewritten in place after an edit, so the
                    // path alone is not a safe cache key.
                    .signature(
                        com.bumptech.glide.signature.ObjectKey(
                            File(image.imagePath).lastModified()
                        )
                    )
                    .centerCrop()
                    .into(view)
            }
        }
        
        // Caption: the block's ordinal, as the design numbers them.
        val ordinal = String.format(Locale.getDefault(), "%03d", position + 1)
        holder.captionText.text = if (image.isText) {
            holder.itemView.context.getString(R.string.ledger_caption_note, ordinal)
        } else {
            ordinal
        }

        // Press feedback is the cell's ripple; the previous scale and
        // translationZ animations raised a shadow this design does not use.
        holder.cardView.setOnClickListener { onImageClick(image) }

        // Long press starts the reorder drag.
        holder.cardView.setOnLongClickListener { false }

        // One overflow replaces the three buttons that used to sit on top of
        // the thumbnail, so the scan itself is never covered.
        holder.overflowButton.setOnClickListener { anchor ->
            val context = anchor.context
            val popup = PopupMenu(context, anchor)
            popup.menu.add(0, MENU_EDIT, 0, context.getString(R.string.edit))
            if (image.isImage) {
                popup.menu.add(0, MENU_PDF, 1, context.getString(R.string.ledger_save_as_pdf))
                popup.menu.add(0, MENU_SHARE_PDF, 2, context.getString(R.string.ledger_share_this_pdf))
                popup.menu.add(0, MENU_SHARE, 3, context.getString(R.string.share))
                popup.menu.add(0, MENU_DOWNLOAD, 4, context.getString(R.string.ledger_save_to_gallery))
                popup.menu.add(0, MENU_READ_TEXT, 5, context.getString(R.string.ledger_ocr_action))
            }
            popup.menu.add(0, MENU_DELETE, 6, context.getString(R.string.delete))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT -> { onEditClick(image); true }
                    MENU_PDF -> { onPdfClick(image); true }
                    MENU_SHARE_PDF -> { onSharePdfClick(image); true }
                    MENU_READ_TEXT -> { onReadTextClick(image); true }
                    MENU_SHARE -> { onShareClick(image); true }
                    MENU_DOWNLOAD -> { onDownloadClick(image); true }
                    MENU_DELETE -> { onDeleteClick(image); true }
                    else -> false
                }
            }
            popup.show()
        }
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
        
        // Renumber the whole list, not just the span that moved. Blocks are
        // created with orders starting at 1 while this numbered from 0, so
        // renumbering a range left the untouched blocks on a different scale
        // and their orders could collide — which made a drag appear to land
        // in the wrong place, or snap back.
        for (i in images.indices) {
            images[i] = images[i].copy(order = i)
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