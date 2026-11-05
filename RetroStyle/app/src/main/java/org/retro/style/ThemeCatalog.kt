// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.graphics.Color
import android.util.Log
import java.io.File
import org.json.JSONObject

/**
 * One theme pack: themes/<id>/theme.json, installed to /product/etc/retro-style/themes/<id>.json
 * next to its overlays. docs/THEME_SPEC.md describes every field.
 */
data class ThemePack(
    val id: String,
    val name: String,
    val summary: String,
    val category: String,
    val order: Int,
    /** Null means "the user's own wallpaper colors". */
    val seed: Int?,
    val paletteStyle: String,
    val night: String,
    val black: Boolean,
    val widgets: String,
    val corner: Float?,
    val skin: String,
    val panel: Int?,
    val accent: Int?,
    val barApps: Boolean,
) {
    /** Overlay package for one of [ThemeCatalog.TARGETS], whether or not the pack ships it. */
    fun overlay(target: String): String = "org.retro.theme.$id.${target.replace('_', '.')}"

    val isKitKatSkin: Boolean
        get() = skin == SKIN_KITKAT

    companion object {
        const val SKIN_STOCK = "stock"
        const val SKIN_KITKAT = "kitkat"
        const val NIGHT_SYSTEM = "system"
        const val NIGHT_DARK = "dark"
        const val NIGHT_LIGHT = "light"

        fun parse(json: JSONObject): ThemePack {
            val palette = json.optJSONObject("palette") ?: JSONObject()
            val shell = json.optJSONObject("shell") ?: JSONObject()
            val shape = json.optJSONObject("shape") ?: JSONObject()
            return ThemePack(
                id = json.getString("id"),
                name = json.getString("name"),
                summary = json.optString("summary"),
                category = json.optString("category", "community"),
                order = json.optInt("order", 0),
                seed =
                    if (palette.optString("source") == "preset") color(palette.optString("seed"))
                    else null,
                paletteStyle = palette.optString("style", "TONAL_SPOT"),
                night = json.optString("night", NIGHT_SYSTEM),
                black = json.optBoolean("black", false),
                widgets = json.optString("widgets", "default"),
                corner = shape.optString("corner").removeSuffix("dp").toFloatOrNull(),
                skin = shell.optString("skin", SKIN_STOCK),
                panel = color(shell.optString("panel")),
                accent = color(shell.optString("accent")),
                barApps = shell.optBoolean("bar_apps", true),
            )
        }

        private fun color(value: String): Int? =
            value.takeIf { it.startsWith("#") }?.let { runCatching { Color.parseColor(it) }.getOrNull() }
    }
}

/**
 * The theme packs installed on this device. retro-style's build installs one JSON file per pack
 * (themes/<id>/Android.bp, prebuilt_etc); any app may read /product/etc.
 */
object ThemeCatalog {
    private const val TAG = "RetroStyle"

    /** Where packs are installed. Later folders win when two have the same id. */
    private val DIRS =
        listOf("/system/etc/retro-style/themes", "/system_ext/etc/retro-style/themes", "/product/etc/retro-style/themes")

    /** Folders a pack may have overlays for (tools/theme_tool.py TARGETS). */
    const val TARGET_ANDROID = "android"
    const val TARGET_SYSTEMUI = "systemui"
    const val TARGET_SETTINGS = "settings"
    const val TARGET_LAUNCHER3 = "launcher3"
    const val TARGET_FONT = "font"

    /** Overlays the app switches itself (category org.retro.theme). */
    val SWITCHED_TARGETS = listOf(TARGET_ANDROID, TARGET_SYSTEMUI, TARGET_SETTINGS, TARGET_LAUNCHER3)

    /** Icon packs SystemUI applies from the theme JSON, by theme JSON category. */
    val ICON_TARGETS =
        mapOf(
            "icons_android" to "android.theme.customization.icon_pack.android",
            "icons_systemui" to "android.theme.customization.icon_pack.systemui",
            "icons_settings" to "android.theme.customization.icon_pack.settings",
            "icons_launcher" to "android.theme.customization.icon_pack.launcher",
        )

    /** Shown when no pack is installed (a build without retro-style's themes). */
    val FALLBACK =
        ThemePack(
            id = "material",
            name = "Material You",
            summary = "",
            category = "modern",
            order = 0,
            seed = null,
            paletteStyle = "TONAL_SPOT",
            night = ThemePack.NIGHT_SYSTEM,
            black = false,
            widgets = "default",
            corner = null,
            skin = ThemePack.SKIN_STOCK,
            panel = null,
            accent = null,
            barApps = true,
        )

    /** Settings groups, in the order the page shows them. */
    val CATEGORIES = listOf("modern", "android", "community")

    @Volatile private var cached: List<ThemePack>? = null

    fun packs(): List<ThemePack> =
        cached
            ?: load().also { cached = it }

    fun find(id: String?): ThemePack? = packs().firstOrNull { it.id == id }

    private fun load(): List<ThemePack> {
        val byId = linkedMapOf<String, ThemePack>()
        for (dir in DIRS) {
            val files = File(dir).listFiles { f -> f.name.endsWith(".json") } ?: continue
            for (file in files) {
                try {
                    val pack = ThemePack.parse(JSONObject(file.readText()))
                    byId[pack.id] = pack
                } catch (e: Exception) {
                    Log.w(TAG, "skipping theme pack $file", e)
                }
            }
        }
        val packs = byId.values.ifEmpty { listOf(FALLBACK) }
        return packs.sortedWith(
            compareBy<ThemePack>({ CATEGORIES.indexOf(it.category).let { i -> if (i < 0) 99 else i } }, { it.order }, { it.name })
        )
    }
}
