package com.resurrection.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Saturation (x) and brightness (y) square for one hue.
 */
public class ColorPadView extends View {
    public interface OnChangeListener {
        void onChange(float saturation, float value);
    }

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mClip = new Path();
    private final RectF mBounds = new RectF();
    private final float mRadius;
    private final float mThumbRadius;
    private Shader mValueShader;
    private Shader mSaturationShader;
    private float mHue;
    private float mSaturation = 1;
    private float mValue = 1;
    private OnChangeListener mListener;

    public ColorPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        mRadius = 20 * density;
        mThumbRadius = 13 * density;
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(3 * density);
        mRing.setColor(Color.WHITE);
        mRing.setShadowLayer(3 * density, 0, density, 0x66000000);
    }

    public void setOnChangeListener(OnChangeListener listener) {
        mListener = listener;
    }

    public void setColor(float hue, float saturation, float value) {
        if (hue != mHue) {
            mHue = hue;
            updateSaturationShader();
        }
        mSaturation = saturation;
        mValue = value;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBounds.set(0, 0, w, h);
        mClip.reset();
        mClip.addRoundRect(mBounds, mRadius, mRadius, Path.Direction.CW);
        mValueShader = new LinearGradient(0, 0, 0, h, Color.TRANSPARENT, Color.BLACK,
                Shader.TileMode.CLAMP);
        updateSaturationShader();
    }

    private void updateSaturationShader() {
        mSaturationShader = new LinearGradient(0, 0, Math.max(1, getWidth()), 0, Color.WHITE,
                Color.HSVToColor(new float[] {mHue, 1, 1}), Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int saved = canvas.save();
        canvas.clipPath(mClip);
        mFill.setShader(mSaturationShader);
        canvas.drawRect(mBounds, mFill);
        mFill.setShader(mValueShader);
        canvas.drawRect(mBounds, mFill);
        canvas.restoreToCount(saved);

        float x = mSaturation * getWidth();
        float y = (1 - mValue) * getHeight();
        mThumb.setColor(Color.HSVToColor(new float[] {mHue, mSaturation, mValue}));
        canvas.drawCircle(x, y, mThumbRadius, mThumb);
        canvas.drawCircle(x, y, mThumbRadius, mRing);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Keep the bottom sheet from taking the drag.
                getParent().requestDisallowInterceptTouchEvent(true);
                // fall through
            case MotionEvent.ACTION_MOVE:
                mSaturation = clamp(event.getX() / getWidth());
                mValue = clamp(1 - event.getY() / getHeight());
                invalidate();
                if (mListener != null) {
                    mListener.onChange(mSaturation, mValue);
                }
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private static float clamp(float f) {
        return Math.max(0, Math.min(1, f));
    }
}
