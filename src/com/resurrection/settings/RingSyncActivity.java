/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.LinearInterpolator;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Ring sync, RR's easter egg behind Android version: rings close in on the RR mark; tap when one
 * lines up. Perfect hits build a combo and speed things up; three misses end the game.
 */
public class RingSyncActivity extends Activity {
    private static final String PREFS = "ring_sync";
    private static final String KEY_BEST = "best";
    private static final String KEY_VIBRATE = "vibrate";
    private static final String KEY_EMBER = "rr_ember_unlocked";
    private static final int EMBER_SCORE = 50;
    private static final String EMBER = "FF5A1F";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GameView game = new GameView(this);
        setContentView(game);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsController insets = getWindow().getInsetsController();
        if (insets != null) {
            insets.hide(WindowInsets.Type.systemBars());
            insets.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void useEmber() {
        try {
            new AccentController(this).apply("#" + EMBER);
        } catch (SecurityException e) {
            // Left as it was.
        }
    }

    private void androidEgg() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setClassName("android",
                    "com.android.internal.app.PlatLogoActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            // Not on this build.
        }
    }

    private static final class Ring {
        float t; // 0 at spawn, 1 at the target ring

        Ring(float t) {
            this.t = t;
        }
    }

    private final class GameView extends View {
        private static final int TITLE = 0;
        private static final int PLAYING = 1;
        private static final int OVER = 2;
        private static final float START = 1.45f; // spawn radius, in target radii
        private static final float GOOD = 0.09f;
        private static final float PERFECT = 0.035f;

        private final float mDensity = getResources().getDisplayMetrics().density;
        private final int mAccent = getColor(android.R.color.system_accent1_400);
        private final SharedPreferences mPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        private final Drawable mMark = getDrawable(R.drawable.remixer_rr_mark);
        private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mTargetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mBg = new Paint();
        private final List<Ring> mRings = new ArrayList<>();
        private final RectF mEmberButton = new RectF();
        private final RectF mAndroidButton = new RectF();
        private final RectF mVibrateButton = new RectF();
        private final ValueAnimator mClock = ValueAnimator.ofFloat(0f, 1f);

        private int mState = TITLE;
        private int mScore;
        private int mCombo;
        private int mLives;
        private int mBest;
        private boolean mNewBest;
        private boolean mEmberNow;
        private boolean mVibrate;
        private long mLast;
        private float mSpawnIn;
        private float mFlash;
        private String mCallout;

        GameView(Context context) {
            super(context);
            mBest = mPrefs.getInt(KEY_BEST, 0);
            mVibrate = mPrefs.getBoolean(KEY_VIBRATE, true);
            mRingPaint.setStyle(Paint.Style.STROKE);
            mRingPaint.setStrokeWidth(3 * mDensity);
            mRingPaint.setColor(mAccent);
            mGlowPaint.setStyle(Paint.Style.STROKE);
            mGlowPaint.setStrokeWidth(7 * mDensity);
            mGlowPaint.setColor(mAccent);
            mGlowPaint.setMaskFilter(new BlurMaskFilter(8 * mDensity, BlurMaskFilter.Blur.NORMAL));
            mTargetPaint.setStyle(Paint.Style.STROKE);
            mTargetPaint.setStrokeWidth(2 * mDensity);
            mTargetPaint.setColor(0x4DFFFFFF);
            mText.setColor(Color.WHITE);
            mText.setTextAlign(Paint.Align.CENTER);
            mText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            mMark.setTint(mAccent);
            mClock.setDuration(1000);
            mClock.setRepeatCount(ValueAnimator.INFINITE);
            mClock.setInterpolator(new LinearInterpolator());
            mClock.addUpdateListener(a -> step());
            setHapticFeedbackEnabled(true);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            mClock.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            mClock.cancel();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            mBg.setShader(new RadialGradient(w / 2f, h * 0.44f, Math.max(w, h) * 0.6f,
                    0xFF2A0610, 0xFF050203, Shader.TileMode.CLAMP));
        }

