package com.dockeep.app.adapter

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.R
import com.dockeep.app.database.Document
import com.google.android.material.card.MaterialCardView

class DocumentAdapter(
    private val documents: List<Document>,
    private val onDocumentClick: (Document) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

    class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val placeholderCircle: TextView = itemView.findViewById(R.id.placeholderCircle)
        val titleText: TextView = itemView.findViewById(R.id.titleText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document, parent, false)
        return DocumentViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
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
        
        // Add 3D effect and animations
        holder.cardView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Press down animation
                    ObjectAnimator.ofFloat(holder.cardView, "scaleX", 0.95f).apply {
                        duration = 150
                        interpolator = DecelerateInterpolator()
                        start()
                    }
                    ObjectAnimator.ofFloat(holder.cardView, "scaleY", 0.95f).apply {
                        duration = 150
                        interpolator = DecelerateInterpolator()
                        start()
                    }
                    holder.cardView.cardElevation = holder.itemView.context.resources.getDimension(R.dimen.card_elevation_hover)
                }
                MotionEvent.ACTION_UP, 
                MotionEvent.ACTION_CANCEL -> {
                    // Release animation
                    ObjectAnimator.ofFloat(holder.cardView, "scaleX", 1.0f).apply {
                        duration = 150
                        interpolator = DecelerateInterpolator()
                        start()
                    }
                    ObjectAnimator.ofFloat(holder.cardView, "scaleY", 1.0f).apply {
                        duration = 150
                        interpolator = DecelerateInterpolator()
                        start()
                    }
                    holder.cardView.cardElevation = holder.itemView.context.resources.getDimension(R.dimen.card_elevation)
                    
                    // Manually call performClick when touch is released
                    if (event.action == MotionEvent.ACTION_UP) {
                        view.performClick()
                    }
                }
            }
            true // Consume touch event
        }
        
        // Set click listeners
        holder.cardView.setOnClickListener {
            // Add a subtle click animation
            ObjectAnimator.ofFloat(holder.cardView, "translationZ", 40f).apply {
                duration = 150
                interpolator = DecelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(holder.cardView, "translationZ", 0f).apply {
                duration = 150
                interpolator = DecelerateInterpolator()
                startDelay = 150
                start()
            }
            onDocumentClick(document)
        }
    }

    override fun getItemCount() = documents.size
}