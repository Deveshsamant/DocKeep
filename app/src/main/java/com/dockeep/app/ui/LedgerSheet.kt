package com.dockeep.app.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

/**
 * Docks a dialog to the bottom edge as a Ledger sheet.
 *
 * The design has no floating cards: a sheet is a full-width panel flush with
 * the bottom of the screen, closed by a 2px rule along its top edge. The
 * dialog window therefore loses its own background and inset so the layout's
 * own `ledger_sheet_background` is all that shows.
 *
 * Generic over [Dialog] because the screens mix `android.app.AlertDialog` and
 * AppCompat's, and the call site needs its own type back for chaining.
 */
fun <T : Dialog> T.dockAsLedgerSheet(): T {
    window?.let { window ->
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setGravity(Gravity.BOTTOM)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // A dialog carries its own window, so the activity's edge-to-edge
        // handling does not reach it. Take the navigation bar here instead:
        // the sheet's own background then runs to the very bottom of the
        // screen — which is the whole point of a docked sheet — while its
        // contents stay clear of the bar.
        //
        // Whether a dialog window is edge to edge by default varies by
        // release. Asking for it explicitly and then reading the inset works
        // out the same either way: where the window already fits the system
        // bars, the inset arrives as zero and nothing moves.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = window.decorView.findViewById<View>(android.R.id.content)
        content?.let { view ->
            val basePadding = view.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val bars = Edge.barsOf(insets)
                v.setPadding(
                    v.paddingLeft,
                    v.paddingTop,
                    v.paddingRight,
                    basePadding + bars.bottom
                )
                insets
            }
            ViewCompat.requestApplyInsets(view)
        }
    }
    return this
}