        private float target() {
            return Math.min(getWidth(), getHeight()) * 0.31f;
        }

        /** Seconds a ring takes to close in; shorter as the combo grows. */
        private float travel() {
            return Math.max(0.9f, 2.2f - mCombo * 0.06f);
        }

        private void step() {
            long now = System.nanoTime();
            float dt = mLast == 0 ? 0 : (now - mLast) / 1e9f;
            mLast = now;
            mFlash = Math.max(0, mFlash - dt * 2.5f);
            if (mState == PLAYING) {
                mSpawnIn -= dt;
                if (mSpawnIn <= 0) {
                    mRings.add(new Ring(0));
                    mSpawnIn = travel() * 0.55f;
                }
                for (Iterator<Ring> it = mRings.iterator(); it.hasNext(); ) {
                    Ring ring = it.next();
                    ring.t += dt / travel();
                    if (radius(ring) < target() * (1 - GOOD)) {
                        it.remove();
                        miss();
                    }
                }
            }
            invalidate();
        }

        private float radius(Ring ring) {
            return target() * (START - (START - 1) * ring.t);
        }

        private void start() {
            mState = PLAYING;
            mScore = 0;
            mCombo = 0;
            mLives = 3;
            mRings.clear();
            mSpawnIn = 0.4f;
            mCallout = null;
        }

        private void miss() {
            mCombo = 0;
            mLives--;
            mCallout = null;
            if (mVibrate) performHapticFeedback(HapticFeedbackConstants.REJECT);
            if (mLives <= 0) end();
        }

        private void end() {
            mState = OVER;
            mRings.clear();
            mNewBest = mScore > mBest;
            if (mNewBest) {
                mBest = mScore;
                mPrefs.edit().putInt(KEY_BEST, mBest).apply();
            }
            mEmberNow = false;
            if (mScore >= EMBER_SCORE && Settings.System.getIntForUser(getContentResolver(),
                    KEY_EMBER, 0, UserHandle.USER_CURRENT) == 0) {
                Settings.System.putIntForUser(getContentResolver(), KEY_EMBER, 1,
                        UserHandle.USER_CURRENT);
                mEmberNow = true;
            }
        }

