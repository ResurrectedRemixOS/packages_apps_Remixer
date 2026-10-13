/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.chargereport;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;

/** One charge: watts as a filled curve in the accent, battery level as a line. */
public class ChargeCurveView extends View {
    private final float mDensity = getResources().getDisplayMetrics().density;
    private final Paint mWatts = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArea = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLevel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private JSONArray mCurve = new JSONArray();

    public ChargeCurveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        int accent = context.getColor(android.R.color.system_accent1_400);
        mWatts.setStyle(Paint.Style.STROKE);
        mWatts.setStrokeWidth(2 * mDensity);
        mWatts.setColor(accent);
        mArea.setColor((accent & 0x00FFFFFF) | 0x40000000);
        mLevel.setStyle(Paint.Style.STROKE);
        mLevel.setStrokeWidth(1.5f * mDensity);
        mLevel.setColor(context.getColor(android.R.color.system_neutral1_400));
    }

    public void setCurve(JSONArray curve) {
        mCurve = curve != null ? curve : new JSONArray();
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
        mPath.reset();
        for (int i = 0; i < n; i++) {
            JSONArray p = mCurve.optJSONArray(i);
            float x = (float) (w * p.optDouble(0) / end);
            float y = (float) (h - h * p.optDouble(1) / peak);
            if (i == 0) mPath.moveTo(x, y); else mPath.lineTo(x, y);
        }
        canvas.drawPath(mPath, mWatts);
        mPath.reset();
        for (int i = 0; i < n; i++) {
            JSONArray p = mCurve.optJSONArray(i);
            float x = (float) (w * p.optDouble(0) / end);
            float y = (float) (h - h * p.optDouble(2) / 100.0);
            if (i == 0) mPath.moveTo(x, y); else mPath.lineTo(x, y);
        }
        canvas.drawPath(mPath, mLevel);
    }
}
