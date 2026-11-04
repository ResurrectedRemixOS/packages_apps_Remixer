/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.nightstand;

import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import java.util.Calendar;

/**
 * The Nightstand screen, dim, as the always-on display will show it: the face SystemUI draws for
 * the night, the next alarm under it and the battery ring lower down, at the same fractions of
 * the height as SystemUI's NightstandSection.
 */
public class NightstandPreviewView extends View {
    private static final String AUTHORITY = "com.android.systemui.clockfaces";
    private static final String KEY_FACE = "rr_nightstand_face";
    private static final String KEY_BRIGHTNESS = "rr_nightstand_brightness";
    private static final float SCREEN_RATIO = 19.5f / 9f;
    private static final float CLOCK_TOP = 0.12f;
    private static final float ALARM_TOP = 0.46f;
    private static final float BATTERY_TOP = 0.62f;
    private static final int RENDER_WIDTH = 720;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBitmap = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path mBolt = new Path();
    private final RectF mRect = new RectF();
    private final RectF mScreen = new RectF();
    private final float mDensity;
    private final Drawable mAlarmIcon;

    private Bitmap mFace;
    private int mStyle = Integer.MIN_VALUE;
    private float mDim = 1f;
    private String mAlarm;
    private int mLevel = 100;
    private boolean mCharging;

