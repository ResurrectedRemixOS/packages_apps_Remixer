/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.graphics.ColorUtils;

/** A sketch of the Settings home in one of its styles, drawn in the accent, for the picker. */
public class SettingsStylePreviewView extends View {
    /** Category tiles, all in the accent. */
    public static final int RR = 0;
    /** Category tiles, each in its own hue near the accent. */
    public static final int RR_HUES = 1;
    /** The RR list with neon ring icons. */
    public static final int CLASSIC = 2;
    /** Android's own home. */
    public static final int ORIGINAL = 3;

    // The sketch is drawn in a 180 by 320 dp screen and scaled to the view.
    private static final float W = 180f;
    private static final float H = 320f;
    // The hue turns the homepage gives its tiles, in order.
    private static final int[] OFFSETS = {0, 18, -18, 36, -36, 54, -54, 72};
    private static final int[] AOSP = {0xFF4285F4, 0xFF34A853, 0xFFF9AB00, 0xFFEA4335,
            0xFFA142F4, 0xFF24C1E0, 0xFFFF6D00};

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final boolean mNight;
    private final int mBg;
    private final int mCard;
    private final int mInk;
    private final int mSubInk;
    private final int mAccentHigh;
    private final int mAccentDeep;
    private final int mAccentDeeper;
    private int mStyle = RR_HUES;
    private boolean mMini;

    public SettingsStylePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mNight = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (mNight) {
            mBg = ColorUtils.blendARGB(color(android.R.color.system_neutral1_900), Color.BLACK, 0.4f);
            mCard = color(android.R.color.system_neutral1_800);
            mInk = color(android.R.color.system_neutral1_50);
            mSubInk = color(android.R.color.system_neutral2_300);
            mAccentHigh = color(android.R.color.system_accent1_400);
            mAccentDeep = color(android.R.color.system_accent1_800);
            mAccentDeeper = ColorUtils.blendARGB(color(android.R.color.system_accent1_900),
                    Color.BLACK, 0.5f);
        } else {
            mBg = color(android.R.color.system_neutral1_50);
            mCard = Color.WHITE;
            mInk = color(android.R.color.system_neutral1_900);
            mSubInk = color(android.R.color.system_neutral2_600);
            mAccentHigh = color(android.R.color.system_accent1_600);
            mAccentDeep = color(android.R.color.system_accent1_100);
            mAccentDeeper = ColorUtils.blendARGB(color(android.R.color.system_accent1_50),
                    Color.WHITE, 0.5f);
        }
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    public void setStyle(int style) {
        mStyle = style;
        invalidate();
    }

