package com.dockeep.app.ui

import android.app.Activity
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import androidx.core.content.res.ResourcesCompat
import android.content.res.ColorStateList
import com.dockeep.app.R

/**
 * Marks the active tab in the shared bottom navigation bar.
 *
 * `partial_ledger_bottom_nav` is included by every top-level screen, so each
 * one declares which tab it owns and this applies the design's three signals
 * at once: the label goes Bold and full-contrast, the icon loses its muted
 * tint, and the short accent underline appears.
 */
object LedgerNav {

    enum class Tab { DOCS, PEOPLE, YOU }

    fun markActive(activity: Activity, active: Tab) {
        apply(activity, Tab.DOCS, active, R.id.navDocsLabel, R.id.navDocsIcon, R.id.navDocsUnderline)
        apply(activity, Tab.PEOPLE, active, R.id.navPeopleLabel, R.id.navPeopleIcon, R.id.navPeopleUnderline)
        apply(activity, Tab.YOU, active, R.id.navYouLabel, R.id.navYouIcon, R.id.navYouUnderline)
    }

    private fun apply(
        activity: Activity,
        tab: Tab,
        active: Tab,
        labelId: Int,
        iconId: Int,
        underlineId: Int
    ) {
        val label: TextView = activity.findViewById(labelId) ?: return
        val icon: ImageView = activity.findViewById(iconId) ?: return
        val underline: View = activity.findViewById(underlineId) ?: return

        val isActive = tab == active

        TextViewCompat.setTextAppearance(
            label,
            if (isActive) R.style.TextAppearance_Ledger_TabActive
            else R.style.TextAppearance_Ledger_Tab
        )

        // setTextAppearance does not re-apply fontFamily on every API level,
        // so the weight is set explicitly.
        label.typeface = ResourcesCompat.getFont(
            activity,
            if (isActive) R.font.archivo_bold else R.font.archivo_medium
        )

        val tint = ContextCompat.getColor(
            activity,
            if (isActive) R.color.ledger_text else R.color.ledger_text_secondary
        )
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(tint))

        underline.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
    }
}
