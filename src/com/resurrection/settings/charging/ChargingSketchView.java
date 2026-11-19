package com.resurrection.settings.charging;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

final class ChargingSketchView extends View {
    static final int ONEPLUS = 0;
    static final int MOTO = 1;
    static final int NOTHING = 2;
    private static final long FRAME_MS = 40;
    private static final String OVERLAY_PREFIX = "com.android.systemui.charging_animation.";

    private final int mKind;
    private final int mLevel;
    private final float mDensity;
    private long mStart = SystemClock.uptimeMillis();
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mOval = new RectF();
    private final List<Bitmap> mFrames = new ArrayList<>();
    private Bitmap mGlare;
    private SweepGradient mSweep;

    ChargingSketchView(Context context, int kind, int level, Executor loader) {
        super(context);
        mKind = kind;
        mLevel = level;
        mDensity = getResources().getDisplayMetrics().density;
        if (kind != ONEPLUS) {
            loader.execute(this::load);
        }
    }

    private void load() {
        String pkg = OVERLAY_PREFIX + (mKind == MOTO ? "moto" : "nothing");
        List<Bitmap> frames = new ArrayList<>();
        Bitmap glare = null;
        try {
            Resources res = getContext().getPackageManager().getResourcesForApplication(pkg);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            for (Integer id : ids(res, pkg, mKind == MOTO
                    ? "config_chargingAnimTurboLineFrames" : "config_chargingAnimFrames")) {
                Bitmap b = BitmapFactory.decodeResource(res, id, opts);
                if (b != null) {
                    frames.add(b);
                }
            }
            if (mKind == NOTHING) {
                List<Integer> g = ids(res, pkg, "config_chargingAnimGlare");
                if (!g.isEmpty()) {
                    glare = BitmapFactory.decodeResource(res, g.get(0), opts);
                }
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return;
        }
        Bitmap finalGlare = glare;
        post(() -> {
            mFrames.addAll(frames);
            mGlare = finalGlare;
            invalidate();
        });
    }

    private static List<Integer> ids(Resources res, String pkg, String name) {
        List<Integer> ids = new ArrayList<>();
        int array = res.getIdentifier(name, "array", pkg);
        if (array == 0) {
            return ids;
        }
        TypedArray ta = res.obtainTypedArray(array);
        for (int i = 0; i < ta.length(); i++) {
            int id = ta.getResourceId(i, 0);
            if (id != 0) {
                ids.add(id);
            }
        }
        ta.recycle();
        return ids;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long t = SystemClock.uptimeMillis() - mStart;
        float w = getWidth();
        float h = getHeight();
        canvas.drawColor(0x8C000000);
        if (mKind == NOTHING) {
            drawNothing(canvas, t, w, h);
        } else {
            drawRing(canvas, t, w, h);
        }
        postInvalidateOnAnimation();
    }

    private void drawNothing(Canvas canvas, long t, float w, float h) {
        if (!mFrames.isEmpty()) {
            Bitmap b = mFrames.get((int) (t / FRAME_MS % mFrames.size()));
            float dh = b.getHeight() * w / b.getWidth();
            canvas.drawBitmap(b, null, new RectF(0, (h - dh) / 2, w, (h + dh) / 2), null);
        }
        if (mGlare != null) {
            float gw = mGlare.getWidth() * 2f;
            float gh = mGlare.getHeight() * 2f;
            canvas.drawBitmap(mGlare, null, new RectF((w - gw) / 2, h - gh / 2, (w + gw) / 2, h + gh / 2), null);
        }
    }

    private void drawRing(Canvas canvas, long t, float w, float h) {
        boolean moto = mKind == MOTO;
        float r = w * 0.27f;
        float cx = w / 2;
        float cy = h * 0.42f;
        mOval.set(cx - r, cy - r, cx + r, cy + r);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setShader(null);
        mPaint.setStrokeWidth((moto ? 5 : 12) * mDensity);
        mPaint.setColor(0x1FFFFFFF);
        canvas.drawOval(mOval, mPaint);
        if (moto) {
            float p = Math.min(1f, t / 1200f);
            mPaint.setColor(0xFF38B6FF);
            mPaint.setShadowLayer(12 * mDensity, 0, 0, 0xFF1E90FF);
            canvas.drawArc(mOval, 110, 320 * p, false, mPaint);
            mPaint.clearShadowLayer();
            if (!mFrames.isEmpty() && t < mFrames.size() * FRAME_MS) {
                Bitmap b = mFrames.get((int) (t / FRAME_MS));
                float dh = (h - cy - r);
                float dw = b.getWidth() * dh / b.getHeight();
                canvas.drawBitmap(b, null, new RectF(cx - dw / 2, cy + r, cx + dw / 2, h), null);
            }
        } else {
            if (mSweep == null) {
                mSweep = new SweepGradient(cx, cy, new int[] {Color.TRANSPARENT, 0x80FF5A36,
                        Color.WHITE}, new float[] {0.55f, 0.8f, 1f});
            }
            canvas.save();
            canvas.rotate(t * 0.26f % 360, cx, cy);
            mPaint.setShader(mSweep);
            canvas.drawOval(mOval, mPaint);
            mPaint.setShader(null);
            canvas.restore();
        }
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.WHITE);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTextSize(r * 0.5f);
        int shown = Math.round(mLevel * Math.min(1f, t / 1200f));
        canvas.drawText(shown + "%", cx, cy + r * 0.18f, mPaint);
        if (moto && t > mFrames.size() * FRAME_MS + 2400) {
            mStart = SystemClock.uptimeMillis();
        }
    }
}
