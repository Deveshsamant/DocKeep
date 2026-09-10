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
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale

class DragDropDocumentAdapter(
    private var documents: MutableList<Document>,
    private val imageMap: Map<Long, String?>? = null,
    private val imageCountMap: Map<Long, Int>? = null,
    private val onDocumentClick: (Document) -> Unit,
    private val onDocumentMoved: (List<Document>) -> Unit,
    /** Raised when a long press starts a selection, or a tap changes one. */
    private val onSelectionChanged: (Set<Long>) -> Unit = {},
    /** Begins a reorder drag for the given holder. */
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {}
) : RecyclerView.Adapter<DragDropDocumentAdapter.DocumentViewHolder>() {

    // Add debounce mechanism to prevent multiple rapid clicks
    private var lastClickTime: Long = 0
    private val CLICK_DELAY: Long = 300 // Reduced delay for more responsive feel
    
    // Flag to prevent UI refresh during drag operations
    private var isDragging = false

    // Only the manual ordering may be dragged; see setReorderEnabled.
    private var reorderEnabled = true

    // Scan counts for the meta line, filled in once the images are queried.
    private var imageCounts: Map<Long, Int> = imageCountMap.orEmpty()

    // "12 Nov" — the design's short, tracked date on the cell meta line.
    private val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

    class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: View = itemView.findViewById(R.id.cardView)
        val placeholderCircle: TextView = itemView.findViewById(R.id.placeholderCircle)
        val titleText: TextView = itemView.findViewById(R.id.titleText)
        val metaText: TextView = itemView.findViewById(R.id.metaText)
        val tagText: TextView = itemView.findViewById(R.id.tagText)
        val selectionCheck: View = itemView.findViewById(R.id.selectionCheck)
    }

    // ── Selection ───────────────────────────────────────────────────────

    private var selectionMode = false
    private val selected = mutableSetOf<Long>()

    /** Tag names per document, for the chip line on each cell. */
    private var tagsByDocument: Map<Long, List<String>> = emptyMap()

    fun isSelectionMode(): Boolean = selectionMode

    fun selectedIds(): Set<Long> = selected.toSet()

    fun selectedDocuments(): List<Document> = documents.filter { it.id in selected }

    /** Leaves selection mode and clears the marks. */
    fun clearSelection() {
        if (!selectionMode && selected.isEmpty()) return
        selectionMode = false
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged(emptySet())
    }

    fun selectAll() {
        selectionMode = true
        selected.clear()
        selected.addAll(documents.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged(selectedIds())
    }

    private fun toggle(document: Document) {
        if (!selected.add(document.id)) selected.remove(document.id)
        if (selected.isEmpty()) {
            selectionMode = false
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds())
    }

    fun setTagsByDocument(tags: Map<Long, List<String>>) {
        tagsByDocument = tags
        if (!isDragging) notifyDataSetChanged()
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
        
        // The letter tile is the one saturated element on the cell. Tint the
        // square background directly rather than through a card.
        val colorScheme = ColorUtils.getPlaceholderColorScheme(holder.itemView.context, document.name)
        holder.placeholderCircle.background?.mutate()?.setColorFilter(
            colorScheme.backgroundColor,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        holder.placeholderCircle.setTextColor(colorScheme.textColor)

        // Meta line: "4 scans · 12 Nov".
        val context = holder.itemView.context
        val scanCount = imageCounts[document.id]
        val scans = when (scanCount) {
            null -> null
            1 -> context.getString(R.string.ledger_meta_scan_one)
            else -> context.getString(R.string.ledger_meta_scans, scanCount)
        }
        val updated = dateFormat.format(document.updatedAt)
        holder.metaText.text = if (scans == null) updated else "$scans · $updated"
        
        // Tags, when the document carries any.
        val tags = tagsByDocument[document.id].orEmpty()
        if (tags.isEmpty()) {
            holder.tagText.visibility = View.GONE
        } else {
            holder.tagText.visibility = View.VISIBLE
            holder.tagText.text = tags.joinToString("  ") { "#" + it.uppercase() }
        }

        // Selection marker.
        val isSelected = document.id in selected
        holder.selectionCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.cardView.setBackgroundResource(
            if (isSelected) R.drawable.ledger_cell_selected else R.drawable.ledger_cell
        )

        // Press feedback is the cell's own ripple. The previous scale and
        // translationZ animations cast a real elevation shadow, which is the
        // one thing this design does not have.
        holder.cardView.setOnClickListener {
            if (selectionMode) {
                toggle(document)
                return@setOnClickListener
            }
            // Debounce so a double tap cannot open the document twice.
            val now = System.currentTimeMillis()
            if (now - lastClickTime > CLICK_DELAY) {
                lastClickTime = now
                onDocumentClick(document)
            }
        }

        // Long press has two jobs, and selection state decides which:
        //   - out of selection mode it starts one, which is the gesture users
        //     reach for first;
        //   - inside it, holding an already-selected cell begins the reorder
        //     drag, so manual ordering is still reachable.
        holder.cardView.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                toggle(document)
            } else if (document.id in selected && reorderEnabled) {
                onStartDrag(holder)
            }
            true
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

    /**
     * Drag-to-reorder is only meaningful while the grid shows the manual
     * order. The derived orderings behind the Recent / By person / A–Z tabs
     * would fight any move the user made, so the callback consults this.
     */
    fun setReorderEnabled(enabled: Boolean) {
        reorderEnabled = enabled
    }

    fun isReorderEnabled(): Boolean = reorderEnabled

    /**
     * Supplies the per-document scan counts shown on the cell's meta line.
     * Counts arrive asynchronously, after the documents themselves, so this
     * refreshes the visible rows when they land.
     */
    fun setImageCounts(counts: Map<Long, Int>) {
        imageCounts = counts.toMap()
        if (!isDragging) {
            notifyDataSetChanged()
        }
    }
}