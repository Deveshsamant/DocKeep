package com.dockeep.app.adapter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.sign

class DocumentItemTouchHelperCallback(
    private val adapter: DragDropDocumentAdapter
) : ItemTouchHelper.Callback() {

    // Adjust the move threshold to prevent quick drops when dragging over other cards
    override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return 0.2f // Lower threshold for more sensitive movement
    }
    
    // Adjust animation duration for smoother movement
    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float
    ): Long {
        return 200 // Slightly longer for smoother completion
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        val swipeFlags = 0 // No swipe gestures
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition
        
        // Move item immediately without delay
        if (fromPosition != toPosition && fromPosition >= 0 && toPosition >= 0) {
            return adapter.moveItem(fromPosition, toPosition)
        }
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No swipe functionality
    }

    override fun isLongPressDragEnabled(): Boolean {
        // The adapter owns the long press: it starts a selection, or begins a
        // drag itself via startDrag when a selected cell is held. Letting the
        // touch helper also claim the gesture would make the two race.
        return false
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        // Disable swipe
        return false
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            // Set dragging flag to prevent UI refresh during drag operations
            adapter.setDragging(true)
            
            if (viewHolder is DragDropDocumentAdapter.DocumentViewHolder) {
                // The dragged cell dims rather than lifting: this design has
                // no elevation to raise it into.
                viewHolder.cardView.apply {
                    animate()
                        .alpha(0.7f)
                        .setDuration(120)
                        .start()
                }
                
                // Add haptic feedback for drag start
                performHapticFeedback(viewHolder.itemView, true)
            }
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder is DragDropDocumentAdapter.DocumentViewHolder) {
            viewHolder.cardView.apply {
                animate()
                    .alpha(1.0f)
                    .setDuration(160)
                    .start()
            }
            
            // Add haptic feedback for drag end
            performHapticFeedback(viewHolder.itemView, false)
        }
        
        // Clear dragging flag to allow UI refresh after drag operations
        adapter.setDragging(false)
    }
    
    // Override this to improve drag smoothness and scrolling behavior for long-distance moves
    override fun interpolateOutOfBoundsScroll(
        recyclerView: RecyclerView,
        viewSize: Int,
        viewSizeOutOfBounds: Int,
        totalSize: Int,
        msSinceStartScroll: Long
    ): Int {
        val direction = sign(viewSizeOutOfBounds.toFloat()).toInt()
        val outOfBounds = abs(viewSizeOutOfBounds)
        // Increase the scroll area and speed for better long-distance dragging
        val cappedOutBounds = kotlin.math.min(outOfBounds, 300) // Extended scroll zone
        // Use a more aggressive time ratio for faster scrolling
        val timeRatio = kotlin.math.min(msSinceStartScroll / 800f, 1f) // Faster acceleration
        // Increase scroll speed for long-distance moves
        return (direction * cappedOutBounds * timeRatio * 50).toInt() // Increased speed
    }
    
    // Improve drag smoothness with magnetic snapping effect
    override fun onChildDraw(
        c: android.graphics.Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            // Apply smoother movement during drag with magnetic snapping effect
            val adjustedDx = dX * 0.9f // Reduced friction for smoother movement
            val adjustedDy = dY * 0.9f
            
            super.onChildDraw(c, recyclerView, viewHolder, adjustedDx, adjustedDy, actionState, isCurrentlyActive)
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
    
    // Helper method for haptic feedback
    private fun performHapticFeedback(view: View, isStart: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = view.context.getSystemService(Vibrator::class.java)
                if (isStart) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (e: Exception) {
            // Ignore if haptic feedback is not available
        }
    }
}