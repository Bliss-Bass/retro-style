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
import java.util.concurrent.Executors

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
 * Applies the style at boot: SystemUI and Launcher3 then start with the right look on the next
 * restart, and first boot records the build defaults. While the setup wizard runs, SystemUI writes
 * its own color defaults over the theme JSON, so [SetupCompleteJob] writes it again afterwards.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        val app = context.applicationContext
        Thread {
                try {
                    StyleApplier.onBoot(app)
                    SetupCompleteJob.scheduleIfNeeded(app)
                } finally {
                    result.finish()
                }
            }
            .start()
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
        ApplyWorker.apply(context) { result.finish() }
    }

    companion object {
        const val ACTION_APPLY = "org.retro.style.action.APPLY"
    }
}

/** Writes the style's theme JSON again once `user_setup_complete` turns 1. */
class SetupCompleteJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        if (setupComplete(this)) {
            StyleApplier.applyThemeJson(this, StyleSettings.pack(contentResolver))
        } else {
            scheduleIfNeeded(this)
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 1

        private fun setupComplete(context: Context) =
            Settings.Secure.getInt(context.contentResolver, "user_setup_complete", 0) == 1

        fun scheduleIfNeeded(context: Context) {
            if (setupComplete(context)) return
            val uri = Settings.Secure.getUriFor("user_setup_complete")
            val job =
                JobInfo.Builder(JOB_ID, ComponentName(context, SetupCompleteJob::class.java))
                    .addTriggerContentUri(JobInfo.TriggerContentUri(uri, 0))
                    .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        }
    }
}