    private final ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            refresh();
        }
    };

    public NightstandPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDensity = getResources().getDisplayMetrics().density;
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mText.setFontFeatureSettings("tnum");
        mAlarmIcon = context.getDrawable(android.R.drawable.ic_lock_idle_alarm);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ContentResolver cr = getContext().getContentResolver();
        cr.registerContentObserver(Settings.System.getUriFor(KEY_FACE), false, mObserver,
                UserHandle.USER_ALL);
        cr.registerContentObserver(Settings.System.getUriFor(KEY_BRIGHTNESS), false, mObserver,
                UserHandle.USER_ALL);
        cr.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE), false,
                mObserver, UserHandle.USER_ALL);
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onDetachedFromWindow();
    }

    /** Re-reads the face, the brightness cap, the next alarm and the battery. */
    public void refresh() {
        ContentResolver cr = getContext().getContentResolver();
        int cap = Settings.System.getIntForUser(cr, KEY_BRIGHTNESS, 40, UserHandle.USER_CURRENT);
        mDim = 0.45f + 0.55f * Math.max(1, Math.min(255, cap)) / 255f;
        int night = Settings.System.getIntForUser(cr, KEY_FACE, -1, UserHandle.USER_CURRENT);
        int current = Settings.Secure.getIntForUser(cr,
                Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE, 0, UserHandle.USER_CURRENT);
        // Only a custom face swaps for another custom face; the system clock stays.
        int style = night > 0 && current > 0 ? night : current;

        AlarmManager alarms = getContext().getSystemService(AlarmManager.class);
        AlarmManager.AlarmClockInfo next = alarms != null ? alarms.getNextAlarmClock() : null;
        if (next != null) {
            Calendar at = Calendar.getInstance();
            at.setTimeInMillis(next.getTriggerTime());
            mAlarm = DateFormat.format(DateFormat.is24HourFormat(getContext())
                    ? "EEE HH:mm" : "EEE h:mm a", at).toString();
        } else {
            mAlarm = null;
        }
        Intent battery = getContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            mLevel = scale > 0 ? level * 100 / scale : level;
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            mCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }
        if (style != mStyle) {
            mStyle = style;
            mFace = null;
            if (style > 0) render(style);
        }
        invalidate();
    }

    /** The face as SystemUI draws it, cropped to its ink. */
    private void render(int style) {
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                Bundle extras = new Bundle();
                extras.putInt("style", style);
                extras.putInt("width", RENDER_WIDTH);
                extras.putFloat("height_fraction", 0.5f);
                Bundle result = getContext().getContentResolver()
                        .call(AUTHORITY, "render", null, extras);
                byte[] png = result != null ? result.getByteArray("png") : null;
                if (png != null) {
                    bitmap = crop(BitmapFactory.decodeByteArray(png, 0, png.length));
                }
            } catch (Exception e) {
                bitmap = null;
            }
            final Bitmap done = bitmap;
            mHandler.post(() -> {
                if (style != mStyle) return;
                mFace = done;
                invalidate();
            });
        }, "NightstandFace").start();
    }

    private static Bitmap crop(Bitmap source) {
        int left = source.getWidth(), top = source.getHeight(), right = 0, bottom = 0;
        int[] row = new int[source.getWidth()];
        for (int y = 0; y < source.getHeight(); y++) {
            source.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int x = 0; x < row.length; x++) {
                if ((row[x] >>> 24) > 16) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right <= left || bottom <= top) return source;
        return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float width = Math.min(getWidth() - getPaddingStart() - getPaddingEnd(),
                height / SCREEN_RATIO);
        height = width * SCREEN_RATIO;
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        mScreen.set(left, top, left + width, top + height);
        float radius = 22 * mDensity;
        mFill.setColor(Color.BLACK);
        canvas.drawRoundRect(mScreen, radius, radius, mFill);
        mStroke.setStrokeWidth(mDensity);
        mStroke.setColor(0x33FFFFFF);
        canvas.drawRoundRect(mScreen, radius, radius, mStroke);

        // One preview pixel per lock screen dp.
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float s = width / (dm.widthPixels / dm.density);
        int layer = canvas.saveLayerAlpha(mScreen, Math.round(255 * mDim));
        canvas.clipRect(mScreen);
        float clockBottom = drawClock(canvas, s);
        drawAlarm(canvas, s, clockBottom);
        drawBattery(canvas, s);
        canvas.restoreToCount(layer);
    }

    /** The face, or the system clock's big digits; returns where it ends. */
    private float drawClock(Canvas canvas, float s) {
        float width = mScreen.width();
        float y = mScreen.top + mScreen.height() * CLOCK_TOP;
        if (mFace != null) {
            float w = Math.min(width * 0.82f, mFace.getWidth() * s * 0.5f);
            float h = w * mFace.getHeight() / mFace.getWidth();
            float x = mScreen.left + (width - w) / 2f;
            mRect.set(x, y, x + w, y + h);
            canvas.drawBitmap(mFace, null, mRect, mBitmap);
            return y + h;
        }
        if (mStyle > 0) return y + 80 * s;
        Calendar now = Calendar.getInstance();
        String time = DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm",
                now).toString();
        mText.setColor(Color.WHITE);
        mText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mText.setTextSize(width * 0.3f);
        float baseline = y - mText.ascent();
        canvas.drawText(time, mScreen.centerX(), baseline, mText);
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return baseline + mText.descent() * 0.6f;
    }

    private void drawAlarm(Canvas canvas, float s, float clockBottom) {
        if (mAlarm == null) return;
        float y = mStyle > 0 && mFace != null ? clockBottom + 16 * s
                : mScreen.top + mScreen.height() * ALARM_TOP;
        mText.setColor(Color.WHITE);
        mText.setTextSize(13 * s);
        float icon = 18 * s;
        float pad = 10 * s;
        float textWidth = mText.measureText(mAlarm);
        float w = pad + icon + pad / 2f + textWidth + pad;
        float h = icon + pad;
        float x = mScreen.centerX() - w / 2f;
        mRect.set(x, y, x + w, y + h);
        mFill.setColor(0x1AFFFFFF);
        canvas.drawRoundRect(mRect, 20 * s, 20 * s, mFill);
        mStroke.setStrokeWidth(Math.max(1f, s));
        mStroke.setColor(0x2AFFFFFF);
        canvas.drawRoundRect(mRect, 20 * s, 20 * s, mStroke);
        if (mAlarmIcon != null) {
            int ix = Math.round(x + pad);
            int iy = Math.round(y + pad / 2f);
            mAlarmIcon.setBounds(ix, iy, Math.round(ix + icon), Math.round(iy + icon));
            mAlarmIcon.setTint(getContext().getColor(android.R.color.system_accent1_300));
            mAlarmIcon.draw(canvas);
        }
        mText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(mAlarm, x + pad + icon + pad / 2f,
                y + h / 2f - (mText.descent() + mText.ascent()) / 2f, mText);
        mText.setTextAlign(Paint.Align.CENTER);
    }

    /** The ring as it looks on the always-on display: white, the track faint. */
    private void drawBattery(Canvas canvas, float s) {
        float size = 128 * s;
        float stroke = Math.max(2f, size * 0.075f);
        float cx = mScreen.centerX();
        float cy = mScreen.top + mScreen.height() * BATTERY_TOP + size / 2f;
        float r = size / 2f - stroke;
        mRect.set(cx - r, cy - r, cx + r, cy + r);
        mStroke.setStrokeWidth(stroke);
        mStroke.setColor(0x40FFFFFF);
        canvas.drawArc(mRect, 0f, 360f, false, mStroke);
        mStroke.setColor(Color.WHITE);
        canvas.drawArc(mRect, -90f, 360f * mLevel / 100f, false, mStroke);
        mText.setColor(Color.WHITE);
        mText.setTextSize(size * 0.22f);
        canvas.drawText(mLevel + "%", cx, cy - (mText.descent() + mText.ascent()) / 2f, mText);
        if (mCharging) {
            float b = size * 0.11f;
            mBolt.rewind();
            mBolt.moveTo(cx + b * 0.1f, cy + r * 0.42f);
            mBolt.lineTo(cx - b * 0.5f, cy + r * 0.42f + b * 1.4f);
            mBolt.lineTo(cx, cy + r * 0.42f + b * 1.4f);
            mBolt.lineTo(cx - b * 0.1f, cy + r * 0.42f + b * 2.6f);
            mBolt.lineTo(cx + b * 0.5f, cy + r * 0.42f + b * 1.1f);
            mBolt.lineTo(cx, cy + r * 0.42f + b * 1.1f);
            mBolt.close();
            mFill.setColor(Color.WHITE);
            canvas.drawPath(mBolt, mFill);
        }
    }
}
