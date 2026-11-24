// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Runs [StyleApplier] off the main thread, one apply at a time. */
object ApplyWorker {
    private val executor = Executors.newSingleThreadExecutor()

    fun apply(context: Context, done: () -> Unit = {}) {
        val app = context.applicationContext
        executor.execute {
            try {
                StyleApplier.apply(app)
            } finally {
                done()
            }
        }
    }
}

/**
 * Applies the style at boot and when the user unlocks with no prior apply: SystemUI and Launcher3
 * then start with the right look, and first boot records the build defaults.
 *
 * BOOT_COMPLETED alone is not enough on first boot / factory reset: the receiver can race package
 * scanning, and during Setup Wizard the launcher/taskbar often starts before (or instead of) a
 * successful apply. [SetupCompleteJob] runs a full apply once `user_setup_complete` flips so the
 * system bar shows when home becomes Launcher3, without needing Settings > UI style.
 *
 * [ACTION_USER_UNLOCKED] only applies when [StyleApplier.APPLIED_SETTING] is still empty, so
 * normal unlocks do not restart the launcher.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_USER_UNLOCKED) {
            return
        }
        val result = goAsync()
        val app = context.applicationContext
        Thread {
                try {
                    val applied =
                        Settings.Secure.getString(app.contentResolver, StyleApplier.APPLIED_SETTING)
                    val needsApply =
                        action == Intent.ACTION_BOOT_COMPLETED || applied.isNullOrEmpty()
                    Log.i(
                        TAG,
                        "boot trigger action=$action appliedEmpty=${applied.isNullOrEmpty()} " +
                            "willApply=$needsApply",
                    )
                    if (needsApply) {
                        StyleApplier.onBoot(app)
                    }
                    SetupCompleteJob.scheduleIfNeeded(app)
                } catch (t: Throwable) {
                    Log.e(TAG, "boot apply failed action=$action", t)
                } finally {
                    result.finish()
                }
            }
            .start()
    }

    companion object {
        private const val TAG = "RetroStyle"
    }
}

/**
 * `adb shell am broadcast -a org.retro.style.action.APPLY -n org.retro.style/.ApplyReceiver`
 * applies the secure settings, for scripts and tests. Only callers holding WRITE_SECURE_SETTINGS
 * (the shell, system apps) may send it.
 */
class ApplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_APPLY) return
        val result = goAsync()
        Log.i(TAG, "APPLY broadcast received")
        ApplyWorker.apply(context) { result.finish() }
    }

    companion object {
        const val ACTION_APPLY = "org.retro.style.action.APPLY"
        private const val TAG = "RetroStyle"
    }
}

/**
 * Full style apply once `user_setup_complete` turns 1. Theme JSON alone is not enough: the
 * taskbar process may have started during Setup Wizard before [StyleApplier.APPLIED_SETTING]
 * existed, and only a launcher restart after home is Launcher3 brings up the system bar.
 */
class SetupCompleteJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        if (setupComplete(this)) {
            if (appliedAfterSetup.compareAndSet(false, true)) {
                Log.i(TAG, "user_setup_complete=1; running full style apply")
                ApplyWorker.apply(this) { jobFinished(params, false) }
                return true
            }
            // Already applied after setup in this process; still refresh theme JSON in case
            // Setup Wizard overwrote Monet defaults.
            StyleApplier.applyThemeJson(this, StyleSettings.pack(contentResolver))
            return false
        }
        scheduleIfNeeded(this)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val TAG = "RetroStyle"
        private const val JOB_ID = 1
        private val appliedAfterSetup = AtomicBoolean(false)

        private fun setupComplete(context: Context) =
            Settings.Secure.getInt(context.contentResolver, "user_setup_complete", 0) == 1

        fun scheduleIfNeeded(context: Context) {
            if (setupComplete(context)) {
                // Setup already done (reboot after provisioning): ensure applied string exists.
                val applied =
                    Settings.Secure.getString(
                        context.contentResolver,
                        StyleApplier.APPLIED_SETTING,
                    )
                if (applied.isNullOrEmpty()) {
                    Log.i(TAG, "setup complete but retro_ui_applied empty; applying now")
                    ApplyWorker.apply(context)
                }
                return
            }
            val uri = Settings.Secure.getUriFor("user_setup_complete")
            val job =
                JobInfo.Builder(JOB_ID, ComponentName(context, SetupCompleteJob::class.java))
                    .addTriggerContentUri(JobInfo.TriggerContentUri(uri, 0))
                    .setTriggerContentMaxDelay(5_000L)
                    .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
            Log.i(TAG, "scheduled SetupCompleteJob until user_setup_complete=1")
        }
    }
}
