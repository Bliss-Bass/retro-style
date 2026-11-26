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
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Mini tablet system bar preview: fixed tray at the end, plus Nav and Apps sections.
 * Drag onto left / center / right to set the zone; tap to cycle content justification inside
 * that zone (or disable Apps). Empty slots are drop targets only while dragging.
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

    /** Drag finished: [occupant] should be placed at [zone] (swap as needed). */
    var onOccupantDropped: ((BarOccupant, BarZone) -> Unit)? = null

    /** Tap (no drag): cycle justification / disabled for [occupant]. */
    var onOccupantTapped: ((BarOccupant) -> Unit)? = null

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
    private val slotRects = Array(3) { RectF() }
    private val trayRect = RectF()
    private val tmpRect = RectF()
    private val navRect = RectF()
    private val appsRect = RectF()
    private val dragRect = RectF()

    private var touchOccupant: BarOccupant? = null
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var dragX = 0f
    private var dragY = 0f
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var dropHighlight: BarZone? = null

    private val touchSlop: Float
        get() = dp(8f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = resolveSize(dp(96f).toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutSlots(w, h)
    }

    private fun layoutSlots(w: Int, h: Int) {
        val pad = dp(6f)
        val trayW = dp(64f)
        barRect.set(pad, pad, w - pad, h - pad)
        val inner = RectF(barRect)
        inner.right -= trayW + dp(4f)
        val slotW = inner.width() / 3f
        for (i in 0 until 3) {
            slotRects[i].set(
                inner.left + i * slotW + dp(2f),
                inner.top + dp(3f),
                inner.left + (i + 1) * slotW - dp(2f),
                inner.bottom - dp(3f),
            )
        }
        trayRect.set(
            inner.right + dp(4f),
            inner.top + dp(3f),
            barRect.right - dp(4f),
            inner.bottom - dp(3f),
        )
        updateOccupantRects()
    }

    private fun updateOccupantRects() {
        sectionRectFor(zones.nav, navRect)
        val appsZone = zones.apps
        if (appsEnabled && appsZone != null) {
            sectionRectFor(appsZone, appsRect)
        } else {
            // Dimmed stub in the last zone Apps occupied (not always the end slot).
            val stub =
                when (val last = zones.appsLast) {
                    zones.nav -> BarZone.entries.firstOrNull { it != zones.nav } ?: BarZone.END
                    else -> last
                }
            appsRect.set(slotRects[stub.ordinal])
        }
    }

    private fun sectionRectFor(zone: BarZone, out: RectF) {
        out.set(slotRects[zone.ordinal])
    }

    override fun onDraw(canvas: Canvas) {
        updateOccupantRects()
        val radius = if (kitKat) dp(2f) else dp(20f)
        barPaint.color = barColor
        canvas.drawRoundRect(barRect, radius, radius, barPaint)

        if (dragging) {
            val highlight = dropHighlight
            for (i in 0 until 3) {
                val zone = BarZone.entries[i]
                strokePaint.color =
                    if (zone == highlight) accentColor else contentColor and 0x28FFFFFF.toInt()
                strokePaint.strokeWidth = if (zone == highlight) dp(2f) else dp(1f)
                val slotR = if (kitKat) dp(2f) else dp(12f)
                canvas.drawRoundRect(slotRects[i], slotR, slotR, strokePaint)
            }
            strokePaint.strokeWidth = dp(1.5f)
        }

        val hideNav = dragging && touchOccupant == BarOccupant.NAV
        val hideApps = dragging && touchOccupant == BarOccupant.APPS

        if (!hideNav) {
            drawSection(canvas, navRect, BarOccupant.NAV, zones.navJustify, enabled = true)
        }
        if (appsEnabled && zones.apps != null) {
            if (!hideApps) {
                drawSection(canvas, appsRect, BarOccupant.APPS, zones.appsJustify, enabled = true)
            }
        } else if (!dragging) {
            drawSection(canvas, appsRect, BarOccupant.APPS, BarJustify.START, enabled = false)
        }

        drawTray(canvas, trayRect)

        if (dragging && touchOccupant != null) {
            dragRect.set(if (touchOccupant == BarOccupant.NAV) navRect else appsRect)
            val w = dragRect.width()
            val h = dragRect.height()
            dragRect.set(dragX - w / 2f, dragY - h / 2f, dragX + w / 2f, dragY + h / 2f)
            val justify =
                if (touchOccupant == BarOccupant.NAV) zones.navJustify else zones.appsJustify
            drawSection(canvas, dragRect, touchOccupant!!, justify, enabled = true, dragging = true)
        }
    }

    private fun drawSection(
        canvas: Canvas,
        rect: RectF,
        occupant: BarOccupant,
        justify: BarJustify,
        enabled: Boolean,
        dragging: Boolean = false,
    ) {
        val r = if (kitKat) dp(2f) else dp(14f)
        strokePaint.color =
            if (dragging) accentColor
            else if (enabled) accentColor and 0xCCFFFFFF.toInt()
            else contentColor and 0x44FFFFFF.toInt()
        if (enabled || dragging) {
            fillPaint.color = accentColor and 0x28FFFFFF.toInt()
            canvas.drawRoundRect(rect, r, r, fillPaint)
        }
        canvas.drawRoundRect(rect, r, r, strokePaint)

        val savedContent = contentColor
        val savedAccent = accentColor
        if (!enabled) {
            contentColor = contentColor and 0x55FFFFFF.toInt()
            accentColor = accentColor and 0x55FFFFFF.toInt()
        }
        when (occupant) {
            BarOccupant.NAV -> drawNav(canvas, rect, justify)
            BarOccupant.APPS -> drawApps(canvas, rect, justify, enabled)
        }
        if (enabled || dragging) {
            drawDragHandle(canvas, rect)
        }
        contentColor = savedContent
        accentColor = savedAccent
    }

    /** Material-style grabber at the top of a movable card. */
    private fun drawDragHandle(canvas: Canvas, rect: RectF) {
        fillPaint.color = contentColor and 0x66FFFFFF.toInt()
        val handleW = dp(18f)
        val handleH = dp(3f)
        tmpRect.set(
            rect.centerX() - handleW / 2f,
            rect.top + dp(4f),
            rect.centerX() + handleW / 2f,
            rect.top + dp(4f) + handleH,
        )
        canvas.drawRoundRect(tmpRect, handleH / 2f, handleH / 2f, fillPaint)
    }

    private fun contentStartX(rect: RectF, contentWidth: Float, justify: BarJustify): Float {
        val pad = dp(8f)
        val available = rect.width() - pad * 2f
        val width = minOf(contentWidth, available)
        return when (justify) {
            BarJustify.START -> rect.left + pad
            BarJustify.END -> rect.right - pad - width
            BarJustify.CENTER -> rect.centerX() - width / 2f
        }
    }

    private fun justifyLabel(justify: BarJustify): String =
        context.getString(
            when (justify) {
                BarJustify.START -> R.string.preview_justify_start
                BarJustify.CENTER -> R.string.preview_justify_center
                BarJustify.END -> R.string.preview_justify_end
            }
        )

    private fun drawNav(canvas: Canvas, rect: RectF, justify: BarJustify) {
        val labelH = dp(18f)
        val iconsBottom = rect.bottom - labelH - dp(2f)
        val iconsTop = rect.top + dp(10f)
        val cy = (iconsTop + iconsBottom) / 2f
        val color = if (kitKat) accentColor else contentColor
        fillPaint.color = color
        strokePaint.color = color
        strokePaint.strokeWidth = dp(2f)

        val buttons = mutableListOf("back", "home", "recents")
        for (extra in extraButtons) {
            if (extra in StyleSettings.BAR_BUTTONS) buttons.add(extra)
        }
        val slot = minOf(dp(24f), (rect.width() - dp(16f)) / buttons.size.coerceAtLeast(1))
        val total = slot * buttons.size
        var cx = contentStartX(rect, total, justify) + slot / 2f
        for (button in buttons) {
            drawNavGlyph(canvas, button, cx, cy, slot * 0.36f)
            cx += slot
        }

        textPaint.color = contentColor
        textPaint.textSize = dp(10f)
        canvas.drawText(
            context.getString(R.string.preview_nav_label) + " · " + justifyLabel(justify),
            rect.centerX(),
            rect.bottom - dp(5f),
            textPaint,
        )
        strokePaint.strokeWidth = dp(1.5f)
    }

    private fun drawNavGlyph(canvas: Canvas, type: String, cx: Float, cy: Float, size: Float) {
        when (type) {
            "back" -> {
                if (kitKat) {
                    path.reset()
                    path.moveTo(cx + size * 0.45f, cy - size)
                    path.lineTo(cx - size * 0.55f, cy)
                    path.lineTo(cx + size * 0.45f, cy + size)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                } else {
                    // Material 3 gesture/back: rounded chevron.
                    strokePaint.strokeWidth = dp(2.25f)
                    strokePaint.strokeCap = Paint.Cap.ROUND
                    strokePaint.strokeJoin = Paint.Join.ROUND
                    path.reset()
                    path.moveTo(cx + size * 0.35f, cy - size * 0.85f)
                    path.lineTo(cx - size * 0.45f, cy)
                    path.lineTo(cx + size * 0.35f, cy + size * 0.85f)
                    canvas.drawPath(path, strokePaint)
                    strokePaint.strokeCap = Paint.Cap.BUTT
                    strokePaint.strokeJoin = Paint.Join.MITER
                    strokePaint.strokeWidth = dp(1.5f)
                }
            }
            "home" -> {
                if (kitKat) {
                    canvas.drawCircle(cx, cy, size * 0.85f, strokePaint)
                } else {
                    // Material 3 home: rounded rectangle pill.
                    tmpRect.set(
                        cx - size * 0.85f,
                        cy - size * 0.55f,
                        cx + size * 0.85f,
                        cy + size * 0.55f,
                    )
                    canvas.drawRoundRect(tmpRect, size * 0.45f, size * 0.45f, strokePaint)
                }
            }
            "recents" -> {
                if (kitKat) {
                    tmpRect.set(
                        cx - size * 0.7f,
                        cy - size * 0.7f,
                        cx + size * 0.7f,
                        cy + size * 0.7f,
                    )
                    canvas.drawRoundRect(tmpRect, dp(1.5f), dp(1.5f), strokePaint)
                } else {
                    // Material 3 recents: tall rounded square.
                    tmpRect.set(
                        cx - size * 0.55f,
                        cy - size * 0.85f,
                        cx + size * 0.55f,
                        cy + size * 0.85f,
                    )
                    canvas.drawRoundRect(tmpRect, size * 0.28f, size * 0.28f, strokePaint)
                }
            }
            "vol_down",
            "vol_up" -> {
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

    private fun drawApps(canvas: Canvas, rect: RectF, justify: BarJustify, enabled: Boolean) {
        val labelH = dp(18f)
        val iconsBottom = rect.bottom - labelH - dp(2f)
        val iconsTop = rect.top + dp(10f)
        val cy = (iconsTop + iconsBottom) / 2f
        fillPaint.color = contentColor
        strokePaint.color = contentColor
        strokePaint.strokeWidth = dp(1.5f)

        val cell = dp(3.5f)
        val gap = dp(2.5f)
        val gridW = cell * 3 + gap * 2
        val icon = dp(12f)
        val iconGap = dp(5f)
        val hotseat = 4
        val contentW = gridW + dp(10f) + hotseat * icon + (hotseat - 1) * iconGap
        val start = contentStartX(rect, contentW, justify)
        val gridLeft = start
        val gridTop = cy - gridW / 2f
        val cellR = if (kitKat) dp(0.8f) else dp(1.6f)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val x = gridLeft + col * (cell + gap)
                val y = gridTop + row * (cell + gap)
                tmpRect.set(x, y, x + cell, y + cell)
                canvas.drawRoundRect(tmpRect, cellR, cellR, fillPaint)
            }
        }

        var x = gridLeft + gridW + dp(10f)
        val maxRight = rect.right - dp(6f)
        var drawn = 0
        val iconR = if (kitKat) dp(2.5f) else dp(4f)
        while (drawn < hotseat && x + icon <= maxRight) {
            tmpRect.set(x, cy - icon / 2f, x + icon, cy + icon / 2f)
            canvas.drawRoundRect(tmpRect, iconR, iconR, fillPaint)
            fillPaint.color = contentColor and 0x44FFFFFF.toInt()
            tmpRect.inset(dp(2f), dp(2f))
            canvas.drawRoundRect(tmpRect, iconR * 0.6f, iconR * 0.6f, fillPaint)
            fillPaint.color = contentColor
            x += icon + iconGap
            drawn++
        }

        textPaint.color = contentColor
        textPaint.textSize = dp(10f)
        val label =
            if (enabled) {
                context.getString(R.string.preview_apps_label) + " · " + justifyLabel(justify)
            } else {
                context.getString(R.string.preview_apps_off_label)
            }
        canvas.drawText(label, rect.centerX(), rect.bottom - dp(5f), textPaint)
    }

    private fun drawTray(canvas: Canvas, rect: RectF) {
        val r = if (kitKat) dp(2f) else dp(14f)
        fillPaint.color = accentColor and 0x22FFFFFF.toInt()
        canvas.drawRoundRect(rect, r, r, fillPaint)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                dragX = event.x
                dragY = event.y
                dragging = false
                dropHighlight = null
                touchOccupant = hitOccupant(event.x, event.y)
                if (touchOccupant == null) return false
                val src = if (touchOccupant == BarOccupant.NAV) navRect else appsRect
                dragOffsetX = event.x - src.centerX()
                dragOffsetY = event.y - src.centerY()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchOccupant == null) return false
                dragX = event.x
                dragY = event.y
                if (!dragging && hypot(event.x - downX, event.y - downY) > touchSlop) {
                    // Disabled apps stub is tap-only (re-enable / cycle), not draggable.
                    if (touchOccupant == BarOccupant.APPS && (!appsEnabled || zones.apps == null)) {
                        return true
                    }
                    dragging = true
                }
                if (dragging) {
                    dropHighlight = nearestSlot(event.x)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val occupant = touchOccupant
                val wasDragging = dragging
                val highlight = dropHighlight
                touchOccupant = null
                dragging = false
                dropHighlight = null
                invalidate()
                if (occupant == null) return false
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) return true
                if (wasDragging && highlight != null) {
                    onOccupantDropped?.invoke(occupant, highlight)
                } else if (!wasDragging && abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop) {
                    onOccupantTapped?.invoke(occupant)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitOccupant(x: Float, y: Float): BarOccupant? {
        updateOccupantRects()
        // Prefer the section under the point; nav wins ties if overlapping (shouldn't).
        if (appsEnabled && zones.apps != null && appsRect.contains(x, y)) return BarOccupant.APPS
        if (navRect.contains(x, y)) return BarOccupant.NAV
        if ((!appsEnabled || zones.apps == null) && appsRect.contains(x, y)) return BarOccupant.APPS
        return null
    }

    private fun nearestSlot(x: Float): BarZone {
        var best = BarZone.START
        var bestDist = Float.MAX_VALUE
        for (zone in BarZone.entries) {
            val d = abs(slotRects[zone.ordinal].centerX() - x)
            if (d < bestDist) {
                bestDist = d
                best = zone
            }
        }
        return best
    }

    fun applyPackChrome(pack: ThemePack) {
        kitKat = pack.isKitKatSkin
        barColor = pack.panel ?: if (pack.isKitKatSkin) 0xFF000000.toInt() else 0xFF1C1B1F.toInt()
        accentColor = pack.accent ?: if (pack.isKitKatSkin) 0xFF33B5E5.toInt() else 0xFFD0BCFF.toInt()
        contentColor = 0xFFFFFFFF.toInt()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
