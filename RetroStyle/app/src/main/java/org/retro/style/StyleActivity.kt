// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.os.Bundle
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Settings > System > UI style. Each control writes one [StyleSettings] value and applies it right
 * away ([ApplyWorker]); SystemUI and the launcher restart when they need to, and the desktop
 * windows (this page included) come back afterwards.
 */
class StyleActivity : AppCompatActivity() {

    private var themedKitKat = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The page follows the chosen style: KitKat gets the Holo theme, the others the system
        // palette that style sets.
        themedKitKat = StyleSettings.style(contentResolver) == StyleSettings.STYLE_KITKAT
        setTheme(if (themedKitKat) R.style.Theme_RetroStyle_KitKat else R.style.Theme_RetroStyle)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_style)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    private fun bind() {
        val cr = contentResolver
        bindGroup(
            R.id.style_group,
            StyleSettings.KEY_STYLE,
            StyleSettings.style(cr),
            mapOf(
                R.id.style_material to StyleSettings.STYLE_MATERIAL,
                R.id.style_m3 to StyleSettings.STYLE_M3,
                R.id.style_kitkat to StyleSettings.STYLE_KITKAT,
            ),
            onChanged = {
                bindSystemBarOptions()
                val kitKat = StyleSettings.style(contentResolver) == StyleSettings.STYLE_KITKAT
                if (kitKat != themedKitKat) recreate()
            },
        )
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
