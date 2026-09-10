package com.dockeep.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.R

/**
 * Draws the Ledger grid.
 *
 * The design replaces every card shadow and radius with a 2px rule, so cells
 * sit flush against one another and the only separation is a hairline. This
 * decoration reserves that hairline between columns and rows and paints it,
 * which keeps the RecyclerView's own background free — otherwise the rule
 * colour would flood the empty area below a short list.
 *
 * The trailing rule after the final row is deliberate: it closes the grid the
 * same way the header rule opens it.
 */
class LedgerGridDecoration(
    context: Context,
    private val spanCount: Int
) : RecyclerView.ItemDecoration() {

    private val thickness: Int =
        (RULE_DP * context.resources.displayMetrics.density + 0.5f).toInt()

    private val paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.ledger_rule)
        style = Paint.Style.FILL
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        // Reserve the vertical rule for every cell except those in the last
        // column, so the grid stays flush with the screen edges.
        if (position % spanCount != spanCount - 1) {
            outRect.right = thickness
        }
        outRect.bottom = thickness
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue

            val params = child.layoutParams as RecyclerView.LayoutParams
            val left = child.left - params.leftMargin
            val right = child.right + params.rightMargin
            val top = child.top - params.topMargin
            val bottom = child.bottom + params.bottomMargin

            // Horizontal rule closing this row.
            canvas.drawRect(
                left.toFloat(),
                bottom.toFloat(),
                (right + thickness).toFloat(),
                (bottom + thickness).toFloat(),
                paint
            )

            // Vertical rule between columns.
            if (position % spanCount != spanCount - 1) {
                canvas.drawRect(
                    right.toFloat(),
                    top.toFloat(),
                    (right + thickness).toFloat(),
                    bottom.toFloat(),
                    paint
                )
            }
        }
    }

    private companion object {
        const val RULE_DP = 2f
    }
}
