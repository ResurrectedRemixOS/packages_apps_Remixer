package com.resurrection.settings.charging;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.Date;

public class ChargingStageView extends FrameLayout {
    private final int mScreenW;
    private final int mScreenH;
    private final float mDensity;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable mWallpaper;
    private float mScale = 1f;
    private String mStatus;

    public ChargingStageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        Rect bounds = context.getSystemService(WindowManager.class).getMaximumWindowMetrics()
                .getBounds();
        mScreenW = Math.min(bounds.width(), bounds.height());
        mScreenH = Math.max(bounds.width(), bounds.height());
        mDensity = getResources().getDisplayMetrics().density;
        try {
            mWallpaper = WallpaperManager.getInstance(context).getDrawable(WallpaperManager.FLAG_LOCK);
        } catch (RuntimeException e) {
            mWallpaper = null;
        }
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(mDensity);
        mRing.setColor(0x24FFFFFF);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 26 * mDensity);
            }
        });
        setClipToOutline(true);
        setElevation(10 * mDensity);
    }

    public void setEffect(View view) {
        removeAllViews();
        if (view != null) {
            addView(view);
        }
    }

    public void setStatus(String status) {
        mStatus = status;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, Math.round(width * (float) mScreenH / mScreenW));
        int w = MeasureSpec.makeMeasureSpec(mScreenW, MeasureSpec.EXACTLY);
        int h = MeasureSpec.makeMeasureSpec(mScreenH, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(w, h);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mScale = getWidth() / (float) mScreenW;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(0, 0, mScreenW, mScreenH);
            child.setPivotX(0);
            child.setPivotY(0);
            child.setScaleX(mScale);
            child.setScaleY(mScale);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.scale(mScale, mScale);
        if (mWallpaper != null) {
            int iw = Math.max(1, mWallpaper.getIntrinsicWidth());
            int ih = Math.max(1, mWallpaper.getIntrinsicHeight());
            float s = Math.max(mScreenW / (float) iw, mScreenH / (float) ih);
            int dw = Math.round(iw * s);
            int dh = Math.round(ih * s);
            int l = (mScreenW - dw) / 2;
            int t = (mScreenH - dh) / 2;
            mWallpaper.setBounds(l, t, l + dw, t + dh);
            mWallpaper.draw(canvas);
            canvas.drawColor(0x59000000);
        } else {
            mPaint.setShader(new LinearGradient(0, 0, mScreenW * 0.2f, mScreenH,
                    new int[] {0xFF4A3A44, 0xFF241A22, 0xFF0D0A0E}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, mScreenW, mScreenH, mPaint);
            mPaint.setShader(null);
        }
        Date now = new Date();
        mPaint.setColor(0xE6FFFFFF);
        mPaint.setTextAlign(Paint.Align.LEFT);
        mPaint.setTextSize(72 * mDensity);
        canvas.drawText(DateFormat.getTimeFormat(getContext()).format(now), 28 * mDensity,
                120 * mDensity, mPaint);
        mPaint.setColor(0xB3FFFFFF);
        mPaint.setTextSize(16 * mDensity);
        canvas.drawText(DateFormat.format("EEE d MMM", now).toString(), 32 * mDensity,
                152 * mDensity, mPaint);
        if (mStatus != null) {
            mPaint.setTextAlign(Paint.Align.CENTER);
            mPaint.setColor(0xD9FFFFFF);
            mPaint.setTextSize(15 * mDensity);
            canvas.drawText(mStatus, mScreenW / 2f, mScreenH - 132 * mDensity, mPaint);
        }
        mPaint.setColor(0x29FFFFFF);
        canvas.drawCircle(56 * mDensity, mScreenH - 72 * mDensity, 26 * mDensity, mPaint);
        canvas.drawCircle(mScreenW - 56 * mDensity, mScreenH - 72 * mDensity, 26 * mDensity, mPaint);
        canvas.restore();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        float r = 26 * mDensity;
        canvas.drawRoundRect(mRing.getStrokeWidth() / 2, mRing.getStrokeWidth() / 2,
                getWidth() - mRing.getStrokeWidth() / 2, getHeight() - mRing.getStrokeWidth() / 2,
                r, r, mRing);
    }

    int screenWidth() {
        return mScreenW;
    }

    int screenHeight() {
        return mScreenH;
    }
}
