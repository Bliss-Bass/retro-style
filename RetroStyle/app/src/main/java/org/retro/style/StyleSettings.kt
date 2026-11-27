// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.content.ContentResolver
import android.os.SystemProperties
import android.provider.Settings

/**
 * The user's retro style choices, stored as secure settings (the page writes them; adb can too).
 * [KEY_STYLE] holds a theme pack id ([ThemeCatalog]).
 *
 * An unset choice means "use the default": the build default from the product makefile
 * (`ro.retro.ui_style.default`, `ro.retro.ui_layout.default`) or one derived from other choices.
 * [StyleApplier] resolves them and writes the result to [StyleApplier.APPLIED_SETTING], which is
 * what SystemUI and Launcher3 read.
 */
object StyleSettings {
    const val KEY_STYLE = "retro_ui_style"
    const val KEY_VARIANT = "retro_ui_style_variant"
    const val KEY_LAYOUT = "retro_ui_layout"
    const val KEY_ANCHOR = "retro_panel_anchor"
    const val KEY_HIDE_STATUS_BAR = "retro_ui_hide_status_bar"
    const val KEY_BAR_APPS = "retro_ui_bar_apps"
    const val KEY_BAR_APPS_GRAVITY = "retro_ui_bar_apps_gravity"
    const val KEY_BAR_NAV_GRAVITY = "retro_ui_bar_nav_gravity"
    /** Content alignment inside the Apps / Nav zone (independent of which zone they occupy). */
    const val KEY_BAR_APPS_JUSTIFY = "retro_ui_bar_apps_justify"
    const val KEY_BAR_NAV_JUSTIFY = "retro_ui_bar_nav_justify"
    const val KEY_BAR_BUTTONS = "retro_ui_bar_buttons"
    const val KEY_CLOCK_SECONDS = "retro_ui_bar_clock_seconds"
    const val KEY_BAR_BACKGROUND = "retro_ui_bar_background"
    const val KEY_BLACK = "retro_ui_black"
    /** Shade panel fill `#AARRGGBB`; unset follows the pack (`shell.panel`). */
    const val KEY_PANEL_COLOR = "retro_ui_panel_color"
    /** `1`/`0` localized panel blur; unset follows the pack (`shell.blur_enabled`). */
    const val KEY_PANEL_BLUR = "retro_ui_panel_blur"
    /** Blur radius in dp; unset follows the pack (`shell.blur_radius`). */
    const val KEY_PANEL_BLUR_RADIUS = "retro_ui_panel_blur_radius"
    const val KEY_DIALOG_ALPHA = "retro_ui_dialog_alpha"
    const val KEY_DIALOG_BLUR_RADIUS = "retro_ui_dialog_blur_radius"
    const val KEY_WINDOW_ALPHA = "retro_ui_window_alpha"
    /** Unfocused freeform leash alpha; unset follows [KEY_WINDOW_ALPHA]. */
    const val KEY_WINDOW_ALPHA_UNFOCUSED = "retro_ui_window_alpha_unfocused"
    const val KEY_WINDOW_BLUR_RADIUS = "retro_ui_window_blur_radius"
    /** Unfocused freeform blur radius dp; unset follows [KEY_WINDOW_BLUR_RADIUS]. */
    const val KEY_WINDOW_BLUR_RADIUS_UNFOCUSED = "retro_ui_window_blur_radius_unfocused"
    /**
     * Comma-separated packages that stay opaque in freeform (user blocklist). Merged with the
     * Shell built-in camera/media blocklist. Empty / `none` clears the user list.
     */
    const val KEY_WINDOW_ALPHA_BLOCKLIST = "retro_ui_window_alpha_blocklist"

    const val DEFAULT_BLUR_RADIUS = 25

    /**
     * Common packages offered in UI Style for the freeform glass blocklist (browsers stay solid
     * when focused). Only checked entries are written; the Shell still hard-blocks camera/media.
     */
    val WINDOW_ALPHA_BLOCKLIST_PRESETS =
        listOf(
            "com.android.chrome",
            "com.chrome.browser",
            "org.lineageos.jelly",
            "com.android.browser",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.android.documentsui",
            "com.google.android.apps.docs",
        )

    /** System bar background on the home screen; stock leaves it to the launcher. */
    const val BAR_BG_STOCK = "stock"
    val BAR_BACKGROUNDS = listOf(BAR_BG_STOCK, "solid", "gradient", "translucent", "transparent")

    /** Preset panel fills offered in UI Style (ARGB). */
    val PANEL_COLOR_PRESETS =
        listOf(
            "pack",
            "#FF000000",
            "#F0000000",
            "#CC1A1F2E",
            "#8C000000",
            "#66000000",
            "#4DFFFFFF",
        )

    /** The pack used when the chosen and build default packs aren't installed. */
    const val STYLE_MATERIAL = "material"

