// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.content.ContentResolver

/**
 * In-memory UI style choices. The page edits this and only writes [StyleSettings] + runs
 * [ApplyWorker] when the user hits Apply.
 */
data class StyleDraft(
    var styleId: String,
    var variant: String,
    var layout: String,
    var barApps: Boolean,
    var zones: BarZones,
    var barBackground: String,
    var barButtons: List<String>,
    var anchor: String,
    var hideStatusBar: Boolean,
    var black: Boolean?,
) {
    fun pack(): ThemePack =
        ThemeCatalog.find(styleId)
            ?: ThemeCatalog.find(StyleSettings.STYLE_MATERIAL)
            ?: ThemeCatalog.packs().first()

    /** Effective layout: KitKat skins always use the tablet system bar. */
    fun effectiveLayout(): String =
        if (pack().isKitKatSkin) StyleSettings.LAYOUT_SYSTEM_BAR else layout

    fun isSystemBar(): Boolean = effectiveLayout() == StyleSettings.LAYOUT_SYSTEM_BAR

    fun copyOf(): StyleDraft =
        copy(
            zones = zones.copy(),
            barButtons = barButtons.toList(),
        )

    fun matches(other: StyleDraft): Boolean =
        styleId == other.styleId &&
            variant == other.variant &&
            effectiveLayout() == other.effectiveLayout() &&
            barApps == other.barApps &&
            zones.nav == other.zones.nav &&
            zones.apps == other.zones.apps &&
            zones.appsLast == other.zones.appsLast &&
            zones.navJustify == other.zones.navJustify &&
            zones.appsJustify == other.zones.appsJustify &&
            barBackground == other.barBackground &&
            barButtons == other.barButtons &&
            anchor == other.anchor &&
            hideStatusBar == other.hideStatusBar &&
            black == other.black

    fun commit(cr: ContentResolver) {
        val pack = pack()
        StyleSettings.put(cr, StyleSettings.KEY_STYLE, styleId)
        StyleSettings.put(cr, StyleSettings.KEY_VARIANT, variant)
        StyleSettings.put(
            cr,
            StyleSettings.KEY_LAYOUT,
            if (pack.isKitKatSkin) StyleSettings.LAYOUT_SYSTEM_BAR else layout,
        )
        StyleSettings.put(cr, StyleSettings.KEY_BAR_APPS, if (barApps) "1" else "0")
        StyleSettings.put(cr, StyleSettings.KEY_BAR_NAV_GRAVITY, zones.nav.gravity)
        StyleSettings.put(cr, StyleSettings.KEY_BAR_NAV_JUSTIFY, zones.navJustify.value)
        // Always persist the apps zone (including when disabled) so the stub / next enable
        // reopen in the same slot instead of jumping to end.
        val appsZone = zones.apps ?: zones.appsLast
        StyleSettings.put(cr, StyleSettings.KEY_BAR_APPS_GRAVITY, appsZone.gravity)
        StyleSettings.put(cr, StyleSettings.KEY_BAR_APPS_JUSTIFY, zones.appsJustify.value)
        StyleSettings.put(cr, StyleSettings.KEY_BAR_BACKGROUND, barBackground)
        StyleSettings.put(
            cr,
            StyleSettings.KEY_BAR_BUTTONS,
            barButtons.joinToString(",").ifEmpty { "none" },
        )
        StyleSettings.put(cr, StyleSettings.KEY_ANCHOR, anchor)
        StyleSettings.put(cr, StyleSettings.KEY_HIDE_STATUS_BAR, if (hideStatusBar) "1" else "0")
        black?.let { StyleSettings.put(cr, StyleSettings.KEY_BLACK, if (it) "1" else "0") }
    }

    companion object {
        fun load(cr: ContentResolver): StyleDraft {
            val apps = StyleSettings.barApps(cr)
            return StyleDraft(
                styleId = StyleSettings.style(cr),
                variant = StyleSettings.variant(cr),
                layout = StyleSettings.getLayoutChoice(cr),
                barApps = apps,
                zones =
                    BarZones.fromSettings(
                        StyleSettings.navGravity(cr),
                        StyleSettings.appsGravity(cr),
                        apps,
                        StyleSettings.navJustify(cr),
                        StyleSettings.appsJustify(cr),
                    ),
                barBackground = StyleSettings.barBackground(cr),
                barButtons = StyleSettings.barButtons(cr),
                anchor = StyleSettings.anchor(cr),
                hideStatusBar = StyleSettings.hideStatusBar(cr),
                black =
                    when (android.provider.Settings.Secure.getString(cr, StyleSettings.KEY_BLACK)) {
                        "1" -> true
                        "0" -> false
                        else -> null
                    },
            )
        }
    }
}
