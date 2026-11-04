// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.content.om.OverlayManager
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * Applies the user's retro style choices ([StyleSettings]) to the system.
 * 1. Resolves every choice (defaults included) and writes the result to the secure setting
 *    [APPLIED_SETTING], the only thing SystemUI and Launcher3 read (once, when they start).
 * 2. Switches the runtime resource overlays (RROs) for the style and the bar layout.
 * 3. Writes the style's palette, font and icon packs into the theme JSON SystemUI's
 *    ThemeOverlayController applies, keeping the user's own JSON to restore later.
 * 4. Restarts SystemUI and Launcher3 when something they read at start changed, and reopens the
 *    desktop windows the SystemUI restart closed ([WindowSession]).
 *
 * Runs on a worker thread (see [ApplyService]): restarts and window restore take seconds.
 */
object StyleApplier {
    private const val TAG = "RetroStyle"

    /** Resolved style, as `key=value` pairs separated by `;`. Read by SystemUI and Launcher3. */
    const val APPLIED_SETTING = "retro_ui_applied"

    private const val THEME_SETTING = "theme_customization_overlay_packages"
    private const val SAVED_THEME_SETTING = "retro_ui_style_saved_theme"
    private const val DUAL_SHADE_SETTING = "dual_shade"

    /** KitKat style pack overlays, switched together (category org.retro.style). */
    private val KITKAT_OVERLAYS =
        listOf(
            "org.retro.style.kitkat.android",
            "org.retro.style.kitkat.systemui",
            "org.retro.style.kitkat.settings",
            "org.retro.style.kitkat.launcher3",
        )

    /** Zero status bar height: the tray shows the status icons instead. */
    private const val STATUS_BAR_OVERLAY = "org.retro.layout.systembar.android"

    /** The system bar lays out three-button navigation. */
    private const val THREE_BUTTON_NAV_OVERLAY = "com.android.internal.systemui.navbar.threebutton"

    private const val SYSTEMUI = "com.android.systemui"

    /** First boot: turn on Dual Shade unless the user already chose the single shade. */
    fun onBoot(context: Context) {
        val cr = context.contentResolver
        if (Settings.Secure.getString(cr, DUAL_SHADE_SETTING) == null) {
            Settings.Secure.putInt(cr, DUAL_SHADE_SETTING, 1)
        }
        apply(context, restoreWindows = false)
    }

    @Synchronized
    fun apply(context: Context, restoreWindows: Boolean = true) {
        val cr = context.contentResolver
        val style = StyleSettings.style(cr)
        val layout = StyleSettings.layout(cr)
        val kitKat = style == StyleSettings.STYLE_KITKAT
        val systemBar = layout == StyleSettings.LAYOUT_SYSTEM_BAR
        val applied =
            linkedMapOf(
                "style" to style,
                "variant" to StyleSettings.variant(cr),
                "layout" to layout,
                "anchor" to StyleSettings.anchor(cr),
                "bar_apps" to if (StyleSettings.barApps(cr)) "1" else "0",
                "bar_apps_gravity" to StyleSettings.appsGravity(cr),
                "bar_buttons" to StyleSettings.barButtons(cr).joinToString(","),
                "clock_seconds" to if (StyleSettings.clockSeconds(cr)) "1" else "0",
            )
        val previous = parse(Settings.Secure.getString(cr, APPLIED_SETTING))

        // Overlays.
        val om = context.getSystemService(OverlayManager::class.java)
        val user = Process.myUserHandle()
        KITKAT_OVERLAYS.forEach { setOverlay(om, it, kitKat, user) }
        val hideStatusBar = systemBar && (kitKat || StyleSettings.hideStatusBar(cr))
        val statusBarChanged = isOverlayEnabled(om, STATUS_BAR_OVERLAY, user) != hideStatusBar
        setOverlay(om, STATUS_BAR_OVERLAY, hideStatusBar, user)
        if (systemBar) {
            try {
                Hidden.setOverlayExclusiveInCategory(om, THREE_BUTTON_NAV_OVERLAY, user)
            } catch (e: Exception) {
                Log.w(TAG, "could not switch to three-button navigation", e)
            }
        }

        applyThemeJson(cr, style)
        Settings.Secure.putString(cr, APPLIED_SETTING, format(applied))

        // Both read the applied style only when they start.
        val restartSystemUi = systemUiView(previous) != systemUiView(applied) || statusBarChanged
        val restartLauncher = launcherView(previous) != launcherView(applied)
        val am = context.getSystemService(ActivityManager::class.java)
        var windows = emptyList<WindowSession.Window>()
        var oldSystemUiPid = 0
        if (restartSystemUi) {
            if (restoreWindows) windows = WindowSession.save(context)
            oldSystemUiPid = pidOf(am, SYSTEMUI)
            Log.i(TAG, "style, panel anchor or status bar changed; restarting SystemUI")
            restart(context, am, SYSTEMUI)
        }
        if (restartLauncher) {
            homePackage(context)?.let {
                Log.i(TAG, "bar layout, style or bar options changed; restarting $it")
                restart(context, am, it)
            }
        }
        if (restartSystemUi && restoreWindows) {
            WindowSession.restore(context, windows, oldSystemUiPid)
        }
        Log.i(TAG, "applied ${format(applied)}")
    }

