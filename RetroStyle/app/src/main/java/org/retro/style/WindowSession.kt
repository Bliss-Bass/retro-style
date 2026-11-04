// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log

/**
 * Keeps the user's desktop windows across a SystemUI restart.
 *
 * On large screens, desktop windows live in root tasks that WM Shell creates, and WM Shell runs
 * inside SystemUI. When SystemUI dies, WindowManager removes those root tasks and every window in
 * them. [save] notes the visible desktop windows (app and bounds) before the restart; [restore]
 * reopens them at the same bounds once the new SystemUI has set the desktop up again. Apps reopen
 * at their main screen, except the UI style page, which reopens on itself.
 */
object WindowSession {
    private const val TAG = "RetroStyle"
    private const val SYSTEMUI = "com.android.systemui"

    data class Window(val component: ComponentName, val bounds: Rect)

    /** Visible desktop windows, bottom to top. */
    fun save(context: Context): List<Window> {
        val am = context.getSystemService(ActivityManager::class.java)
        // getRunningTasks returns every task to a system app, top first.
        @Suppress("DEPRECATION") val tasks = am.getRunningTasks(Int.MAX_VALUE)
        val launcher = homePackage(context)
        return tasks
            .filter { it.isVisible && Hidden.windowingMode(it) == Hidden.WINDOWING_MODE_FREEFORM }
            .mapNotNull { task ->
                val base = task.baseActivity ?: return@mapNotNull null
                if (base.packageName == launcher || base.packageName == SYSTEMUI) {
                    return@mapNotNull null
                }
                val bounds = Hidden.bounds(task) ?: return@mapNotNull null
                // Reopen the page the user was on if it's ours; other apps at their entry point.
                val top = task.topActivity
                val component = if (top?.packageName == context.packageName) top else base
                Window(component, Rect(bounds))
            }
            .reversed()
    }

    /**
     * Waits for SystemUI to come back with a new process (not [oldSystemUiPid]), gives WM Shell
     * time to set up the desktop, then reopens [windows].
     */
    fun restore(context: Context, windows: List<Window>, oldSystemUiPid: Int) {
        if (windows.isEmpty()) return
        val am = context.getSystemService(ActivityManager::class.java)
        var waited = 0
        while (waited < WAIT_MS) {
            val pid = am.runningAppProcesses?.firstOrNull { it.processName == SYSTEMUI }?.pid
            if (pid != null && pid != oldSystemUiPid) break
            Thread.sleep(POLL_MS)
            waited += POLL_MS.toInt()
        }
        // Windows opened before the new shell organizes the desktop get removed again.
        Thread.sleep(SETTLE_MS)
        Log.i(TAG, "reopening ${windows.size} window(s) closed by the SystemUI restart")
        for (window in windows) {
            val intent =
                Intent(Intent.ACTION_MAIN)
                    .setComponent(window.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic().setLaunchBounds(window.bounds)
            try {
                context.startActivity(intent, options.toBundle())
            } catch (e: RuntimeException) {
                Log.w(TAG, "could not reopen ${window.component}", e)
            }
        }
    }

    private fun homePackage(context: Context): String? =
        context.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo
            ?.packageName

    private const val WAIT_MS = 20_000
    private const val POLL_MS = 500L
    private const val SETTLE_MS = 3_000L
}
