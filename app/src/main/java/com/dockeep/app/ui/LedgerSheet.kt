package com.dockeep.app.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup

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
    }
    return this
}
