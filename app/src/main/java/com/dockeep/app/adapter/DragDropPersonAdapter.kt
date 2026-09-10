package com.dockeep.app.adapter

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.R
import com.dockeep.app.database.Person
import com.dockeep.app.database.Document
import com.dockeep.app.utils.ColorUtils
import java.util.Collections

class DragDropPersonAdapter(
    private var people: MutableList<Person>,
    private val onPersonClick: (Person) -> Unit,
    private val onPersonMoved: (List<Person>) -> Unit,
    private val onPersonDelete: (Person) -> Unit
) : RecyclerView.Adapter<DragDropPersonAdapter.PersonViewHolder>() {

    // Add debounce mechanism to prevent multiple rapid clicks
    private var lastClickTime: Long = 0
    private val CLICK_DELAY: Long = 300 // Reduced delay for more responsive feel
    
    // Flag to prevent UI refresh during drag operations
    private var isDragging = false

    class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: View = itemView.findViewById(R.id.cardView)
        val personInitialTextView: TextView = itemView.findViewById(R.id.personInitialTextView)
        val personNameTextView: TextView = itemView.findViewById(R.id.personNameTextView)
        val personMetaTextView: TextView = itemView.findViewById(R.id.personMetaTextView)
        val swatchRow: View = itemView.findViewById(R.id.personSwatchRow)
        val swatches: List<View> = listOf(
            itemView.findViewById(R.id.swatch1),
            itemView.findViewById(R.id.swatch2),
            itemView.findViewById(R.id.swatch3),
            itemView.findViewById(R.id.swatch4)
        )
        val swatchOverflow: TextView = itemView.findViewById(R.id.swatchOverflow)
    }

    /**
     * Documents grouped by person, used for the meta line and the colour
     * signature. Supplied by the activity once the document list loads.
     */
    private var documentsByPerson: Map<Long, List<Document>> = emptyMap()

    /** The id of the person who is the phone's owner, labelled "· you". */
    private var selfPersonId: Long? = null

    fun setDocumentsByPerson(grouped: Map<Long, List<Document>>) {
        documentsByPerson = grouped
        if (!isDragging) {
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person, parent, false)
        return PersonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        // Check if position is valid
        if (position < 0 || position >= people.size) {
            return
        }
        
        val person = people[position]
        
        // Set person name
        holder.personNameTextView.text = person.name
        
        // Set initial letter
        val initial = if (person.name.isNotEmpty()) {
            person.name.first().uppercaseChar().toString()
        } else {
            holder.itemView.context.getString(R.string.document_initial_placeholder)
        }
        holder.personInitialTextView.text = initial
        
        // Set consistent background and text color for placeholder based on person name
        val context = holder.itemView.context
        val colorScheme = ColorUtils.getPlaceholderColorScheme(context, person.name)
        holder.personInitialTextView.background?.mutate()?.setColorFilter(
            colorScheme.backgroundColor,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        holder.personInitialTextView.setTextColor(colorScheme.textColor)

        // Meta: how many documents sit on this shelf.
        val docs = documentsByPerson[person.id].orEmpty()
        holder.personMetaTextView.text = when {
            docs.size == 1 -> context.getString(R.string.ledger_document_count_one)
            else -> context.getString(R.string.ledger_documents_count, docs.size)
        }

        // Colour signature: the letter-tile hue of the first four documents,
        // then "+N" for the remainder.
        if (docs.isEmpty()) {
            holder.swatchRow.visibility = View.GONE
        } else {
            holder.swatchRow.visibility = View.VISIBLE
            holder.swatches.forEachIndexed { index, swatch ->
                val doc = docs.getOrNull(index)
                if (doc == null) {
                    swatch.visibility = View.GONE
                } else {
                    swatch.visibility = View.VISIBLE
                    val hue = ColorUtils.getPlaceholderColorScheme(context, doc.name)
                    swatch.background?.mutate()?.setColorFilter(
                        hue.backgroundColor,
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }
            }
            val remaining = docs.size - holder.swatches.size
            if (remaining > 0) {
                holder.swatchOverflow.visibility = View.VISIBLE
                holder.swatchOverflow.text = context.getString(R.string.ledger_plus_n, remaining)
            } else {
                holder.swatchOverflow.visibility = View.GONE
            }
        }
        
        // Set click listeners with ultra-smooth animations
        holder.cardView.setOnClickListener {
            // Debounce clicks to prevent multiple rapid openings
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > CLICK_DELAY) {
                lastClickTime = currentTime
                
                // Press feedback is the cell's ripple; this design has no
                // elevation for a card to rise into.
                onPersonClick(person)
            }
        }
        
        // Set long click listener for drag and drop
        holder.cardView.setOnLongClickListener {
            // Long press will be handled by ItemTouchHelper for drag and drop
            // Return false to indicate we're not consuming the event here
            // The ItemTouchHelper will handle the drag operation
            false
        }
    }

    override fun getItemCount() = people.size

    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        // Check bounds to prevent IndexOutOfBoundsException
        if (fromPosition < 0 || toPosition < 0 || 
            fromPosition >= people.size || toPosition >= people.size) {
            return false
        }
        
        // Don't perform move if positions are the same
        if (fromPosition == toPosition) {
            return false
        }
        
        try {
            // Implement shifting behavior instead of swapping
            val person = people.removeAt(fromPosition)
            
            // Insert at the new position
            people.add(toPosition, person)
            
            // Update order values for all affected people
            for (i in minOf(fromPosition, toPosition)..maxOf(fromPosition, toPosition)) {
                if (i < people.size) {
                    people[i] = people[i].copy(order = i)
                }
            }
            
            notifyItemMoved(fromPosition, toPosition)
            
            // Also notify about order changes in the affected range
            val start = minOf(fromPosition, toPosition)
            val count = kotlin.math.abs(toPosition - fromPosition) + 1
            notifyItemRangeChanged(start, count)
            
            // Pass a copy of the list to avoid ConcurrentModificationException
            onPersonMoved(people.toList())
            return true
        } catch (e: Exception) {
            // Log the exception or handle it appropriately
            return false
        }
    }

    fun updatePeople(newPeople: List<Person>) {
        // Create a new list to avoid reference issues
        people = newPeople.toMutableList()
        // Only notify if not dragging to prevent UI refresh during drag operations
        if (!isDragging) {
            notifyDataSetChanged()
        }
    }
    
    // Add a method to get the current list of people
    fun getCurrentPeople(): List<Person> {
        return people.toList()
    }
    
    // Methods to control drag state
    fun setDragging(dragging: Boolean) {
        isDragging = dragging
    }
}