// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Context
import android.content.om.OverlayManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * Applies the user's retro style choices ([StyleSettings]) and theme pack ([ThemeCatalog]).
 * 1. Resolves every choice (defaults included) and writes the result to the secure setting
 *    [APPLIED_SETTING], the only thing SystemUI and Launcher3 read (once, when they start).
 * 2. Turns on the chosen pack's overlays and off every other pack's, and switches the overlays
 *    for the bar layout.
 * 3. Writes the pack's palette, font and icon packs into the theme JSON SystemUI's
 *    ThemeOverlayController applies, keeping the user's own JSON to restore later.
 * 4. Forces dark or light mode and LineageOS's black theme when the pack asks, again keeping the
 *    user's own values to restore later.
 * 5. Restarts SystemUI and Launcher3 when something they read at start changed, and reopens the
 *    desktop windows the SystemUI restart closed ([WindowSession]).
 *
 * Runs on a worker thread ([ApplyWorker]): restarts and window restore take seconds.
 */
object StyleApplier {
    private const val TAG = "RetroStyle"

    /** Resolved style, as `key=value` pairs separated by `;`. Read by SystemUI and Launcher3. */
    const val APPLIED_SETTING = "retro_ui_applied"

    private const val THEME_SETTING = "theme_customization_overlay_packages"
    private const val SAVED_THEME_SETTING = "retro_ui_style_saved_theme"
    private const val SAVED_NIGHT_SETTING = "retro_ui_style_saved_night"
    private const val SAVED_BLACK_SETTING = "retro_ui_style_saved_black"
    private const val DUAL_SHADE_SETTING = "dual_shade"
    private const val SETUP_COMPLETE_SETTING = "user_setup_complete"
    private const val SETTLE_BEFORE_RESTART_MS = 2_000L
    private const val DISMISS_TRIES = 5
    private const val DISMISS_POLL_MS = 600L

    /** UserHandle.PER_USER_RANGE: a uid is user * 100000 + app ID. */
    private const val PER_USER_RANGE = 100_000

    /** Theme JSON categories a pack may set (ThemeOverlayApplier in SystemUI). */
    private const val PALETTE = "android.theme.customization.system_palette"
    private const val COLOR_SOURCE = "android.theme.customization.color_source"
    private const val THEME_STYLE = "android.theme.customization.theme_style"
    private const val FONT = "android.theme.customization.font"
    private const val TIMESTAMP = "_applied_timestamp"
    private val PALETTE_KEYS =
        listOf(
            PALETTE,
            COLOR_SOURCE,
            THEME_STYLE,
            "android.theme.customization.accent_color",
            "android.theme.customization.color_index",
            "android.theme.customization.color_both",
        )

    /** LineageOS's pure black dark theme, in its own settings provider. */
    private val LINEAGE_SETTINGS = Uri.parse("content://lineagesettings")
    private const val LINEAGE_BLACK = "berry_black_theme"

    /** Zero status bar height: the tray shows the status icons instead. */
    private const val STATUS_BAR_OVERLAY = "org.retro.layout.systembar.android"

    /** The system bar lays out three-button navigation. */
    private const val THREE_BUTTON_NAV_OVERLAY = "com.android.internal.systemui.navbar.threebutton"

    private const val SYSTEMUI = "com.android.systemui"

    fun onBoot(context: Context) {
        apply(context, restoreWindows = false)
    }

    /**
     * Dual Shade panels unless the user already chose the single shade. SystemUI treats an unset
     * `dual_shade` as off while Settings shows it as on, so it must be written, not left unset.
     */
    private fun ensureDualShade(cr: ContentResolver) {
        if (Settings.Secure.getString(cr, DUAL_SHADE_SETTING) == null) {
            Settings.Secure.putInt(cr, DUAL_SHADE_SETTING, 1)
        }
    }

