/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.volume;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.PathParser;

public class VolumePanelPreview extends View {
    private static final String BELL = "M12 22a2 2 0 0 0 2-2h-4a2 2 0 0 0 2 2zm6-6v-5c0-3.07-1.64-5.64"
            + "-4.5-6.32V4a1.5 1.5 0 0 0-3 0v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1z";
    private static final String SPEAKER = "M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05"
            + "c1.48-.73 2.5-2.25 2.5-4.02z";
    private static final String TUNE = "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2"
            + "H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z";
    private static final long LOOP_MS = 6000;

    private final float mDp;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mBell = PathParser.createPathFromPathData(BELL);
    private final Path mSpeaker = PathParser.createPathFromPathData(SPEAKER);
    private final Path mTune = PathParser.createPathFromPathData(TUNE);
    private final Path mIcon = new Path();
    private final Path mShape = new Path();
    private final Matrix mMatrix = new Matrix();
    private final RectF mRect = new RectF();
    private final RectF mPart = new RectF();
    private final float[] mRadii = new float[8];
    private final Typeface mBold = Typeface.create(Typeface.DEFAULT, 700, false);
    private final Typeface mMono = Typeface.create(Typeface.MONOSPACE, 700, false);
    private final ValueAnimator mLoop = ValueAnimator.ofFloat(0f, 1f);
    private VolumeLook mLook;
    private boolean mLeft;
    private boolean mRinger = true;
    private boolean mTicks = true;
    private boolean mPercent = true;
    private boolean mRunning;
    private float mLevel = 0.34f;

    public VolumePanelPreview(Context context) {
        super(context);
        mDp = getResources().getDisplayMetrics().density;
        mStroke.setStyle(Paint.Style.STROKE);
        mGlow.setStyle(Paint.Style.STROKE);
        mGlow.setMaskFilter(new BlurMaskFilter(6 * mDp, BlurMaskFilter.Blur.NORMAL));
        mText.setTextSize(12 * mDp);
        mLoop.setDuration(LOOP_MS);
        mLoop.setRepeatCount(ValueAnimator.INFINITE);
        mLoop.setInterpolator(new LinearInterpolator());
        mLoop.addUpdateListener(a -> {
            mLevel = level(a.getAnimatedFraction());
            invalidate();
        });
    }

    void set(VolumeLook look, boolean left, boolean ringer, boolean ticks, boolean percent) {
        mLook = look;
        mLeft = left;
        mRinger = ringer;
        mTicks = ticks;
        mPercent = percent;
        invalidate();
    }

    void setRunning(boolean running) {
        mRunning = running;
        sync();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sync();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mLoop.cancel();
    }

    private void sync() {
        if (mRunning && isAttachedToWindow()) {
            if (!mLoop.isStarted()) {
                mLoop.start();
            }
        } else {
            mLoop.cancel();
        }
    }

    private static float level(float t) {
        if (t < 0.10f) {
            return 0.34f;
        } else if (t < 0.32f) {
            return lerp(0.34f, 0.72f, (t - 0.10f) / 0.22f);
        } else if (t < 0.46f) {
            return 0.72f;
        } else if (t < 0.68f) {
            return lerp(0.72f, 0.52f, (t - 0.46f) / 0.22f);
        } else if (t < 0.82f) {
            return 0.52f;
        }
        return lerp(0.52f, 0.34f, (t - 0.82f) / 0.18f);
    }

