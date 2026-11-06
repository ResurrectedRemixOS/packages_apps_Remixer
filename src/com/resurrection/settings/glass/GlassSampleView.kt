/*
 * SPDX-FileCopyrightText: 2026 Project Catalyxx
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
import com.android.internal.util.catalyxx.GlassSettings
import com.android.settings.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The glass in [GlassPreview]'s window: a small copy of what a page sets, in the drawables
 * SurfaceFlinger draws as glass, with the settings of the place it copies. Where glass is off the
 * copy is plain, as the real one is. On the Touch page it also answers a finger.
 */
internal class GlassSampleView(
    context: Context,
    private val kind: GlassPreviewKind,
    private val viewWidth: Int,
    private val viewHeight: Int,
) : View(context) {

    /** A piece of glass, in pixels from this view's corner, and what's on it. */
    private inner class Piece(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        var radius: Float,
        // The first of these with glass on sets the look
        val places: List<String>,
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

        val width
            get() = right - left

        val height
            get() = bottom - top

        fun scale() = 1f - depth * 2f * squishDp * density / max(width, height)
    }

    private val density = resources.displayMetrics.density
    private val fullWidth = viewWidth.toFloat()
    private val fullHeight = viewHeight.toFloat()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
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
    private val markPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK_INK
            strokeWidth = dp(3f)
            strokeCap = Paint.Cap.ROUND
        }
    private val icons = HashMap<Int, Drawable>()
    private val trianglePath = Path()
    private val clipPath = Path()

    private val pieces = buildPieces()

    // Touch page
    private var pressed: Piece? = null
    private var pressedAt = 0L
    private var touchX = 0f
    private var touchY = 0f
    private var glowRadius = 0f
    private var glowColor = Color.WHITE
    private var glowRainbow = false
    private val glowShader by lazy(LazyThreadSafetyMode.NONE) { RuntimeShader(GLOW_SHADER) }
    private val glowPaint = Paint()
    private val frames =
        TimeAnimator().apply {
            setTimeListener { _, _, deltaMs -> step(min(deltaMs, 32L)) }
        }
    private val release = Runnable { releasePressed() }

    private fun dp(value: Float) = value * density

    private fun string(id: Int) = context.getString(id)

    private fun buildPieces(): List<Piece> =
        when (kind) {
            GlassPreviewKind.LOCK_SCREEN -> lockScreenPieces()
            GlassPreviewKind.NOTIFICATIONS -> notificationPieces()
            GlassPreviewKind.QUICK_SETTINGS -> tilePieces()
            GlassPreviewKind.VOLUME -> volumePieces()
            GlassPreviewKind.POWER -> powerPieces()
            else -> allGlassPieces()
        }

    /** What all glass looks like: glass with no text on it, which stays clear, and with text. */
    private fun allGlassPieces(): List<Piece> {
        val side = dp(14f)
        val gap = dp(6f)
        val up = dp(27f)
        val middle = fullWidth / 2
        return listOf(
            Piece(side, up, middle - gap, fullHeight - up, dp(28f), BUTTON_PLACES) {
                drawCardLabel(this, it, string(R.string.glass_preview_buttons))
            },
            Piece(middle + gap, up, fullWidth - side, fullHeight - up, dp(28f), TEXT_PLACES) {
                drawCardLabel(this, it, string(R.string.glass_preview_panels))
            },
        )
    }

    private fun drawCardLabel(canvas: Canvas, piece: Piece, label: String) {
        canvas.drawText(label, dp(18f), piece.height / 2 + dp(6f), titlePaint)
    }

    /** The lock screen's shortcuts and fingerprint button, under the real clock. */
    private fun lockScreenPieces(): List<Piece> {
        val size = dp(42f)
        val top = fullHeight - dp(48f)
        val places = listOf(GlassSettings.PLACE_LOCKSCREEN)
        val buttons =
            intArrayOf(
                InternalR.drawable.ic_qs_flashlight,
                InternalR.drawable.ic_lock,
                InternalR.drawable.perm_group_camera,
            )
        return buttons.mapIndexed { i, icon ->
            val middle = fullWidth * (i + 1) / 4
            Piece(middle - size / 2, top, middle + size / 2, top + size, size / 2, places) {
                drawIcon(this, icon, it.width / 2, it.height / 2, dp(22f), Color.WHITE)
            }
        }
    }

    /** A notification, and the music player below it. */
    private fun notificationPieces(): List<Piece> {
        val side = dp(14f)
        val places = listOf(GlassSettings.PLACE_NOTIFICATIONS)
        return listOf(
            Piece(side, dp(10f), fullWidth - side, dp(84f), dp(26f), places) {
                fill.color = context.getColor(android.R.color.system_accent1_200)
                drawCircle(dp(36f), it.height / 2, dp(19f), fill)
                drawIcon(this, R.drawable.ic_rr_tune, dp(36f), it.height / 2, dp(22f), BLACK_INK)
                val room = it.width - dp(82f)
                drawText(string(R.string.liquid_glass_title), dp(68f), dp(32f), titlePaint)
                drawText(
                    TextUtils.ellipsize(
                            string(R.string.glass_preview_sample_text),
                            textPaint,
                            room,
                            TextUtils.TruncateAt.END,
                        )
                        .toString(),
                    dp(68f),
                    dp(54f),
                    textPaint,
                )
            },
            Piece(side, dp(92f), fullWidth - side, dp(150f), dp(26f), places) { drawPlayer(this, it) },
        )
    }

    private fun drawPlayer(canvas: Canvas, piece: Piece) {
        // What GlassSettings.MUSIC_PLAYER paints over the glass
        val player =
            GlassSettings.getInt(context.contentResolver, GlassSettings.MUSIC_PLAYER,
                GlassSettings.PLAYER_TINTED,
            )
        if (piece.isGlass && player != GlassSettings.PLAYER_CLEAR) {
            if (player == GlassSettings.PLAYER_ALBUM_ART) {
                fill.color = Color.WHITE
                fill.shader =
                    LinearGradient(
                        0f,
                        0f,
                        piece.width,
                        piece.height,
                        ART_FROM,
                        ART_TO,
                        Shader.TileMode.CLAMP,
                    )
            } else {
                fill.color = context.getColor(android.R.color.system_accent1_500) and PLAYER_TINT_MASK
            }
            canvas.drawRoundRect(0f, 0f, piece.width, piece.height, piece.radius, piece.radius, fill)
            fill.shader = null
        }
        val middleY = piece.height / 2
        drawIcon(canvas, android.R.drawable.ic_media_play, dp(32f), middleY, dp(30f), Color.WHITE)
        canvas.drawText(string(R.string.glass_preview_now_playing), dp(58f), middleY + dp(5f), textPaint)
        val from = dp(170f)
        val to = piece.width - dp(24f)
        fill.color = FAINT_WHITE
        canvas.drawRoundRect(from, middleY - dp(2f), to, middleY + dp(2f), dp(2f), dp(2f), fill)
        fill.color = Color.WHITE
        val played = from + (to - from) * 0.55f
        canvas.drawRoundRect(from, middleY - dp(2f), played, middleY + dp(2f), dp(2f), dp(2f), fill)
    }

    /** Four Quick Settings tiles, on and off. */
    private fun tilePieces(): List<Piece> {
        val side = dp(14f)
        val gap = dp(6f)
        val middle = fullWidth / 2
        val tiles =
            arrayOf(
                Triple(R.string.glass_preview_tile_internet, InternalR.drawable.ic_wifi_signal_4, true),
                Triple(R.string.glass_preview_tile_bluetooth, InternalR.drawable.ic_qs_bluetooth, false),
                Triple(R.string.glass_preview_tile_flashlight, InternalR.drawable.ic_qs_flashlight, false),
                Triple(R.string.glass_preview_tile_dark, InternalR.drawable.ic_qs_ui_mode_night, true),
            )
        val places = listOf(GlassSettings.PLACE_SHADE)
        return tiles.mapIndexed { i, (name, icon, isOn) ->
            val left = if (i % 2 == 0) side else middle + gap
            val right = if (i % 2 == 0) middle - gap else fullWidth - side
            val top = dp(if (i < 2) 12f else 86f)
            Piece(left, top, right, top + dp(62f), dp(31f), places) {
                val ink = if (isOn) BLACK_INK else Color.WHITE
                if (isOn) {
                    fill.color = ON_TILE
                    drawRoundRect(0f, 0f, it.width, it.height, it.radius, it.radius, fill)
                }
                drawIcon(this, icon, dp(32f), it.height / 2, dp(22f), ink)
                textPaint.color = ink
                val room = it.width - dp(62f)
                val label =
                    TextUtils.ellipsize(string(name), textPaint, room, TextUtils.TruncateAt.END)
                drawText(label.toString(), dp(54f), it.height / 2 + dp(5f), textPaint)
                textPaint.color = SOFT_WHITE
            }
        }
    }

    /** The volume panel: one tall pill with the ringer on top and the slider below. */
    private fun volumePieces(): List<Piece> {
        val places = listOf(GlassSettings.PLACE_VOLUME)
        return listOf(
            Piece(dp(22f), dp(8f), dp(84f), fullHeight - dp(8f), dp(31f), places) {
                val middle = it.width / 2
                fill.color = DARK_WELL
                drawCircle(middle, dp(29f), dp(21f), fill)
                drawIcon(
                    this,
                    InternalR.drawable.ic_audio_ring_notif,
                    middle,
                    dp(29f),
                    dp(22f),
                    context.getColor(android.R.color.system_accent1_100),
                )
                val inset = dp(11f)
                val from = dp(58f)
                val to = it.height - dp(11f)
                fill.color = DARK_WELL
                drawRoundRect(inset, from, it.width - inset, to, dp(13f), dp(13f), fill)
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
                drawRoundRect(inset, level, it.width - inset, to, dp(13f), dp(13f), fill)
                fill.shader = null
                fill.color = context.getColor(android.R.color.system_accent1_100)
                drawRoundRect(inset - dp(3f), level - dp(2f), it.width - inset + dp(3f), level + dp(2f), dp(2f), dp(2f), fill)
            }
        )
    }

    /** The power menu: one sheet of round buttons, and a dialog beside it. */
    private fun powerPieces(): List<Piece> {
        val side = dp(14f)
        val split = fullWidth * 0.6f
        val buttons =
            intArrayOf(
                InternalR.drawable.ic_lock_power_off,
                InternalR.drawable.ic_restart,
                InternalR.drawable.ic_screenshot,
            )
        return listOf(
            Piece(side, dp(10f), split, fullHeight - dp(10f), dp(28f), listOf(GlassSettings.PLACE_POWER_MENU)) {
                for (i in 0..buttons.size) {
                    val x = it.width * (if (i % 2 == 0) 0.27f else 0.73f)
                    val y = it.height * (if (i < 2) 0.27f else 0.73f)
                    fill.color = if (i == 0) EMERGENCY_RED else DARK_WELL
                    drawCircle(x, y, dp(25f), fill)
                    if (i == 0) {
                        drawEmergencyMark(this, x, y)
                    } else {
                        drawIcon(this, buttons[i - 1], x, y, dp(22f), Color.WHITE)
                    }
                }
            },
            Piece(
                split + dp(12f),
                dp(30f),
                fullWidth - side,
                fullHeight - dp(30f),
                dp(26f),
                listOf(GlassSettings.PLACE_DIALOGS),
            ) {
                drawText(string(R.string.glass_preview_dialog), dp(16f), dp(32f), titlePaint)
                fill.color = FAINT_WHITE
                val end = it.width - dp(16f)
                drawRoundRect(dp(16f), dp(48f), end, dp(54f), dp(3f), dp(3f), fill)
                drawRoundRect(dp(16f), dp(62f), end - dp(24f), dp(68f), dp(3f), dp(3f), fill)
            },
        )
    }

    /** The asterisk on the power menu's emergency button. */
    private fun drawEmergencyMark(canvas: Canvas, x: Float, y: Float) {
        val reach = dp(9f)
        for (turn in 0 until 3) {
            val angle = Math.PI / 2 + Math.PI * turn / 3
            val dx = (reach * cos(angle)).toFloat()
            val dy = (reach * sin(angle)).toFloat()
            canvas.drawLine(x - dx, y - dy, x + dx, y + dy, markPaint)
        }
    }

    private fun drawIcon(canvas: Canvas, id: Int, x: Float, y: Float, size: Float, color: Int) {
        val icon = icons.getOrPut(id) { context.getDrawable(id)!!.mutate() }
        icon.setTint(color)
        val half = (size / 2).roundToInt()
        icon.setBounds(x.roundToInt() - half, y.roundToInt() - half, x.roundToInt() + half, y.roundToInt() + half)
        icon.draw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val viewRoot = viewRootImpl ?: return
        for (piece in pieces) {
            piece.glass =
                viewRoot.createBackgroundBlurDrawable().apply {
                    // Nothing behind it in this window to make a hole through
                    setXfermode(null)
                }
        }
        refresh()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        frames.cancel()
        removeCallbacks(release)
        for (piece in pieces) {
            piece.glass?.setVisible(false, false)
            piece.glass = null
        }
    }

    /** Reads the settings again. */
    fun refresh() {
        val resolver = context.contentResolver
        val isBlurOn = GlassPreview.isBlurOn(context)
        for (piece in pieces) {
            val glass = piece.glass ?: continue
            val place =
                piece.places.firstOrNull { GlassSettings.isOn(resolver, it) } ?: piece.places.first()
            piece.isGlass = isBlurOn && GlassSettings.isOn(resolver, place)
            piece.hasRim = piece.isGlass && GlassSettings.hasRim(resolver, place)
            glass.setVisible(piece.isGlass, false)
            glass.setCornerRadius(piece.radius)
            if (piece.isGlass) {
                val look = GlassSettings.look(context, place, ANDROID_TINT)
                glass.setGlass(look)
                // Flat glass is plain blur, and the tint is the place's to draw
                glass.setColor(
                    if (look == null) GlassSettings.tintColor(context, place, ANDROID_TINT)
                    else Color.TRANSPARENT
                )
                glass.setBlurRadius(
                    GlassSettings.scaleBlurRadius(
                        dp(BLUR_RADIUS_DP).roundToInt(),
                        GlassSettings.blurScale(resolver, place),
                    )
                )
                glass.setGlassMelt(
                    if (kind == GlassPreviewKind.MOTION) GlassSettings.meltSize(resolver, density) else 0f
                )
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        for (piece in pieces) {
            val scale = piece.scale()
            val x = piece.width * (1f - scale) / 2
            val y = piece.height * (1f - scale) / 2
            if (piece.isGlass) {
                val glass = piece.glass ?: continue
                glass.setBounds(
                    (piece.left + x).roundToInt(),
                    (piece.top + y).roundToInt(),
                    (piece.right - x).roundToInt(),
                    (piece.bottom - y).roundToInt(),
                )
                glass.draw(canvas)
            } else {
                fill.color = context.getColor(android.R.color.system_neutral2_800)
                canvas.drawRoundRect(
                    piece.left + x,
                    piece.top + y,
                    piece.right - x,
                    piece.bottom - y,
                    piece.radius,
                    piece.radius,
                    fill,
                )
            }
        }
        for (piece in pieces) {
            if (piece.glow > 0f) drawGlow(canvas, piece)
            val scale = piece.scale()
            val count = canvas.save()
            canvas.translate(piece.left, piece.top)
            canvas.scale(scale, scale, piece.width / 2, piece.height / 2)
            piece.content(canvas, piece)
            canvas.restoreToCount(count)
        }
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
        val scale = piece.scale()
        val x = piece.width * (1f - scale) / 2
        val y = piece.height * (1f - scale) / 2
        clipPath.rewind()
        clipPath.addRoundRect(
            piece.left + x,
            piece.top + y,
            piece.right - x,
            piece.bottom - y,
            piece.radius,
            piece.radius,
            Path.Direction.CW,
        )
        val count = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawPaint(glowPaint)
        canvas.restoreToCount(count)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (kind != GlassPreviewKind.TOUCH) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val piece = pieces.firstOrNull {
                    it.isGlass && event.x in it.left..it.right && event.y in it.top..it.bottom
                } ?: return false
                press(piece, event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                buzz()
                // A quick tap stays pressed long enough for its squish to show
                val left = MIN_PRESS_MS - (SystemClock.uptimeMillis() - pressedAt)
                if (left > 0) postDelayed(release, left) else releasePressed()
            }
            MotionEvent.ACTION_CANCEL -> releasePressed()
        }
        return true
    }

    private fun press(piece: Piece, x: Float, y: Float) {
        removeCallbacks(release)
        releasePressed()
        val resolver = context.contentResolver
        pressed = piece
        pressedAt = SystemClock.uptimeMillis()
        touchX = x
        touchY = y
        val spring = SQUISHES[GlassSettings.getInt(resolver, GlassSettings.SQUISH, GlassSettings.DEFAULT_SQUISH)]
        if (spring != null) {
            piece.squishDp = spring.edgeDp
            piece.damping = spring.pressDamping
            piece.stiffness = spring.pressStiffness
            piece.target = 1f
        }
        if (piece.hasRim && GlassSettings.getInt(resolver, GlassSettings.TOUCH_GLOW, 1) == 1) {
            val style = GlassSettings.getInt(resolver, GlassSettings.TOUCH_GLOW_COLOR, GlassSettings.GLOW_WHITE)
            glowRainbow = style == GlassSettings.GLOW_RAINBOW
            glowColor =
                if (style == GlassSettings.GLOW_ACCENT) {
                    context.getColor(android.R.color.system_accent1_200)
                } else {
                    Color.WHITE
                }
            val size =
                GlassSettings.getInt(resolver, GlassSettings.TOUCH_GLOW_SIZE, GlassSettings.DEFAULT_TOUCH_GLOW_SIZE)
                    .coerceIn(GlassSettings.MIN_TOUCH_GLOW_SIZE, GlassSettings.MAX_TOUCH_GLOW_SIZE)
            glowRadius = dp(GLOW_RADIUS_DP) * size / 100f
            piece.glowTarget = 1f
        }
        frames.start()
    }

    private fun releasePressed() {
        val piece = pressed ?: return
        pressed = null
        val spring = SQUISHES[GlassSettings.getInt(context.contentResolver, GlassSettings.SQUISH, GlassSettings.DEFAULT_SQUISH)]
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
            when (GlassSettings.getInt(context.contentResolver, GlassSettings.TAP_BUZZ, GlassSettings.TAP_BUZZ_OFF)) {
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
                piece.speed += (-piece.stiffness * (piece.depth - piece.target) - friction * piece.speed) * dt
                piece.depth += piece.speed * dt
                left -= dt
            }
            if (kotlin.math.abs(piece.depth - piece.target) < SETTLED && kotlin.math.abs(piece.speed) < SETTLED) {
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
        invalidate()
        if (!isMoving) frames.cancel()
    }

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

        // Android's own tint, if the glass color asks for it: the page has none of its own
        const val ANDROID_TINT = 0x99202124.toInt()
        const val SOFT_WHITE = 0xddffffff.toInt()
        const val FAINT_WHITE = 0x66ffffff
        const val BLACK_INK = 0xff1c1b1f.toInt()
        const val DARK_WELL = 0x55000000
        const val ON_TILE = 0xc0ffffff.toInt()
        const val EMERGENCY_RED = 0xfff85f5a.toInt()
        const val PLAYER_TINT_MASK = 0x66ffffff
        const val ART_FROM = 0xb0e0559a.toInt()
        const val ART_TO = 0xb04a58d8.toInt()
        // For what all glass looks like, whichever places are on
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
