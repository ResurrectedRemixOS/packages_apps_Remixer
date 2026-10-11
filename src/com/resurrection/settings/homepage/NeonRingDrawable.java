/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.homepage;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

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
    private int[] mSweep;

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

    /** Sets the ring's fill, 0 to 1, or {@link #NO_LEVEL}; inactive dims the glyph. */
    public void setState(float level, boolean active) {
        if (level == mLevel && active == mActive) {
            return;
        }
        mLevel = level;
        mActive = active;
        invalidateSelf();
    }

    /** Rings the disc with these colors instead of a level, for the wallpaper entry. */
    public void setSweep(int[] colors) {
        mSweep = colors;
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
        mRing.setShader(null);
        if (mSweep != null) {
            mRing.setShader(new SweepGradient(cx, cy, mSweep, null));
            mRing.setStrokeWidth(stroke * 1.3f);
            mRing.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, cy, ring, mRing);
            mRing.setShader(null);
        } else {
            mRing.setStrokeWidth(stroke);
            mRing.setColor(mTrack);
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
