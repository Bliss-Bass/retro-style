// Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial

package org.retro.style

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Mini tablet system bar: three tappable zones (left / center / right-before-tray) plus a fixed
 * tray at the end. Tap a zone to cycle empty → Nav → Apps → empty ([BarZones.cycleAt]).
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
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
        }
    private val barRect = RectF()
    private val zoneRects = Array(3) { RectF() }
    private val trayRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = resolveSize(dp(56f).toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = dp(8f)
        val trayW = dp(72f)
        barRect.set(pad, pad, w - pad, h - pad)
        val inner = RectF(barRect)
        inner.right -= trayW + dp(4f)
        val zoneW = inner.width() / 3f
        for (i in 0 until 3) {
            zoneRects[i].set(
                inner.left + i * zoneW + dp(2f),
                inner.top + dp(4f),
                inner.left + (i + 1) * zoneW - dp(2f),
                inner.bottom - dp(4f),
            )
        }
        trayRect.set(inner.right + dp(4f), inner.top + dp(4f), barRect.right - dp(4f), inner.bottom - dp(4f))
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
                textPaint.textSize = dp(11f)
                canvas.drawText(zoneLabel(zone), rect.centerX(), rect.centerY() + dp(4f), textPaint)
            }
        }
    }

    private fun drawNav(canvas: Canvas, rect: RectF) {
        fillPaint.color = if (kitKat) accentColor else contentColor
        val cy = rect.centerY()
        val size = dp(5f)
        val gap = dp(14f)
        val cx = rect.centerX()
        // Back chevron, home circle, recents square — simplified glyphs.
        canvas.drawCircle(cx - gap, cy, size, fillPaint)
        canvas.drawCircle(cx, cy, size, fillPaint)
        canvas.drawRect(cx + gap - size, cy - size, cx + gap + size, cy + size, fillPaint)
        textPaint.color = contentColor and 0x99FFFFFF.toInt()
        textPaint.textSize = dp(9f)
        canvas.drawText("Nav", rect.centerX(), rect.bottom - dp(4f), textPaint)
    }

    private fun drawApps(canvas: Canvas, rect: RectF) {
        fillPaint.color = contentColor
        val cy = rect.centerY() - dp(2f)
        val size = dp(4f)
        val gap = dp(12f)
        val cx = rect.centerX()
        for (i in -1..1) {
            canvas.drawCircle(cx + i * gap, cy, size, fillPaint)
        }
        textPaint.color = contentColor and 0x99FFFFFF.toInt()
        textPaint.textSize = dp(9f)
        canvas.drawText("Apps", rect.centerX(), rect.bottom - dp(4f), textPaint)
    }

    private fun drawTray(canvas: Canvas, rect: RectF) {
        val r = if (kitKat) dp(2f) else dp(8f)
        strokePaint.color = accentColor and 0x88FFFFFF.toInt()
        canvas.drawRoundRect(rect, r, r, strokePaint)
        fillPaint.color = accentColor
        val cy = rect.centerY()
        canvas.drawCircle(rect.left + dp(14f), cy, dp(4f), fillPaint)
        canvas.drawCircle(rect.left + dp(28f), cy, dp(4f), fillPaint)
        textPaint.color = accentColor
        textPaint.textSize = dp(12f)
        canvas.drawText("12:00", rect.right - dp(28f), cy + dp(4f), textPaint)
    }

    private fun zoneLabel(zone: BarZone): String =
        when (zone) {
            BarZone.START -> "Left"
            BarZone.CENTER -> "Center"
            BarZone.END -> "Right"
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
