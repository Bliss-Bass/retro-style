// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.slider.Slider

/**
 * Settings > System > UI style (also Settings > Display > Appearance). Edits a [StyleDraft]
 * with a live system-bar preview; Apply writes secure settings and runs [ApplyWorker] once.
 *
 * Advanced options (including Material sliders for shade / dialog / freeform transparency) live
 * in an expanding panel on the main scroll, not a ⋮ overflow menu.
 */
class StyleActivity : AppCompatActivity() {

    private lateinit var draft: StyleDraft
    private lateinit var applied: StyleDraft
    private lateinit var preview: SystemBarPreviewView
    private lateinit var applyButton: MaterialButton
    private lateinit var resetButton: MaterialButton
    private lateinit var advancedBody: View
    private lateinit var advancedExpandIcon: ImageView
    private val styleIds = linkedMapOf<Int, String>()
    private var syncingUi = false
    private var advancedExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_RetroStyle)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_style)

        applied = StyleDraft.load(contentResolver)
        draft = applied.copyOf()
        advancedExpanded = savedInstanceState?.getBoolean(STATE_ADVANCED) == true

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
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

        advancedBody = findViewById(R.id.advanced_body)
        advancedExpandIcon = findViewById(R.id.advanced_expand_icon)
        findViewById<View>(R.id.advanced_header).setOnClickListener {
            advancedExpanded = !advancedExpanded
            updateAdvancedExpanded()
        }
        updateAdvancedExpanded()

        findViewById<MaterialSwitch>(R.id.bar_apps).setOnCheckedChangeListener { _, checked ->
            if (syncingUi) return@setOnCheckedChangeListener
            draft.barApps = checked
            if (checked && draft.zones.apps == null) draft.zones.enableApps()
            if (!checked) draft.zones.disableApps()
            refreshPreviewAndDirty()
        }

        wireAdvancedControls()
        buildStyleList()
        bindAll()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ADVANCED, advancedExpanded)
    }

    override fun onResume() {
        super.onResume()
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

    private fun updateAdvancedExpanded() {
        advancedBody.visibility = if (advancedExpanded) View.VISIBLE else View.GONE
        advancedExpandIcon.rotation = if (advancedExpanded) 180f else 0f
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
        bindAdvanced()
        refreshPreviewAndDirty()
        syncingUi = false
    }

    private fun wireAdvancedControls() {
        findViewById<MaterialSwitch>(R.id.advanced_black).setOnCheckedChangeListener { _, checked ->
            if (syncingUi) return@setOnCheckedChangeListener
            draft.black = checked
            updateDirtyState()
        }

        findViewById<RadioGroup>(R.id.advanced_variant_group).setOnCheckedChangeListener {
            _,
            checkedId ->
            if (syncingUi) return@setOnCheckedChangeListener
            draft.variant =
                if (checkedId == R.id.variant_holo_unified) StyleSettings.VARIANT_HOLO_UNIFIED
                else StyleSettings.VARIANT_KITKAT_STOCK
            updateDirtyState()
        }

        findViewById<MaterialButton>(R.id.advanced_bar_background).setOnClickListener {
            showBarBackgroundDialog()
        }
        findViewById<MaterialButton>(R.id.advanced_bar_buttons).setOnClickListener {
            showBarButtonsDialog()
        }
        findViewById<MaterialButton>(R.id.advanced_panel_color).setOnClickListener {
            showPanelColorDialog()
        }

        findViewById<RadioGroup>(R.id.advanced_anchor_group).setOnCheckedChangeListener {
            _,
            checkedId ->
            if (syncingUi) return@setOnCheckedChangeListener
            draft.anchor =
                if (checkedId == R.id.anchor_top_end) StyleSettings.ANCHOR_TOP_END
                else StyleSettings.ANCHOR_BOTTOM_END
            updateDirtyState()
        }

        findViewById<MaterialSwitch>(R.id.advanced_hide_status_bar).setOnCheckedChangeListener {
            _,
            checked ->
            if (syncingUi) return@setOnCheckedChangeListener
            draft.hideStatusBar = checked
            updateDirtyState()
        }

        findViewById<MaterialSwitch>(R.id.panel_blur_switch).setOnCheckedChangeListener { _, checked ->
            if (syncingUi) return@setOnCheckedChangeListener
            draft.panelBlur = checked
            updateDirtyState()
            bindTransparencySliders()
        }

        findViewById<MaterialButton>(R.id.dialog_use_pack).setOnClickListener {
            draft.dialogAlpha = null
            draft.dialogBlurRadius = null
            bindTransparencySliders()
            updateDirtyState()
        }
        findViewById<MaterialButton>(R.id.window_use_pack).setOnClickListener {
            draft.windowAlpha = null
            draft.windowBlurRadius = null
            bindTransparencySliders()
            updateDirtyState()
        }

        wireSlider(R.id.shade_opacity_slider) { percent ->
            val alpha = ((percent.coerceIn(0f, 100f) / 100f) * 255f).toInt()
            draft.panelColorChoice = hexWithAlpha(currentPanelRgb(), alpha)
            bindPanelColorButton()
            updateDirtyState()
        }
        wireSlider(R.id.panel_blur_slider) { radius ->
            draft.panelBlurRadius = radius.toInt()
            if (draft.panelBlur == null) draft.panelBlur = true
            findViewById<MaterialSwitch>(R.id.panel_blur_switch).let {
                syncingUi = true
                it.isChecked = true
                syncingUi = false
            }
            updateDirtyState()
        }
        wireSlider(R.id.dialog_opacity_slider) { percent ->
            draft.dialogAlpha = ((percent.coerceIn(0f, 100f) / 100f) * 255f).toInt()
            updateDirtyState()
        }
        wireSlider(R.id.dialog_blur_slider) { radius ->
            draft.dialogBlurRadius = radius.toInt()
            if (draft.dialogAlpha == null) {
                draft.dialogAlpha = effectiveDialogAlpha()
            }
            updateDirtyState()
        }
        wireSlider(R.id.window_opacity_slider) { percent ->
            draft.windowAlpha = ((percent.coerceIn(0f, 100f) / 100f) * 255f).toInt()
            updateDirtyState()
        }
        wireSlider(R.id.window_blur_slider) { radius ->
            draft.windowBlurRadius = radius.toInt()
            if (draft.windowAlpha == null) {
                draft.windowAlpha = effectiveWindowAlpha()
            }
            updateDirtyState()
        }
    }

    private fun wireSlider(id: Int, onChange: (Float) -> Unit) {
        findViewById<Slider>(id).addOnChangeListener { _, value, fromUser ->
            if (syncingUi || !fromUser) return@addOnChangeListener
            onChange(value)
            updateSliderLabels()
        }
    }

    private fun bindAdvanced() {
        val pack = draft.pack()
        val hasBlack = StyleApplier.hasBlackTheme(contentResolver)
        findViewById<MaterialSwitch>(R.id.advanced_black).apply {
            visibility = if (hasBlack) View.VISIBLE else View.GONE
            isChecked = draft.black ?: StyleSettings.defaultBlack(contentResolver)
        }

        val showVariant = pack.isKitKatSkin && pack.panel == null
        findViewById<View>(R.id.advanced_variant_label).visibility =
            if (showVariant) View.VISIBLE else View.GONE
        findViewById<RadioGroup>(R.id.advanced_variant_group).apply {
            visibility = if (showVariant) View.VISIBLE else View.GONE
            check(
                if (draft.variant == StyleSettings.VARIANT_HOLO_UNIFIED) R.id.variant_holo_unified
                else R.id.variant_kitkat_stock
            )
        }

        val systemBar = draft.isSystemBar()
        findViewById<MaterialButton>(R.id.advanced_bar_background).isEnabled = systemBar
        findViewById<MaterialButton>(R.id.advanced_bar_buttons).isEnabled = systemBar
        findViewById<MaterialSwitch>(R.id.advanced_hide_status_bar).apply {
            isEnabled = systemBar
            isChecked = draft.hideStatusBar
        }

        findViewById<RadioGroup>(R.id.advanced_anchor_group).check(
            if (draft.anchor == StyleSettings.ANCHOR_TOP_END) R.id.anchor_top_end
            else R.id.anchor_bottom_end
        )

        findViewById<MaterialButton>(R.id.advanced_bar_background).text = barBackgroundLabel()
        findViewById<MaterialButton>(R.id.advanced_bar_buttons).text = barButtonsLabel()
        bindPanelColorButton()

        findViewById<MaterialSwitch>(R.id.panel_blur_switch).isChecked =
            draft.panelBlur ?: (pack.blurEnabled == true)

        bindTransparencySliders()
    }

    private fun bindTransparencySliders() {
        val wasSyncing = syncingUi
        syncingUi = true

        val shadePct = alphaToPercent(currentPanelAlpha())
        findViewById<Slider>(R.id.shade_opacity_slider).value = shadePct

        val blurOn = draft.panelBlur ?: (draft.pack().blurEnabled == true)
        val panelBlur =
            (draft.panelBlurRadius
                    ?: draft.pack().blurRadius
                    ?: StyleSettings.DEFAULT_BLUR_RADIUS)
                .coerceIn(0, 40)
                .toFloat()
        findViewById<Slider>(R.id.panel_blur_slider).apply {
            value = panelBlur
            isEnabled = blurOn
        }

        findViewById<Slider>(R.id.dialog_opacity_slider).value =
            alphaToPercent(effectiveDialogAlpha())
        findViewById<Slider>(R.id.dialog_blur_slider).value =
            (draft.dialogBlurRadius ?: draft.pack().dialogBlurRadius ?: 0)
                .coerceIn(0, 40)
                .toFloat()

        findViewById<Slider>(R.id.window_opacity_slider).value =
            alphaToPercent(effectiveWindowAlpha())
        findViewById<Slider>(R.id.window_blur_slider).value =
            (draft.windowBlurRadius ?: draft.pack().windowBlurRadius ?: 0)
                .coerceIn(0, 40)
                .toFloat()

        updateSliderLabels()
        syncingUi = wasSyncing
    }

    private fun updateSliderLabels() {
        val shadePack = draft.panelColorChoice == "pack"
        setPercentLabel(R.id.shade_opacity_value, R.id.shade_opacity_slider, shadePack)

        val panelBlurPack = draft.panelBlurRadius == null
        setDpLabel(R.id.panel_blur_value, R.id.panel_blur_slider, panelBlurPack)

        val dialogPack = draft.dialogAlpha == null && draft.dialogBlurRadius == null
        setPercentLabel(R.id.dialog_opacity_value, R.id.dialog_opacity_slider, dialogPack)
        setDpLabel(
            R.id.dialog_blur_value,
            R.id.dialog_blur_slider,
            draft.dialogBlurRadius == null,
        )

        val windowPack = draft.windowAlpha == null && draft.windowBlurRadius == null
        setPercentLabel(R.id.window_opacity_value, R.id.window_opacity_slider, windowPack)
        setDpLabel(
            R.id.window_blur_value,
            R.id.window_blur_slider,
            draft.windowBlurRadius == null,
        )
    }

    private fun setPercentLabel(labelId: Int, sliderId: Int, packDefault: Boolean) {
        val pct = findViewById<Slider>(sliderId).value.toInt()
        val text = getString(R.string.slider_percent, pct)
        findViewById<TextView>(labelId).text =
            if (packDefault) getString(R.string.slider_pack_marker, text) else text
    }

    private fun setDpLabel(labelId: Int, sliderId: Int, packDefault: Boolean) {
        val dp = findViewById<Slider>(sliderId).value.toInt()
        val text = getString(R.string.slider_dp, dp)
        findViewById<TextView>(labelId).text =
            if (packDefault) getString(R.string.slider_pack_marker, text) else text
    }

    private fun bindPanelColorButton() {
        findViewById<MaterialButton>(R.id.advanced_panel_color).text = panelColorLabel()
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
            bindAdvanced()
            refreshPreviewAndDirty()
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
            bindAdvanced()
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
                findViewById<MaterialButton>(R.id.advanced_bar_background).text =
                    barBackgroundLabel()
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
                findViewById<MaterialButton>(R.id.advanced_bar_buttons).text = barButtonsLabel()
                refreshPreviewAndDirty()
            }
            .setNegativeButton(android.R.string.cancel, null)
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
                bindPanelColorButton()
                bindTransparencySliders()
                updateDirtyState()
                dialog.dismiss()
            }
            .show()
    }

    private fun barBackgroundLabel(): String =
        when (draft.barBackground) {
            "solid" -> getString(R.string.bar_background_solid)
            "gradient" -> getString(R.string.bar_background_gradient)
            "translucent" -> getString(R.string.bar_background_translucent)
            "transparent" -> getString(R.string.bar_background_transparent)
            else -> getString(R.string.bar_background_stock)
        }

    private fun barButtonsLabel(): String {
        if (draft.barButtons.isEmpty()) return getString(R.string.transparency_off)
        return draft.barButtons.joinToString(", ")
    }

    private fun panelColorLabel(): String {
        val idx = StyleSettings.PANEL_COLOR_PRESETS.indexOf(draft.panelColorChoice)
        if (idx >= 0) {
            return when (idx) {
                0 -> getString(R.string.panel_color_pack)
                1 -> getString(R.string.panel_color_opaque_black)
                2 -> getString(R.string.panel_color_near_black)
                3 -> getString(R.string.panel_color_holo_navy)
                4 -> getString(R.string.panel_color_glass)
                5 -> getString(R.string.panel_color_bar)
                6 -> getString(R.string.panel_color_frost)
                else -> draft.panelColorChoice
            }
        }
        return draft.panelColorChoice
    }

    /** RGB without alpha for shade panel; pack/glass dark fallback when unset. */
    private fun currentPanelRgb(): Int {
        val choice = draft.panelColorChoice
        if (choice.startsWith("#")) {
            return runCatching { Color.parseColor(choice) }.getOrNull()?.and(0x00FFFFFF) ?: 0
        }
        val packPanel = draft.pack().panel
        if (packPanel != null) return packPanel and 0x00FFFFFF
        return 0x000000
    }

    private fun currentPanelAlpha(): Int {
        val choice = draft.panelColorChoice
        if (choice.startsWith("#")) {
            return runCatching { Color.parseColor(choice) }.getOrNull()?.ushr(24) ?: 255
        }
        val packPanel = draft.pack().panel
        if (packPanel != null) return packPanel ushr 24
        return 255
    }

    private fun effectiveDialogAlpha(): Int =
        draft.dialogAlpha ?: draft.pack().dialogAlpha ?: 255

    private fun effectiveWindowAlpha(): Int =
        draft.windowAlpha ?: draft.pack().windowAlpha ?: 255

    private fun alphaToPercent(alpha: Int): Float =
        ((alpha.coerceIn(0, 255) / 255f) * 100f).toInt().toFloat()

    private fun hexWithAlpha(rgb: Int, alpha: Int): String {
        val a = alpha.coerceIn(0, 255)
        val color = (a shl 24) or (rgb and 0x00FFFFFF)
        return String.format("#%08X", color)
    }

    companion object {
        private const val STATE_ADVANCED = "advanced_expanded"
    }
}
