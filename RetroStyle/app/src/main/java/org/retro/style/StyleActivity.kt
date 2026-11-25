// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton

/**
 * Settings > System > UI style (also Settings > Display > Appearance). Edits a [StyleDraft]
 * with a live system-bar preview; Apply writes
 * secure settings and runs [ApplyWorker] once.
 */
class StyleActivity : AppCompatActivity() {

    private lateinit var draft: StyleDraft
    private lateinit var applied: StyleDraft
    private lateinit var preview: SystemBarPreviewView
    private lateinit var applyButton: MaterialButton
    private lateinit var resetButton: MaterialButton
    private val styleIds = linkedMapOf<Int, String>()
    private var syncingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_RetroStyle)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_style)

        applied = StyleDraft.load(contentResolver)
        draft = applied.copyOf()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.inflateMenu(R.menu.menu_style)
        toolbar.setOnMenuItemClickListener {
            prepareAdvancedMenu(toolbar.menu)
            onAdvancedMenu(it)
        }

        preview = findViewById(R.id.system_bar_preview)
        preview.onOccupantDropped = ::onOccupantDropped
        preview.onOccupantTapped = ::onOccupantTapped

        applyButton = findViewById(R.id.apply)
        resetButton = findViewById(R.id.reset)
        applyButton.setOnClickListener { applyDraft() }
        resetButton.setOnClickListener {
            draft = applied.copyOf()
            bindAll()
        }

        findViewById<MaterialSwitch>(R.id.bar_apps).setOnCheckedChangeListener { _, checked ->
            if (syncingUi) return@setOnCheckedChangeListener
            draft.barApps = checked
            if (checked && draft.zones.apps == null) {
                draft.zones.enableApps()
            }
            if (!checked) draft.zones.disableApps()
            refreshPreviewAndDirty()
        }

        buildStyleList()
        bindAll()
    }

    override fun onResume() {
        super.onResume()
        // Reload applied in case another process changed settings; keep draft if dirty.
        val latest = StyleDraft.load(contentResolver)
        if (!isDirty()) {
            applied = latest
            draft = latest.copyOf()
            bindAll()
        } else {
            applied = latest
            updateDirtyState()
        }
    }

    private fun isDirty(): Boolean = !draft.matches(applied)

    private fun updateDirtyState() {
        val dirty = isDirty()
        applyButton.isEnabled = dirty
        resetButton.isEnabled = dirty
    }

    private fun refreshPreviewAndDirty() {
        preview.zones = draft.zones.copy()
        preview.appsEnabled = draft.barApps && draft.isSystemBar()
        preview.extraButtons = draft.barButtons
        preview.applyPackChrome(draft.pack())
        preview.visibility = if (draft.isSystemBar()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.bar_apps).visibility =
            if (draft.isSystemBar()) View.VISIBLE else View.GONE
        updateDirtyState()
    }

    /** Drag: place Nav/Apps on a bar slot (left / center / right). */
    private fun onOccupantDropped(occupant: BarOccupant, zone: BarZone) {
        if (!draft.isSystemBar()) return
        when (occupant) {
            BarOccupant.NAV -> draft.zones.placeNav(zone)
            BarOccupant.APPS -> {
                draft.barApps = true
                draft.zones.placeApps(zone)
            }
        }
        syncBarAppsSwitch()
        refreshPreviewAndDirty()
    }

    /** Tap: cycle justification (left / center / right), or disable Apps. */
    private fun onOccupantTapped(occupant: BarOccupant) {
        if (!draft.isSystemBar()) return
        when (occupant) {
            BarOccupant.NAV -> draft.zones.cycleNavJustify()
            BarOccupant.APPS -> draft.barApps = draft.zones.cycleAppsJustify()
        }
        syncBarAppsSwitch()
        refreshPreviewAndDirty()
    }

    private fun syncBarAppsSwitch() {
        syncingUi = true
        findViewById<MaterialSwitch>(R.id.bar_apps).isChecked = draft.barApps
        syncingUi = false
    }

    private fun applyDraft() {
        draft.commit(contentResolver)
        applied = draft.copyOf()
        updateDirtyState()
        ApplyWorker.apply(this)
    }

    private fun bindAll() {
        syncingUi = true
        bindStyleGroup()
        bindLayout()
        findViewById<MaterialSwitch>(R.id.bar_apps).isChecked = draft.barApps
        refreshPreviewAndDirty()
        syncingUi = false
        prepareAdvancedMenu(findViewById<MaterialToolbar>(R.id.toolbar).menu)
    }

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
                RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            styleIds[button.id] = pack.id
        }
    }

    private fun bindStyleGroup() {
        val group = findViewById<RadioGroup>(R.id.style_group)
        group.setOnCheckedChangeListener(null)
        styleIds.entries.firstOrNull { it.value == draft.styleId }?.let { group.check(it.key) }
        group.setOnCheckedChangeListener { _, checkedId ->
            if (syncingUi) return@setOnCheckedChangeListener
            val id = styleIds[checkedId] ?: return@setOnCheckedChangeListener
            draft.styleId = id
            if (draft.pack().isKitKatSkin) {
                draft.layout = StyleSettings.LAYOUT_SYSTEM_BAR
            }
            bindLayout()
            refreshPreviewAndDirty()
            prepareAdvancedMenu(findViewById<MaterialToolbar>(R.id.toolbar).menu)
        }
    }

    private fun bindLayout() {
        val kitKat = draft.pack().isKitKatSkin
        if (kitKat) draft.layout = StyleSettings.LAYOUT_SYSTEM_BAR
        val group = findViewById<RadioGroup>(R.id.layout_group)
        group.setOnCheckedChangeListener(null)
        group.check(
            if (draft.effectiveLayout() == StyleSettings.LAYOUT_SYSTEM_BAR) R.id.layout_system_bar
            else R.id.layout_stock
        )
        findViewById<MaterialRadioButton>(R.id.layout_stock).apply {
            isEnabled = !kitKat
            text =
                getString(
                    if (kitKat) R.string.layout_stock_kitkat_disabled else R.string.layout_stock
                )
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            if (syncingUi) return@setOnCheckedChangeListener
            draft.layout =
                if (checkedId == R.id.layout_system_bar) StyleSettings.LAYOUT_SYSTEM_BAR
                else StyleSettings.LAYOUT_STOCK
            refreshPreviewAndDirty()
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

    private fun prepareAdvancedMenu(menu: Menu) {
        val pack = draft.pack()
        menu.findItem(R.id.menu_variant)?.isVisible = pack.isKitKatSkin && pack.panel == null
        menu.findItem(R.id.menu_black)?.apply {
            isVisible = StyleApplier.hasBlackTheme(contentResolver)
            isChecked = draft.black ?: StyleSettings.defaultBlack(contentResolver)
        }
        menu.findItem(R.id.menu_bar_background)?.isEnabled = draft.isSystemBar()
        menu.findItem(R.id.menu_bar_buttons)?.isEnabled = draft.isSystemBar()
        menu.findItem(R.id.menu_panel_blur)?.isChecked =
            draft.panelBlur ?: (draft.pack().blurEnabled == true)
        menu.findItem(R.id.menu_hide_status_bar)?.apply {
            isEnabled = draft.isSystemBar()
            isChecked = draft.hideStatusBar
        }
    }

    private fun onAdvancedMenu(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_variant -> showVariantDialog()
            R.id.menu_black -> {
                val next = !(draft.black ?: StyleSettings.defaultBlack(contentResolver))
                draft.black = next
                item.isChecked = next
                updateDirtyState()
            }
            R.id.menu_bar_background -> showBarBackgroundDialog()
            R.id.menu_bar_buttons -> showBarButtonsDialog()
            R.id.menu_panel_color -> showPanelColorDialog()
            R.id.menu_panel_blur -> {
                val current = draft.panelBlur ?: (draft.pack().blurEnabled == true)
                draft.panelBlur = !current
                item.isChecked = !current
                updateDirtyState()
            }
            R.id.menu_panel_blur_radius -> showPanelBlurRadiusDialog()
            R.id.menu_dialog_transparency -> showDialogTransparencyDialog()
            R.id.menu_window_transparency -> showWindowTransparencyDialog()
            R.id.menu_anchor -> showAnchorDialog()
            R.id.menu_hide_status_bar -> {
                draft.hideStatusBar = !draft.hideStatusBar
                item.isChecked = draft.hideStatusBar
                updateDirtyState()
            }
            else -> return false
        }
        return true
    }

    private fun showVariantDialog() {
        val values =
            arrayOf(StyleSettings.VARIANT_KITKAT_STOCK, StyleSettings.VARIANT_HOLO_UNIFIED)
        val labels =
            arrayOf(getString(R.string.variant_kitkat_stock), getString(R.string.variant_holo_unified))
        val checked = values.indexOf(draft.variant).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.section_variant)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                draft.variant = values[which]
                updateDirtyState()
                dialog.dismiss()
            }
            .show()
    }

    private fun showBarBackgroundDialog() {
        val values = StyleSettings.BAR_BACKGROUNDS.toTypedArray()
        val labels =
            arrayOf(
                getString(R.string.bar_background_stock),
                getString(R.string.bar_background_solid),
                getString(R.string.bar_background_gradient),
                getString(R.string.bar_background_translucent),
                getString(R.string.bar_background_transparent),
            )
        val checked = values.indexOf(draft.barBackground).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.section_bar_background)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                draft.barBackground = values[which]
                updateDirtyState()
                dialog.dismiss()
            }
            .show()
    }

    private fun showBarButtonsDialog() {
        val labels =
            arrayOf(
                getString(R.string.bar_button_vol_down),
                getString(R.string.bar_button_vol_up),
                getString(R.string.bar_button_screenshot),
                getString(R.string.bar_button_power),
            )
        val keys = StyleSettings.BAR_BUTTONS.toTypedArray()
        val checked = BooleanArray(keys.size) { keys[it] in draft.barButtons }
        AlertDialog.Builder(this)
            .setTitle(R.string.section_bar_buttons)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                draft.barButtons = keys.filterIndexed { i, _ -> checked[i] }
                refreshPreviewAndDirty()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAnchorDialog() {
        val values = arrayOf(StyleSettings.ANCHOR_TOP_END, StyleSettings.ANCHOR_BOTTOM_END)
        val labels =
            arrayOf(getString(R.string.anchor_top_end), getString(R.string.anchor_bottom_end))
        val checked = values.indexOf(draft.anchor).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.section_anchor)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                draft.anchor = values[which]
                updateDirtyState()
                dialog.dismiss()
            }
            .show()
    }

    private fun showPanelColorDialog() {
        val values = StyleSettings.PANEL_COLOR_PRESETS.toTypedArray()
        val labels =
            arrayOf(
                getString(R.string.panel_color_pack),
                getString(R.string.panel_color_opaque_black),
                getString(R.string.panel_color_near_black),
                getString(R.string.panel_color_holo_navy),
                getString(R.string.panel_color_glass),
                getString(R.string.panel_color_bar),
                getString(R.string.panel_color_frost),
            )
        val checked = values.indexOf(draft.panelColorChoice).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.section_panel_color)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                draft.panelColorChoice = values[which]
                updateDirtyState()
                dialog.dismiss()
            }
            .show()
    }

    private fun showPanelBlurRadiusDialog() {
        val values = arrayOf(null, 15, 25, 40)
        val labels =
            arrayOf(
                getString(R.string.blur_radius_pack),
                getString(R.string.blur_radius_soft),
                getString(R.string.blur_radius_medium),
                getString(R.string.blur_radius_strong),
            )
        val checked = values.indexOf(draft.panelBlurRadius).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.section_panel_blur_radius)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                draft.panelBlurRadius = values[which]
                if (values[which] != null && draft.panelBlur == null) {
                    draft.panelBlur = true
                }
                updateDirtyState()
                dialog.dismiss()
            }
            .show()
    }

    private fun showDialogTransparencyDialog() {
        // pack default / off / glass / strong → (alpha, blurRadius)
        val choices =
            listOf(
                null to null,
                255 to 0,
                200 to 25,
                140 to 40,
            )
        val labels =
            arrayOf(
                getString(R.string.transparency_pack),
                getString(R.string.transparency_off),
                getString(R.string.transparency_glass),
                getString(R.string.transparency_strong),
            )
        val checked =
            choices
                .indexOfFirst {
                    it.first == draft.dialogAlpha && it.second == draft.dialogBlurRadius
                }
                .coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.section_dialog_transparency)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                draft.dialogAlpha = choices[which].first
                draft.dialogBlurRadius = choices[which].second
                updateDirtyState()
                dialog.dismiss()
            }
            .show()
    }

    private fun showWindowTransparencyDialog() {
        val choices =
            listOf(
                null to null,
                255 to 0,
                230 to 20,
                180 to 30,
            )
        val labels =
            arrayOf(
                getString(R.string.transparency_pack),
                getString(R.string.transparency_off),
                getString(R.string.transparency_glass),
                getString(R.string.transparency_strong),
            )
        val checked =
            choices
                .indexOfFirst {
                    it.first == draft.windowAlpha && it.second == draft.windowBlurRadius
                }
                .coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.section_window_transparency)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                draft.windowAlpha = choices[which].first
                draft.windowBlurRadius = choices[which].second
                updateDirtyState()
                dialog.dismiss()
            }
            .show()
    }
}
