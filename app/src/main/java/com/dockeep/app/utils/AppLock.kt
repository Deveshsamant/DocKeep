package com.dockeep.app.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * The vault's lock — policy only.
 *
 * A document vault that opens because someone picked the phone up is not a
 * vault. This object holds whether the lock is armed and when it was last
 * satisfied; the prompt itself belongs to the screen being guarded.
 *
 * Deliberately holds no "currently prompting" flag. An earlier version did,
 * and because this is a singleton the flag outlived the activity that set it:
 * the device-credential fallback launches a separate system activity, and if
 * the host was recreated on the way back the callback that would have cleared
 * the flag was already gone. Every later check then short-circuited and the
 * lock screen stayed up for good. Anything that tracks an in-flight prompt has
 * to die with the activity, so it lives there instead.
 */
object AppLock {

    private const val PREFS = "app_prefs"
    private const val KEY_ENABLED = "app_lock_enabled"

    /**
     * How long the app may sit in the background before it re-locks.
     *
     * Long enough to step out to the camera, the system photo picker or the
     * share sheet and come back without being asked again.
     */
    private const val GRACE_MS = 30_000L

    /** Biometrics or the device PIN: a phone with no fingerprint still has one. */
    const val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    @Volatile
    private var unlockedAt = 0L

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        // Arming the lock should not immediately lock the user out of the
        // screen they armed it from.
        if (enabled) markUnlocked()
    }

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun markUnlocked() {
        unlockedAt = System.currentTimeMillis()
    }

    /** Forces the next guarded screen to ask again. */
    fun invalidate() {
        unlockedAt = 0L
    }

    fun isUnlocked(): Boolean =
        System.currentTimeMillis() - unlockedAt < GRACE_MS

    /**
     * True when the given screen must ask before showing anything.
     *
     * Fails open when the lock is armed but the device can no longer
     * authenticate — an enrolled fingerprint was removed, say. The alternative
     * is a vault nobody can get into.
     */
    fun shouldChallenge(context: Context): Boolean {
        if (!isEnabled(context)) return false
        if (isUnlocked()) return false
        if (!canAuthenticate(context)) {
            markUnlocked()
            return false
        }
        return true
    }
}
