package com.resurrection.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.PathParser;

import com.android.settings.R;

import java.util.Locale;

/** The mini screen on an Everything card: a small, live picture of what the section changes. */
public class HubPreviewView extends View {
    public static final int STATUS_BAR = 0;
    public static final int QUICK_SETTINGS = 1;
    public static final int NOTIFICATIONS = 2;
    public static final int THEME = 3;
    public static final int LOCK_SCREEN = 4;
    public static final int NAVIGATION = 5;
    public static final int MISC = 6;

    /** Pulse bar heights in dp, left to right. */
    private static final float[] BARS = {14, 26, 38, 22, 44, 30, 18, 34, 42, 24, 12, 28, 16};
    /** The RR mark in a 100 x 73.1 box. */
    private static final String MARK = "M60.2 0 98.3 0 99.9 2.3 97.8 7.4 94.9 12.4 92 15.2 88.8 16.5 "
            + "60.5 16.5 55.3 17.5 51 19.5 46.7 22.6 45.1 24.2 19.2 55.6 16.7 57.3 12.3 58.3 7.4 57.8 "
            + "3 56 0.3 53 30.3 16.4 35.6 10.8 41.4 6.3 47.6 2.9 53.9 0.7Z M67.6 26.1 97.5 26.1 99 28.2 "
            + "96.2 34.3 93.4 37.9 90.3 39.5 67.2 39.6 62.2 40.8 58.6 42.7 55.1 45.7 34.8 70.3 32.4 "
            + "72.2 29.3 73 24.7 72.6 20.9 71 19.2 68.5 43.5 38.9 47.7 34.6 52.6 30.8 57.7 28.2 "
            + "62.7 26.6Z";
    private static final String WIFI = "M3 9.5a13 13 0 0 1 18 0 M6.5 13a8 8 0 0 1 11 0 "
            + "M10 16.5a3 3 0 0 1 4 0";
    private static final String SHIELD = "M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z M9 12l2 2 4-4";
    private static final String FINGERPRINT = "M7 10a5 5 0 0 1 10 0v2 M9.5 10.5a2.5 2.5 0 0 1 5 0V14"
            + "a6 6 0 0 1-1.2 3.6 M12 10.5v4a7 7 0 0 1-2 4.8 M5 14v-3";

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Path mPath = new Path();
    private final Matrix mMatrix = new Matrix();
    private final float mDp;
    private int mKind;
    private int mHi = Color.WHITE;
    private int mMid = Color.GRAY;
    private int mSoft = 0x59FFFFFF;
    private int mDeep = Color.BLACK;
    private Typeface mFont = Typeface.DEFAULT_BOLD;
    private int mBattery = -1;
    private float mPhase = 0.25f;
    private ValueAnimator mAnimator;

    public HubPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDp = getResources().getDisplayMetrics().density;
        mLine.setStyle(Paint.Style.STROKE);
        mLine.setStrokeCap(Paint.Cap.ROUND);
        mLine.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setKind(int kind) {
        mKind = kind;
        invalidate();
    }

    public void setColors(int hi, int mid, int deep) {
        mHi = hi;
        mMid = mid;
        mSoft = ColorUtils.setAlphaComponent(hi, 0x59);
        mDeep = deep;
        invalidate();
    }

    public void setFont(Typeface font) {
        mFont = font;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        BatteryManager battery = getContext().getSystemService(BatteryManager.class);
        mBattery = battery != null
                ? battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        if ((mKind == NOTIFICATIONS || mKind == NAVIGATION) && ValueAnimator.areAnimatorsEnabled()) {
            mAnimator = ValueAnimator.ofFloat(0, 1);
            mAnimator.setDuration(2400);
            mAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mAnimator.setInterpolator(new LinearInterpolator());
            mAnimator.addUpdateListener(a -> {
                mPhase = (float) a.getAnimatedValue();
                invalidate();
            });
            mAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        mRect.set(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f));
        mFill.setShader(null);
        mFill.setColor(0x57000000);
        canvas.drawRoundRect(mRect, dp(18), dp(18), mFill);
        mLine.setStrokeWidth(dp(1));
        mLine.setColor(0x17FFFFFF);
        canvas.drawRoundRect(mRect, dp(18), dp(18), mLine);
        switch (mKind) {
            case STATUS_BAR:
                drawStatusBar(canvas, w, h);
                break;
            case QUICK_SETTINGS:
                drawQuickSettings(canvas, w, h);
                break;
            case NOTIFICATIONS:
                drawNotifications(canvas, w, h);
                break;
            case THEME:
                drawTheme(canvas, w, h);
                break;
            case LOCK_SCREEN:
                drawLockScreen(canvas, w, h);
                break;
            case NAVIGATION:
                drawNavigation(canvas, w, h);
                break;
            default:
                drawMisc(canvas, w, h);
                break;
        }
    }