    /** A thumbnail: the top of the screen at the view's width. */
    public void setMini(boolean mini) {
        mMini = mini;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = mMini ? getWidth() / W : Math.min(getWidth() / W, getHeight() / H);
        float left = mMini ? 0f : (getWidth() - W * s) / 2f;
        float top = mMini ? 0f : (getHeight() - H * s) / 2f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(s, s);
        float radius = mMini ? 10f : 18f;
        mRect.set(0f, 0f, W, H);
        canvas.clipRect(mRect);
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mBg);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
        mText.setColor(mInk);
        mText.setTextSize(15f);
        canvas.drawText("Settings", 16f, 30f, mText);
        pill(canvas, 16f, 42f, W - 16f, 58f, 8f, mCard);
        ring(canvas, 27f, 50f, 3.2f, 1.4f, mSubInk, 1f);
        switch (mStyle) {
            case CLASSIC:
                drawClassic(canvas);
                break;
            case ORIGINAL:
                drawOriginal(canvas);
                break;
            default:
                drawTiles(canvas, mStyle == RR_HUES);
        }
        canvas.restore();
    }

    /** The category-tile home: device card, Remixer bar, four tiles and a full row. */
    private void drawTiles(Canvas canvas, boolean hues) {
        gradient(canvas, 12f, 68f, W - 12f, 118f, 14f, 0);
        bar(canvas, 24f, 78f, 84f, 84f, alpha(mInk, 0xCC));
        bar(canvas, 24f, 90f, 60f, 94f, alpha(mInk, 0x80));
        for (int i = 0; i < 4; i++) {
            ring(canvas, 32f + i * 26f, 107f, 5.5f, 1.6f, turn(mAccentHigh, 0), i == 0 ? 1f : 0.45f);
        }
        pill(canvas, 12f, 124f, W - 12f, 142f, 9f, ColorUtils.blendARGB(mAccentDeeper, mAccentDeep, 0.35f));
        pill(canvas, W - 46f, 128f, W - 18f, 138f, 5f, Color.WHITE);
        bar(canvas, 24f, 130f, 70f, 136f, alpha(mInk, 0xCC));
        float tw = (W - 24f - 8f) / 2f;
        float th = 44f;
        for (int i = 0; i < 4; i++) {
            float x = 12f + (i % 2) * (tw + 8f);
            float y = 148f + (i / 2) * (th + 8f);
            int off = hues ? OFFSETS[i] : 0;
            gradient(canvas, x, y, x + tw, y + th, 12f, off);
            ring(canvas, x + 14f, y + 22f, 7f, 2f, turn(mAccentHigh, off), 1f);
            bar(canvas, x + 27f, y + 16f, x + 58f, y + 21f, alpha(mInk, 0xCC));
            bar(canvas, x + 27f, y + 26f, x + 50f, y + 29f, alpha(mInk, 0x80));
        }
        int off = hues ? OFFSETS[4] : 0;
        gradient(canvas, 12f, 252f, W - 12f, 282f, 12f, off);
        ring(canvas, 26f, 267f, 7f, 2f, turn(mAccentHigh, off), 1f);
        bar(canvas, 40f, 262f, 90f, 267f, alpha(mInk, 0xCC));
        bar(canvas, 40f, 271f, 72f, 274f, alpha(mInk, 0x80));
        pill(canvas, 16f, 294f, W - 16f, 310f, 8f, mCard);
    }

    /** The RR list: a device card, then rows with neon ring icons. */
    private void drawClassic(Canvas canvas) {
        gradient(canvas, 12f, 68f, W - 12f, 104f, 14f, 0);
        bar(canvas, 24f, 78f, 84f, 84f, alpha(mInk, 0xCC));
        bar(canvas, 24f, 90f, 60f, 94f, alpha(mInk, 0x80));
        for (int i = 0; i < 7; i++) {
            float y = 112f + i * 28f;
            ring(canvas, 28f, y + 13f, 8f, 2f, mAccentHigh, 0.25f);
            arc(canvas, 28f, y + 13f, 8f, 2f, mAccentHigh, 40f + i * 45f);
            bar(canvas, 46f, y + 7f, 46f + 50f + (i % 3) * 14f, y + 12f, alpha(mInk, 0xB0));
            bar(canvas, 46f, y + 17f, 46f + 36f + (i % 2) * 12f, y + 21f, alpha(mSubInk, 0x90));
        }
    }

    /** Android's home: rows with round coloured icons in two cards. */
    private void drawOriginal(Canvas canvas) {
        pill(canvas, 12f, 68f, W - 12f, 150f, 14f, mCard);
        pill(canvas, 12f, 158f, W - 12f, 266f, 14f, mCard);
        for (int i = 0; i < 7; i++) {
            float y = i < 3 ? 76f + i * 26f : 166f + (i - 3) * 26f;
            mPaint.setShader(null);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(AOSP[i]);
            canvas.drawCircle(30f, y + 11f, 9f, mPaint);
            bar(canvas, 46f, y + 6f, 46f + 52f + (i % 3) * 14f, y + 11f, alpha(mInk, 0xB0));
            bar(canvas, 46f, y + 15f, 46f + 36f + (i % 2) * 12f, y + 19f, alpha(mSubInk, 0x90));
        }
    }

    private void gradient(Canvas canvas, float l, float t, float r, float b, float radius, int turn) {
        mRect.set(l, t, r, b);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setShader(new LinearGradient(0f, t, 0f, b, turn(mAccentDeep, turn),
                turn(mAccentDeeper, turn), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1f);
        mPaint.setColor(alpha(turn(mAccentHigh, turn), 0x40));
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
    }

    private void pill(Canvas canvas, float l, float t, float r, float b, float radius, int color) {
        mRect.set(l, t, r, b);
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(color);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
    }

    private void bar(Canvas canvas, float l, float t, float r, float b, int color) {
        pill(canvas, l, t, r, b, (b - t) / 2f, color);
    }

    private void ring(Canvas canvas, float cx, float cy, float r, float stroke, int color, float on) {
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(alpha(color, Math.round(0xFF * on)));
        canvas.drawCircle(cx, cy, r, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
    }

    private void arc(Canvas canvas, float cx, float cy, float r, float stroke, int color, float sweep) {
        mRect.set(cx - r, cy - r, cx + r, cy + r);
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(color);
        canvas.drawArc(mRect, -90f, sweep, false, mPaint);
        mPaint.setStrokeCap(Paint.Cap.BUTT);
        mPaint.setStyle(Paint.Style.FILL);
    }

    private int color(int res) {
        return getContext().getColor(res);
    }

    private static int alpha(int color, int alpha) {
        return ColorUtils.setAlphaComponent(color, alpha);
    }

    /** The colour turned around the hue wheel, the way the homepage tiles are. */
    private static int turn(int color, int degrees) {
        if (degrees == 0) return color;
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[0] = ((hsl[0] + degrees) % 360f + 360f) % 360f;
        return ColorUtils.HSLToColor(hsl);
    }
}
