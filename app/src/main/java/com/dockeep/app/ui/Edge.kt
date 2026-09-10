package com.dockeep.app.ui

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Edge-to-edge plumbing.
 *
 * From Android 15, an app targeting API 35 or higher always draws behind the
 * status and navigation bars, and `android:statusBarColor` /
 * `navigationBarColor` are ignored outright. On Android 16 there is no opt-out
 * left at all. Nothing in the Ledger design wants to sit underneath a system
 * bar, so every screen keeps the layout it has and simply takes the bars as
 * padding.
 *
 * The same path runs on older releases rather than leaving two behaviours to
 * keep in step, and it costs nothing to look at: the bars turn transparent over
 * the same flat `ledger_bg` the theme was painting them with before.
 *
 * Insets are applied in a listener rather than read once, because they change
 * under the app — rotation, a three-button bar swapped for gestures, a cutout
 * coming into play in landscape.
 */
object Edge {

    /** Bars and cutout together; a cutout can exceed the bar in landscape. */
    private val TYPES =
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

    /**
     * The keyboard, which has to be handled here too.
     *
     * Taking the window out of decor-fitting is what turns off the automatic
     * resize for the IME, so a screen with a text field will happily let the
     * keyboard bury it. Every screen that takes its bars as padding therefore
     * has to take the keyboard as padding as well.
     */
    private val IME = WindowInsetsCompat.Type.ime()

    /**
     * Lays [root] out edge to edge, taking the system bars as padding.
     *
     * Pass [bottomBars] for a screen whose foot is a full-bleed container: each
     * one keeps the bottom edge, so its background continues behind the
     * navigation bar while its contents stay above it, and the root takes no
     * bottom padding of its own. With none named the root takes the bottom
     * inset and the screen simply stops short of the bar.
     *
     * More than one is needed where panels overlap — Home has both the bottom
     * navigation and a full-height settings drawer, and each reaches the
     * bottom edge on its own.
     */
    fun fit(activity: Activity, root: View, vararg bottomBars: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        paintBarIcons(activity, root, light = !activity.isNightMode())

        // Captured once: the listener runs again on rotation and on a
        // gesture/three-button switch, and reading it each time would add the
        // inset to a padding that already contains it.
        val basePadding = bottomBars.map { it.paddingBottom }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(TYPES)
            val keyboard = insets.getInsets(IME).bottom

            // With the keyboard up it covers the navigation bar, so the bar
            // inset is spent and the whole screen — foot bars included — lifts
            // to sit on top of the keyboard instead.
            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                when {
                    keyboard > 0 -> keyboard
                    bottomBars.isEmpty() -> bars.bottom
                    else -> 0
                }
            )
            bottomBars.forEachIndexed { i, bar ->
                bar.updateBottomPadding(
                    basePadding[i] + if (keyboard > 0) 0 else bars.bottom
                )
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /**
     * The viewer's variant: the scan itself stays full-bleed and only the
     * chrome is inset, so a page is still read against the whole screen.
     *
     * Its bars are always light-on-dark — that screen is dark on both themes.
     */
    fun fitChrome(activity: Activity, root: View, topBar: View, bottomBar: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        paintBarIcons(activity, root, light = false)

        val topBase = topBar.paddingTop
        val bottomBase = bottomBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(TYPES)
            topBar.updateTopPadding(topBase + bars.top)
            bottomBar.updateBottomPadding(bottomBase + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /**
     * Sets the status and navigation bar icons to contrast with the page.
     *
     * Without this the light theme draws white icons onto white paper, which
     * is how a transparent status bar usually goes wrong.
     */
    private fun paintBarIcons(activity: Activity, root: View, light: Boolean) {
        WindowInsetsControllerCompat(activity.window, root).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun Activity.isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun View.updateTopPadding(top: Int) =
        setPadding(paddingLeft, top, paddingRight, paddingBottom)

    private fun View.updateBottomPadding(bottom: Int) =
        setPadding(paddingLeft, paddingTop, paddingRight, bottom)

    /** Kept for callers that only need the raw values. */
    fun barsOf(insets: WindowInsetsCompat): Insets = insets.getInsets(TYPES)

    /** The keyboard's height, or 0 when it is down. */
    fun imeOf(insets: WindowInsetsCompat): Int = insets.getInsets(IME).bottom
}