    private void drawStatusBar(Canvas canvas, float w, float h) {
        float pad = dp(11);
        float cy = h / 2 - dp(13);
        text(canvas, time(), pad, cy, 15, Typeface.DEFAULT_BOLD, mHi, Paint.Align.LEFT);

        float right = w - pad - dp(2);
        float level = mBattery >= 0 ? mBattery / 100f : 0.7f;
        mRect.set(right - dp(20), cy - dp(5), right, cy + dp(5));
        mLine.setStrokeWidth(dp(1.5f));
        mLine.setColor(Color.WHITE);
        canvas.drawRoundRect(mRect, dp(3), dp(3), mLine);
        mRect.set(right - dp(18), cy - dp(3), right - dp(18) + dp(16) * level, cy + dp(3));
        fill(canvas, mRect, dp(1), mHi);
        mRect.set(right + dp(0.5f), cy - dp(2), right + dp(2), cy + dp(2));
        fill(canvas, mRect, dp(1), Color.WHITE);

        float wifi = right - dp(20) - dp(6) - dp(13);
        icon(canvas, WIFI, wifi, cy - dp(6.5f), dp(13), 24, 2.4f, Color.WHITE);

        float bars = wifi - dp(6) - dp(14.5f);
        float[] heights = {4, 6, 8.5f, 11};
        for (int i = 0; i < heights.length; i++) {
            float x = bars + i * dp(4);
            mRect.set(x, cy + dp(5.5f) - dp(heights[i]), x + dp(2.5f), cy + dp(5.5f));
            fill(canvas, mRect, dp(1), i == 3 ? 0x59FFFFFF : Color.WHITE);
        }

        float pillY = h / 2 + dp(15);
        float next = pill(canvas, getContext().getString(R.string.remixer_preview_traffic), pad,
                pillY, mSoft, 0);
        if (mBattery >= 0) {
            pill(canvas, String.format(Locale.getDefault(), "%d%%", mBattery), next + dp(6),
                    pillY, 0x1AFFFFFF, 0);
        }
    }

    private void drawQuickSettings(Canvas canvas, float w, float h) {
        float pad = dp(10);
        float top = (h - dp(68)) / 2;
        mRect.set(pad, top, w - pad, top + dp(9));
        fill(canvas, mRect, dp(4.5f), 0x1FFFFFFF);
        mRect.set(pad, top, pad + (w - 2 * pad) * 0.62f, top + dp(9));
        fill(canvas, mRect, dp(4.5f), mHi);

        float tileW = (w - 2 * pad - dp(5)) / 2;
        for (int i = 0; i < 4; i++) {
            boolean on = i == 0 || i == 3;
            float x = pad + (i % 2) * (tileW + dp(5));
            float y = top + dp(15) + (i / 2) * dp(29);
            mRect.set(x, y, x + tileW, y + dp(24));
            fill(canvas, mRect, dp(12), on ? mHi : 0x1AFFFFFF);
            mFill.setColor(on ? mDeep : 0x99FFFFFF);
            canvas.drawCircle(x + dp(12.5f), y + dp(12), dp(4.5f), mFill);
            mRect.set(x + dp(22), y + dp(10.5f), x + tileW - dp(8), y + dp(13.5f));
            fill(canvas, mRect, dp(1.5f), on ? mDeep : 0x59FFFFFF);
        }
    }

