// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

/**
 * Where a section sits on the tablet system bar (tray is fixed past the right). Dragging sets
 * the zone; sections are exclusive — dropping onto an occupied slot swaps.
 */
enum class BarZone(val gravity: String) {
    START(StyleSettings.GRAVITY_START),
    CENTER(StyleSettings.GRAVITY_CENTER),
    END(StyleSettings.GRAVITY_END);

    companion object {
        fun fromGravity(gravity: String): BarZone =
            entries.firstOrNull { it.gravity == gravity } ?: START
    }
}

/**
 * How content lines up inside a section's zone. Tapping cycles this; it does not move the zone.
 */
enum class BarJustify(val value: String) {
    START(StyleSettings.GRAVITY_START),
    CENTER(StyleSettings.GRAVITY_CENTER),
    END(StyleSettings.GRAVITY_END);

    companion object {
        fun fromValue(value: String): BarJustify =
            entries.firstOrNull { it.value == value } ?: START
    }
}

enum class BarOccupant {
    NAV,
    APPS,
}

data class BarZones(
    /** Zone Nav occupies (drag). */
    var nav: BarZone = BarZone.START,
    /** Zone Apps occupies (drag); null when Apps is off the bar. */
    var apps: BarZone? = BarZone.CENTER,
    /** Content alignment inside Nav's zone (tap). */
    var navJustify: BarJustify = BarJustify.START,
    /** Content alignment inside Apps' zone (tap). */
    var appsJustify: BarJustify = BarJustify.CENTER,
) {
    fun occupantAt(zone: BarZone): BarOccupant? =
        when {
            nav == zone -> BarOccupant.NAV
            apps == zone -> BarOccupant.APPS
            else -> null
        }

    fun placeNav(zone: BarZone) {
        if (apps == zone) apps = nav
        nav = zone
    }

    fun placeApps(zone: BarZone) {
        if (nav == zone) nav = apps ?: firstEmpty(excluding = zone) ?: BarZone.START
        apps = zone
    }

    /** Tap: cycle Nav content alignment left → center → right → left. Zone unchanged. */
    fun cycleNavJustify() {
        navJustify = nextJustify(navJustify)
    }

    /**
     * Tap: cycle Apps alignment left → center → right → off the bar.
     * While off, the next tap puts Apps back (empty zone preferred) with left alignment.
     * @return whether Apps remain on the bar
     */
    fun cycleAppsJustify(): Boolean {
        if (apps == null) {
            apps = firstEmpty() ?: BarZone.CENTER
            appsJustify = BarJustify.START
            return true
        }
        return when (appsJustify) {
            BarJustify.START -> {
                appsJustify = BarJustify.CENTER
                true
            }
            BarJustify.CENTER -> {
                appsJustify = BarJustify.END
                true
            }
            BarJustify.END -> {
                apps = null
                false
            }
        }
    }

    private fun nextJustify(from: BarJustify): BarJustify {
        val order = BarJustify.entries
        return order[(order.indexOf(from) + 1) % order.size]
    }

    private fun firstEmpty(excluding: BarZone? = null): BarZone? =
        BarZone.entries.firstOrNull { it != excluding && occupantAt(it) == null }

    companion object {
        fun fromSettings(
            navGravity: String,
            appsGravity: String?,
            appsEnabled: Boolean,
            navJustify: String,
            appsJustify: String,
        ): BarZones {
            var nav = BarZone.fromGravity(navGravity)
            var apps =
                if (appsEnabled) BarZone.fromGravity(appsGravity ?: StyleSettings.GRAVITY_CENTER)
                else null
            if (apps != null && apps == nav) {
                apps = BarZone.entries.firstOrNull { it != nav } ?: BarZone.CENTER
            }
            return BarZones(
                nav = nav,
                apps = apps,
                navJustify = BarJustify.fromValue(navJustify),
                appsJustify = BarJustify.fromValue(appsJustify),
            )
        }
    }
}