    @Synchronized
    fun apply(context: Context, restoreWindows: Boolean = true) {
        val cr = context.contentResolver
        ensureDualShade(cr)
        val setupComplete = Settings.Secure.getInt(cr, SETUP_COMPLETE_SETTING, 0) == 1
        val pack = StyleSettings.pack(cr)
        val layout = StyleSettings.layout(cr)
        val systemBar = layout == StyleSettings.LAYOUT_SYSTEM_BAR
        val applied =
            linkedMapOf(
                // `style` is the shell skin the patches draw; `theme` the pack it came from.
                "style" to if (pack.isKitKatSkin) ThemePack.SKIN_KITKAT else StyleSettings.STYLE_MATERIAL,
                "theme" to pack.id,
                "variant" to StyleSettings.variant(cr),
                "layout" to layout,
                "anchor" to StyleSettings.anchor(cr),
                "bar_apps" to if (StyleSettings.barApps(cr)) "1" else "0",
                "bar_apps_gravity" to StyleSettings.appsGravity(cr),
                "bar_buttons" to StyleSettings.barButtons(cr).joinToString(","),
                "clock_seconds" to if (StyleSettings.clockSeconds(cr)) "1" else "0",
                "panel_color" to pack.panel?.let(::hex).orEmpty(),
                "accent" to pack.accent?.let(::hex).orEmpty(),
            )
        val previous = parse(Settings.Secure.getString(cr, APPLIED_SETTING))

        // Overlays: the chosen pack's on, every other pack's off. Every change here, in the theme
        // JSON or in dark mode makes apps recreate their windows ([resourcesChanged]).
        val om = context.getSystemService(OverlayManager::class.java)
        val user = Process.myUserHandle()
        var resourcesChanged = false
        for (other in ThemeCatalog.packs()) {
            for (target in ThemeCatalog.SWITCHED_TARGETS) {
                resourcesChanged =
                    setOverlay(om, other.overlay(target), other.id == pack.id, user) or resourcesChanged
            }
        }
        val hideStatusBar = systemBar && (pack.isKitKatSkin || StyleSettings.hideStatusBar(cr))
        val statusBarChanged = isOverlayEnabled(om, STATUS_BAR_OVERLAY, user) != hideStatusBar
        resourcesChanged = setOverlay(om, STATUS_BAR_OVERLAY, hideStatusBar, user) or resourcesChanged
        if (systemBar) {
            try {
                Hidden.setOverlayExclusiveInCategory(om, THREE_BUTTON_NAV_OVERLAY, user)
            } catch (e: Exception) {
                Log.w(TAG, "could not switch to three-button navigation", e)
            }
        }

        resourcesChanged = applyThemeJson(context, pack) or resourcesChanged
        resourcesChanged = applyNightMode(context, pack) or resourcesChanged
        applyBlack(cr, pack)
        Settings.Secure.putString(cr, APPLIED_SETTING, format(applied))

        // Both read the applied style only when they start.
        val restartSystemUi = systemUiView(previous) != systemUiView(applied) || statusBarChanged
        val restartLauncher = launcherView(previous) != launcherView(applied)
        val am = context.getSystemService(ActivityManager::class.java)
        var windows = emptyList<WindowSession.Window>()
        var oldSystemUiPid = 0
        var unlockAfter = false
        if (restartSystemUi) {
            if (restoreWindows) windows = WindowSession.save(context)
            oldSystemUiPid = pidOf(am, SYSTEMUI)
            // A new SystemUI shows the lockscreen. When the user was in the device and it has no
            // PIN, pattern or password, take it down again: a style change isn't a reason to lock.
            val km = context.getSystemService(KeyguardManager::class.java)
            unlockAfter = setupComplete && !km.isKeyguardLocked && !km.isDeviceSecure
            if (resourcesChanged) {
                // Apps are recreating their windows for the new resources. A SystemUI restart in
                // the middle removes their desktop tasks under them, and they crash resuming
                // (BadTokenException), so let them finish first.
                Thread.sleep(SETTLE_BEFORE_RESTART_MS)
            }
            Log.i(TAG, "skin, panel colors, panel anchor or status bar changed; restarting SystemUI")
            restart(context, am, SYSTEMUI)
        }
        if (restartLauncher) {
            val home = homePackage(context)
            if (!setupComplete) {
                // Home is the setup wizard; the launcher reads the style when it first starts.
                Log.i(TAG, "bar layout, skin or bar options changed; setup still running, not restarting $home")
            } else if (home != null) {
                Log.i(TAG, "bar layout, skin or bar options changed; restarting $home")
                restart(context, am, home)
            }
        }
        if (restartSystemUi && (restoreWindows || unlockAfter)) {
            WindowSession.waitForNewSystemUi(context, oldSystemUiPid)
            if (unlockAfter) dismissLockscreen(context)
            if (restoreWindows) WindowSession.restore(context, windows)
        }
        Log.i(TAG, "applied ${format(applied)}")
    }

