/*
 * SPDX-FileCopyrightText: 2026 Project Catalyxx
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import com.android.internal.util.catalyxx.GlassClockView
import com.android.settings.R
import com.android.settingslib.utils.ThreadUtils
import kotlin.math.max

/**
 * What the preview's glass sits over, rounded. It has a fixed height, except that the Glass clock
 * takes the height its lines need, with room below them for the lock screen's buttons.
 */
class GlassPreviewStrip
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private val fixedHeight = resources.getDimensionPixelSize(R.dimen.glass_preview_height)
    private val roomForButtons = (ROOM_FOR_BUTTONS_DP * resources.displayMetrics.density).toInt()
    private val corner = resources.getDimension(R.dimen.glass_preview_corner)

    init {
        clipToOutline = true
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, corner)
                }
            }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var height = fixedHeight
        val clock = getChildAt(0) as? GlassClockView
        if (clock != null) {
            clock.measure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            height = max(height, clock.measuredHeight + roomForButtons)
        }
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    private companion object {
        const val ROOM_FOR_BUTTONS_DP = 48f
    }
}

/**
 * What the preview shows behind the glass: the wallpaper, or something that brings out what a dark
 * wallpaper hides. A white app page makes glass turn smoky; bright bands show how far it bends.
 */
class GlassPreviewBackdrop
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : ImageView(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bands =
        LinearGradient(
            0f,
            0f,
            BANDS_X_DP * density,
            BANDS_Y_DP * density,
            BAND_COLORS,
            null,
            Shader.TileMode.REPEAT,
        )

    /** One of the `BACKGROUND_` values. */
    var choice: Int = BACKGROUND_WALLPAPER
        set(value) {
            field = value
            invalidate()
        }

    init {
        scaleType = ScaleType.CENTER_CROP
        // A live wallpaper has no picture to show
        setBackgroundColor(NO_PICTURE_COLOR)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (drawable != null) return
        // RR: the wallpaper as Remixer's other previews read it, off the main thread
        val appContext = context.applicationContext
        ThreadUtils.postOnBackgroundThread {
            val picture = GlassPreviewView.wallpaper(appContext)
            ThreadUtils.postOnMainThread {
                if (picture != null && drawable == null) setImageBitmap(picture)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        when (choice) {
            BACKGROUND_LIGHT -> drawAppPage(canvas)
            BACKGROUND_COLORS -> {
                paint.shader = bands
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            }
            else -> super.onDraw(canvas)
        }
    }

    /** Rows of a white app page, each with a picture and two lines of text. */
    private fun drawAppPage(canvas: Canvas) {
        canvas.drawColor(PAGE_COLOR)
        var row = 0
        var y = 8 * density
        while (y < height) {
            paint.color = if (row % 2 == 0) PICTURE_WARM else PICTURE_COOL
            canvas.drawCircle(30 * density, y + 13 * density, 12 * density, paint)
            val x = 52 * density
            val first = x + (180 + row * 47 % 110) * density
            val second = x + (125 + row * 73 % 165) * density
            paint.color = TEXT_COLOR
            canvas.drawRoundRect(x, y + 3 * density, first, y + 11 * density, 4 * density, 4 * density, paint)
            paint.color = SUBTEXT_COLOR
            canvas.drawRoundRect(x, y + 16 * density, second, y + 22 * density, 3 * density, 3 * density, paint)
            y += ROW_DP * density
            row++
        }
    }

    companion object {
        const val BACKGROUND_WALLPAPER = 0
        const val BACKGROUND_LIGHT = 1
        const val BACKGROUND_COLORS = 2

        private const val NO_PICTURE_COLOR = 0xff201a2b.toInt()
        private const val PAGE_COLOR = 0xfff8f8fa.toInt()
        private const val PICTURE_WARM = 0xfff59678.toInt()
        private const val PICTURE_COOL = 0xff7896eb.toInt()
        private const val TEXT_COLOR = 0xff464850.toInt()
        private const val SUBTEXT_COLOR = 0xffa5a8b0.toInt()
        private const val ROW_DP = 30f

        // One round of the bands, along their slant
        private const val BANDS_X_DP = 213f
        private const val BANDS_Y_DP = 149f
        private val BAND_COLORS =
            intArrayOf(
                0xffff466e.toInt(),
                0xffffaa32.toInt(),
                0xfffaeb5a.toInt(),
                0xff46d796.toInt(),
                0xff3caaff.toInt(),
                0xff965aff.toInt(),
                0xffff466e.toInt(),
            )
    }
}
