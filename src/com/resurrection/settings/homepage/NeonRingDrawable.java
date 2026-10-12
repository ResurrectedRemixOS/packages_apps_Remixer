/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.homepage;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

import com.android.settings.R;

/** A homepage icon: the entry's glyph in a dark disc, ringed by a live level in the accent. */
public class NeonRingDrawable extends Drawable {
    /** Level for an entry with nothing to measure: the track alone. */
    public static final float NO_LEVEL = -1;

    private final Paint mDisc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mOval = new RectF();
    private final Drawable mGlyph;
    private final int mSize;
    private final float mDp;
    private int mArc;
    private int mGlyphColor;
    private int mDimColor;
    private int mTrack;
    private int mInner;
    private int mDiscColor;
    private float mLevel = NO_LEVEL;
    private boolean mActive = true;

    public NeonRingDrawable(Drawable glyph, int sizePx, float density) {
        mGlyph = glyph;
        mSize = sizePx;
        mDp = density;
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setColors(int arc, int glyph, int dim, int track, int inner, int disc) {
        mArc = arc;
        mGlyphColor = glyph;
        mDimColor = dim;
        mTrack = track;
        mInner = inner;
        mDiscColor = disc;
        invalidateSelf();
    }

    /** A static ring for an entry another app adds, so it matches the live entries. */
    public static NeonRingDrawable forTile(Context context, Drawable glyph) {
        Resources res = context.getResources();
        NeonRingDrawable ring = new NeonRingDrawable(glyph.mutate(),
                res.getDimensionPixelSize(R.dimen.dashboard_tile_image_size),
                res.getDisplayMetrics().density);
        ring.applyAccent(context);
        return ring;
    }

    /** Colors the ring from the system accent for the current day or night mode. */
    public void applyAccent(Context context) {
        boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int a200 = context.getColor(android.R.color.system_accent1_200);
        int a300 = context.getColor(android.R.color.system_accent1_300);
        int a400 = context.getColor(android.R.color.system_accent1_400);
        int a500 = context.getColor(android.R.color.system_accent1_500);
        int a600 = context.getColor(android.R.color.system_accent1_600);
        int a700 = context.getColor(android.R.color.system_accent1_700);
        if (night) {
            setColors(a300, a200, 0x61FFFFFF, ColorUtils.setAlphaComponent(a400, 0x29),
                    ColorUtils.setAlphaComponent(a500, 0x47), 0xFF0E0E0F);
        } else {
            setColors(a600, a700, 0x61000000, ColorUtils.setAlphaComponent(a600, 0x2E),
                    ColorUtils.setAlphaComponent(a200, 0x66),
                    context.getColor(android.R.color.system_neutral1_50));
        }
    }

    /** Sets the ring's fill, 0 to 1, or {@link #NO_LEVEL}; inactive dims the glyph. */
    public void setState(float level, boolean active) {
        if (level == mLevel && active == mActive) {
            return;
        }
        mLevel = level;
        mActive = active;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float radius = Math.min(b.width(), b.height()) / 2f;
        mDisc.setShader(new RadialGradient(cx, cy, radius, new int[] {mInner, mDiscColor},
                new float[] {0f, 0.72f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, mDisc);

        float stroke = 2.2f * mDp;
        float ring = radius - 1.8f * mDp;
        mOval.set(cx - ring, cy - ring, cx + ring, cy + ring);
        mRing.setStrokeWidth(stroke);
        // Nothing to measure: a soft full ring in the accent. Off keeps the faint track.
        mRing.setColor(mLevel == NO_LEVEL && mActive
                ? ColorUtils.setAlphaComponent(mArc, 0x4D) : mTrack);
        canvas.drawCircle(cx, cy, ring, mRing);
        if (mLevel > 0) {
            float sweep = 360f * Math.min(mLevel, 1f);
            // A wide faint pass under the arc reads as glow.
            mRing.setStrokeWidth(stroke * 2.6f);
            mRing.setColor(ColorUtils.setAlphaComponent(mArc, 0x40));
            canvas.drawArc(mOval, -90, sweep, false, mRing);
            mRing.setStrokeWidth(stroke);
            mRing.setColor(mArc);
            canvas.drawArc(mOval, -90, sweep, false, mRing);
        }

        int glyph = Math.round(radius);
        int left = Math.round(cx - glyph / 2f);
        int top = Math.round(cy - glyph / 2f);
        mGlyph.setBounds(left, top, left + glyph, top + glyph);
        mGlyph.setTint(mActive ? mGlyphColor : mDimColor);
        mGlyph.draw(canvas);
    }

    @Override
    public int getIntrinsicWidth() {
        return mSize;
    }

    @Override
    public int getIntrinsicHeight() {
        return mSize;
    }

    @Override
    public void setAlpha(int alpha) {
        mDisc.setAlpha(alpha);
        mRing.setAlpha(alpha);
        mGlyph.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mGlyph.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