    /** Dismisses the insecure lockscreen a SystemUI restart brought up; tries for a few seconds. */
    private fun dismissLockscreen(context: Context) {
        val km = context.getSystemService(KeyguardManager::class.java)
        var dismissed = false
        repeat(DISMISS_TRIES) {
            if (km.isDeviceSecure) return
            if (km.isKeyguardLocked) {
                try {
                    Hidden.dismissKeyguard()
                    dismissed = true
                } catch (e: Exception) {
                    Log.w(TAG, "could not dismiss the lockscreen", e)
                    return
                }
            }
            Thread.sleep(DISMISS_POLL_MS)
            if (!km.isKeyguardLocked) {
                if (dismissed) Log.i(TAG, "dismissed the lockscreen the SystemUI restart showed")
                return
            }
        }
        Log.w(TAG, "the lockscreen is still showing after the SystemUI restart")
    }

    /**
     * Writes the pack's theme JSON, which SystemUI turns into Monet palette, font and icon pack
     * overlays. The user's own JSON is saved first and is the base: a pack that sets nothing here
     * (wallpaper colors, no font or icons) restores it. Returns whether the JSON changed.
     */
    fun applyThemeJson(context: Context, pack: ThemePack): Boolean {
        val cr = context.contentResolver
        val om = context.getSystemService(OverlayManager::class.java)
        val user = Process.myUserHandle()
        val installed = { pkg: String -> overlayInstalled(om, pkg, user) }
        val font = pack.overlay(ThemeCatalog.TARGET_FONT).takeIf(installed)
        val icons =
            ThemeCatalog.ICON_TARGETS.mapNotNull { (target, category) ->
                pack.overlay(target).takeIf(installed)?.let { category to it }
            }
        val saved = Settings.Secure.getString(cr, SAVED_THEME_SETTING)
        val current = Settings.Secure.getString(cr, THEME_SETTING)

        if (pack.seed == null && font == null && icons.isEmpty()) {
            if (saved == null) return false
            val own = if (saved == "{}") null else saved
            Settings.Secure.putString(cr, THEME_SETTING, own)
            Settings.Secure.putString(cr, SAVED_THEME_SETTING, null)
            return !sameTheme(current, own)
        }
        val own = saved ?: Settings.Secure.getString(cr, THEME_SETTING) ?: "{}"
        if (saved == null) Settings.Secure.putString(cr, SAVED_THEME_SETTING, own)
        val json = runCatching { JSONObject(own) }.getOrDefault(JSONObject())
        pack.seed?.let { seed ->
            PALETTE_KEYS.forEach(json::remove)
            json.put(PALETTE, hex(seed or 0xFF000000.toInt()).removePrefix("#"))
            json.put(COLOR_SOURCE, "preset")
            json.put(THEME_STYLE, pack.paletteStyle)
        }
        font?.let { json.put(FONT, it) }
        icons.forEach { (category, pkg) -> json.put(category, pkg) }
        if (sameTheme(current, json.toString())) return false
        json.put(TIMESTAMP, System.currentTimeMillis())
        Settings.Secure.putString(cr, THEME_SETTING, json.toString())
        return true
    }

    /** Two theme JSONs ask SystemUI for the same overlays (the timestamp aside). */
    private fun sameTheme(a: String?, b: String?): Boolean {
        fun keys(value: String?): Map<String, Any?> {
            val json = runCatching { JSONObject(value ?: "{}") }.getOrDefault(JSONObject())
            return json.keys().asSequence().filter { it != TIMESTAMP }.associateWith { json.opt(it) }
        }
        return keys(a) == keys(b)
    }

    /**
     * Forces the pack's dark or light mode; packs that leave it alone restore the user's own.
     * Returns whether the mode changed.
     */
    private fun applyNightMode(context: Context, pack: ThemePack): Boolean {
        val cr = context.contentResolver
        val ui = context.getSystemService(UiModeManager::class.java)
        val saved = Settings.Secure.getString(cr, SAVED_NIGHT_SETTING)?.toIntOrNull()
        val wanted =
            when (pack.night) {
                ThemePack.NIGHT_DARK -> UiModeManager.MODE_NIGHT_YES
                ThemePack.NIGHT_LIGHT -> UiModeManager.MODE_NIGHT_NO
                else -> null
            }
        try {
            val before = ui.nightMode
            if (wanted != null) {
                if (saved == null) Settings.Secure.putString(cr, SAVED_NIGHT_SETTING, before.toString())
                if (before != wanted) ui.nightMode = wanted
            } else if (saved != null) {
                if (before != saved) ui.nightMode = saved
                Settings.Secure.putString(cr, SAVED_NIGHT_SETTING, null)
            }
            return ui.nightMode != before
        } catch (e: Exception) {
            Log.w(TAG, "could not set the dark mode", e)
            return false
        }
    }

