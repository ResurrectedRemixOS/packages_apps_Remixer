/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.volume;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

final class VolumeGlyph extends Drawable {
    private static final float W = 46f;
    private static final float H = 56f;

    private final VolumeLook mLook;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Path mClip = new Path();

    VolumeGlyph(VolumeLook look) {
        mLook = look;
        mStroke.setStyle(Paint.Style.STROKE);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width() / W, b.height() / H);
        canvas.save();
        canvas.translate(b.exactCenterX() - W * s / 2, b.exactCenterY() - H * s / 2);
        canvas.scale(s, s);
        mStroke.setStrokeWidth(1f);
        if (mLook.classic) {
            classic(canvas);
        } else {
            vertical(canvas);
        }
        canvas.restore();
    }

    private void classic(Canvas canvas) {
        mRect.set(0, 19, 46, 37);
        mFill.setColor(mLook.panel);
        canvas.drawRoundRect(mRect, 6, 6, mFill);
        mClip.reset();
        mClip.addRoundRect(mRect, 6, 6, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(mClip);
        mFill.setColor(mLook.accent);
        canvas.drawRect(0, 19, 46, 21, mFill);
        canvas.restore();
        mRect.set(5, 27, 41, 33);
        mFill.setColor(mLook.trackBg);
        canvas.drawRoundRect(mRect, 3, 3, mFill);
        mRect.set(5, 27, 27, 33);
        mFill.setColor(mLook.active);
        canvas.drawRoundRect(mRect, 3, 3, mFill);
    }

    private void vertical(Canvas canvas) {
        mRect.set(13, 1, 33, 55);
        if (mLook.glow) {
            mStroke.setStrokeWidth(3f);
            mStroke.setColor(ColorUtils.setAlphaComponent(mLook.accent, 0x4D));
            canvas.drawRoundRect(mRect, 8, 8, mStroke);
            mStroke.setStrokeWidth(1f);
        }
        mFill.setColor(mLook.panel);
        canvas.drawRoundRect(mRect, 8, 8, mFill);
        if (mLook.panelStroke != 0) {
            mStroke.setColor(mLook.panelStroke);
            canvas.drawRoundRect(mRect, 8, 8, mStroke);
        }
        if (mLook.ringerArea != 0) {
            mClip.reset();
            mClip.addRoundRect(mRect, 8, 8, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(mClip);
            mFill.setColor(mLook.ringerArea);
            canvas.drawRect(13, 1, 33, 14, mFill);
            canvas.restore();
        }
        if (mLook.ringerBg != 0) {
            mFill.setColor(mLook.ringerBg);
            canvas.drawCircle(23, 9, 5, mFill);
        }
        if (mLook.ringerStroke != 0) {
            mStroke.setColor(mLook.ringerStroke);
            canvas.drawCircle(23, 9, 4.5f, mStroke);
        }
        float left = mLook.pill ? 16 : 18;
        float right = mLook.pill ? 30 : 28;
        float r = mLook.pill ? 7 : 4;
        mRect.set(left, 17, right, 50);
        if (mLook.trackBg != 0) {
            mFill.setColor(mLook.trackBg);
            canvas.drawRoundRect(mRect, r, r, mFill);
        }
        mClip.reset();
        mClip.addRoundRect(mRect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(mClip);
        mFill.setColor(mLook.active);
        canvas.drawRect(left, 50 - 33 * 0.58f, right, 50, mFill);
        canvas.restore();
        if (mLook.trackStroke != 0) {
            mStroke.setColor(mLook.trackStroke);
            canvas.drawRoundRect(mRect, r, r, mStroke);
        }
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