    const val VARIANT_KITKAT_STOCK = "kitkat_stock"
    const val VARIANT_HOLO_UNIFIED = "holo_unified"

    const val LAYOUT_STOCK = "stock"
    const val LAYOUT_SYSTEM_BAR = "system_bar"

    const val ANCHOR_TOP_END = "top_end"
    const val ANCHOR_BOTTOM_END = "bottom_end"

    const val GRAVITY_START = "start"
    const val GRAVITY_CENTER = "center"
    const val GRAVITY_END = "end"

    /** Extra system bar buttons, in the order Launcher3 places them after back/home/recents. */
    val BAR_BUTTONS = listOf("vol_down", "vol_up", "screenshot", "power")
    private const val NO_BAR_BUTTONS = "none"

    private fun get(cr: ContentResolver, key: String): String? =
        Settings.Secure.getString(cr, key)?.takeIf { it.isNotEmpty() }

    /** Stores a choice; null clears it back to the default. */
    fun put(cr: ContentResolver, key: String, value: String?) {
        Settings.Secure.putString(cr, key, value)
    }

    /** The chosen theme pack: the user's, else the build default, else Material You. */
    fun pack(cr: ContentResolver): ThemePack =
        ThemeCatalog.find(get(cr, KEY_STYLE))
            ?: ThemeCatalog.find(SystemProperties.get("ro.retro.ui_style.default", ""))
            ?: ThemeCatalog.find(STYLE_MATERIAL)
            ?: ThemeCatalog.packs().first()

    fun style(cr: ContentResolver): String = pack(cr).id

    fun variant(cr: ContentResolver): String =
        get(cr, KEY_VARIANT)?.takeIf { it == VARIANT_HOLO_UNIFIED } ?: VARIANT_KITKAT_STOCK

    /** Raw layout choice (or build default), before KitKat coerces it to the system bar. */
    fun getLayoutChoice(cr: ContentResolver): String =
        (get(cr, KEY_LAYOUT) ?: SystemProperties.get("ro.retro.ui_layout.default", "")).takeIf {
            it == LAYOUT_SYSTEM_BAR
        } ?: LAYOUT_STOCK

    fun layout(cr: ContentResolver): String {
        val chosen = getLayoutChoice(cr)
        // KitKat's shell skin is the tablet system bar (nav + tray). Stock taskbar + KitKat
        // overlays leave start-aligned nav/apps and a black bar that is not a taskbar.
        return if (pack(cr).isKitKatSkin) LAYOUT_SYSTEM_BAR else chosen
    }

    /** The panels open from the tray on the system bar, under the status bar otherwise. */
    fun anchor(cr: ContentResolver): String =
        get(cr, KEY_ANCHOR)?.takeIf { it == ANCHOR_TOP_END || it == ANCHOR_BOTTOM_END }
            ?: if (layout(cr) == LAYOUT_SYSTEM_BAR) ANCHOR_BOTTOM_END else ANCHOR_TOP_END

    /** Hide the top status bar on the system bar; packs with the KitKat skin always do. */
    fun hideStatusBar(cr: ContentResolver): Boolean = get(cr, KEY_HIDE_STATUS_BAR) == "1"

    /** App icons and running tasks on the system bar; unset follows the pack (shell.bar_apps). */
    fun barApps(cr: ContentResolver): Boolean =
        when (get(cr, KEY_BAR_APPS)) {
            "1" -> true
            "0" -> false
            else -> pack(cr).barApps
        }

    fun appsGravity(cr: ContentResolver): String =
        get(cr, KEY_BAR_APPS_GRAVITY)?.takeIf { it == GRAVITY_START || it == GRAVITY_END }
            ?: GRAVITY_CENTER

    /** Nav buttons on the system bar; unset keeps them at the start (classic). */
    fun navGravity(cr: ContentResolver): String =
        get(cr, KEY_BAR_NAV_GRAVITY)?.takeIf { it == GRAVITY_CENTER || it == GRAVITY_END }
            ?: GRAVITY_START

    fun appsJustify(cr: ContentResolver): String =
        get(cr, KEY_BAR_APPS_JUSTIFY)?.takeIf {
            it == GRAVITY_START || it == GRAVITY_CENTER || it == GRAVITY_END
        } ?: GRAVITY_CENTER

    fun navJustify(cr: ContentResolver): String =
        get(cr, KEY_BAR_NAV_JUSTIFY)?.takeIf {
            it == GRAVITY_START || it == GRAVITY_CENTER || it == GRAVITY_END
        } ?: GRAVITY_START

    /** Extra buttons on the system bar; none until the user turns them on. */
    fun barButtons(cr: ContentResolver): List<String> {
        val chosen = get(cr, KEY_BAR_BUTTONS).orEmpty().split(',').map { it.trim() }
        return BAR_BUTTONS.filter { it in chosen }
    }

