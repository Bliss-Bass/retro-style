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
    const val KEY_BAR_BUTTONS = "retro_ui_bar_buttons"
    const val KEY_CLOCK_SECONDS = "retro_ui_bar_clock_seconds"
    const val KEY_BAR_BACKGROUND = "retro_ui_bar_background"
    const val KEY_BLACK = "retro_ui_black"

    /** System bar background on the home screen; stock leaves it to the launcher. */
    const val BAR_BG_STOCK = "stock"
    val BAR_BACKGROUNDS = listOf(BAR_BG_STOCK, "solid", "gradient", "translucent", "transparent")

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

    fun layout(cr: ContentResolver): String =
        (get(cr, KEY_LAYOUT) ?: SystemProperties.get("ro.retro.ui_layout.default", "")).takeIf {
            it == LAYOUT_SYSTEM_BAR
        } ?: LAYOUT_STOCK

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

    /** Pure black dark mode (LineageOS black theme): the user's choice, else the pack's `black`. */
    fun black(cr: ContentResolver): Boolean =
        when (get(cr, KEY_BLACK)) {
            "1" -> true
            "0" -> false
            else -> pack(cr).black
        }

    /** The system bar's home screen background: the user's, else the pack's (shell.bar_home). */
    fun barBackground(cr: ContentResolver): String =
        get(cr, KEY_BAR_BACKGROUND)?.takeIf { it in BAR_BACKGROUNDS }
            ?: pack(cr).barHome?.takeIf { it in BAR_BACKGROUNDS }
            ?: BAR_BG_STOCK
}
