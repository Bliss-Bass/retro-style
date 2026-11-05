// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton

/**
 * Settings > System > UI style. Each control writes one [StyleSettings] value and applies it right
 * away ([ApplyWorker]); SystemUI and the launcher restart when they need to, and the desktop
 * windows (this page included) come back afterwards. The style list shows every installed theme
 * pack ([ThemeCatalog]).
 */
class StyleActivity : AppCompatActivity() {

    private var pageTheme = 0
    private val styleIds = linkedMapOf<Int, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The page follows the chosen pack: Holo Dark packs get the Holo look, square-cornered
        // packs square controls, all of them the system palette the pack sets.
        pageTheme = pageTheme(StyleSettings.pack(contentResolver))
        setTheme(pageTheme)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_style)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        buildStyleList()
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    private fun pageTheme(pack: ThemePack): Int =
        when {
            pack.widgets == "holo" && pack.night == ThemePack.NIGHT_DARK -> R.style.Theme_RetroStyle_KitKat
            pack.widgets != "default" || (pack.corner ?: Float.MAX_VALUE) <= 4f ->
                R.style.Theme_RetroStyle_Square
            else -> R.style.Theme_RetroStyle
        }

    /** One radio button per pack, under a header per category. */
    private fun buildStyleList() {
        val group = findViewById<RadioGroup>(R.id.style_group)
        group.removeAllViews()
        styleIds.clear()
        var category: String? = null
        for (pack in ThemeCatalog.packs()) {
            if (pack.category != category) {
                category = pack.category
                categoryLabel(category)?.let { group.addView(header(it)) }
            }
            val button =
                MaterialRadioButton(this).apply {
                    id = View.generateViewId()
                    text = label(pack)
                    minHeight = dp(56)
                    setPadding(paddingLeft, dp(6), paddingRight, dp(6))
                }
            group.addView(
                button,
                RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT),
            )
            styleIds[button.id] = pack.id
        }
    }

    private fun categoryLabel(category: String): Int? =
        when (category) {
            "modern" -> R.string.category_modern
            "android" -> R.string.category_android
            "community" -> R.string.category_community
            else -> null
        }

    private fun header(text: Int): TextView =
        TextView(this, null, 0, R.style.RetroStyle_Category).apply { setText(text) }

    /** The pack name, with its summary in smaller secondary text under it. */
    private fun label(pack: ThemePack): CharSequence {
        if (pack.summary.isEmpty()) return pack.name
        val secondary = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, secondary, true)
        val color = getColorStateList(secondary.resourceId).defaultColor
        return SpannableStringBuilder(pack.name).apply {
            append('\n')
            val start = length
            append(pack.summary)
            setSpan(RelativeSizeSpan(0.85f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun bind() {
        val cr = contentResolver
        bindGroup(
            R.id.style_group,
            StyleSettings.KEY_STYLE,
            StyleSettings.style(cr),
            styleIds,
            onChanged = {
                // The pack may bring its own defaults (black, apps, home background).
                bindBlack()
                bindSystemBarOptions()
                bindVariant()
                if (pageTheme(StyleSettings.pack(contentResolver)) != pageTheme) recreate()
            },
        )
        bindVariant()
        bindBlack()
        bindGroup(
            R.id.variant_group,
            StyleSettings.KEY_VARIANT,
            StyleSettings.variant(cr),
            mapOf(
                R.id.variant_kitkat_stock to StyleSettings.VARIANT_KITKAT_STOCK,
                R.id.variant_holo_unified to StyleSettings.VARIANT_HOLO_UNIFIED,
            ),
        )
        bindGroup(
            R.id.layout_group,
            StyleSettings.KEY_LAYOUT,
            StyleSettings.layout(cr),
            mapOf(
                R.id.layout_stock to StyleSettings.LAYOUT_STOCK,
                R.id.layout_system_bar to StyleSettings.LAYOUT_SYSTEM_BAR,
            ),
            onChanged = ::bindSystemBarOptions,
        )
        bindSystemBarOptions()
        bindGroup(
            R.id.anchor_group,
            StyleSettings.KEY_ANCHOR,
            StyleSettings.anchor(cr),
            mapOf(
                R.id.anchor_top_end to StyleSettings.ANCHOR_TOP_END,
                R.id.anchor_bottom_end to StyleSettings.ANCHOR_BOTTOM_END,
            ),
        )
        findViewById<MaterialSwitch>(R.id.hide_status_bar).apply {
            setOnCheckedChangeListener(null)
            isChecked = StyleSettings.hideStatusBar(cr)
            setOnCheckedChangeListener { _, checked ->
                save(StyleSettings.KEY_HIDE_STATUS_BAR, if (checked) "1" else "0")
            }
        }
    }

    /** Pure black follows the pack until the user flips it; only on builds with the black theme. */
    private fun bindBlack() {
        val cr = contentResolver
        findViewById<MaterialSwitch>(R.id.black_theme).apply {
            visibility = if (StyleApplier.hasBlackTheme(cr)) View.VISIBLE else View.GONE
            setOnCheckedChangeListener(null)
            isChecked = StyleSettings.black(cr)
            setOnCheckedChangeListener { _, checked ->
                save(StyleSettings.KEY_BLACK, if (checked) "1" else "0")
            }
        }
    }

    /** The KitKat panel colors only apply to packs with the KitKat skin and no panel color. */
    private fun bindVariant() {
        val pack = StyleSettings.pack(contentResolver)
        val visible = if (pack.isKitKatSkin && pack.panel == null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.variant_section).visibility = visible
        findViewById<View>(R.id.variant_group).visibility = visible
    }

    /** Apps, their position and the extra buttons only apply to the tablet system bar. */
    private fun bindSystemBarOptions() {
        val cr = contentResolver
        val systemBar = StyleSettings.layout(cr) == StyleSettings.LAYOUT_SYSTEM_BAR
        val apps = StyleSettings.barApps(cr)
        findViewById<MaterialSwitch>(R.id.bar_apps).apply {
            setOnCheckedChangeListener(null)
            isChecked = apps
            isEnabled = systemBar
            setOnCheckedChangeListener { _, checked ->
                save(StyleSettings.KEY_BAR_APPS, if (checked) "1" else "0")
                bindSystemBarOptions()
            }
        }
        bindGroup(
            R.id.apps_gravity_group,
            StyleSettings.KEY_BAR_APPS_GRAVITY,
            StyleSettings.appsGravity(cr),
            mapOf(
                R.id.apps_gravity_start to StyleSettings.GRAVITY_START,
                R.id.apps_gravity_center to StyleSettings.GRAVITY_CENTER,
                R.id.apps_gravity_end to StyleSettings.GRAVITY_END,
            ),
        )
        val group = findViewById<RadioGroup>(R.id.apps_gravity_group)
        for (i in 0 until group.childCount) group.getChildAt(i).isEnabled = systemBar && apps

        bindGroup(
            R.id.bar_background_group,
            StyleSettings.KEY_BAR_BACKGROUND,
            StyleSettings.barBackground(cr),
            mapOf(
                R.id.bar_background_stock to "stock",
                R.id.bar_background_solid to "solid",
                R.id.bar_background_gradient to "gradient",
                R.id.bar_background_translucent to "translucent",
                R.id.bar_background_transparent to "transparent",
            ),
        )
        val backgrounds = findViewById<RadioGroup>(R.id.bar_background_group)
        for (i in 0 until backgrounds.childCount) backgrounds.getChildAt(i).isEnabled = systemBar

        val buttons = StyleSettings.barButtons(cr)
        mapOf(
                R.id.bar_button_vol_down to "vol_down",
                R.id.bar_button_vol_up to "vol_up",
                R.id.bar_button_screenshot to "screenshot",
                R.id.bar_button_power to "power",
            )
            .forEach { (id, button) ->
                findViewById<MaterialSwitch>(id).apply {
                    setOnCheckedChangeListener(null)
                    isChecked = button in buttons
                    isEnabled = systemBar
                    setOnCheckedChangeListener { _, checked ->
                        StyleSettings.setBarButton(cr, button, checked)
                        ApplyWorker.apply(this@StyleActivity)
                    }
                }
            }
    }

    private fun save(key: String, value: String) {
        StyleSettings.put(contentResolver, key, value)
        ApplyWorker.apply(this)
    }

    private fun bindGroup(
        groupId: Int,
        key: String,
        current: String,
        values: Map<Int, String>,
        onChanged: () -> Unit = {},
    ) {
        val group = findViewById<RadioGroup>(groupId)
        group.setOnCheckedChangeListener(null)
        values.entries.firstOrNull { it.value == current }?.let { group.check(it.key) }
        group.setOnCheckedChangeListener { _, checkedId ->
            values[checkedId]?.let {
                save(key, it)
                onChanged()
            }
        }
    }
}
