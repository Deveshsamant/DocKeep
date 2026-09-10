package com.dockeep.app.utils

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Offers the user an update that Play would otherwise install in silence.
 *
 * Play's default is to auto-update over Wi-Fi with no notification at all, so
 * without this a user has no way to learn that a newer version exists — they
 * simply have it, eventually, whenever Play gets round to it. That is fine for
 * a cosmetic change and not fine for a fix someone is waiting on.
 *
 * The flow is deliberately the *flexible* one rather than immediate: this app
 * holds documents people may need at a border or a counter, and blocking the
 * vault behind a full-screen update would be indefensible. The download runs
 * in the background while the app stays usable, and the only thing ever asked
 * of the user is a restart once it has finished.
 *
 * None of this works on a build that did not come from Play — a sideloaded
 * APK, an emulator image, a debug run — where the API fails with an install
 * error. That is expected rather than exceptional, so failures are logged and
 * swallowed.
 */
class AppUpdates(
    private val activity: ComponentActivity,
    private val onReadyToInstall: () -> Unit
) : DefaultLifecycleObserver {

    private companion object {
        const val TAG = "AppUpdates"
    }

    private val manager = AppUpdateManagerFactory.create(activity)

    /**
     * Registered eagerly, because a result contract has to be in place before
     * the activity starts. The result itself is uninteresting: if the user
     * declines, the right response is to leave them alone.
     */
    private val flow = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    /** Guards against restarting the flow every time the activity resumes. */
    private var offered = false

    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) onReadyToInstall()
    }

    init {
        manager.registerListener(installListener)
        activity.lifecycle.addObserver(this)
    }

    /**
     * Asks Play whether a newer version exists, and starts the download if so.
     *
     * Also catches the case where a download finished while the app was in the
     * background: the install listener will not fire again for it, so the
     * prompt has to be raised from the current state instead.
     */
    override fun onResume(owner: LifecycleOwner) {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> onReadyToInstall()

                    !offered &&
                        info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                        offered = true
                        runCatching {
                            manager.startUpdateFlowForResult(
                                info,
                                flow,
                                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                            )
                        }.onFailure { Log.w(TAG, "Could not start the update flow", it) }
                    }
                }
            }
            .addOnFailureListener {
                // Overwhelmingly this is a build Play does not own: a debug
                // run, or an APK installed by hand. Nothing to tell the user.
                Log.d(TAG, "No update information available: ${it.message}")
            }
    }

    /** Restarts into the downloaded version. */
    fun install() {
        runCatching { manager.completeUpdate() }
            .onFailure { Log.w(TAG, "Could not complete the update", it) }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        manager.unregisterListener(installListener)
    }
}
