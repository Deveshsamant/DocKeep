package com.dockeep.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Draws redaction boxes over a scan.
 *
 * A document you share is rarely one you want read in full: an account number,
 * a signature, an address. Dragging across the preview blacks that area out,
 * and the boxes are burned into the pixels on save — not stored as metadata a
 * viewer could peel off, which is the only kind of redaction worth having.
 *
 * Boxes are held in the preview's own coordinate space and reported back as
 * fractions of the image, so the same marks apply to the full-resolution file.
 */
class RedactionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Finished boxes, in view coordinates. */
    private val boxes = mutableListOf<RectF>()

    /** The box currently being dragged out, if any. */
    private var pending: RectF? = null

    private var startX = 0f
    private var startY = 0f

    /** The image's drawn bounds inside this view, set by the host. */
    private var imageBounds: RectF? = null

    var isActive: Boolean = false
        set(value) {
            field = value
            // Leaving the tool discards nothing; it just stops capturing.
            invalidate()
        }

    /** Raised whenever the number of boxes changes, for enabling Undo. */
    var onChanged: (Int) -> Unit = {}

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    /** Tells the overlay where the image actually sits inside the view. */
    fun setImageBounds(bounds: RectF) {
        imageBounds = bounds
        invalidate()
    }

    fun count(): Int = boxes.size

    fun undo() {
        if (boxes.isNotEmpty()) {
            boxes.removeAt(boxes.size - 1)
            onChanged(boxes.size)
            invalidate()
        }
    }

    fun clear() {
        if (boxes.isEmpty()) return
        boxes.clear()
        onChanged(0)
        invalidate()
    }

    /**
     * The boxes as fractions of the image, so they can be scaled onto the
     * full-resolution bitmap regardless of how large the preview was.
     */
    fun boxesAsFractions(): List<RectF> {
        val bounds = imageBounds ?: return emptyList()
        if (bounds.width() <= 0f || bounds.height() <= 0f) return emptyList()

        return boxes.map { box ->
            RectF(
                ((box.left - bounds.left) / bounds.width()).coerceIn(0f, 1f),
                ((box.top - bounds.top) / bounds.height()).coerceIn(0f, 1f),
                ((box.right - bounds.left) / bounds.width()).coerceIn(0f, 1f),
                ((box.bottom - bounds.top) / bounds.height()).coerceIn(0f, 1f)
            )
        }.filter { it.width() > 0.001f && it.height() > 0.001f }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isActive) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                pending = RectF(startX, startY, startX, startY)
                // The host may be a scroll container; keep the gesture here.
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                pending?.set(
                    minOf(startX, event.x),
                    minOf(startY, event.y),
                    maxOf(startX, event.x),
                    maxOf(startY, event.y)
                )
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val box = pending
                pending = null
                parent?.requestDisallowInterceptTouchEvent(false)

                // A tap is not a redaction; require a real drag.
                val min = 8f * resources.displayMetrics.density
                if (box != null && box.width() > min && box.height() > min) {
                    boxes.add(box)
                    onChanged(boxes.size)
                }
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in boxes) canvas.drawRect(box, fill)
        pending?.let { box ->
            canvas.drawRect(box, fill)
            // A light edge while dragging, so the box is visible against ink.
            canvas.drawRect(box, guide)
        }
    }
}
