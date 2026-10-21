/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.homepage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/** The small sketch on a homepage category tile: signal bars, sliders, pattern dots... */
public class TilePreviewView extends View {
    static final int SIGNAL = 0;
    static final int PILLS = 1;
    static final int SLIDERS = 2;
    static final int DOTS = 3;
    static final int TYPE = 4;

    private static final float[] BAR_HEIGHTS = {6, 10, 15, 20};
    private static final boolean[] PATTERN = {true, true, false, false, true, true};

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final float mDp;
    private int mKind;
    private int mOn;
    private int mOff;
    private float mLevelA = 0.45f;
    private float mLevelB = 0.6f;

    public TilePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDp = getResources().getDisplayMetrics().density;
        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    void setKind(int kind) {
        mKind = kind;
        invalidate();
    }

    void setColors(int on, int off) {
        mOn = on;
        mOff = off;
        invalidate();
    }

    /** Signal uses the first level; sliders use both. */
    void setLevels(float a, float b) {
        mLevelA = Math.max(0f, Math.min(1f, a));
        mLevelB = Math.max(0f, Math.min(1f, b));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        switch (mKind) {
            case SIGNAL -> drawSignal(canvas);
            case PILLS -> drawPills(canvas);
            case SLIDERS -> drawSliders(canvas);
            case DOTS -> drawDots(canvas);
            case TYPE -> drawType(canvas);
            default -> {
            }
        }
    }

    private void drawSignal(Canvas canvas) {
        final int lit = Math.round(mLevelA * BAR_HEIGHTS.length);
        final float bottom = getHeight();
        float x = start();
        for (int i = 0; i < BAR_HEIGHTS.length; i++) {
            mPaint.setColor(i < lit ? mOn : mOff);
            mRect.set(x, bottom - BAR_HEIGHTS[i] * mDp, x + 5 * mDp, bottom);
            canvas.drawRoundRect(mRect, 2 * mDp, 2 * mDp, mPaint);
            x += 8 * mDp;
        }
    }

    private void drawPills(Canvas canvas) {
        pill(canvas, 4 * mDp, getWidth());
        pill(canvas, 16 * mDp, getWidth() * 0.7f);
    }

    private void pill(Canvas canvas, float cy, float end) {
        final float x = start();
        mPaint.setColor(mOn);
        canvas.drawCircle(x + 4 * mDp, cy, 4 * mDp, mPaint);
        mPaint.setColor(mOff);
        mRect.set(x + 14 * mDp, cy - 2.5f * mDp, Math.max(x + 20 * mDp, end - getPaddingEnd()),
                cy + 2.5f * mDp);
        canvas.drawRoundRect(mRect, 3 * mDp, 3 * mDp, mPaint);
    }

    private void drawSliders(Canvas canvas) {
        track(canvas, 2 * mDp, mLevelA);
        track(canvas, 13 * mDp, mLevelB);
    }

    private void track(Canvas canvas, float top, float level) {
        final float x = start();
        final float end = getWidth() - getPaddingEnd();
        mPaint.setColor(mOff);
        mRect.set(x, top, end, top + 5 * mDp);
        canvas.drawRoundRect(mRect, 3 * mDp, 3 * mDp, mPaint);
        if (level > 0f) {
            mPaint.setColor(mOn);
            mRect.set(x, top, x + Math.max(5 * mDp, (end - x) * level), top + 5 * mDp);
            canvas.drawRoundRect(mRect, 3 * mDp, 3 * mDp, mPaint);
        }
    }

    private void drawDots(Canvas canvas) {
        final float x = start();
        for (int i = 0; i < PATTERN.length; i++) {
            mPaint.setColor(PATTERN[i] ? mOn : mOff);
            canvas.drawCircle(x + (3 + (i % 3) * 10) * mDp, (5 + (i / 3) * 10) * mDp, 3 * mDp,
                    mPaint);
        }
    }

    private void drawType(Canvas canvas) {
        final float baseline = getHeight() - 2 * mDp;
        final float x = start();
        mPaint.setColor(mOn);
        mPaint.setTextSize(sp(20));
        canvas.drawText("Aa", x, baseline, mPaint);
        final float big = mPaint.measureText("Aa");
        mPaint.setTextSize(sp(12));
        mPaint.setAlpha(Math.round(0.7f * 255));
        canvas.drawText("Aa", x + big + 6 * mDp, baseline, mPaint);
        mPaint.setAlpha(255);
    }

    private float start() {
        return getPaddingStart();
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }
}
