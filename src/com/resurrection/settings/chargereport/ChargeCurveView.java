/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.chargereport;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;

/**
 * One charge: watts as a filled curve in the accent, battery level as a line and, on the detail
 * view, the temperature dashed. Points are [seconds, watts, level, temperature].
 */
public class ChargeCurveView extends View {
    public static final int TEMP_COLOR = 0xFFFFB74D;

    private final float mDensity = getResources().getDisplayMetrics().density;
    private final Paint mWatts = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArea = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLevel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTemp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private JSONArray mCurve = new JSONArray();
    private boolean mShowTemp;

    public ChargeCurveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mWatts.setStyle(Paint.Style.STROKE);
        mWatts.setStrokeWidth(2 * mDensity);
        mWatts.setStrokeJoin(Paint.Join.ROUND);
        mWatts.setColor(wattsColor(context));
        mArea.setColor((wattsColor(context) & 0x00FFFFFF) | 0x40000000);
        mLevel.setStyle(Paint.Style.STROKE);
        mLevel.setStrokeWidth(1.5f * mDensity);
        mLevel.setColor(levelColor(context));
        mTemp.setStyle(Paint.Style.STROKE);
        mTemp.setStrokeWidth(1.5f * mDensity);
        mTemp.setColor(TEMP_COLOR);
        mTemp.setPathEffect(new DashPathEffect(new float[] {4 * mDensity, 3 * mDensity}, 0));
    }

    public static int wattsColor(Context context) {
        return context.getColor(android.R.color.system_accent1_400);
    }

    public static int levelColor(Context context) {
        return context.getColor(android.R.color.system_neutral1_400);
    }

    public void setCurve(JSONArray curve) {
        mCurve = curve != null ? curve : new JSONArray();
        invalidate();
    }

    public void setShowTemperature(boolean show) {
        mShowTemp = show;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = mCurve.length();
        if (n < 2) return;
        float w = getWidth();
        float h = getHeight() - 2 * mDensity;
        double end = Math.max(1, mCurve.optJSONArray(n - 1).optDouble(0));
        double peak = 1;
        for (int i = 0; i < n; i++) peak = Math.max(peak, mCurve.optJSONArray(i).optDouble(1));
        mPath.reset();
        mPath.moveTo(0, h);
        for (int i = 0; i < n; i++) {
            JSONArray p = mCurve.optJSONArray(i);
            mPath.lineTo((float) (w * p.optDouble(0) / end), (float) (h - h * p.optDouble(1) / peak));
        }
        mPath.lineTo(w, h);
        mPath.close();
        canvas.drawPath(mPath, mArea);
        line(canvas, 1, 0, peak, w, h, end, mWatts);
        line(canvas, 2, 0, 100, w, h, end, mLevel);
        if (!mShowTemp) return;
        double lo = Double.MAX_VALUE;
        double hi = 0;
        for (int i = 0; i < n; i++) {
            double t = mCurve.optJSONArray(i).optDouble(3, 0);
            if (t <= 0) continue;
            lo = Math.min(lo, t);
            hi = Math.max(hi, t);
        }
        if (hi <= 0) return;
        lo = Math.min(lo, 20);
        hi = Math.max(hi, lo + 10);
        line(canvas, 3, lo, hi, w, h, end, mTemp);
    }

    private void line(Canvas canvas, int index, double lo, double hi, float w, float h, double end,
            Paint paint) {
        mPath.reset();
        boolean started = false;
        for (int i = 0; i < mCurve.length(); i++) {
            JSONArray p = mCurve.optJSONArray(i);
            if (p.length() <= index) continue;
            float x = (float) (w * p.optDouble(0) / end);
            float y = (float) (h - h * (p.optDouble(index) - lo) / (hi - lo));
            if (!started) {
                mPath.moveTo(x, y);
                started = true;
            } else {
                mPath.lineTo(x, y);
            }
        }
        if (started) canvas.drawPath(mPath, paint);
    }
}
