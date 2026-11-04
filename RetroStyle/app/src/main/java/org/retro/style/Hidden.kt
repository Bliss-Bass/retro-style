// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.app.ActivityManager
import android.app.TaskInfo
import android.content.om.OverlayManager
import android.graphics.Rect
import android.os.UserHandle

/**
 * The few hidden framework members the app needs, called through reflection so the same source
 * builds with Gradle (public SDK) and Soong (platform_apis). Platform-signed apps are exempt from
 * the hidden API restrictions, so these calls work on the device.
 */
internal object Hidden {
    /** WindowConfiguration.WINDOWING_MODE_FREEFORM: desktop windows. */
    const val WINDOWING_MODE_FREEFORM = 5

    /** ActivityManager.killUid (SystemApi, needs KILL_UID): kills every process of [uid]. */
    fun killUid(am: ActivityManager, uid: Int, reason: String) {
        ActivityManager::class
            .java
            .getMethod("killUid", Int::class.javaPrimitiveType, String::class.java)
            .invoke(am, uid, reason)
    }

    /** TaskInfo.configuration.windowConfiguration, which holds the window's bounds and mode. */
    private fun windowConfiguration(task: TaskInfo): Any? {
        val configuration = TaskInfo::class.java.getField("configuration").get(task) ?: return null
        return configuration.javaClass.getField("windowConfiguration").get(configuration)
    }

    fun bounds(task: TaskInfo): Rect? =
        windowConfiguration(task)?.let { it.javaClass.getMethod("getBounds").invoke(it) as? Rect }

    fun windowingMode(task: TaskInfo): Int =
        windowConfiguration(task)?.let {
            it.javaClass.getMethod("getWindowingMode").invoke(it) as? Int
        } ?: 0

    /** OverlayManager.getOverlayInfo (SystemApi); null when the overlay isn't installed. */
    fun overlayInfo(om: OverlayManager, pkg: String, user: UserHandle): Any? =
        OverlayManager::class
            .java
            .getMethod("getOverlayInfo", String::class.java, UserHandle::class.java)
            .invoke(om, pkg, user)

    /** OverlayInfo.isEnabled (SystemApi). */
    fun isOverlayEnabled(om: OverlayManager, pkg: String, user: UserHandle): Boolean {
        val info = overlayInfo(om, pkg, user) ?: return false
        return info.javaClass.getMethod("isEnabled").invoke(info) as Boolean
    }

    /** OverlayManager.setEnabled (SystemApi, needs CHANGE_OVERLAY_PACKAGES). */
    fun setOverlayEnabled(om: OverlayManager, pkg: String, enable: Boolean, user: UserHandle) {
        OverlayManager::class
            .java
            .getMethod(
                "setEnabled",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                UserHandle::class.java,
            )
            .invoke(om, pkg, enable, user)
    }

    /** OverlayManager.setEnabledExclusiveInCategory (SystemApi): one overlay of its category. */
    fun setOverlayExclusiveInCategory(om: OverlayManager, pkg: String, user: UserHandle) {
        OverlayManager::class
            .java
            .getMethod("setEnabledExclusiveInCategory", String::class.java, UserHandle::class.java)
            .invoke(om, pkg, user)
    }
}