    private void drawNotifications(Canvas canvas, float w, float h) {
        float glow = 0.55f + 0.45f * (float) Math.sin(mPhase * 2 * Math.PI);
        mRect.set(w / 2 - dp(21), h / 2 - dp(36), w / 2 + dp(21), h / 2 + dp(36));
        float[][] halo = {{9, 0.18f}, {6, 0.3f}, {3.5f, 0.5f}};
        for (float[] ring : halo) {
            mLine.setStrokeWidth(dp(ring[0]));
            mLine.setColor(ColorUtils.setAlphaComponent(mHi, Math.round(255 * ring[1] * glow)));
            canvas.drawRoundRect(mRect, dp(11), dp(11), mLine);
        }
        fill(canvas, mRect, dp(11), Color.BLACK);
        mLine.setStrokeWidth(dp(2));
        mLine.setColor(mHi);
        canvas.drawRoundRect(mRect, dp(11), dp(11), mLine);
        float top = mRect.top;
        mRect.set(w / 2 - dp(11), top + dp(7), w / 2 + dp(11), top + dp(12));
        fill(canvas, mRect, dp(2.5f), 0x8CFFFFFF);
        text(canvas, time(), w / 2, top + dp(30), 10,
                Typeface.create("sans-serif-light", Typeface.NORMAL), Color.WHITE,
                Paint.Align.CENTER);
    }

    private void drawTheme(Canvas canvas, float w, float h) {
        mText.setTypeface(Typeface.create(mFont, Typeface.BOLD));
        mText.setTextSize(dp(30));
        float aa = mText.measureText("Aa");
        float total = aa + dp(10) + dp(50) + dp(10) + dp(32);
        float x = (w - total) / 2;
        float cy = h / 2;
        text(canvas, "Aa", x, cy, 30, Typeface.create(mFont, Typeface.BOLD), mHi,
                Paint.Align.LEFT);

        x += aa + dp(10);
        int[] dots = {mHi, mMid, 0xFFF2F2F2};
        for (int i = 0; i < dots.length; i++) {
            float cx = x + dp(11) + i * dp(14);
            mFill.setColor(dots[i]);
            canvas.drawCircle(cx, cy, dp(11), mFill);
            mLine.setStrokeWidth(dp(2));
            mLine.setColor(0xFF111111);
            canvas.drawCircle(cx, cy, dp(10), mLine);
        }

        x += dp(50) + dp(10);
        mRect.set(x, cy - dp(26), x + dp(32), cy + dp(26));
        mFill.setShader(new LinearGradient(mRect.left, mRect.top, mRect.right, mRect.bottom,
                mMid, 0xFF050506, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(mRect, dp(8), dp(8), mFill);
        mFill.setShader(null);
        Path mark = PathParser.createPathFromPathData(MARK);
        mMatrix.setScale(dp(28) / 100f, dp(28) / 100f);
        mMatrix.postTranslate(x + dp(2), cy - dp(10.2f));
        mark.transform(mMatrix);
        mFill.setColor(mHi);
        canvas.drawPath(mark, mFill);
    }

    private void drawLockScreen(Canvas canvas, float w, float h) {
        String date = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(),
                "EEEMMMd"), System.currentTimeMillis()).toString().toUpperCase(Locale.getDefault());
        mText.setLetterSpacing(0.06f);
        text(canvas, date, w / 2, h / 2 - dp(27), 9,
                Typeface.create("sans-serif-medium", Typeface.NORMAL), 0xBFFFFFFF,
                Paint.Align.CENTER);
        mText.setLetterSpacing(0);
        text(canvas, time(), w / 2, h / 2 - dp(6), 30,
                Typeface.create("sans-serif-light", Typeface.NORMAL), Color.WHITE,
                Paint.Align.CENTER);
        float cy = h / 2 + dp(23);
        mLine.setStrokeWidth(dp(5));
        mLine.setColor(ColorUtils.setAlphaComponent(mHi, 0x4D));
        canvas.drawCircle(w / 2, cy, dp(11), mLine);
        mLine.setStrokeWidth(dp(1.5f));
        mLine.setColor(mHi);
        canvas.drawCircle(w / 2, cy, dp(11), mLine);
        icon(canvas, FINGERPRINT, w / 2 - dp(7), cy - dp(7), dp(14), 24, 1.8f, mHi);
    }

