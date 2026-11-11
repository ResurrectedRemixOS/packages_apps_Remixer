/*
 * SPDX-FileCopyrightText: 2026 Project Catalyxx
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.android.internal.R as InternalR
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.internal.util.catalyxx.GlassLook
import com.android.internal.util.catalyxx.GlassSettings
import com.android.settings.R
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The glass in [GlassPreview]'s window: small copies of what the page sets, in the drawables
 * SurfaceFlinger draws as glass, each with the settings of the place it copies. Where glass is off
 * the copy is plain, as the real one is. Each [GlassScene] has its own pieces: every place glass
 * has, the edge under a loupe, glass that answers a finger, and glass in motion.
 */
internal class GlassSampleView(
    context: Context,
    private val viewWidth: Int,
    private val viewHeight: Int,
) : View(context) {

    /** What a piece does besides being glass. */
    private enum class Role {
        PLAIN,
        // The ringer picker: a tap moves the glass selector
        RINGER,
        // A slider: a drag moves its level, under a glass knob
        SLIDER,
        // The panel that opens out of the screen's edge and closes again
        MORPH,
        // The glass in the loupe: a corner of another piece, three times the size
        LOUPE,
    }

    /** A piece of glass, in pixels from this view's corner as it rests, and what's on it. */
    private inner class Piece(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radius: Float,
        // The first of these with glass on sets the look
        val places: List<String>,
        val role: Role = Role.PLAIN,
        val melts: Boolean = false,
        val content: Canvas.(Piece) -> Unit = {},
    ) {
        var glass: BackgroundBlurDrawable? = null
        var isGlass = false
        var hasRim = false

        // How far it's squished, 0 to 1, on a spring
        var depth = 0f
        var speed = 0f
        var target = 0f
        var damping = 1f
        var stiffness = 1000f
        var squishDp = 0f

        // How bright the touch glow is, 0 to 1
        var glow = 0f
        var glowTarget = 0f

        // How it's drawn away from rest: scaled about a point of it, moved and faded
        var scaleX = 1f
        var scaleY = 1f
        var pivotX = 0.5f
        var pivotY = 0.5f
        var shiftX = 0f
        var fade = 1f

        val width
            get() = right - left

        val height
            get() = bottom - top

        fun squish() = 1f - depth * 2f * squishDp * density / max(width, height)
    }

    private val density = resources.displayMetrics.density
    private val fullWidth = viewWidth.toFloat()
    private val fullHeight = viewHeight.toFloat()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
        }
    private val titlePaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(16f)
            typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        }
    private val textPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SOFT_WHITE
            textSize = dp(14f)
        }
    private val badgePaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK_INK
            textSize = dp(12f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, 700, false)
        }
    private val icons = HashMap<Int, Drawable>()
    private val clipPath = Path()
    private val drawn = RectF()
    private val radii = FloatArray(8)

    /** What the pieces show. */
    var scene = GlassScene.ALL
        set(value) {
            if (field != value) {
                field = value
                rebuild()
            }
        }

    /** The loupe the Edges scene looks through, for what's behind the glass to draw under it. */
    var loupe: GlassLoupe? = null
        private set

    /** Runs when the pieces have been laid out again, which can change [loupe]. */
    var onRebuilt: (() -> Unit)? = null

    private var pieces = emptyList<Piece>()
    // Whether the pieces were laid out for RR's lens edge, which the loupe looks at on a panel
    private var builtForLens = false
    private var running = true
    // How much of the page shows, 0 to 1, as it comes and goes
    private var shown = 1f

    // The settings the scenes move by, read with the looks
    private var selectorOn = true
    private var magnify = GlassSettings.DEFAULT_SELECTOR_MAGNIFY
    private var stretchOn = true
    private var morphs = true
    private var morphStyle = GlassSettings.DEFAULT_MORPH_STYLE
    private var morphSpeed = GlassSettings.DEFAULT_MORPH_SPEED

    // Touch scene
    private var touched: Piece? = null
    private var pressed: Piece? = null
    private var pressedAt = 0L
    private var touchX = 0f
    private var touchY = 0f
    private var glowRadius = 0f
    private var glowColor = Color.WHITE
    private var glowRainbow = false
    private val glowShader by lazy(LazyThreadSafetyMode.NONE) { RuntimeShader(GLOW_SHADER) }
    private val glowPaint = Paint()
    private var ringerIndex = 1
    // The selector's middle along the ringer, or below 0 until it's first drawn
    private var lensX = -1f
    private var lensStretch = 1f
    private var sliderLevel = 0.6f
    private var sliderHeld = false

    // Motion scene: where the panel is in its round of opening, staying and closing, in ms
    private var motionMs = 0f

    private val frames =
        TimeAnimator().apply {
            setTimeListener { _, _, deltaMs -> step(min(deltaMs, 32L)) }
        }
    private val release = Runnable { releasePressed() }

    private fun dp(value: Float) = value * density

    private fun string(id: Int) = context.getString(id)

    /** Whether Liquid glass has RR's lens edge now. */
    private fun hasLensEdge() =
        GlassSettings.getInt(
            context.contentResolver,
            GlassSettings.EDGE_STYLE,
            GlassSettings.DEFAULT_EDGE_STYLE,
        ) != GlassSettings.EDGE_CATALYXX

    private fun buildPieces(): List<Piece> =
        when (scene) {
            GlassScene.EDGE -> edgePieces()
            GlassScene.TOUCH -> touchPieces()
            GlassScene.MOTION -> motionPieces()
            else -> allPieces()
        }

    /** Every place glass has, a piece each: switch a place off and its piece goes plain. */
    private fun allPieces(): List<Piece> {
        val side = dp(14f)
        val gap = dp(5f)
        val middle = fullWidth / 2
        val end = fullWidth - side
        val rowTop = dp(148f)
        val rowBottom = dp(200f)
        val round = (rowBottom - rowTop) / 2
        return listOf(
            Piece(side, dp(10f), end, dp(72f), dp(24f), NOTIFICATIONS) {
                drawNotification(this, it)
            },
            Piece(side, dp(82f), middle - gap, dp(138f), dp(28f), SHADE) {
                drawTile(
                    this,
                    it,
                    R.string.glass_preview_tile_internet,
                    InternalR.drawable.ic_wifi_signal_4,
                    true,
                )
            },
            Piece(middle + gap, dp(82f), end, dp(138f), dp(28f), SHADE) {
                drawTile(
                    this,
                    it,
                    R.string.glass_preview_tile_bluetooth,
                    InternalR.drawable.ic_qs_bluetooth,
                    false,
                )
            },
            Piece(side, rowTop, end - dp(198f), rowBottom, round, VOLUME) {
                drawLevel(this, it, 0.62f, InternalR.drawable.ic_audio_vol)
            },
            // Clear glass bends what's beside it into its rim: at the stage's side that's the
            // page, a bright sliver in the light theme, so the one clear piece stays off it
            Piece(end - dp(188f), rowTop, end - dp(136f), rowBottom, round, LOCKSCREEN) {
                drawIcon(
                    this,
                    InternalR.drawable.ic_qs_flashlight,
                    it.width / 2,
                    it.height / 2,
                    dp(22f),
                    Color.WHITE,
                )
            },
            Piece(end - dp(126f), rowTop, end - dp(74f), rowBottom, round, POWER) {
                drawIcon(
                    this,
                    InternalR.drawable.ic_lock_power_off,
                    it.width / 2,
                    it.height / 2,
                    dp(22f),
                    Color.WHITE,
                )
            },
            Piece(end - dp(64f), rowTop, end, rowBottom, dp(18f), DIALOGS) {
                val label = string(R.string.glass_stage_ok)
                drawText(
                    label,
                    (it.width - titlePaint.measureText(label)) / 2,
                    it.height / 2 + dp(6f),
                    titlePaint,
                )
            },
        )
    }

    /**
     * The edge up close: glass with text on it above glass without, and a corner of one of them
     * in the loupe. RR's lens edge shows on the panel; without it the edge only bends on clear
     * glass, the button.
     */
    private fun edgePieces(): List<Piece> {
        val layout = GlassEdgeLayout(fullWidth, fullHeight, density)
        val lens = hasLensEdge()
        val looksAt = if (lens) TEXT_PLACES else BUTTON_PLACES
        val through = layout.loupe(if (lens) layout.panelTop else layout.buttonTop)
        loupe = through
        val inset = layout.margin * layout.scale
        return listOf(
            Piece(
                layout.left,
                layout.panelTop,
                layout.right,
                layout.panelBottom,
                layout.radius,
                TEXT_PLACES,
            ) {
                drawCardLabel(this, it, string(R.string.glass_preview_panels))
            },
            Piece(
                layout.left,
                layout.buttonTop,
                layout.right,
                layout.buttonBottom,
                layout.radius,
                BUTTON_PLACES,
            ) {
                drawCardLabel(this, it, string(R.string.glass_preview_buttons))
            },
            // Its top right corner; the piece carries on past the loupe's left and bottom
            Piece(
                through.frame.left,
                through.frame.top + inset,
                through.frame.right - inset,
                through.frame.bottom,
                layout.radius * layout.scale,
                looksAt,
                Role.LOUPE,
            ),
        )
    }

    /** Glass under a finger: a tile to press, the ringer with its selector, and a slider. */
    private fun touchPieces(): List<Piece> {
        val side = dp(14f)
        val gap = dp(5f)
        val middle = fullWidth / 2
        val end = fullWidth - side
        // Two rows, in the middle of the stage's height
        val top = (fullHeight - dp(150f)) / 2
        return listOf(
            Piece(side, top, middle - gap, top + dp(70f), dp(26f), SHADE) {
                drawTile(
                    this,
                    it,
                    R.string.glass_preview_tile_internet,
                    InternalR.drawable.ic_wifi_signal_4,
                    false,
                )
            },
            Piece(middle + gap, top, end, top + dp(70f), dp(35f), VOLUME, Role.RINGER) {
                drawRinger(this, it)
            },
            Piece(side, top + dp(90f), end, top + dp(150f), dp(30f), SHADE, Role.SLIDER) {
                drawSlider(this, it)
            },
        )
    }

    /**
     * Glass in motion: two pieces close enough to melt together, and a panel that keeps opening
     * out of the edge of the screen, where the volume keys are, and closing.
     */
    private fun motionPieces(): List<Piece> {
        val side = dp(14f)
        val end = fullWidth - side
        val panelWidth = dp(62f)
        // The pair, in the middle of the stage's height beside the panel
        val top = (fullHeight - dp(118f)) / 2
        return listOf(
            Piece(
                side,
                top,
                end - panelWidth - dp(30f),
                top + dp(64f),
                dp(24f),
                NOTIFICATIONS,
                melts = true,
            ) {
                drawIcon(
                    this,
                    android.R.drawable.ic_media_play,
                    dp(32f),
                    it.height / 2,
                    dp(30f),
                    Color.WHITE,
                )
                drawText(
                    string(R.string.glass_preview_now_playing),
                    dp(58f),
                    it.height / 2 + dp(5f),
                    textPaint,
                )
            },
            Piece(
                side + dp(40f),
                top + dp(72f),
                side + dp(160f),
                top + dp(118f),
                dp(23f),
                NOTIFICATIONS,
                melts = true,
            ) {
                // A stop button
                fill.color = Color.WHITE
                val half = dp(7f)
                drawRoundRect(
                    it.width / 2 - half,
                    it.height / 2 - half,
                    it.width / 2 + half,
                    it.height / 2 + half,
                    dp(2f),
                    dp(2f),
                    fill,
                )
            },
            Piece(
                    end - panelWidth,
                    dp(12f),
                    end,
                    fullHeight - dp(12f),
                    dp(31f),
                    VOLUME,
                    Role.MORPH,
                ) {
                    drawVolumePanel(this, it)
                }
                .apply {
                    // It opens out of the edge beside it
                    pivotX = 1f
                },
        )
    }

    private fun drawCardLabel(canvas: Canvas, piece: Piece, label: String) {
        canvas.drawText(label, dp(18f), piece.height / 2 + dp(6f), titlePaint)
    }

    private fun drawNotification(canvas: Canvas, piece: Piece) {
        fill.color = context.getColor(android.R.color.system_accent1_200)
        canvas.drawCircle(dp(34f), piece.height / 2, dp(19f), fill)
        drawIcon(canvas, R.drawable.ic_rr_tune, dp(34f), piece.height / 2, dp(22f), BLACK_INK)
        val room = piece.width - dp(80f)
        canvas.drawText(string(R.string.liquid_glass_title), dp(66f), dp(27f), titlePaint)
        canvas.drawText(
            TextUtils.ellipsize(
                    string(R.string.glass_preview_sample_text),
                    textPaint,
                    room,
                    TextUtils.TruncateAt.END,
                )
                .toString(),
            dp(66f),
            dp(48f),
            textPaint,
        )
    }

    private fun drawTile(canvas: Canvas, piece: Piece, name: Int, icon: Int, isOn: Boolean) {
        val ink = if (isOn) BLACK_INK else Color.WHITE
        if (isOn) {
            fill.color = ON_TILE
            canvas.drawRoundRect(0f, 0f, piece.width, piece.height, piece.radius, piece.radius, fill)
        }
        drawIcon(canvas, icon, dp(30f), piece.height / 2, dp(22f), ink)
        textPaint.color = ink
        val label =
            TextUtils.ellipsize(
                string(name),
                textPaint,
                piece.width - dp(60f),
                TextUtils.TruncateAt.END,
            )
        canvas.drawText(label.toString(), dp(52f), piece.height / 2 + dp(5f), textPaint)
        textPaint.color = SOFT_WHITE
    }

    /** A slider filled to [level], with [icon] at its start. */
    private fun drawLevel(canvas: Canvas, piece: Piece, level: Float, icon: Int) {
        val pad = dp(5f)
        val round = piece.height / 2 - pad
        val to = pad + max((piece.width - 2 * pad) * level, 2 * round)
        fill.color = Color.WHITE
        fill.shader =
            LinearGradient(
                pad,
                0f,
                to,
                0f,
                context.getColor(android.R.color.system_accent1_400),
                context.getColor(android.R.color.system_accent1_200),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(pad, pad, to, piece.height - pad, round, round, fill)
        fill.shader = null
        drawIcon(canvas, icon, pad + round, piece.height / 2, dp(20f), BLACK_INK)
    }

    /** The ringer picker: the picked mode sits in the glass selector, which slides to a tap. */
    private fun drawRinger(canvas: Canvas, piece: Piece) {
        val cell = ringerCell(piece)
        val middleY = piece.height / 2
        val at = ringerX(piece, ringerIndex)
        if (lensX < 0f) {
            lensX = at
        }
        val lens = selectorOn && piece.isGlass
        if (lens) {
            val width = dp(LENS_WIDTH_DP) * lensStretch
            val height = piece.height - dp(12f)
            drawLens(
                canvas,
                lensX - width / 2,
                middleY - height / 2,
                lensX + width / 2,
                middleY + height / 2,
            )
        } else {
            fill.color = DARK_WELL
            canvas.drawCircle(at, middleY, dp(22f), fill)
        }
        for (i in RINGER_ICONS.indices) {
            val x = ringerX(piece, i)
            // What's under the selector shows bigger through it
            val under = if (lens) 1f - min(abs(x - lensX) / cell, 1f) else 0f
            val size = dp(22f) * (1f + MAGNIFY[magnify.coerceIn(0, 2)] * under)
            drawIcon(canvas, RINGER_ICONS[i], x, middleY, size, Color.WHITE)
        }
    }

    /** How wide each of the ringer's modes is: they stop short of its round ends. */
    private fun ringerCell(piece: Piece) =
        (piece.width - 2 * dp(RINGER_INSET_DP)) / RINGER_ICONS.size

    /** Where the middle of the ringer's mode at [index] is, from the ringer's left. */
    private fun ringerX(piece: Piece, index: Int) =
        dp(RINGER_INSET_DP) + ringerCell(piece) * (index + 0.5f)

    /** A slider whose knob turns into a glass lens while it's dragged. */
    private fun drawSlider(canvas: Canvas, piece: Piece) {
        drawLevel(canvas, piece, sliderLevel, InternalR.drawable.ic_audio_vol)
        val pad = dp(5f)
        val round = piece.height / 2 - pad
        val x = pad + max((piece.width - 2 * pad) * sliderLevel, 2 * round)
        if (sliderHeld && selectorOn && piece.isGlass) {
            val width = dp(LENS_WIDTH_DP) * (if (stretchOn) HELD_STRETCH else 1f)
            drawLens(canvas, x - width / 2, -dp(4f), x + width / 2, piece.height + dp(4f))
        } else {
            fill.color = Color.WHITE
            canvas.drawRoundRect(
                x - dp(2f),
                pad + dp(6f),
                x + dp(2f),
                piece.height - pad - dp(6f),
                dp(2f),
                dp(2f),
                fill,
            )
        }
    }

    /** A small lens of glass, as the glass selector looks. */
    private fun drawLens(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val round = (bottom - top) / 2
        fill.color = Color.WHITE
        fill.shader =
            LinearGradient(left, top, right, bottom, LENS_FROM, LENS_TO, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(left, top, right, bottom, round, round, fill)
        fill.shader = null
        line.color = LENS_RIM
        line.strokeWidth = dp(1f)
        line.pathEffect = null
        canvas.drawRoundRect(left, top, right, bottom, round, round, line)
    }

    /** The volume panel: the ringer on top and the slider below. */
    private fun drawVolumePanel(canvas: Canvas, piece: Piece) {
        val middle = piece.width / 2
        fill.color = DARK_WELL
        canvas.drawCircle(middle, dp(29f), dp(21f), fill)
        drawIcon(
            canvas,
            InternalR.drawable.ic_audio_ring_notif,
            middle,
            dp(29f),
            dp(22f),
            context.getColor(android.R.color.system_accent1_100),
        )
        val inset = dp(11f)
        val from = dp(58f)
        val to = piece.height - dp(11f)
        fill.color = DARK_WELL
        canvas.drawRoundRect(inset, from, piece.width - inset, to, dp(13f), dp(13f), fill)
        val level = to - (to - from) * 0.66f
        fill.color = Color.WHITE
        fill.shader =
            LinearGradient(
                0f,
                level,
                0f,
                to,
                context.getColor(android.R.color.system_accent1_400),
                context.getColor(android.R.color.system_accent1_200),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(inset, level, piece.width - inset, to, dp(13f), dp(13f), fill)
        fill.shader = null
    }

    private fun drawIcon(canvas: Canvas, id: Int, x: Float, y: Float, size: Float, color: Int) {
        val icon = icons.getOrPut(id) { context.getDrawable(id)!!.mutate() }
        icon.setTint(color)
        val half = (size / 2).roundToInt()
        icon.setBounds(
            x.roundToInt() - half,
            y.roundToInt() - half,
            x.roundToInt() + half,
            y.roundToInt() + half,
        )
        icon.draw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rebuild()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        frames.cancel()
        removeCallbacks(release)
        dropGlass()
    }

    private fun dropGlass() {
        for (piece in pieces) {
            piece.glass?.setVisible(false, false)
            piece.glass = null
        }
    }

    /** Lays the scene's pieces out again, each with its glass. */
    private fun rebuild() {
        frames.cancel()
        removeCallbacks(release)
        pressed = null
        touched = null
        sliderHeld = false
        lensX = -1f
        lensStretch = 1f
        motionMs = 0f
        dropGlass()
        loupe = null
        builtForLens = hasLensEdge()
        pieces = buildPieces()
        onRebuilt?.invoke()
        val viewRoot = viewRootImpl ?: return
        for (piece in pieces) {
            piece.glass =
                viewRoot.createBackgroundBlurDrawable().apply {
                    // Nothing behind it in this window to make a hole through
                    setXfermode(null)
                }
        }
        refresh()
        if (scene == GlassScene.MOTION && running) {
            frames.start()
        }
    }

    /** Reads the settings again. */
    fun refresh() {
        if (scene == GlassScene.EDGE && builtForLens != hasLensEdge()) {
            // The loupe looks at another piece now
            rebuild()
            return
        }
        val resolver = context.contentResolver
        val isBlurOn = GlassPreview.isBlurOn(context)
        selectorOn = GlassSettings.getInt(resolver, GlassSettings.SELECTOR, 1) == 1
        magnify =
            GlassSettings.getInt(
                resolver,
                GlassSettings.SELECTOR_MAGNIFY,
                GlassSettings.DEFAULT_SELECTOR_MAGNIFY,
            )
        stretchOn = GlassSettings.getInt(resolver, GlassSettings.STRETCH, 1) == 1
        morphs = GlassSettings.morphs(resolver, GlassSettings.MORPH_VOLUME)
        morphStyle =
            GlassSettings.getInt(
                resolver,
                GlassSettings.MORPH_STYLE,
                GlassSettings.DEFAULT_MORPH_STYLE,
            )
        morphSpeed =
            GlassSettings.getInt(
                    resolver,
                    GlassSettings.MORPH_SPEED,
                    GlassSettings.DEFAULT_MORPH_SPEED,
                )
                .coerceIn(GlassSettings.MORPH_SLOW, GlassSettings.MORPH_FAST)
        for (piece in pieces) {
            val glass = piece.glass ?: continue
            val place =
                piece.places.firstOrNull { GlassSettings.isOn(resolver, it) } ?: piece.places.first()
            piece.isGlass = isBlurOn && GlassSettings.isOn(resolver, place)
            piece.hasRim = piece.isGlass && GlassSettings.hasRim(resolver, place)
            glass.setVisible(piece.isGlass, false)
            if (!piece.isGlass) {
                continue
            }
            // The loupe's glass is the same glass, three times the size
            val zoom = if (piece.role == Role.LOUPE) LOUPE_SCALE else 1f
            val look = GlassSettings.look(context, place, ANDROID_TINT)?.let { zoomed(it, zoom) }
            glass.setGlass(look)
            // Flat glass is plain blur, and the tint is the place's to draw
            glass.setColor(
                if (look == null) GlassSettings.tintColor(context, place, ANDROID_TINT)
                else Color.TRANSPARENT
            )
            glass.setBlurRadius(
                GlassSettings.scaleBlurRadius(
                    (dp(BLUR_RADIUS_DP) * zoom).roundToInt(),
                    GlassSettings.blurScale(resolver, place),
                )
            )
            if (piece.role == Role.LOUPE) {
                glass.setCornerRadius(0f, piece.radius, 0f, 0f)
                glass.setGlassJoinedSides(
                    BackgroundBlurDrawable.GLASS_SIDE_LEFT or BackgroundBlurDrawable.GLASS_SIDE_BOTTOM
                )
            }
            glass.setGlassMelt(if (piece.melts) GlassSettings.meltSize(resolver, density) else 0f)
            shape(piece)
        }
        invalidate()
    }

    /**
     * Gives [piece]'s glass its corners and how much of it shows. Never while drawing: what a
     * drawable is told then reaches SurfaceFlinger with the frame after, and a still scene has
     * none, which left the glass with square corners.
     */
    private fun shape(piece: Piece) {
        val glass = piece.glass ?: return
        glass.alpha = (255 * shown * piece.fade).roundToInt()
        if (piece.role != Role.LOUPE) {
            glass.setCornerRadius(piece.radius * min(piece.scaleX, piece.scaleY))
        }
    }

    /** [look] for glass [zoom] times the size, which casts no shadow in the loupe. */
    private fun zoomed(look: GlassLook, zoom: Float): GlassLook =
        if (zoom == 1f) {
            look
        } else {
            GlassLook(
                look.rimWidth * zoom,
                look.bend,
                look.frost,
                look.rimLight,
                look.dispersion,
                0f,
                look.tintOverDark,
                look.tintOverLight,
                look.adaptPoint,
                look.lensEdge,
            )
        }

    /** Fades the glass with the page, as it comes and goes: [alpha] is how much of it shows. */
    fun fade(alpha: Float) {
        if (alpha != shown) {
            shown = alpha
            this.alpha = alpha
            for (piece in pieces) {
                shape(piece)
            }
            invalidate()
        }
    }

    /** Whether the scene moves: it rests while the page is behind something else. */
    fun setRunning(running: Boolean) {
        this.running = running
        if (!running) {
            frames.cancel()
        } else if (scene == GlassScene.MOTION && isAttachedToWindow) {
            frames.start()
        }
    }

    /** Where [piece] is drawn now, into [drawn]. */
    private fun place(piece: Piece) {
        val squish = piece.squish()
        val scaleX = piece.scaleX * squish
        val scaleY = piece.scaleY * squish
        val x = piece.left + piece.width * piece.pivotX
        val y = piece.top + piece.height * piece.pivotY
        drawn.set(
            x + (piece.left - x) * scaleX + piece.shiftX,
            y + (piece.top - y) * scaleY,
            x + (piece.right - x) * scaleX + piece.shiftX,
            y + (piece.bottom - y) * scaleY,
        )
    }

    override fun onDraw(canvas: Canvas) {
        for (piece in pieces) {
            place(piece)
            val radius = piece.radius * min(piece.scaleX, piece.scaleY)
            if (piece.isGlass) {
                val glass = piece.glass ?: continue
                glass.setBounds(
                    drawn.left.roundToInt(),
                    drawn.top.roundToInt(),
                    drawn.right.roundToInt(),
                    drawn.bottom.roundToInt(),
                )
                glass.draw(canvas)
            } else {
                fill.color = context.getColor(android.R.color.system_neutral2_800)
                fill.alpha = (255 * piece.fade).roundToInt()
                if (piece.role == Role.LOUPE) {
                    // Only its top right corner is round
                    radii.fill(0f)
                    radii[2] = radius
                    radii[3] = radius
                    clipPath.rewind()
                    clipPath.addRoundRect(drawn, radii, Path.Direction.CW)
                    canvas.drawPath(clipPath, fill)
                } else {
                    canvas.drawRoundRect(drawn, radius, radius, fill)
                }
                fill.alpha = 255
            }
        }
        for (piece in pieces) {
            if (piece.fade <= 0f) {
                continue
            }
            place(piece)
            if (piece.glow > 0f) {
                drawGlow(canvas, piece)
            }
            val squish = piece.squish()
            val count =
                if (piece.fade < 1f) {
                    canvas.saveLayerAlpha(drawn, (255 * piece.fade).roundToInt())
                } else {
                    canvas.save()
                }
            canvas.translate(piece.shiftX, 0f)
            canvas.scale(
                piece.scaleX * squish,
                piece.scaleY * squish,
                piece.left + piece.width * piece.pivotX,
                piece.top + piece.height * piece.pivotY,
            )
            canvas.translate(piece.left, piece.top)
            piece.content(canvas, piece)
            canvas.restoreToCount(count)
        }
        when (scene) {
            GlassScene.EDGE -> drawLoupeFrame(canvas)
            GlassScene.MOTION -> drawKeys(canvas)
            else -> {}
        }
    }

    /** The loupe's frame and how much bigger it shows, and a dashed mark around what it shows. */
    private fun drawLoupeFrame(canvas: Canvas) {
        val through = loupe ?: return
        line.color = Color.WHITE
        line.strokeWidth = dp(2f)
        line.pathEffect = null
        canvas.drawRoundRect(through.frame, through.corner, through.corner, line)
        val seen = through.frame.width() / through.scale
        line.strokeWidth = dp(1.5f)
        line.pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
        canvas.drawRoundRect(
            through.sourceLeft,
            through.sourceTop,
            through.sourceLeft + seen,
            through.sourceTop + seen,
            dp(6f),
            dp(6f),
            line,
        )
        line.pathEffect = null
        val label = string(R.string.glass_stage_loupe)
        val right = through.frame.right - dp(8f)
        val bottom = through.frame.bottom - dp(8f)
        val width = badgePaint.measureText(label) + dp(16f)
        fill.color = Color.WHITE
        canvas.drawRoundRect(right - width, bottom - dp(22f), right, bottom, dp(11f), dp(11f), fill)
        canvas.drawText(label, right - width / 2, bottom - dp(7f), badgePaint)
    }

    /** The volume keys on the edge of the phone, where the panel opens out of. */
    private fun drawKeys(canvas: Canvas) {
        val panel = pieces.firstOrNull { it.role == Role.MORPH } ?: return
        val middle = (panel.top + panel.bottom) / 2
        fill.color = SOFT_WHITE
        canvas.drawRoundRect(
            fullWidth - dp(4f),
            middle - dp(30f),
            fullWidth + dp(4f),
            middle + dp(30f),
            dp(4f),
            dp(4f),
            fill,
        )
    }

    /** The soft pool of light under a finger, as Quick Settings tiles draw it. */
    private fun drawGlow(canvas: Canvas, piece: Piece) {
        val shader = glowShader
        shader.setFloatUniform("touchPoint", touchX, touchY)
        shader.setFloatUniform("touchRadius", glowRadius)
        shader.setFloatUniform(
            "touchColor",
            Color.red(glowColor) / 255f,
            Color.green(glowColor) / 255f,
            Color.blue(glowColor) / 255f,
        )
        shader.setFloatUniform("touchRainbow", if (glowRainbow) 1f else 0f)
        shader.setFloatUniform("amount", piece.glow)
        glowPaint.shader = shader
        clipPath.rewind()
        clipPath.addRoundRect(drawn, piece.radius, piece.radius, Path.Direction.CW)
        val count = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawPaint(glowPaint)
        canvas.restoreToCount(count)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (scene != GlassScene.TOUCH) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val piece =
                    pieces.firstOrNull {
                        event.x in it.left..it.right && event.y in it.top..it.bottom
                    } ?: return false
                when (piece.role) {
                    Role.SLIDER -> {
                        sliderHeld = true
                        dragSlider(piece, event.x)
                    }
                    Role.RINGER -> {}
                    else -> if (piece.isGlass) press(piece, event.x, event.y) else return false
                }
                touched = piece
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                touched?.takeIf { it.role == Role.SLIDER }?.let { dragSlider(it, event.x) }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val piece = touched
                touched = null
                if (piece != null && piece.role == Role.SLIDER) {
                    sliderHeld = false
                    invalidate()
                } else if (piece != null && piece.role == Role.RINGER) {
                    ringerIndex =
                        ((event.x - piece.left - dp(RINGER_INSET_DP)) / ringerCell(piece))
                            .toInt()
                            .coerceIn(0, RINGER_ICONS.size - 1)
                    buzz()
                    frames.start()
                } else {
                    buzz()
                    // A quick tap stays pressed long enough for its squish to show
                    val left = MIN_PRESS_MS - (SystemClock.uptimeMillis() - pressedAt)
                    if (left > 0) postDelayed(release, left) else releasePressed()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                touched = null
                sliderHeld = false
                releasePressed()
                invalidate()
            }
        }
        return true
    }

    private fun dragSlider(piece: Piece, x: Float) {
        val pad = dp(5f)
        sliderLevel = ((x - piece.left - pad) / (piece.width - 2 * pad)).coerceIn(0f, 1f)
        invalidate()
    }

    private fun press(piece: Piece, x: Float, y: Float) {
        removeCallbacks(release)
        releasePressed()
        val resolver = context.contentResolver
        pressed = piece
        pressedAt = SystemClock.uptimeMillis()
        touchX = x
        touchY = y
        val spring =
            SQUISHES[
                GlassSettings.getInt(resolver, GlassSettings.SQUISH, GlassSettings.DEFAULT_SQUISH)]
        if (spring != null) {
            piece.squishDp = spring.edgeDp
            piece.damping = spring.pressDamping
            piece.stiffness = spring.pressStiffness
            piece.target = 1f
        }
        if (piece.hasRim && GlassSettings.getInt(resolver, GlassSettings.TOUCH_GLOW, 1) == 1) {
            val style =
                GlassSettings.getInt(
                    resolver,
                    GlassSettings.TOUCH_GLOW_COLOR,
                    GlassSettings.GLOW_WHITE,
                )
            glowRainbow = style == GlassSettings.GLOW_RAINBOW
            glowColor =
                if (style == GlassSettings.GLOW_ACCENT) {
                    context.getColor(android.R.color.system_accent1_200)
                } else {
                    Color.WHITE
                }
            val size =
                GlassSettings.getInt(
                        resolver,
                        GlassSettings.TOUCH_GLOW_SIZE,
                        GlassSettings.DEFAULT_TOUCH_GLOW_SIZE,
                    )
                    .coerceIn(GlassSettings.MIN_TOUCH_GLOW_SIZE, GlassSettings.MAX_TOUCH_GLOW_SIZE)
            glowRadius = dp(GLOW_RADIUS_DP) * size / 100f
            piece.glowTarget = 1f
        }
        frames.start()
    }

    private fun releasePressed() {
        val piece = pressed ?: return
        pressed = null
        val spring =
            SQUISHES[
                GlassSettings.getInt(
                    context.contentResolver,
                    GlassSettings.SQUISH,
                    GlassSettings.DEFAULT_SQUISH,
                )]
        if (spring != null) {
            piece.damping = spring.releaseDamping
            piece.stiffness = spring.releaseStiffness
        }
        piece.target = 0f
        piece.glowTarget = 0f
        frames.start()
    }

    private fun buzz() {
        val feedback =
            when (
                GlassSettings.getInt(
                    context.contentResolver,
                    GlassSettings.TAP_BUZZ,
                    GlassSettings.TAP_BUZZ_OFF,
                )
            ) {
                GlassSettings.TAP_BUZZ_LIGHT -> HapticFeedbackConstants.CONTEXT_CLICK
                GlassSettings.TAP_BUZZ_MEDIUM -> HapticFeedbackConstants.VIRTUAL_KEY
                else -> return
            }
        performHapticFeedback(feedback, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    private fun step(deltaMs: Long) {
        var isMoving = false
        for (piece in pieces) {
            var left = deltaMs / 1000f
            while (left > 0f) {
                val dt = min(left, SPRING_STEP_S)
                val friction = 2f * piece.damping * sqrt(piece.stiffness)
                piece.speed +=
                    (-piece.stiffness * (piece.depth - piece.target) - friction * piece.speed) * dt
                piece.depth += piece.speed * dt
                left -= dt
            }
            if (abs(piece.depth - piece.target) < SETTLED && abs(piece.speed) < SETTLED) {
                piece.depth = piece.target
                piece.speed = 0f
            } else {
                isMoving = true
            }
            val fade = deltaMs / (if (piece.glowTarget > piece.glow) GLOW_IN_MS else GLOW_OUT_MS)
            piece.glow =
                if (piece.glowTarget > piece.glow) min(piece.glowTarget, piece.glow + fade)
                else max(piece.glowTarget, piece.glow - fade)
            if (piece.glow != piece.glowTarget) isMoving = true
        }
        if (stepSelector(deltaMs)) isMoving = true
        if (scene == GlassScene.MOTION) {
            stepMotion(deltaMs)
            isMoving = true
        }
        invalidate()
        if (!isMoving) frames.cancel()
    }

    /** Slides the glass selector toward the picked ringer mode; true while it's on its way. */
    private fun stepSelector(deltaMs: Long): Boolean {
        val ringer = pieces.firstOrNull { it.role == Role.RINGER } ?: return false
        if (lensX < 0f) return false
        val at = ringerX(ringer, ringerIndex)
        val before = lensX
        lensX += (at - lensX) * (1f - exp(-deltaMs / LENS_FOLLOW_MS))
        if (abs(at - lensX) < 0.5f) {
            lensX = at
        }
        // It stretches like jelly the faster it goes
        val pace = abs(lensX - before) / max(deltaMs, 1L) / dp(1f)
        val stretch = if (stretchOn) 1f + min(pace, 1f) * (HELD_STRETCH - 1f) * 2f else 1f
        lensStretch += (stretch - lensStretch) * 0.35f
        if (lensX == at && abs(lensStretch - 1f) < 0.01f) {
            lensStretch = 1f
            return false
        }
        return true
    }

    /**
     * Moves the panel through its round: out of the edge, there for a while, back into the edge
     * and gone for a moment. With Morph on it grows out of the keys as the real panels do, as a
     * drop that swells and settles in the Liquid style; with it off it slides in, as Android's.
     */
    private fun stepMotion(deltaMs: Long) {
        val panel = pieces.firstOrNull { it.role == Role.MORPH } ?: return
        val open = if (morphs) MORPH_OPEN_MS[morphSpeed] else SLIDE_MS
        val close = open * 0.8f
        motionMs = (motionMs + deltaMs) % (open + MOTION_STAY_MS + close + MOTION_GONE_MS)
        val opening = motionMs < open
        val out =
            when {
                opening -> motionMs / open
                motionMs < open + MOTION_STAY_MS -> 1f
                motionMs < open + MOTION_STAY_MS + close ->
                    1f - (motionMs - open - MOTION_STAY_MS) / close
                else -> 0f
            }
        if (!morphs) {
            val eased = ease(out)
            panel.scaleX = 1f
            panel.scaleY = 1f
            panel.shiftX = (1f - eased) * dp(SLIDE_DP)
            panel.fade = eased
            shape(panel)
            return
        }
        panel.shiftX = 0f
        panel.fade = if (out > 0f) 1f else 0f
        if (morphStyle == GlassSettings.MORPH_LIQUID && opening) {
            // A thin drop along the edge that swells a little past its size, then settles
            when {
                out < 0.6f -> {
                    val eased = ease(out / 0.6f)
                    panel.scaleX = DROP_X + (SWELL_X - DROP_X) * eased
                    panel.scaleY = DROP_Y + (SWELL_Y - DROP_Y) * eased
                }
                out < 0.8f -> {
                    val back = (out - 0.6f) / 0.2f
                    panel.scaleX = SWELL_X + (DIP_X - SWELL_X) * back
                    panel.scaleY = SWELL_Y + (DIP_Y - SWELL_Y) * back
                }
                else -> {
                    val settle = (out - 0.8f) / 0.2f
                    panel.scaleX = DIP_X + (1f - DIP_X) * settle
                    panel.scaleY = DIP_Y + (1f - DIP_Y) * settle
                }
            }
        } else {
            val eased = ease(out)
            panel.scaleX = DROP_X + (1f - DROP_X) * eased
            panel.scaleY = DROP_Y + (1f - DROP_Y) * eased
        }
        shape(panel)
    }

    /** Eases [t], 0 to 1, in and out. */
    private fun ease(t: Float) = t * t * (3f - 2f * t)

    /** One of GlassSettings' `SQUISH_` levels. */
    private class Squish(
        val edgeDp: Float,
        val pressDamping: Float,
        val pressStiffness: Float,
        val releaseDamping: Float,
        val releaseStiffness: Float,
    )

    private companion object {
        const val BLUR_RADIUS_DP = 30f
        const val GLOW_RADIUS_DP = 48f
        const val GLOW_IN_MS = 100f
        const val GLOW_OUT_MS = 250f
        const val MIN_PRESS_MS = 80L
        const val SPRING_STEP_S = 0.002f
        const val SETTLED = 0.002f
        const val LOUPE_SCALE = GlassEdgeLayout.SCALE

        // The glass selector: its width, how fast it follows a pick, and how far it stretches
        const val LENS_WIDTH_DP = 52f
        // How far the ringer's modes stay from its ends, which keeps the selector inside them
        const val RINGER_INSET_DP = 4f
        const val LENS_FOLLOW_MS = 70f
        const val HELD_STRETCH = 1.25f
        // How much bigger the selector shows what's under it, for each of GlassSettings' MAGNIFY_
        val MAGNIFY = floatArrayOf(0f, 0.14f, 0.32f)
        val RINGER_ICONS =
            intArrayOf(
                InternalR.drawable.ic_audio_ring_notif,
                InternalR.drawable.ic_audio_ring_notif_vibrate,
                InternalR.drawable.ic_audio_ring_notif_mute,
            )

        // The panel's round, in ms: how long it takes to open at each of GlassSettings' MORPH_
        // speeds, or to slide in, and how long it stays and is gone
        val MORPH_OPEN_MS = floatArrayOf(620f, 440f, 300f)
        const val SLIDE_MS = 300f
        const val MOTION_STAY_MS = 1400f
        const val MOTION_GONE_MS = 600f
        const val SLIDE_DP = 76f
        // The panel's size as it leaves the edge, at its swell and at the dip after it
        const val DROP_X = 0.18f
        const val DROP_Y = 0.3f
        const val SWELL_X = 1.07f
        const val SWELL_Y = 1.04f
        const val DIP_X = 0.975f
        const val DIP_Y = 0.985f

        // Android's own tint, if the glass color asks for it: the page has none of its own
        const val ANDROID_TINT = 0x99202124.toInt()
        const val SOFT_WHITE = 0xddffffff.toInt()
        const val BLACK_INK = 0xff1c1b1f.toInt()
        const val DARK_WELL = 0x55000000
        const val ON_TILE = 0xc0ffffff.toInt()
        const val LENS_FROM = 0x57ffffff
        const val LENS_TO = 0x1affffff
        const val LENS_RIM = 0x80ffffff.toInt()

        val NOTIFICATIONS = listOf(GlassSettings.PLACE_NOTIFICATIONS)
        val SHADE = listOf(GlassSettings.PLACE_SHADE)
        val LOCKSCREEN = listOf(GlassSettings.PLACE_LOCKSCREEN)
        val VOLUME = listOf(GlassSettings.PLACE_VOLUME)
        val POWER = listOf(GlassSettings.PLACE_POWER_MENU)
        val DIALOGS = listOf(GlassSettings.PLACE_DIALOGS)
        // For what glass with nothing to read on it and with text looks like, whichever places
        // are on
        val BUTTON_PLACES =
            listOf(
                GlassSettings.PLACE_LOCKSCREEN,
                GlassSettings.PLACE_VOLUME,
                GlassSettings.PLACE_NOTIFICATIONS,
                GlassSettings.PLACE_DIALOGS,
                GlassSettings.PLACE_POWER_MENU,
                GlassSettings.PLACE_SHADE,
            )
        val TEXT_PLACES =
            listOf(
                GlassSettings.PLACE_NOTIFICATIONS,
                GlassSettings.PLACE_DIALOGS,
                GlassSettings.PLACE_POWER_MENU,
                GlassSettings.PLACE_SHADE,
                GlassSettings.PLACE_VOLUME,
                GlassSettings.PLACE_LOCKSCREEN,
            )

        // As SystemUI's OwnGlassSquish
        val SQUISHES =
            mapOf(
                GlassSettings.SQUISH_SUBTLE to Squish(1.5f, 1f, 1500f, 1f, 700f),
                GlassSettings.SQUISH_NORMAL to Squish(3f, 1f, 1500f, 0.5f, 500f),
                GlassSettings.SQUISH_BOUNCY to Squish(5f, 0.8f, 1200f, 0.3f, 400f),
            )

        const val GLOW_SHADER =
            """
            uniform float2 touchPoint;
            uniform float touchRadius;
            uniform float3 touchColor;
            uniform float touchRainbow;
            uniform float amount;
            half4 main(float2 p) {
                float2 fromTouch = p - touchPoint;
                float reach = length(fromTouch) / touchRadius;
                float3 color = touchRainbow > 0.5
                        ? mix(float3(1.0),
                              0.5 + 0.5 * cos(atan(fromTouch.y, fromTouch.x) +
                                              float3(0.0, 2.094, 4.189)),
                              0.6 * smoothstep(0.0, 0.8, reach))
                        : touchColor;
                float alpha = 0.35 * amount * exp(-2.0 * reach * reach);
                return half4(half3(color * alpha), half(alpha));
            }
            """
    }
}
