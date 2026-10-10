/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.about;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.android.settings.R;

/**
 * The About phone hero: an accent gradient with a dot texture, the RR mark inside rings with a
 * comet circling it, and the device name, model and build chips on top.
 */
public class AboutHeroView extends FrameLayout {

    private static final long SPIN_MS = 9000;
    private static final long ORBIT_MS = 14000;
    private static final long PULSE_MS = 2400;
    private static final float COMET_SWEEP = 109f;

    private final Paint mBackground = new Paint();
    private final Paint mDots = new Paint();
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mComet = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOrbit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mCometBounds = new RectF();
    private final Drawable mMark;
    private final float mDp;
    private ValueAnimator mSpin;
    private boolean mVisible;
    private float mCenterX;
    private float mCenterY;

    public AboutHeroView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mDp = AboutUi.dp(context, 1);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 32 * mDp);
            }
        });
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(1.5f * mDp);
        mRing.setColor(0xFFFFFFFF);
        mGlass.setColor(0x0FFFFFFF);
        mComet.setStyle(Paint.Style.STROKE);
        mComet.setStrokeWidth(3 * mDp);
        mComet.setStrokeCap(Paint.Cap.ROUND);
        mOrbit.setColor(0xFFFFFFFF);
        mMark = context.getDrawable(R.drawable.ic_settings_remixer_filled).mutate();
        mMark.setTint(0xFFFFFFFF);
        // The page is new, so private values start hidden again.
        AboutPrivacy.reset();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Context context = getContext();
        ((TextView) findViewById(R.id.rr_about_device)).setText(AboutProps.deviceName());
        ((TextView) findViewById(R.id.rr_about_model)).setText(AboutProps.modelLine(context));
        LinearLayout chips = findViewById(R.id.rr_about_chips);
        chips.addView(chip(context.getString(R.string.rr_about_android,
                Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY), false));
        chips.addView(chip(AboutProps.releaseLabel(context), AboutProps.isOfficial()));
        String variant = AboutProps.variantLabel(context);
        if (variant != null) {
            chips.addView(chip(variant, false));
        }
    }

    private TextView chip(String text, boolean check) {
        Context context = getContext();
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(13);
        chip.setTypeface(Typeface.create(Typeface.DEFAULT, 700, false));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding((int) (12 * mDp), 0, (int) (12 * mDp), 0);
        chip.setBackground(AboutUi.round(context, 0x29FFFFFF, 15));
        if (check) {
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.rr_about_ic_check,
                    0, 0, 0);
            chip.setCompoundDrawablePadding((int) (6 * mDp));
        }
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, (int) (30 * mDp));
        params.setMarginEnd((int) (8 * mDp));
        chip.setLayoutParams(params);
        return chip;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) {
            return;
        }
        Context context = getContext();
        int start = AboutUi.color(context, android.R.color.system_accent1_500);
        int middle = AboutUi.color(context, android.R.color.system_accent1_800);
        int end = ColorUtils.blendARGB(
                AboutUi.color(context, android.R.color.system_accent1_900), 0xFF000000, 0.5f);
        mBackground.setShader(new RadialGradient(0.88f * w, 0.06f * h,
                (float) Math.hypot(w, h), new int[] {start, middle, end},
                new float[] {0f, 0.46f, 1f}, Shader.TileMode.CLAMP));

        // Dots every 14dp, fading out from the top right corner.
        int tile = Math.max(1, Math.round(14 * mDp));
        Bitmap dot = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888);
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(0x1CFFFFFF);
        new Canvas(dot).drawCircle(tile / 2f, tile / 2f, mDp, dotPaint);
        Shader dots = new BitmapShader(dot, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        double angle = Math.toRadians(215);
        float dx = (float) Math.sin(angle);
        float dy = (float) -Math.cos(angle);
        float half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2f;
        Shader fade = new LinearGradient(w / 2f - dx * half, h / 2f - dy * half,
                w / 2f + dx * half, h / 2f + dy * half, new int[] {0xFFFFFFFF, 0x00FFFFFF},
                new float[] {0f, 0.62f}, Shader.TileMode.CLAMP);
        mDots.setShader(new ComposeShader(fade, dots, PorterDuff.Mode.SRC_IN));

        mCenterX = w - 89 * mDp;
        mCenterY = 97 * mDp;
        float r = 100 * mDp;
        mCometBounds.set(mCenterX - r, mCenterY - r, mCenterX + r, mCenterY + r);
        float tail = 1f - COMET_SWEEP / 360f;
        mComet.setShader(new SweepGradient(mCenterX, mCenterY,
                new int[] {0x00FFFFFF, 0x00FFFFFF, 0xF2FFFFFF}, new float[] {0f, tail, 1f}));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, mBackground);
        canvas.drawRect(0, 0, w, h, mDots);

        mRing.setAlpha(18);
        canvas.drawCircle(mCenterX, mCenterY, 128 * mDp, mRing);
        mRing.setAlpha(28);
        canvas.drawCircle(mCenterX, mCenterY, 100 * mDp, mRing);
        canvas.drawCircle(mCenterX, mCenterY, 72 * mDp, mGlass);
        mRing.setAlpha(46);
        canvas.drawCircle(mCenterX, mCenterY, 72 * mDp, mRing);

        long now = SystemClock.uptimeMillis();
        canvas.save();
        canvas.rotate(360f * (now % SPIN_MS) / SPIN_MS, mCenterX, mCenterY);
        canvas.drawArc(mCometBounds, 360f - COMET_SWEEP, COMET_SWEEP, false, mComet);
        canvas.restore();

        double orbit = Math.toRadians(-360.0 * (now % ORBIT_MS) / ORBIT_MS - 90);
        float pulse = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * (now % PULSE_MS) / PULSE_MS);
        mOrbit.setAlpha(Math.round(255 * (0.55f + 0.45f * pulse)));
        canvas.drawCircle(mCenterX + (float) Math.cos(orbit) * 128 * mDp,
                mCenterY + (float) Math.sin(orbit) * 128 * mDp, 4 * mDp, mOrbit);

        int mark = Math.round(32 * mDp);
        mMark.setBounds(Math.round(mCenterX) - mark, Math.round(mCenterY) - mark,
                Math.round(mCenterX) + mark, Math.round(mCenterY) + mark);
        mMark.draw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSpin();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSpin();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        mVisible = isVisible;
        updateSpin();
    }

    private void updateSpin() {
        boolean run = mVisible && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled();
        if (!run) {
            stopSpin();
            return;
        }
        if (mSpin != null) {
            return;
        }
        // Drives redraws; the angles come from the clock so the motion stays smooth.
        mSpin = ValueAnimator.ofFloat(0f, 1f);
        mSpin.setDuration(SPIN_MS);
        mSpin.setRepeatCount(ValueAnimator.INFINITE);
        mSpin.setInterpolator(new LinearInterpolator());
        mSpin.addUpdateListener(animation -> invalidate());
        mSpin.start();
    }

    private void stopSpin() {
        if (mSpin != null) {
            mSpin.cancel();
            mSpin = null;
        }
    }
}