    private void drawNavigation(Canvas canvas, float w, float h) {
        float bottom = h - dp(23);
        float left = (w - dp(BARS.length * 5 + (BARS.length - 1) * 3)) / 2;
        for (int i = 0; i < BARS.length; i++) {
            float scale = mAnimator == null ? 1f
                    : 0.55f + 0.45f * Math.abs((float) Math.sin(mPhase * 2 * Math.PI + i * 0.62));
            float x = left + i * dp(8);
            mRect.set(x, bottom - dp(BARS[i]) * scale, x + dp(5), bottom);
            fill(canvas, mRect, dp(2.5f), mHi);
        }
        mRect.set(w / 2 - dp(22), h - dp(15), w / 2 + dp(22), h - dp(10));
        fill(canvas, mRect, dp(2.5f), 0xD9FFFFFF);
    }

    private void drawMisc(Canvas canvas, float w, float h) {
        float pad = dp(10);
        float top = h / 2 - dp(22.5f);
        pill(canvas, getContext().getString(R.string.remixer_preview_pif), pad,
                top + dp(10), mSoft, 1);
        pill(canvas, getContext().getString(R.string.remixer_preview_trickystore), pad,
                top + dp(35), 0x1AFFFFFF, 2);
    }

    /** Draws a pill with optional leading mark (1 shield, 2 dot); returns its right edge. */
    private float pill(Canvas canvas, String label, float x, float cy, int color, int lead) {
        mText.setTypeface(Typeface.DEFAULT_BOLD);
        mText.setTextSize(dp(lead == 0 ? 10 : 9.5f));
        float leadW = lead == 0 ? 0 : dp(lead == 1 ? 11 : 7) + dp(6);
        float width = dp(16) + leadW + mText.measureText(label);
        mRect.set(x, cy - dp(10), x + width, cy + dp(10));
        fill(canvas, mRect, dp(10), color);
        float tx = x + dp(8);
        if (lead == 1) {
            icon(canvas, SHIELD, tx, cy - dp(5.5f), dp(11), 24, 2.4f, mHi);
        } else if (lead == 2) {
            mFill.setColor(mHi);
            canvas.drawCircle(tx + dp(3.5f), cy, dp(3.5f), mFill);
        }
        text(canvas, label, tx + leadW, cy, lead == 0 ? 10 : 9.5f, Typeface.DEFAULT_BOLD,
                Color.WHITE, Paint.Align.LEFT);
        return x + width;
    }

    private void icon(Canvas canvas, String data, float x, float y, float size, float box,
            float stroke, int color) {
        Path path = PathParser.createPathFromPathData(data);
        mMatrix.setScale(size / box, size / box);
        mMatrix.postTranslate(x, y);
        path.transform(mMatrix);
        mLine.setStrokeWidth(stroke * size / box);
        mLine.setColor(color);
        canvas.drawPath(path, mLine);
    }

    /** Draws text vertically centered on cy. */
    private void text(Canvas canvas, String value, float x, float cy, float sizeDp,
            Typeface typeface, int color, Paint.Align align) {
        mText.setTypeface(typeface);
        mText.setTextSize(dp(sizeDp));
        mText.setColor(color);
        mText.setTextAlign(align);
        Paint.FontMetrics m = mText.getFontMetrics();
        canvas.drawText(value, x, cy - (m.ascent + m.descent) / 2, mText);
        mText.setTextAlign(Paint.Align.LEFT);
    }

    private void fill(Canvas canvas, RectF rect, float radius, int color) {
        mFill.setShader(null);
        mFill.setColor(color);
        canvas.drawRoundRect(rect, radius, radius, mFill);
    }

    private String time() {
        return DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "H:mm" : "h:mm",
                System.currentTimeMillis()).toString();
    }

    private float dp(float value) {
        return value * mDp;
    }
}
