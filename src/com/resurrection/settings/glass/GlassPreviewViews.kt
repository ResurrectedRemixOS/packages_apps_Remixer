/*
 * SPDX-FileCopyrightText: 2026 Project Catalyxx
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the preview's glass sits over, rounded. It has a fixed height, except that the Glass clock
 * takes the height its lines need, with room below them for what's laid over the strip's bottom.
 */
class GlassPreviewStrip
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private val corner = resources.getDimension(R.dimen.glass_preview_corner)

    /** The strip's height in pixels, and its least with the Glass clock in it. */
    var stageHeight = resources.getDimensionPixelSize(R.dimen.glass_preview_height)
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    /** The room kept under the Glass clock, in pixels. */
    var roomBelowClock = (ROOM_FOR_BUTTONS_DP * resources.displayMetrics.density).toInt()
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

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
        var height = stageHeight
        val clock = getChildAt(0) as? GlassClockView
        if (clock != null) {
            clock.measure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            height = max(height, clock.measuredHeight + roomBelowClock)
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
 * A loupe on the stage: [frame] shows what's in the square at ([sourceLeft], [sourceTop]), whose
 * side is the frame's over [scale], that many times bigger. In the stage's pixels.
 */
class GlassLoupe(
    val frame: RectF,
    val sourceLeft: Float,
    val sourceTop: Float,
    val scale: Float,
    val corner: Float,
)

/**
 * Where the Edges scene puts things on a stage of [width] by [height] pixels: a panel above a
 * button on the left, and on the right a loupe on the top right corner of one of them.
 */
internal class GlassEdgeLayout(width: Float, height: Float, density: Float) {
    // Far enough in for the button's rim, at the bend it has to start with, not to reach the page
    val left = 20 * density
    val right = width * 0.5f
    val panelTop = 12 * density
    val panelBottom = height * 0.5f - 5 * density
    val buttonTop = height * 0.5f + 5 * density
    val buttonBottom = height - 12 * density
    val radius = 28 * density
    val scale = SCALE

    // How far inside the loupe's sides the corner it looks at shows, before it's made bigger
    val margin = 8 * density

    private val size = min(height - 24 * density, width - right - 28 * density)
    private val loupeLeft = width - 14 * density - size
    private val loupeTop = (height - size) / 2
    private val loupeCorner = 14 * density

    /** The loupe on the top right corner of the piece whose top is at [pieceTop]. */
    fun loupe(pieceTop: Float) =
        GlassLoupe(
            RectF(loupeLeft, loupeTop, loupeLeft + size, loupeTop + size),
            right - (size / scale - margin),
            pieceTop - margin,
            scale,
            loupeCorner,
        )

    companion object {
        const val SCALE = 3f
    }
}

/**
 * What the preview shows behind the glass: the wallpaper, or something that brings out what a dark
 * wallpaper hides. A white app page makes glass turn smoky; lit lines show how far it bends.
 */
class GlassPreviewBackdrop
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : ImageView(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val loupePath = Path()
    private val lines = litLines()
    // A little of the theme's color over the lines' ground, more of it at the top
    private var wash: Shader? = null

    /** One of the `BACKGROUND_` values. */
    var choice: Int = BACKGROUND_WALLPAPER
        set(value) {
            field = value
            invalidate()
        }

    /**
     * RR: a loupe to draw this again under, bigger: the glass in it is bigger by as much, so
     * what's behind it has to be too. Null for none.
     */
    var loupe: GlassLoupe? = null
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

    /**
     * RR: thin lit lines on a slant, two in the theme's color and then a white one, round after
     * round. They cross every side of a piece of glass, so its bend shows all the way round, and
     * the white ones give Rainbow edges white light to split. They take the place of bands of
     * every color, which had nothing of RR in them.
     */
    private fun litLines(): Shader {
        val accent = context.getColor(android.R.color.system_accent1_200)
        val colors = ArrayList<Int>()
        val stops = ArrayList<Float>()
        fun stop(color: Int, alpha: Float, atDp: Float) {
            colors.add(faded(color, alpha))
            stops.add(atDp / LINES_ROUND_DP)
        }
        stop(accent, 0f, 0f)
        for ((color, middle) in listOf(accent to 14f, accent to 42f, Color.WHITE to 70f)) {
            stop(color, 0f, middle - LINE_HALO_DP)
            stop(color, 0.3f, middle - LINE_CORE_DP)
            stop(color, 1f, middle - LINE_CORE_DP)
            stop(color, 1f, middle + LINE_CORE_DP)
            stop(color, 0.3f, middle + LINE_CORE_DP)
            stop(color, 0f, middle + LINE_HALO_DP)
        }
        stop(Color.WHITE, 0f, LINES_ROUND_DP)
        return LinearGradient(
            0f,
            0f,
            LINES_X_DP * density,
            LINES_Y_DP * density,
            colors.toIntArray(),
            stops.toFloatArray(),
            Shader.TileMode.REPEAT,
        )
    }

    /** [color] at [alpha], 0 to 1. */
    private fun faded(color: Int, alpha: Float) =
        Color.argb((255 * alpha).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val tint = context.getColor(android.R.color.system_accent1_400)
        wash =
            LinearGradient(
                0f,
                0f,
                0f,
                h.toFloat(),
                faded(tint, 0.13f),
                faded(tint, 0.03f),
                Shader.TileMode.CLAMP,
            )
    }

    override fun onDraw(canvas: Canvas) {
        drawBackdrop(canvas)
        val through = loupe ?: return
        val count = canvas.save()
        loupePath.rewind()
        loupePath.addRoundRect(through.frame, through.corner, through.corner, Path.Direction.CW)
        canvas.clipPath(loupePath)
        canvas.drawColor(NO_PICTURE_COLOR)
        canvas.translate(through.frame.left, through.frame.top)
        canvas.scale(through.scale, through.scale)
        canvas.translate(-through.sourceLeft, -through.sourceTop)
        drawBackdrop(canvas)
        canvas.restoreToCount(count)
    }

    private fun drawBackdrop(canvas: Canvas) {
        when (choice) {
            BACKGROUND_LIGHT -> drawAppPage(canvas)
            BACKGROUND_LINES -> {
                paint.color = LINES_GROUND
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                // A shader draws through the paint's own alpha
                paint.color = Color.WHITE
                paint.shader = wash
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = lines
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            }
            else -> super.onDraw(canvas)
        }
    }

    /** Rows of a white app page, each with a picture and two lines of text. */
    private fun drawAppPage(canvas: Canvas) {
        paint.color = PAGE_COLOR
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
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
        const val BACKGROUND_LINES = 2

        private const val NO_PICTURE_COLOR = 0xff201a2b.toInt()
        private const val PAGE_COLOR = 0xfff8f8fa.toInt()
        private const val PICTURE_WARM = 0xfff59678.toInt()
        private const val PICTURE_COOL = 0xff7896eb.toInt()
        private const val TEXT_COLOR = 0xff464850.toInt()
        private const val SUBTEXT_COLOR = 0xffa5a8b0.toInt()
        private const val ROW_DP = 30f

        // The lines: their ground, how long one round of three is along the slant and across
        // and down, and half of each line's lit middle and of the glow around it
        private const val LINES_GROUND = 0xff0a0c11.toInt()
        private const val LINES_ROUND_DP = 84f
        private const val LINES_X_DP = 68.8f
        private const val LINES_Y_DP = 48.2f
        private const val LINE_CORE_DP = 1f
        private const val LINE_HALO_DP = 7f
    }
}