    private static float lerp(float from, float to, float t) {
        float e = t * t * (3 - 2 * t);
        return from + (to - from) * e;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float cy = h - 72 * mDp;
        float x = 96 * mDp + 30 * mDp;
        mArt.setShader(new LinearGradient(x - 18 * mDp, cy - 18 * mDp, x + 18 * mDp, cy + 18 * mDp,
                new int[] {0xFFFF8A5C, 0xFFFF4D8D, 0xFF7B4DFF}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawNowBar(canvas);
        if (mLook == null) {
            return;
        }
        if (mLook.classic) {
            drawClassic(canvas);
        } else {
            drawVertical(canvas);
        }
    }

    private void drawNowBar(Canvas canvas) {
        float cy = getHeight() - 72 * mDp;
        float r = 25 * mDp;
        mRect.set(96 * mDp, cy - 30 * mDp, getWidth() - 96 * mDp, cy + 30 * mDp);
        mFill.setColor(0xDB380913);
        canvas.drawRoundRect(mRect, r, r, mFill);
        mStroke.setStrokeWidth(mDp);
        mStroke.setColor(0x14FFFFFF);
        canvas.drawRoundRect(mRect, r, r, mStroke);
        canvas.drawCircle(mRect.left + 30 * mDp, cy, 18 * mDp, mArt);
        mFill.setColor(0x8CFFFFFF);
        mPart.set(mRect.left + 58 * mDp, cy - 4 * mDp, mRect.right - 24 * mDp, cy + 4 * mDp);
        canvas.drawRoundRect(mPart, 4 * mDp, 4 * mDp, mFill);
    }

    private void drawVertical(Canvas canvas) {
        VolumeLook l = mLook;
        float width = 68 * mDp;
        float height = (10 + (mRinger ? 58 : 0) + (mPercent ? 22 : 0) + 200 + 42 + 8) * mDp;
        float x = mLeft ? 8 * mDp : getWidth() - 8 * mDp - width;
        float y = (getHeight() - height) / 2f;
        float r = 28 * mDp;
        mRect.set(x, y, x + width, y + height);
        panel(canvas, l, r);
        if (l.ringerArea != 0 && mRinger) {
            mShape.reset();
            mShape.addRoundRect(mRect, r, r, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(mShape);
            mFill.setColor(l.ringerArea);
            canvas.drawRect(x, y, x + width, y + 63 * mDp, mFill);
            canvas.restore();
        }
        float cx = x + width / 2f;
        float top = y + 10 * mDp;
        if (mRinger) {
            ringer(canvas, l, cx, top + 24 * mDp, 24 * mDp, 22);
            top += 58 * mDp;
        }
        if (mPercent) {
            percent(canvas, l, cx, top + 12 * mDp, Paint.Align.CENTER);
            top += 22 * mDp;
        }
        if (l.pill) {
            pillTrack(canvas, l, cx, top);
        } else {
            handleTrack(canvas, l, cx, top);
        }
        top += 210 * mDp;
        icon(canvas, mTune, cx, top + 16 * mDp, 20, l.ringerFg);
    }

    private void handleTrack(Canvas canvas, VolumeLook l, float cx, float top) {
        float height = 200 * mDp;
        float bottom = top + height;
        float at = bottom - height * mLevel;
        float gap = 8 * mDp;
        float big = 14 * mDp;
        float small = 4 * mDp;
        mPart.set(cx - 20 * mDp, top, cx + 20 * mDp, at - gap);
        if (mPart.height() > 0) {
            corners(big, big, small, small);
            part(canvas, l.trackBg, l.trackStroke, l.trackStrokeDp, l.glow ? l.accent : 0);
        }
        mPart.set(cx - 20 * mDp, at + gap, cx + 20 * mDp, bottom);
        if (mPart.height() > 0) {
            corners(small, small, big, big);
            part(canvas, l.active, 0, 0, l.glow ? l.accent : 0);
        }
        if (mTicks) {
            mFill.setColor(l.tick);
            for (float ty = top + 7 * mDp; ty < bottom - 4 * mDp; ty += 14 * mDp) {
                canvas.drawCircle(cx, ty, 1.5f * mDp, mFill);
            }
        }
        mFill.setColor(l.thumb);
        mPart.set(cx - 26 * mDp, at - 2 * mDp, cx + 26 * mDp, at + 2 * mDp);
        canvas.drawRoundRect(mPart, 2 * mDp, 2 * mDp, mFill);
        icon(canvas, mSpeaker, cx, bottom - 20 * mDp, 20, l.iconFg);
    }

    private void pillTrack(Canvas canvas, VolumeLook l, float cx, float top) {
        float height = 200 * mDp;
        float bottom = top + height;
        float r = 26 * mDp;
        mPart.set(cx - r, top, cx + r, bottom);
        mFill.setColor(l.trackBg);
        canvas.drawRoundRect(mPart, r, r, mFill);
        mShape.reset();
        mShape.addRoundRect(mPart, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(mShape);
        mFill.setColor(l.active);
        canvas.drawRect(cx - r, bottom - height * mLevel, cx + r, bottom, mFill);
        if (mTicks) {
            mFill.setColor(l.tick);
            for (float ty = top + 15 * mDp; ty < bottom - 8 * mDp; ty += 14 * mDp) {
                canvas.drawCircle(cx, ty, 1.5f * mDp, mFill);
            }
        }
        canvas.restore();
        icon(canvas, mSpeaker, cx, bottom - 26 * mDp, 24, l.iconFg);
    }

    private void drawClassic(Canvas canvas) {
        VolumeLook l = mLook;
        float x = 8 * mDp;
        float y = 40 * mDp;
        float r = 20 * mDp;
        mRect.set(x, y, getWidth() - 8 * mDp, y + 60 * mDp);
        panel(canvas, l, r);
        mShape.reset();
        mShape.addRoundRect(mRect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(mShape);
        mFill.setColor(l.accent);
        canvas.drawRect(mRect.left, mRect.top, mRect.right, mRect.top + 3 * mDp, mFill);
        canvas.restore();
        float cy = mRect.centerY();
        float start = mRect.left + 10 * mDp;
        if (mRinger) {
            ringer(canvas, l, start + 20 * mDp, cy, 20 * mDp, 20);
            start += 48 * mDp;
        }
        float end = mRect.right - 10 * mDp;
        icon(canvas, mTune, end - 14 * mDp, cy, 20, l.ringerFg);
        end -= 36 * mDp;
        if (mPercent) {
            percent(canvas, l, end, cy + 4 * mDp, Paint.Align.RIGHT);
            end -= 42 * mDp;
        }
        float at = start + (end - start) * mLevel;
        float gap = 8 * mDp;
        float big = 16 * mDp;
        float small = 4 * mDp;
        mPart.set(at + gap, cy - 16 * mDp, end, cy + 16 * mDp);
        if (mPart.width() > 0) {
            corners(small, big, big, small);
            part(canvas, l.trackBg, 0, 0, 0);
        }
        mPart.set(start, cy - 16 * mDp, at - gap, cy + 16 * mDp);
        if (mPart.width() > 0) {
            corners(big, small, small, big);
            part(canvas, l.active, 0, 0, 0);
        }
        if (mTicks) {
            mFill.setColor(l.tick);
            for (float tx = start + 7 * mDp; tx < end - 4 * mDp; tx += 14 * mDp) {
                canvas.drawCircle(tx, cy, 1.5f * mDp, mFill);
            }
        }
        mFill.setColor(l.thumb);
        mPart.set(at - 1.5f * mDp, cy - 20 * mDp, at + 1.5f * mDp, cy + 20 * mDp);
        canvas.drawRoundRect(mPart, 1.5f * mDp, 1.5f * mDp, mFill);
        icon(canvas, mSpeaker, start + 18 * mDp, cy, 20, l.iconFg);
    }

    private void panel(Canvas canvas, VolumeLook l, float r) {
        if (l.glow) {
            mGlow.setStrokeWidth(4 * mDp);
            mGlow.setColor(ColorUtils.setAlphaComponent(l.accent, 0x8C));
            canvas.drawRoundRect(mRect, r, r, mGlow);
        }
        mFill.setColor(l.panel);
        canvas.drawRoundRect(mRect, r, r, mFill);
        if (l.panelStroke != 0) {
            mStroke.setStrokeWidth(l.panelStrokeDp * mDp);
            mStroke.setColor(l.panelStroke);
            canvas.drawRoundRect(mRect, r, r, mStroke);
        }
    }

    private void ringer(Canvas canvas, VolumeLook l, float cx, float cy, float radius, int iconDp) {
        if (l.ringerBg != 0) {
            mFill.setColor(l.ringerBg);
            canvas.drawCircle(cx, cy, radius, mFill);
        }
        if (l.ringerStroke != 0) {
            mStroke.setStrokeWidth(mDp);
            mStroke.setColor(l.ringerStroke);
            canvas.drawCircle(cx, cy, radius - mDp / 2, mStroke);
        }
        icon(canvas, mBell, cx, cy, iconDp, l.ringerFg);
    }

    private void percent(Canvas canvas, VolumeLook l, float x, float baseline, Paint.Align align) {
        mText.setTypeface(l.mono ? mMono : mBold);
        mText.setTextAlign(align);
        mText.setColor(l.percentFg);
        canvas.drawText(Math.round(mLevel * 100) + "%", x, baseline, mText);
    }

    private void corners(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        mRadii[0] = mRadii[1] = topLeft;
        mRadii[2] = mRadii[3] = topRight;
        mRadii[4] = mRadii[5] = bottomRight;
        mRadii[6] = mRadii[7] = bottomLeft;
    }

    private void part(Canvas canvas, int fill, int stroke, float strokeDp, int glow) {
        mShape.reset();
        mShape.addRoundRect(mPart, mRadii, Path.Direction.CW);
        if (glow != 0) {
            mGlow.setStrokeWidth(3 * mDp);
            mGlow.setColor(ColorUtils.setAlphaComponent(glow, 0x8C));
            canvas.drawPath(mShape, mGlow);
        }
        if (fill != 0) {
            mFill.setColor(fill);
            canvas.drawPath(mShape, mFill);
        }
        if (stroke != 0) {
            mStroke.setStrokeWidth(strokeDp * mDp);
            mStroke.setColor(stroke);
            canvas.drawPath(mShape, mStroke);
        }
    }

    private void icon(Canvas canvas, Path source, float cx, float cy, int sizeDp, int color) {
        float s = sizeDp * mDp / 24f;
        mMatrix.setScale(s, s);
        mMatrix.postTranslate(cx - 12 * s, cy - 12 * s);
        source.transform(mMatrix, mIcon);
        mFill.setColor(color);
        canvas.drawPath(mIcon, mFill);
    }
}
