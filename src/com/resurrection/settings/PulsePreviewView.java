package com.resurrection.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Animated stand-in for SystemUI's PulseRenderer, fed by a fake beat. Keep the drawing in step
 * with com.android.systemui.rr.pulse.PulseRenderer.
 */
public class PulsePreviewView extends View {
    static final int STYLE_LINES = 0;
    static final int STYLE_FADING = 1;
    static final int STYLE_BLOCKS = 2;
    static final int STYLE_MIRROR = 3;
    static final int COLOR_CUSTOM = 0;
    static final int COLOR_ACCENT = 1;
    static final int COLOR_GRADIENT = 2;
    static final int[] GRADIENT = {0xFFFF3B5C, 0xFFFF9F1C, 0xFFFFE14D, 0xFF3DDC97,
            0xFF2EC4F1, 0xFF7B61FF, 0xFFFF3B9A};

    private static final int FRAME_MS = 25;
    private static final int BEAT_MS = 480;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFade = new Paint();
    private final RectF mRect = new RectF();
    private final Random mRandom = new Random(7);
    private float[] mLevels = new float[0];
    private float[] mPhases = new float[0];
    private Bitmap mBitmap;
    private Canvas mBitmapCanvas;
    private LinearGradient mShader;
    private int mShaderWidth;

    private int mStyle;
    private int mColorMode;
    private int mColor;
    private int mAccent;
    private int mOpacity = 90;

    public PulsePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFade.setColor(0xE6FFFFFF);
        mFade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        mAccent = context.getColor(android.R.color.system_accent1_300);
        setBars(28);
    }

    void setStyle(int style) {
        if (style != mStyle) {
            mStyle = style;
            recycleBitmap();
            invalidate();
        }
    }

    void setColors(int mode, int color) {
        mColorMode = mode;
        mColor = color | 0xFF000000;
        invalidate();
    }

    void setOpacity(int percent) {
        mOpacity = percent;
        invalidate();
    }

    void setBars(int bars) {
        if (bars == mLevels.length) {
            return;
        }
        mLevels = new float[bars];
        mPhases = new float[bars];
        for (int i = 0; i < bars; i++) {
            mPhases[i] = mRandom.nextFloat() * 6.283f;
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        recycleBitmap();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        step();
        preparePaint(width);
        if (mStyle == STYLE_FADING) {
            if (mBitmap == null || mBitmap.getWidth() != width
                    || mBitmap.getHeight() != height) {
                recycleBitmap();
                mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mBitmapCanvas = new Canvas(mBitmap);
            }
            mBitmapCanvas.drawPaint(mFade);
            drawBars(mBitmapCanvas, width, height, STYLE_LINES);
            canvas.drawBitmap(mBitmap, 0f, 0f, null);
        } else {
            drawBars(canvas, width, height, mStyle);
        }
        if (isShown()) {
            postInvalidateDelayed(FRAME_MS);
        }
    }

    /** Bass-heavy bars that kick on the beat and shimmer in between. */
    private void step() {
        long now = SystemClock.uptimeMillis();
        float beat = 1f - (now % BEAT_MS) / (float) BEAT_MS;
        beat = beat * beat;
        float t = now / 1000f;
        int n = mLevels.length;
        for (int i = 0; i < n; i++) {
            float x = i / (float) Math.max(1, n - 1);
            float body = 0.85f - 0.45f * x;
            float shimmer = 0.5f + 0.5f * (float) Math.sin(t * (3f + 9f * x) + mPhases[i]);
            float kick = beat * (1f - x) * 0.55f;
            float target = Math.min(1f, body * (0.25f + 0.55f * shimmer) + kick
                    + mRandom.nextFloat() * 0.08f);
            mLevels[i] += (target - mLevels[i]) * 0.45f;
        }
    }

    private void preparePaint(int width) {
        if (mColorMode == COLOR_GRADIENT) {
            if (mShader == null || mShaderWidth != width) {
                mShader = new LinearGradient(0f, 0f, width, 0f, GRADIENT, null,
                        Shader.TileMode.CLAMP);
                mShaderWidth = width;
            }
            mPaint.setShader(mShader);
            mPaint.setColor(Color.WHITE);
        } else {
            mPaint.setShader(null);
            mPaint.setColor(mColorMode == COLOR_ACCENT ? mAccent : mColor);
        }
        mPaint.setAlpha(Math.round(255 * mOpacity / 100f));
    }

    private void drawBars(Canvas canvas, int width, int height, int style) {
        int n = mLevels.length;
        float gap = Math.max(2f, width / (n * 6f));
        float thick = Math.max(2f, (width - gap * (n + 1)) / n);
        float radius = thick / 2f;
        float bottom = height - 2f;
        for (int i = 0; i < n; i++) {
            float amp = mLevels[i] * (height - 4f);
            if (amp < 1f) {
                continue;
            }
            float left = gap + i * (thick + gap);
            if (style == STYLE_BLOCKS) {
                float block = Math.max(3f, Math.min(thick, height / 8f));
                float step = block + Math.max(2f, block * 0.35f);
                int count = Math.max(1, Math.round(amp / step));
                for (int k = 0; k < count; k++) {
                    float top = bottom - k * step - block;
                    mRect.set(left, top, left + thick, top + block);
                    canvas.drawRoundRect(mRect, block * 0.3f, block * 0.3f, mPaint);
                }
            } else if (style == STYLE_MIRROR) {
                float center = height / 2f;
                mRect.set(left, center - amp / 2f, left + thick, center + amp / 2f);
                canvas.drawRoundRect(mRect, radius, radius, mPaint);
            } else {
                mRect.set(left, bottom - amp, left + thick, bottom);
                canvas.drawRoundRect(mRect, radius, radius, mPaint);
            }
        }
    }

    private void recycleBitmap() {
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
            mBitmapCanvas = null;
        }
    }
}