    /**
     * Writes the style's theme JSON, which SystemUI turns into Monet palette, font and icon pack
     * overlays. Material You restores whatever the user had before.
     */
    fun applyThemeJson(cr: ContentResolver, style: String) {
        val json = themeJson(style)
        val saved = Settings.Secure.getString(cr, SAVED_THEME_SETTING)
        if (json != null) {
            if (saved == null) {
                Settings.Secure.putString(
                    cr,
                    SAVED_THEME_SETTING,
                    Settings.Secure.getString(cr, THEME_SETTING) ?: "{}",
                )
            }
            Settings.Secure.putString(cr, THEME_SETTING, json)
        } else if (saved != null) {
            Settings.Secure.putString(cr, THEME_SETTING, if (saved == "{}") null else saved)
            Settings.Secure.putString(cr, SAVED_THEME_SETTING, null)
        }
    }

    /** Null means "the user's own colors" (Material You). */
    private fun themeJson(style: String): String? {
        val json =
            when (style) {
                StyleSettings.STYLE_KITKAT ->
                    JSONObject()
                        .put("android.theme.customization.system_palette", "FF33B5E5")
                        .put("android.theme.customization.color_source", "preset")
                        .put("android.theme.customization.theme_style", "VIBRANT")
                        .put("android.theme.customization.font", "org.retro.style.kitkat.font")
                        .put(
                            "android.theme.customization.icon_pack.android",
                            "org.retro.style.kitkat.icons.android",
                        )
                        .put(
                            "android.theme.customization.icon_pack.systemui",
                            "org.retro.style.kitkat.icons.systemui",
                        )
                // Material 3 baseline palette instead of wallpaper colors.
                StyleSettings.STYLE_M3 ->
                    JSONObject()
                        .put("android.theme.customization.system_palette", "FF6750A4")
                        .put("android.theme.customization.color_source", "preset")
                        .put("android.theme.customization.theme_style", "TONAL_SPOT")
                else -> return null
            }
        return json.put("_applied_timestamp", System.currentTimeMillis()).toString()
    }

    /** What SystemUI draws differently: KitKat skin, its panel color, the panel anchor. */
    private fun systemUiView(applied: Map<String, String>): List<Any> {
        val kitKat = applied["style"] == StyleSettings.STYLE_KITKAT
        return listOf(
            kitKat,
            kitKat && applied["variant"] == StyleSettings.VARIANT_HOLO_UNIFIED,
            applied["anchor"] == StyleSettings.ANCHOR_BOTTOM_END,
        )
    }

    /** What the taskbar draws differently; the bar options only matter on the system bar. */
    private fun launcherView(applied: Map<String, String>): List<Any?> {
        val systemBar = applied["layout"] == StyleSettings.LAYOUT_SYSTEM_BAR
        val kitKat = applied["style"] == StyleSettings.STYLE_KITKAT
        if (!systemBar) return listOf(false, kitKat)
        return listOf(
            true,
            kitKat,
            applied["bar_apps"] ?: if (kitKat) "0" else "1",
            applied["bar_apps_gravity"] ?: StyleSettings.GRAVITY_CENTER,
            applied["bar_buttons"].orEmpty(),
            applied["clock_seconds"] ?: "0",
        )
    }

    /** Kills every process of [packageName]; persistent SystemUI and the launcher restart. */
    private fun restart(context: Context, am: ActivityManager, packageName: String) {
        try {
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            Hidden.killUid(am, uid, "retro style changed")
        } catch (e: Exception) {
            Log.w(TAG, "could not restart $packageName", e)
        }
    }

    private fun pidOf(am: ActivityManager, processName: String): Int =
        am.runningAppProcesses?.firstOrNull { it.processName == processName }?.pid ?: 0

    private fun homePackage(context: Context): String? =
        context.packageManager
            .resolveActivity(
                android.content
                    .Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME),
                0,
            )
            ?.activityInfo
            ?.packageName

    private fun setOverlay(om: OverlayManager, pkg: String, enable: Boolean, user: UserHandle) {
        try {
            if (Hidden.overlayInfo(om, pkg, user) == null) return
            Hidden.setOverlayEnabled(om, pkg, enable, user)
        } catch (e: Exception) {
            Log.w(TAG, "could not ${if (enable) "enable" else "disable"} $pkg", e)
        }
    }

    private fun isOverlayEnabled(om: OverlayManager, pkg: String, user: UserHandle): Boolean =
        try {
            Hidden.isOverlayEnabled(om, pkg, user)
        } catch (e: Exception) {
            false
        }

    fun format(values: Map<String, String>): String =
        values.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun parse(value: String?): Map<String, String> =
        value
            .orEmpty()
            .split(';')
            .mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim()
                else null
            }
            .toMap()
}
