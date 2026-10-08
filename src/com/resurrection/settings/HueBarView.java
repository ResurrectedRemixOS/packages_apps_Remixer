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
 * Rainbow bar that picks a hue from 0 to 360.
 */
public class HueBarView extends View {
    public interface OnChangeListener {
        void onChange(float hue);
    }

    private static final int[] RAINBOW = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
            0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
    private static final float STEP = 5;

    private final Paint mBar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final float mThumbRadius;
    private float mHue;
    private OnChangeListener mListener;

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

    public void setHue(float hue) {
        mHue = hue;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBounds.set(0, 0, w, h);
        mBar.setShader(new LinearGradient(0, 0, w, 0, RAINBOW, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = getHeight() / 2f;
        canvas.drawRoundRect(mBounds, radius, radius, mBar);
        float x = radius + mHue / 360f * (getWidth() - 2 * radius);
        mThumb.setColor(Color.HSVToColor(new float[] {mHue, 1, 1}));
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
                change(Math.max(0, Math.min(1, f)) * 360);
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            change(mHue + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? STEP : -STEP));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0, 360, mHue));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            change(mHue + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? STEP : -STEP));
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    private void change(float hue) {
        mHue = Math.max(0, Math.min(360, hue));
        invalidate();
        if (mListener != null) {
            mListener.onChange(mHue);
        }
    }
}