    /** LineageOS's black theme for packs with `black`; elsewhere the user's own value. */
    private fun applyBlack(cr: ContentResolver, pack: ThemePack) {
        val saved = Settings.Secure.getString(cr, SAVED_BLACK_SETTING)
        try {
            val current = lineageSecure(cr, LINEAGE_BLACK) ?: return
            if (pack.black) {
                if (saved == null) Settings.Secure.putString(cr, SAVED_BLACK_SETTING, current)
                if (current != "1") putLineageSecure(cr, LINEAGE_BLACK, "1")
            } else if (saved != null) {
                if (current != saved) putLineageSecure(cr, LINEAGE_BLACK, saved)
                Settings.Secure.putString(cr, SAVED_BLACK_SETTING, null)
            }
        } catch (e: Exception) {
            // Not LineageOS: no black theme.
            Log.i(TAG, "no LineageOS black theme here: ${e.message}")
        }
    }

    /** Null when the provider is missing; "0" when the setting was never written. */
    private fun lineageSecure(cr: ContentResolver, key: String): String? {
        val result = cr.call(LINEAGE_SETTINGS, "GET_secure", key, null) ?: return null
        return result.getString("value") ?: "0"
    }

    private fun putLineageSecure(cr: ContentResolver, key: String, value: String) {
        cr.call(LINEAGE_SETTINGS, "PUT_secure", key, Bundle().apply { putString("value", value) })
    }

    /** What SystemUI draws differently: skin, panel colors, the panel anchor. */
    private fun systemUiView(applied: Map<String, String>): List<Any?> {
        val kitKat = applied["style"] == ThemePack.SKIN_KITKAT
        return listOf(
            kitKat,
            kitKat && applied["variant"] == StyleSettings.VARIANT_HOLO_UNIFIED,
            applied["anchor"] == StyleSettings.ANCHOR_BOTTOM_END,
            applied["panel_color"].orEmpty(),
            if (kitKat) applied["accent"].orEmpty() else "",
        )
    }

    /** What the taskbar draws differently; the bar options only matter on the system bar. */
    private fun launcherView(applied: Map<String, String>): List<Any?> {
        val systemBar = applied["layout"] == StyleSettings.LAYOUT_SYSTEM_BAR
        val kitKat = applied["style"] == ThemePack.SKIN_KITKAT
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

    /**
     * Kills every process of [packageName]'s user ID; persistent SystemUI and the launcher
     * restart. Apps that run as the system user ID (Settings, setup wizards, SmartDock, this app)
     * share it, so killing it would take all of them down: those are left alone.
     */
    private fun restart(context: Context, am: ActivityManager, packageName: String) {
        try {
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            if (uid % PER_USER_RANGE < Process.FIRST_APPLICATION_UID) {
                Log.w(TAG, "not restarting $packageName: it shares the system user ID with other apps")
                return
            }
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

    private fun overlayInstalled(om: OverlayManager, pkg: String, user: UserHandle): Boolean =
        try {
            Hidden.overlayInfo(om, pkg, user) != null
        } catch (e: Exception) {
            false
        }

    /** Returns whether the overlay was switched. */
    private fun setOverlay(om: OverlayManager, pkg: String, enable: Boolean, user: UserHandle): Boolean {
        try {
            if (Hidden.overlayInfo(om, pkg, user) == null) return false
            if (Hidden.isOverlayEnabled(om, pkg, user) == enable) return false
            Hidden.setOverlayEnabled(om, pkg, enable, user)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "could not ${if (enable) "enable" else "disable"} $pkg", e)
            return false
        }
    }

    private fun isOverlayEnabled(om: OverlayManager, pkg: String, user: UserHandle): Boolean =
        try {
            Hidden.isOverlayEnabled(om, pkg, user)
        } catch (e: Exception) {
            false
        }

    /** #AARRGGBB, the form SystemUI and Launcher3 parse back with Color.parseColor. */
    fun hex(color: Int): String = "#%08X".format(color)

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
