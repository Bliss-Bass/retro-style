// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

/**
 * Exclusive left / center / right zones on the tablet system bar (tray is fixed at the screen
 * end, past right). Nav and apps each occupy at most one zone; placing into an occupied zone
 * swaps. Justification for Launcher3 is derived from the zone.
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

enum class BarOccupant {
    NAV,
    APPS,
}

data class BarZones(
    var nav: BarZone = BarZone.START,
    /** Null when apps are off the bar. */
    var apps: BarZone? = BarZone.CENTER,
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

    fun clear(zone: BarZone) {
        when (occupantAt(zone)) {
            // Move the occupant to another empty zone so this one frees up.
            BarOccupant.NAV -> nav = firstEmpty(excluding = zone) ?: BarZone.START
            BarOccupant.APPS -> apps = firstEmpty(excluding = zone)
            null -> Unit
        }
    }

    /**
     * Cycle this zone: empty → Nav → Apps → empty (Apps skipped when [appsEnabled] is false).
     * Moving an occupant leaves its previous zone empty unless a swap is required.
     */
    fun cycleAt(zone: BarZone, appsEnabled: Boolean) {
        if (!appsEnabled) apps = null
        val here = occupantAt(zone)
        val next =
            when {
                !appsEnabled ->
                    when (here) {
                        null -> BarOccupant.NAV
                        else -> null
                    }
                else ->
                    when (here) {
                        null -> BarOccupant.NAV
                        BarOccupant.NAV -> BarOccupant.APPS
                        BarOccupant.APPS -> null
                    }
            }
        when (next) {
            null -> clear(zone)
            BarOccupant.NAV -> placeNav(zone)
            BarOccupant.APPS -> placeApps(zone)
        }
        if (!appsEnabled) apps = null
    }

    /** Place [tool] into [zone] (swap if needed). */
    fun place(tool: BarOccupant, zone: BarZone, appsEnabled: Boolean) {
        when (tool) {
            BarOccupant.NAV -> placeNav(zone)
            BarOccupant.APPS -> if (appsEnabled) placeApps(zone)
        }
        if (!appsEnabled) apps = null
    }

    private fun firstEmpty(excluding: BarZone? = null): BarZone? =
        BarZone.entries.firstOrNull { it != excluding && occupantAt(it) == null }

    companion object {
        fun fromGravities(navGravity: String, appsGravity: String?, appsEnabled: Boolean): BarZones {
            var nav = BarZone.fromGravity(navGravity)
            var apps = if (appsEnabled) BarZone.fromGravity(appsGravity ?: StyleSettings.GRAVITY_CENTER) else null
            if (apps != null && apps == nav) {
                apps = BarZone.entries.firstOrNull { it != nav } ?: BarZone.CENTER
            }
            return BarZones(nav = nav, apps = apps)
        }
    }
}
