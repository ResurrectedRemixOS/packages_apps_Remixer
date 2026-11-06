/*
 * SPDX-FileCopyrightText: 2026 Project Catalyxx
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.internal.util.catalyxx.GlassLook
import com.android.internal.util.catalyxx.GlassSettings
import com.android.settings.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Melt together, to try (GlassSettings.MELT): drops of the system's glass over the wallpaper, to
 * drag into each other. The panels' necks are gone in a moment; here it holds as long as you like.
 */
class GlassMeltActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setDecorFitsSystemWindows(false)
        val root = FrameLayout(this)
        root.addView(GlassDropsView(this))
        val hint =
            TextView(this).apply {
                setText(R.string.glass_melt_try_hint)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setShadowLayer(6f, 0f, 1f, Color.BLACK)
                gravity = Gravity.CENTER
            }
        hint.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val side = (24 * resources.displayMetrics.density).roundToInt()
            view.setPadding(side, bars.top + side, side, side)
            insets
        }
        root.addView(
            hint,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )
        setContentView(root)
    }
}

/** The drops, in their window's own blur drawables, which SurfaceFlinger draws as glass. */
private class GlassDropsView(context: Context) : View(context) {

    /** A drop with its middle at ([x], [y]), which start as shares of the view's size. */
    private class Drop(
        val width: Float,
        val height: Float,
        val cornerRadius: Float,
        var x: Float,
        var y: Float,
    ) {
        var glass: BackgroundBlurDrawable? = null
    }

    private val density = context.resources.displayMetrics.density
    private val drops =
        listOf(
            Drop(120 * density, 120 * density, 60 * density, 0.3f, 0.45f),
            Drop(72 * density, 72 * density, 36 * density, 0.72f, 0.38f),
            Drop(176 * density, 64 * density, 32 * density, 0.5f, 0.68f),
        )
    private var isPlaced = false

    // The drop under each finger
    private val held = SparseArray<Drop>()
    private val offsets = SparseArray<FloatArray>()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val viewRoot = viewRootImpl ?: return
        val resolver = context.contentResolver
        val size = GlassSettings.meltSize(resolver, density)
        // The first place with glass on, clear ones first: their glass shows the wallpaper
        var place = GlassSettings.PLACE_LOCKSCREEN
        var look: GlassLook? = null
        for (candidate in PLACES) {
            look = GlassSettings.look(context, candidate, DARK_TINT)
            if (look != null) {
                place = candidate
                break
            }
        }
        val blurRadius =
            GlassSettings.scaleBlurRadius(
                (BLUR_RADIUS_DP * density).roundToInt(),
                GlassSettings.blurScale(resolver, place),
            )
        for (drop in drops) {
            drop.glass =
                viewRoot.createBackgroundBlurDrawable().apply {
                    // Nothing behind it in this window to make a hole through. This window can't
                    // send SurfaceFlinger the phone's tilt
                    setXfermode(null)
                    setGlassFixedLight(true)
                    setGlass(look)
                    setBlurRadius(blurRadius)
                    setCornerRadius(drop.cornerRadius)
                    setGlassMelt(size)
                }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        for (drop in drops) {
            drop.glass?.setVisible(false, false)
            drop.glass = null
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (!isPlaced && w > 0 && h > 0) {
            isPlaced = true
            for (drop in drops) {
                drop.x *= w
                drop.y *= h
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (drop in drops) {
            val glass = drop.glass ?: continue
            glass.setBounds(
                (drop.x - drop.width / 2).roundToInt(),
                (drop.y - drop.height / 2).roundToInt(),
                (drop.x + drop.width / 2).roundToInt(),
                (drop.y + drop.height / 2).roundToInt(),
            )
            glass.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val drop = dropAt(event.getX(index), event.getY(index))
                if (drop != null) {
                    val id = event.getPointerId(index)
                    held.put(id, drop)
                    offsets.put(
                        id,
                        floatArrayOf(drop.x - event.getX(index), drop.y - event.getY(index)),
                    )
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val drop = held.get(id) ?: continue
                    val offset = offsets.get(id)
                    // Kept on the screen, its middle at least
                    drop.x = (event.getX(i) + offset[0]).coerceIn(0f, width.toFloat())
                    drop.y = (event.getY(i) + offset[1]).coerceIn(0f, height.toFloat())
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(event.actionIndex)
                held.remove(id)
                offsets.remove(id)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                held.clear()
                offsets.clear()
            }
        }
        return true
    }

    /** The free drop nearest to a finger at ([x], [y]), if it's on one or close by. */
    private fun dropAt(x: Float, y: Float): Drop? {
        var nearest: Drop? = null
        var nearestDistance = FINGER_REACH_DP * density
        for (drop in drops) {
            if (held.indexOfValue(drop) >= 0) {
                continue
            }
            val distance =
                hypot(
                    max(0f, abs(x - drop.x) - drop.width / 2),
                    max(0f, abs(y - drop.y) - drop.height / 2),
                )
            if (distance < nearestDistance) {
                nearest = drop
                nearestDistance = distance
            }
        }
        return nearest
    }

    private companion object {
        const val BLUR_RADIUS_DP = 30f
        const val FINGER_REACH_DP = 24f
        // Android's own tint, if the glass color asks for it: this page has none of its own
        const val DARK_TINT = 0x99202124.toInt()

        // Glass that carries no text stays clear, so the lock screen's comes first.
        val PLACES =
            arrayOf(
                GlassSettings.PLACE_LOCKSCREEN,
                GlassSettings.PLACE_VOLUME,
                GlassSettings.PLACE_DIALOGS,
                GlassSettings.PLACE_NOTIFICATIONS,
                GlassSettings.PLACE_SHADE,
            )
    }
}
