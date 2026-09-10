package com.dockeep.app.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager

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
        // handling does not reach it — and it must not. Taking a docked sheet
        // out of decor fitting and padding it by the insets made it grow by
        // the keyboard's height instead of sitting on top of it, leaving the
        // sheet stranded at the top of the screen over a blank gap.
        //
        // Letting the framework fit this window does the right thing on its
        // own: the sheet rests above the navigation bar, and above the
        // keyboard when one is up. The only thing given up is the background
        // bleeding behind the navigation bar, which is not worth a sheet that
        // misplaces itself the moment a field is focused.
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }
    return this
}
