package com.resurrection.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Rainbow bar that picks a hue from 0 to 360, or any range colored by a {@link ColorFunction}.
 */
public class HueBarView extends View {
    public interface OnChangeListener {
        void onChange(float value);
    }

    /** Called when a drag ends, and after each key or accessibility step. */
    public interface OnReleaseListener {
        void onRelease(float value);
    }

    /** Colors the bar and the thumb for a value. */
    public interface ColorFunction {
        int colorAt(float value);
    }

    private static final float STEP = 5;
    /** Gradient stops across the range. */
    private static final int STOPS = 13;

    private final Paint mBar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final float mThumbRadius;
    private float mMin = 0;
    private float mMax = 360;
    private ColorFunction mColors = hue -> Color.HSVToColor(new float[] {hue, 1, 1});
    private float mValue;
    private OnChangeListener mListener;
    private OnReleaseListener mReleaseListener;

    public HueBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        mThumbRadius = 13 * density;
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(3 * density);
        mRing.setColor(Color.WHITE);
        mRing.setShadowLayer(3 * density, 0, density, 0x66000000);
        setFocusable(true);
    }

    public void setOnChangeListener(OnChangeListener listener) {
        mListener = listener;
    }

    public void setOnReleaseListener(OnReleaseListener listener) {
        mReleaseListener = listener;
    }

    public void setRange(float min, float max) {
        mMin = min;
        mMax = max;
        updateColors();
    }

    public void setColorFunction(ColorFunction colors) {
        mColors = colors;
        updateColors();
    }

    /** Rebuilds the bar after the color function's output changed. */
    public void updateColors() {
        float radius = getHeight() / 2f;
        if (getWidth() > 2 * radius) {
            int[] stops = new int[STOPS];
            for (int i = 0; i < STOPS; i++) {
                stops[i] = mColors.colorAt(mMin + (mMax - mMin) * i / (STOPS - 1));
            }
            // Spans the thumb's travel so the thumb sits on its own color.
            mBar.setShader(new LinearGradient(radius, 0, getWidth() - radius, 0, stops, null,
                    Shader.TileMode.CLAMP));
        }
        invalidate();
    }

    public void setValue(float value) {
        mValue = value;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBounds.set(0, 0, w, h);
        updateColors();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = getHeight() / 2f;
        canvas.drawRoundRect(mBounds, radius, radius, mBar);
        float x = radius + (mValue - mMin) / (mMax - mMin) * (getWidth() - 2 * radius);
        mThumb.setColor(mColors.colorAt(mValue));
        canvas.drawCircle(x, radius, mThumbRadius, mThumb);
        canvas.drawCircle(x, radius, mThumbRadius, mRing);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                // fall through
            case MotionEvent.ACTION_MOVE:
                float radius = getHeight() / 2f;
                float f = (event.getX() - radius) / Math.max(1, getWidth() - 2 * radius);
                change(mMin + Math.max(0, Math.min(1, f)) * (mMax - mMin));
                return true;
            case MotionEvent.ACTION_UP:
                release();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                release();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            change(mValue + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? STEP : -STEP));
            release();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, mMin, mMax, mValue));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            change(mValue + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? STEP : -STEP));
            release();
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    private void change(float value) {
        mValue = Math.max(mMin, Math.min(mMax, value));
        invalidate();
        if (mListener != null) {
            mListener.onChange(mValue);
        }
    }

    private void release() {
        if (mReleaseListener != null) {
            mReleaseListener.onRelease(mValue);
        }
    }
}