    fun setBarButton(cr: ContentResolver, button: String, show: Boolean) {
        val current = barButtons(cr)
        val wanted = BAR_BUTTONS.filter { if (it == button) show else it in current }
        put(cr, KEY_BAR_BUTTONS, wanted.joinToString(",").ifEmpty { NO_BAR_BUTTONS })
    }

    fun clockSeconds(cr: ContentResolver): Boolean = get(cr, KEY_CLOCK_SECONDS) == "1"

    /** Pure black dark mode (LineageOS black theme): the user's choice, else [defaultBlack]. */
    fun black(cr: ContentResolver): Boolean =
        when (get(cr, KEY_BLACK)) {
            "1" -> true
            "0" -> false
            else -> defaultBlack(cr)
        }

    /** Pure black before the user chooses: the build's (`ro.retro.ui_black.default`), else the pack's. */
    fun defaultBlack(cr: ContentResolver): Boolean =
        when (SystemProperties.get("ro.retro.ui_black.default", "")) {
            "1" -> true
            "0" -> false
            else -> pack(cr).black
        }

    /**
     * The system bar's home screen background: the user's, else the build's
     * (`ro.retro.ui_bar_background.default`), else the pack's (shell.bar_home), else stock.
     */
    fun barBackground(cr: ContentResolver): String =
        get(cr, KEY_BAR_BACKGROUND)?.takeIf { it in BAR_BACKGROUNDS }
            ?: SystemProperties.get("ro.retro.ui_bar_background.default", "").takeIf { it in BAR_BACKGROUNDS }
            ?: pack(cr).barHome?.takeIf { it in BAR_BACKGROUNDS }
            ?: BAR_BG_STOCK

    /** Resolved panel fill color, or null for the skin's own. */
    fun panelColor(cr: ContentResolver): Int? =
        parseColor(get(cr, KEY_PANEL_COLOR)) ?: pack(cr).panel

    /** Raw panel color setting for the draft UI (`pack` means follow the style). */
    fun panelColorChoice(cr: ContentResolver): String =
        get(cr, KEY_PANEL_COLOR)?.takeIf { it.startsWith("#") } ?: "pack"

    fun panelBlur(cr: ContentResolver): Boolean =
        when (get(cr, KEY_PANEL_BLUR)) {
            "1" -> true
            "0" -> false
            else -> pack(cr).blurEnabled == true
        }

    fun panelBlurRadius(cr: ContentResolver): Int =
        get(cr, KEY_PANEL_BLUR_RADIUS)?.toIntOrNull()?.takeIf { it >= 0 }
            ?: pack(cr).blurRadius
            ?: DEFAULT_BLUR_RADIUS

    fun dialogAlpha(cr: ContentResolver): Int? =
        parseAlphaOrColor(get(cr, KEY_DIALOG_ALPHA)) ?: pack(cr).dialogAlpha

    fun dialogBlurRadius(cr: ContentResolver): Int? =
        get(cr, KEY_DIALOG_BLUR_RADIUS)?.toIntOrNull()?.takeIf { it >= 0 }
            ?: pack(cr).dialogBlurRadius

    fun windowAlpha(cr: ContentResolver): Int? =
        parseAlphaOrColor(get(cr, KEY_WINDOW_ALPHA)) ?: pack(cr).windowAlpha

    /** Unfocused freeform alpha: explicit override, else same as focused [windowAlpha]. */
    fun windowAlphaUnfocused(cr: ContentResolver): Int? =
        parseAlphaOrColor(get(cr, KEY_WINDOW_ALPHA_UNFOCUSED))
            ?: pack(cr).windowAlphaUnfocused
            ?: windowAlpha(cr)

    fun windowBlurRadius(cr: ContentResolver): Int? =
        get(cr, KEY_WINDOW_BLUR_RADIUS)?.toIntOrNull()?.takeIf { it >= 0 }
            ?: pack(cr).windowBlurRadius

    /** Unfocused freeform blur: explicit override, else same as focused [windowBlurRadius]. */
    fun windowBlurRadiusUnfocused(cr: ContentResolver): Int? =
        get(cr, KEY_WINDOW_BLUR_RADIUS_UNFOCUSED)?.toIntOrNull()?.takeIf { it >= 0 }
            ?: pack(cr).windowBlurRadiusUnfocused
            ?: windowBlurRadius(cr)

    /** User freeform glass blocklist packages (no built-ins). */
    fun windowAlphaBlocklist(cr: ContentResolver): List<String> {
        val raw = get(cr, KEY_WINDOW_ALPHA_BLOCKLIST) ?: return emptyList()
        if (raw == "none") return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun parseColor(value: String?): Int? =
        value?.takeIf { it.startsWith("#") }?.let {
            runCatching { android.graphics.Color.parseColor(it) }.getOrNull()
        }

    private fun parseAlphaOrColor(value: String?): Int? {
        if (value.isNullOrEmpty()) return null
        if (value.startsWith("#")) return parseColor(value)
        return value.toIntOrNull()?.coerceIn(0, 255)
    }
}