        private void tap() {
            Ring closest = null;
            float best = Float.MAX_VALUE;
            for (Ring ring : mRings) {
                float off = Math.abs(radius(ring) / target() - 1);
                if (off < best) {
                    best = off;
                    closest = ring;
                }
            }
            if (closest == null || best > GOOD) {
                miss();
                return;
            }
            mRings.remove(closest);
            boolean perfect = best <= PERFECT;
            mCombo = perfect ? mCombo + 1 : 0;
            mScore += perfect ? 1 + mCombo / 3 : 1;
            mCallout = perfect ? getString(R.string.ring_sync_perfect) : null;
            mFlash = 1f;
            if (mVibrate) {
                performHapticFeedback(perfect ? HapticFeedbackConstants.CONFIRM
                        : HapticFeedbackConstants.CLOCK_TICK);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
            float x = event.getX();
            float y = event.getY();
            if (mState == PLAYING) {
                tap();
            } else if (mAndroidButton.contains(x, y)) {
                androidEgg();
            } else if (mVibrateButton.contains(x, y)) {
                mVibrate = !mVibrate;
                mPrefs.edit().putBoolean(KEY_VIBRATE, mVibrate).apply();
                if (mVibrate) performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            } else if (mState == OVER && mEmberNow && mEmberButton.contains(x, y)) {
                useEmber();
                mEmberNow = false;
            } else {
                start();
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h * 0.44f;
            float r = target();
            canvas.drawRect(0, 0, w, h, mBg);
            canvas.drawCircle(cx, cy, r, mTargetPaint);
            for (Ring ring : mRings) {
                float rr = radius(ring);
                int alpha = (int) (255 * Math.min(1f, ring.t * 2f));
                mGlowPaint.setAlpha(alpha / 2);
                mRingPaint.setAlpha(alpha);
                canvas.drawCircle(cx, cy, rr, mGlowPaint);
                canvas.drawCircle(cx, cy, rr, mRingPaint);
            }
            if (mFlash > 0) {
                mGlowPaint.setAlpha((int) (200 * mFlash));
                canvas.drawCircle(cx, cy, r, mGlowPaint);
            }
            int mw = (int) (r * 0.95f);
            int mh = (int) (mw * 522.7f / 715.3f);
            mMark.setBounds(new Rect((int) cx - mw / 2, (int) cy - mh / 2,
                    (int) cx + mw / 2, (int) cy + mh / 2));
            mMark.draw(canvas);

            float below = cy + r + 64 * mDensity;
            if (mState == PLAYING) {
                text(canvas, String.valueOf(mScore), 24 * mDensity, cx * 0.35f, 56 * mDensity, 1f);
                text(canvas, "×" + mCombo, 20 * mDensity, w - cx * 0.35f, 56 * mDensity, 0.8f);
                text(canvas, "●●●".substring(0, Math.max(0, mLives)), 14 * mDensity, cx,
                        56 * mDensity, 0.7f);
                if (mCallout != null && mFlash > 0) {
                    mText.setColor(mAccent);
                    text(canvas, mCallout, 22 * mDensity, cx, below, mFlash);
                    mText.setColor(Color.WHITE);
                }
            } else {
                if (mState == TITLE) {
                    text(canvas, getString(R.string.ring_sync_name), 26 * mDensity, cx, below, 1f);
                    text(canvas, getString(R.string.ring_sync_tap), 14 * mDensity, cx,
                            below + 32 * mDensity, 0.75f);
                } else {
                    text(canvas, String.valueOf(mScore), 48 * mDensity, cx, below, 1f);
                    text(canvas, mNewBest ? getString(R.string.ring_sync_new_best)
                            : getString(R.string.ring_sync_best, mBest), 14 * mDensity, cx,
                            below + 30 * mDensity, 0.75f);
                    if (mEmberNow) {
                        float top = below + 54 * mDensity;
                        mEmberButton.set(cx - 110 * mDensity, top, cx + 110 * mDensity,
                                top + 44 * mDensity);
                        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                        fill.setColor(0xFFFF5A1F);
                        canvas.drawRoundRect(mEmberButton, 22 * mDensity, 22 * mDensity, fill);
                        text(canvas, getString(R.string.ring_sync_ember) + " · "
                                + getString(R.string.ring_sync_use_ember), 13 * mDensity, cx,
                                top + 27 * mDensity, 1f);
                    }
                    text(canvas, getString(R.string.ring_sync_again), 13 * mDensity, cx,
                            h - 96 * mDensity, 0.6f);
                }
                text(canvas, getString(R.string.ring_sync_best, mBest), 13 * mDensity, cx,
                        56 * mDensity, 0.6f);
                float y = h - 48 * mDensity;
                mAndroidButton.set(cx - 120 * mDensity, y - 24 * mDensity, cx + 120 * mDensity,
                        y + 16 * mDensity);
                text(canvas, getString(R.string.ring_sync_android), 12 * mDensity, cx, y, 0.45f);
                float vy = h - 140 * mDensity;
                mVibrateButton.set(cx - 120 * mDensity, vy - 24 * mDensity, cx + 120 * mDensity,
                        vy + 16 * mDensity);
                text(canvas, getString(mVibrate ? R.string.ring_sync_vibrate_on
                        : R.string.ring_sync_vibrate_off), 12 * mDensity, cx, vy, 0.6f);
            }
        }

        private void text(Canvas canvas, String value, float size, float x, float y, float a) {
            mText.setTextSize(size);
            mText.setAlpha((int) (255 * a));
            canvas.drawText(value, x, y, mText);
        }
    }
}
