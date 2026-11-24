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
    /**
     * Last zone Apps occupied while enabled. The disabled preview stub and re-enable both use
     * this so Apps do not jump to the end slot when turned off.
     */
    var appsLast: BarZone = BarZone.CENTER,
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
        apps?.let { appsLast = it }
    }

    fun placeApps(zone: BarZone) {
        if (nav == zone) nav = apps ?: firstEmpty(excluding = zone) ?: BarZone.START
        apps = zone
        appsLast = zone
    }

    /** Turn Apps off while remembering [appsLast] for the dimmed stub / next enable. */
    fun disableApps() {
        apps?.let { appsLast = it }
        apps = null
    }

    /**
     * Turn Apps back on in [appsLast] when free, else the first empty zone (never on top of Nav).
     */
    fun enableApps(preferred: BarZone? = null): BarZone {
        val want = preferred ?: appsLast
        val zone =
            when {
                want != nav -> want
                else -> firstEmpty() ?: BarZone.entries.first { it != nav }
            }
        apps = zone
        appsLast = zone
        return zone
    }

    /** Tap: cycle Nav content alignment left → center → right → left. Zone unchanged. */
    fun cycleNavJustify() {
        navJustify = nextJustify(navJustify)
    }

    /**
     * Tap: cycle Apps alignment left → center → right → off the bar.
     * While off, the next tap puts Apps back at [appsLast] (or an empty zone) with left alignment.
     * @return whether Apps remain on the bar
     */
    fun cycleAppsJustify(): Boolean {
        if (apps == null) {
            enableApps()
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
                disableApps()
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
            val remembered =
                BarZone.fromGravity(appsGravity ?: StyleSettings.GRAVITY_CENTER).let { zone ->
                    if (zone == nav) {
                        BarZone.entries.firstOrNull { it != nav } ?: BarZone.CENTER
                    } else {
                        zone
                    }
                }
            val apps = if (appsEnabled) remembered else null
            return BarZones(
                nav = nav,
                apps = apps,
                appsLast = remembered,
                navJustify = BarJustify.fromValue(navJustify),
                appsJustify = BarJustify.fromValue(appsJustify),
            )
        }
    }
}
