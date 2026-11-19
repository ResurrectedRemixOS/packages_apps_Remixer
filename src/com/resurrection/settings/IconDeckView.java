/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class IconDeckView extends View {
    private final Paint mBase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGroove = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDisc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNotch = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mDensity;
    private Drawable mIcon;
    private boolean mActive;
    private int mAccent = Color.WHITE;
    private int mDiscColor = 0x29FFFFFF;
    private float mAngle;
    private ValueAnimator mSpin;

    public IconDeckView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;
        mGroove.setStyle(Paint.Style.STROKE);
        mGroove.setStrokeWidth(mDensity);
        mGroove.setColor(0x12FFFFFF);
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(3 * mDensity);
        mNotch.setColor(0x66FFFFFF);
    }

    public void setIcon(Drawable icon) {
        mIcon = icon == null ? null : icon.mutate();
        if (mIcon != null) {
            mIcon.setTint(Color.WHITE);
        }
        invalidate();
    }

    public void setColors(int accent, int disc) {
        mAccent = accent;
        mDiscColor = disc;
        invalidate();
    }

    public void setActive(boolean active) {
        mActive = active;
        invalidate();
    }

    public void spin(int direction) {
        if (mSpin != null) {
            mSpin.cancel();
        }
        float from = mAngle;
        mSpin = ValueAnimator.ofFloat(from, from + 72 * direction);
        mSpin.setDuration(420);
        mSpin.setInterpolator(new DecelerateInterpolator());
        mSpin.addUpdateListener(a -> {
            mAngle = (float) a.getAnimatedValue();
            invalidate();
        });
        mSpin.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float r = Math.min(w, h) / 2f;
        mBase.setShader(new RadialGradient(w / 2f, h / 2f, r, new int[] {0xFF1B1416, 0xFF070506},
                new float[] {0f, 0.7f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy);
        float ring = 1.5f * mDensity;
        canvas.drawCircle(cx, cy, r - ring, mBase);
        float disc = 37 * mDensity;
        for (float g = disc + 4 * mDensity; g < r - 4 * mDensity; g += 5 * mDensity) {
            canvas.drawCircle(cx, cy, g, mGroove);
        }
        mRing.setColor(mActive ? mAccent : 0x1AFFFFFF);
        canvas.drawCircle(cx, cy, r - ring, mRing);
        canvas.save();
        canvas.rotate(mAngle, cx, cy);
        canvas.drawCircle(cx, cy - r + 9 * mDensity, 2.5f * mDensity, mNotch);
        canvas.restore();
        mDisc.setColor(mActive ? mDiscColor : 0x0FFFFFFF);
        canvas.drawCircle(cx, cy, disc, mDisc);
        if (mIcon != null) {
            float box = 46 * mDensity;
            int iw = Math.max(1, mIcon.getIntrinsicWidth()), ih = Math.max(1, mIcon.getIntrinsicHeight());
            float scale = Math.min(box / iw, box / ih);
            int w = Math.round(iw * scale), h = Math.round(ih * scale);
            mIcon.setBounds(Math.round(cx - w / 2f), Math.round(cy - h / 2f),
                    Math.round(cx + w / 2f), Math.round(cy + h / 2f));
            mIcon.draw(canvas);
        }
    }
}
