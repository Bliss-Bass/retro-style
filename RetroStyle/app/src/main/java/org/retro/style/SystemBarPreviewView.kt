// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Mini tablet system bar: three tappable zones (left / center / right-before-tray) plus a fixed
 * tray at the end. Occupied zones draw nav buttons or apps-menu + hotseat icons with labels.
 * Tap Nav/Apps to advance that item across zones; tap an empty zone to place the selected tool.
 */
class SystemBarPreviewView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr) {

    var zones: BarZones = BarZones()
        set(value) {
            field = value
            invalidate()
        }

    var appsEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** Extra system-bar buttons from the draft (`vol_down`, `vol_up`, …). */
    var extraButtons: List<String> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var kitKat: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var barColor: Int = 0xFF000000.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var accentColor: Int = 0xFF33B5E5.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var contentColor: Int = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var onZoneTapped: ((BarZone) -> Unit)? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
        }
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    private val path = Path()
    private val barRect = RectF()
    private val zoneRects = Array(3) { RectF() }
    private val trayRect = RectF()
    private val tmpRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = resolveSize(dp(88f).toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = dp(6f)
        val trayW = dp(64f)
        barRect.set(pad, pad, w - pad, h - pad)
        val inner = RectF(barRect)
        inner.right -= trayW + dp(4f)
        val zoneW = inner.width() / 3f
        for (i in 0 until 3) {
            zoneRects[i].set(
                inner.left + i * zoneW + dp(2f),
                inner.top + dp(3f),
                inner.left + (i + 1) * zoneW - dp(2f),
                inner.bottom - dp(3f),
            )
        }
        trayRect.set(
            inner.right + dp(4f),
            inner.top + dp(3f),
            barRect.right - dp(4f),
            inner.bottom - dp(3f),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val radius = if (kitKat) dp(2f) else dp(12f)
        barPaint.color = barColor
        canvas.drawRoundRect(barRect, radius, radius, barPaint)

        BarZone.entries.forEachIndexed { i, zone ->
            drawZone(canvas, zoneRects[i], zone, zones.occupantAt(zone))
        }
        drawTray(canvas, trayRect)
    }

    private fun drawZone(canvas: Canvas, rect: RectF, zone: BarZone, occupant: BarOccupant?) {
        val selected = occupant != null
        strokePaint.color = if (selected) accentColor else contentColor and 0x40FFFFFF.toInt()
        val r = if (kitKat) dp(2f) else dp(8f)
        if (selected) {
            fillPaint.color = accentColor and 0x33FFFFFF.toInt()
            canvas.drawRoundRect(rect, r, r, fillPaint)
        }
        canvas.drawRoundRect(rect, r, r, strokePaint)

        when (occupant) {
            BarOccupant.NAV -> drawNav(canvas, rect)
            BarOccupant.APPS -> drawApps(canvas, rect)
            null -> {
                textPaint.color = contentColor and 0x66FFFFFF.toInt()
                textPaint.textSize = dp(12f)
                canvas.drawText(zoneLabel(zone), rect.centerX(), rect.centerY() + dp(4f), textPaint)
            }
        }
    }

    private fun drawNav(canvas: Canvas, rect: RectF) {
        val labelH = dp(16f)
        val iconsBottom = rect.bottom - labelH - dp(2f)
        val iconsTop = rect.top + dp(6f)
        val cy = (iconsTop + iconsBottom) / 2f
        val color = if (kitKat) accentColor else contentColor
        fillPaint.color = color
        strokePaint.color = color
        strokePaint.strokeWidth = dp(2f)

        val buttons = mutableListOf("back", "home", "recents")
        for (extra in extraButtons) {
            if (extra in StyleSettings.BAR_BUTTONS) buttons.add(extra)
        }
        val slot = minOf(dp(22f), (rect.width() - dp(8f)) / buttons.size.coerceAtLeast(1))
        val total = slot * buttons.size
        var cx = rect.centerX() - total / 2f + slot / 2f
        for (button in buttons) {
            drawNavGlyph(canvas, button, cx, cy, slot * 0.38f)
            cx += slot
        }

        textPaint.color = contentColor
        textPaint.textSize = dp(11f)
        canvas.drawText(
            context.getString(R.string.preview_nav_label),
            rect.centerX(),
            rect.bottom - dp(5f),
            textPaint,
        )
        strokePaint.strokeWidth = dp(1.5f)
    }

    private fun drawNavGlyph(canvas: Canvas, type: String, cx: Float, cy: Float, size: Float) {
        when (type) {
            "back" -> {
                path.reset()
                path.moveTo(cx + size * 0.45f, cy - size)
                path.lineTo(cx - size * 0.55f, cy)
                path.lineTo(cx + size * 0.45f, cy + size)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            "home" -> {
                if (kitKat) {
                    canvas.drawCircle(cx, cy, size * 0.85f, strokePaint)
                } else {
                    path.reset()
                    path.moveTo(cx, cy - size)
                    path.lineTo(cx + size, cy)
                    path.lineTo(cx + size * 0.55f, cy)
                    path.lineTo(cx + size * 0.55f, cy + size)
                    path.lineTo(cx - size * 0.55f, cy + size)
                    path.lineTo(cx - size * 0.55f, cy)
                    path.lineTo(cx - size, cy)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
            }
            "recents" -> {
                tmpRect.set(cx - size * 0.7f, cy - size * 0.7f, cx + size * 0.7f, cy + size * 0.7f)
                canvas.drawRoundRect(tmpRect, dp(1.5f), dp(1.5f), strokePaint)
            }
            "vol_down",
            "vol_up" -> {
                // Speaker wedge
                path.reset()
                path.moveTo(cx - size * 0.2f, cy - size * 0.45f)
                path.lineTo(cx - size * 0.7f, cy - size * 0.45f)
                path.lineTo(cx - size * 0.7f, cy + size * 0.45f)
                path.lineTo(cx - size * 0.2f, cy + size * 0.45f)
                path.lineTo(cx + size * 0.35f, cy + size)
                path.lineTo(cx + size * 0.35f, cy - size)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            "screenshot" -> {
                tmpRect.set(cx - size * 0.75f, cy - size * 0.55f, cx + size * 0.75f, cy + size * 0.55f)
                canvas.drawRoundRect(tmpRect, dp(2f), dp(2f), strokePaint)
                canvas.drawCircle(cx, cy, size * 0.28f, fillPaint)
            }
            "power" -> {
                canvas.drawCircle(cx, cy, size * 0.75f, strokePaint)
                canvas.drawLine(cx, cy - size, cx, cy - size * 0.15f, strokePaint)
            }
        }
    }

    private fun drawApps(canvas: Canvas, rect: RectF) {
        val labelH = dp(16f)
        val iconsBottom = rect.bottom - labelH - dp(2f)
        val iconsTop = rect.top + dp(6f)
        val cy = (iconsTop + iconsBottom) / 2f
        fillPaint.color = contentColor
        strokePaint.color = contentColor
        strokePaint.strokeWidth = dp(1.5f)

        // Apps menu (3×3 grid) on the left of the zone.
        val cell = dp(3.5f)
        val gap = dp(2.5f)
        val gridW = cell * 3 + gap * 2
        val gridLeft = rect.left + dp(8f)
        val gridTop = cy - gridW / 2f
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val x = gridLeft + col * (cell + gap)
                val y = gridTop + row * (cell + gap)
                tmpRect.set(x, y, x + cell, y + cell)
                canvas.drawRoundRect(tmpRect, dp(0.8f), dp(0.8f), fillPaint)
            }
        }

        // Hotseat / running-task icon stubs to the right of the grid.
        val icon = dp(11f)
        val iconGap = dp(5f)
        val count = 4
        var x = gridLeft + gridW + dp(10f)
        val maxRight = rect.right - dp(6f)
        var drawn = 0
        while (drawn < count && x + icon <= maxRight) {
            tmpRect.set(x, cy - icon / 2f, x + icon, cy + icon / 2f)
            canvas.drawRoundRect(tmpRect, dp(2.5f), dp(2.5f), fillPaint)
            // Inner well so stubs read as app icons.
            fillPaint.color = contentColor and 0x44FFFFFF.toInt()
            tmpRect.inset(dp(2f), dp(2f))
            canvas.drawRoundRect(tmpRect, dp(1.5f), dp(1.5f), fillPaint)
            fillPaint.color = contentColor
            x += icon + iconGap
            drawn++
        }

        textPaint.color = contentColor
        textPaint.textSize = dp(11f)
        canvas.drawText(
            context.getString(R.string.preview_apps_label),
            rect.centerX(),
            rect.bottom - dp(5f),
            textPaint,
        )
    }

    private fun drawTray(canvas: Canvas, rect: RectF) {
        val r = if (kitKat) dp(2f) else dp(8f)
        strokePaint.color = accentColor and 0x88FFFFFF.toInt()
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(rect, r, r, strokePaint)
        fillPaint.color = accentColor
        val cy = rect.centerY()
        canvas.drawCircle(rect.left + dp(12f), cy, dp(3.5f), fillPaint)
        canvas.drawCircle(rect.left + dp(24f), cy, dp(3.5f), fillPaint)
        textPaint.color = accentColor
        textPaint.textSize = dp(11f)
        canvas.drawText("12:00", rect.right - dp(24f), cy + dp(4f), textPaint)
    }

    private fun zoneLabel(zone: BarZone): String =
        when (zone) {
            BarZone.START -> context.getString(R.string.preview_zone_left)
            BarZone.CENTER -> context.getString(R.string.preview_zone_center)
            BarZone.END -> context.getString(R.string.preview_zone_right)
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) {
            return event.action == MotionEvent.ACTION_DOWN || super.onTouchEvent(event)
        }
        val x = event.x
        val y = event.y
        BarZone.entries.forEachIndexed { i, zone ->
            if (zoneRects[i].contains(x, y)) {
                onZoneTapped?.invoke(zone)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun applyPackChrome(pack: ThemePack) {
        kitKat = pack.isKitKatSkin
        barColor = pack.panel ?: if (pack.isKitKatSkin) 0xFF000000.toInt() else 0xFF1C1B1F.toInt()
        accentColor = pack.accent ?: if (pack.isKitKatSkin) 0xFF33B5E5.toInt() else 0xFFD0BCFF.toInt()
        contentColor = 0xFFFFFFFF.toInt()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
