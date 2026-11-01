/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.settings.R;

/**
 * A Quick settings mock in one of the SystemUI QS styles. Sizes are SystemUI dp on a 340 wide
 * panel, scaled to the view. Mini mode drops the text for list thumbnails.
 */
public class QsStylePreviewView extends View {
    public static final int STOCK = 0;
    public static final int REMIX = 1;
    public static final int COMPACT = 2;
    public static final int GLASS = 3;
    public static final int CIRCLES = 4;
    public static final int CLASSIC = 5;
    public static final int CARDS = 6;
    public static final int NEON = 8;
    public static final int BENTO = 9;
    public static final int SHAPES = 10;
    public static final int BLUEPRINT = 11;
    public static final int DUO = 12;
    public static final int SOFT = 13;

    private static final float WIDTH = 340f;
    // The big preview keeps this much dp at the bottom clear for its label chip.
    private static final float LABEL_SPACE = 52f;
    private static final float FADE = 32f;
    // Clock, date and player on the lockscreen, which have to fit above the label.
    private static final float LOCK_HEIGHT = 264f;
    private static final int[] ICONS = {
        R.drawable.remixer_qs_preview_wifi, R.drawable.remixer_qs_preview_bluetooth,
        R.drawable.remixer_qs_preview_flashlight, R.drawable.remixer_qs_preview_dnd,
        R.drawable.remixer_qs_preview_rotate, R.drawable.remixer_qs_preview_dark,
        R.drawable.remixer_qs_preview_hotspot, R.drawable.remixer_qs_preview_airplane,
        R.drawable.remixer_qs_preview_data_saver, R.drawable.remixer_qs_preview_nfc,
        R.drawable.remixer_qs_preview_sync, R.drawable.remixer_qs_preview_qr,
    };
    private static final boolean[] ON = {
        true, true, false, false, true, true, false, false, false, true, true, false,
    };
    // Internet and Bluetooth are wide where the style has wide tiles.
    private static final int WIDE = 2;

    private static final int[] LOCK_ICONS = {
        R.drawable.remixer_qs_preview_music, R.drawable.remixer_qs_preview_phone,
        R.drawable.remixer_qs_preview_prev, R.drawable.remixer_qs_preview_next,
        R.drawable.remixer_qs_preview_like, R.drawable.remixer_qs_preview_pause,
    };
    private static final int L_MUSIC = 0;
    private static final int L_PHONE = 1;
    private static final int L_PREV = 2;
    private static final int L_NEXT = 3;
    private static final int L_LIKE = 4;
    private static final int L_PAUSE = 5;
    // Dark base under see-through cards on the lockscreen, as SystemUI draws it.
    private static final int LOCK_BASE = 0x9E0E0F11;

    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final int SURFACE = 0x1AFFFFFF;
    private static final int ON_SURFACE = 0xFFEDEEF0;
    private static final int MUTED = 0xFFB4B8BE;
    private static final int SOFT_SURFACE = 0xFF1C1716;
    // Lobes an on tile grows in Shapes, and how deep, as SystemUI picks them.
    private static final int[] LOBES = {8, 4, 6, 12, 5};
    private static final float[] DEPTHS = {.07f, .13f, .09f, .045f, .1f};

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFade = new Paint();
    private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final android.graphics.Path mPath = new android.graphics.Path();
    private final Drawable[] mIcons = new Drawable[ICONS.length];
    private final Drawable[] mLockIcons = new Drawable[LOCK_ICONS.length];
    private final String[] mLabels;
    private final String mOn;
    private final String mOff;
    private final int mPrimary;
    private final int mOnPrimary;
    private final int mAccent500;
    private final int mAccent600;
    private final int mAccent700;
    private final int mAccent900;
    // The bright accent the newer styles fill with, and the ink on it.
    private final int mVivid;
    private final int mVividInk;
    private final float mDensity;
    private int mStyle;
    private boolean mMini;
    private boolean mLockscreen;
    private boolean mCardHidden;
    private float mScale;

    public QsStylePreviewView(Context context) {
        this(context, null);
    }

    public QsStylePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDensity = getResources().getDisplayMetrics().density;
        mFade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        mPrimary = context.getColor(android.R.color.system_accent1_200);
        mOnPrimary = context.getColor(android.R.color.system_accent1_800);
        mAccent500 = context.getColor(android.R.color.system_accent1_500);
        mAccent600 = context.getColor(android.R.color.system_accent1_600);
        mAccent700 = context.getColor(android.R.color.system_accent1_700);
        mAccent900 = context.getColor(android.R.color.system_accent1_900);
        mVivid = context.getColor(android.R.color.system_primary_dark);
        mVividInk = context.getColor(android.R.color.system_on_primary_dark);
        mLabels = getResources().getStringArray(R.array.remixer_qs_preview_labels);
        mOn = getResources().getString(R.string.remixer_qs_preview_on);
        mOff = getResources().getString(R.string.remixer_qs_preview_off);
        for (int i = 0; i < ICONS.length; i++) {
            Drawable icon = context.getDrawable(ICONS[i]);
            mIcons[i] = icon != null ? icon.mutate() : null;
        }
        for (int i = 0; i < LOCK_ICONS.length; i++) {
            Drawable icon = context.getDrawable(LOCK_ICONS[i]);
            mLockIcons[i] = icon != null ? icon.mutate() : null;
        }
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        (mMini ? 14 : 28) * mDensity);
            }
        });
        setClipToOutline(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setStyle(int style) {
        if (mStyle != style) {
            mStyle = style;
            invalidate();
        }
    }

    /** Draws the lockscreen media card in the style instead of the QS panel. */
    public void setLockscreen(boolean lockscreen) {
        mLockscreen = lockscreen;
        invalidate();
    }

    /** Leaves the media card out of the lockscreen, for a player that is off. */
    public void setCardHidden(boolean hidden) {
        mCardHidden = hidden;
        invalidate();
    }

    public void setMini(boolean mini) {
        mMini = mini;
        invalidateOutline();
        invalidate();
    }

    private float d(float value) {
        return value * mScale;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (mLockscreen) {
            drawLockscreen(canvas, w, h);
            return;
        }
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mStyle == SOFT ? SOFT_SURFACE : 0xFF0A0B0D);
        canvas.drawRect(0, 0, w, h, mPaint);
        // Soft stays flat; its tiles need the panel's own color around them.
        if (mStyle != SOFT) {
            final int glow = (mAccent500 & 0x00FFFFFF) | 0x66000000;
            mPaint.setShader(new RadialGradient(w * .2f, h * .08f, Math.max(w, h) * .7f,
                    glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, mPaint);
            mPaint.setShader(null);
        }

        final float pad = (mMini ? 6 : 14) * mDensity;
        mScale = (w - pad * 2) / WIDTH;
        final float clear = mMini ? h - pad : h - LABEL_SPACE * mDensity;
        if (!mMini) {
            canvas.saveLayer(0, 0, w, clear, null);
        }
        canvas.save();
        canvas.translate(pad, pad);
        float y = drawSlider(canvas) + 12;
        drawTiles(canvas, y, (clear - pad) / mScale);
        canvas.restore();
        if (!mMini) {
            // Tiles fade out above the label instead of running under it.
            final float fade = clear - FADE * mDensity;
            mFade.setShader(new LinearGradient(0, fade, 0, clear, Color.BLACK,
                    Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawRect(0, fade, w, clear, mFade);
            canvas.restore();
        }
    }

    private float drawSlider(Canvas canvas) {
        switch (mStyle) {
            case REMIX: {
                mRect.set(0, 0, d(WIDTH), d(72));
                mPaint.setShader(new LinearGradient(0, 0, mRect.right, mRect.bottom,
                        mAccent600, mAccent900, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(mRect, d(26), d(26), mPaint);
                mPaint.setShader(null);
                pill(canvas, 14, 14, WIDTH - 28, 44, 22, 0x29FFFFFF, Color.WHITE);
                return 72;
            }
            case COMPACT:
                pill(canvas, 0, 0, WIDTH, 30, 15, SURFACE, mPrimary);
                return 30;
            case GLASS:
                pill(canvas, 0, 0, WIDTH, 40, 20, 0x0DFFFFFF, (mPrimary & 0x00FFFFFF) | 0x8C000000);
                outline(canvas, 0, 0, WIDTH, 40, 20, 0x29FFFFFF);
                return 40;
            case CLASSIC: {
                mPaint.setColor(mPrimary);
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(d(2.2f));
                canvas.drawCircle(d(12), d(12), d(5), mPaint);
                mPaint.setStyle(Paint.Style.FILL);
                pill(canvas, 34, 10, WIDTH - 34, 4, 2, 0x2EFFFFFF, mPrimary);
                canvas.drawCircle(d(34 + (WIDTH - 34) * .62f), d(12), d(10), mPaint);
                return 24;
            }
            case NEON: {
                final float knob = 34 + (WIDTH - 34) * .62f;
                sun(canvas, 12, 12, mVivid);
                pill(canvas, 34, 9, WIDTH - 34, 6, 3, 0x1AFFFFFF, mVivid);
                mGlow.setColor(alpha(mVivid, .6f));
                mGlow.setMaskFilter(new BlurMaskFilter(d(5), BlurMaskFilter.Blur.NORMAL));
                mRect.set(d(34), d(9), d(knob), d(15));
                canvas.drawRoundRect(mRect, d(3), d(3), mGlow);
                mPaint.setColor(0xFF050307);
                canvas.drawCircle(d(knob), d(12), d(10), mPaint);
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(d(2));
                mPaint.setColor(mVivid);
                canvas.drawCircle(d(knob), d(12), d(10), mPaint);
                mPaint.setStyle(Paint.Style.FILL);
                return 24;
            }
            case BENTO:
                pill(canvas, 0, 0, WIDTH, 52, 22, 0x1AFFFFFF, mVivid);
                return 52;
            case SHAPES: {
                final float split = WIDTH * .62f;
                mPaint.setColor(mVivid);
                round(canvas, 0, 2, split - 6, 52, 18);
                round(canvas, split - 2, 0, split + 2, 54, 2);
                mPaint.setColor(0x2EFFFFFF);
                round(canvas, split + 6, 2, WIDTH, 52, 18);
                return 54;
            }
            case BLUEPRINT: {
                mPaint.setColor(0x40FFFFFF);
                for (int i = 0; i <= 10; i++) {
                    final float x = WIDTH * i / 10f;
                    round(canvas, x - .5f, i % 5 == 0 ? 10 : 16, x + .5f, 24, 0);
                }
                round(canvas, 0, 23.5f, WIDTH, 24.5f, 0);
                mPaint.setColor(mVivid);
                round(canvas, 0, 23, WIDTH * .62f, 25, 0);
                round(canvas, WIDTH * .62f - 1, 0, WIDTH * .62f + 1, 24, 0);
                return 26;
            }
            case DUO: {
                mPaint.setColor(mVivid);
                segment(canvas, 0, 0, 44, 44, 14, 5);
                sun(canvas, 22, 22, mVividInk);
                final float split = 47 + (WIDTH - 47) * .62f;
                mPaint.setColor(0x12FFFFFF);
                segment(canvas, 47, 0, WIDTH, 44, 5, 14);
                mPaint.setColor(alpha(mVivid, .3f));
                segment(canvas, 47, 0, split, 44, 5, 0);
                mPaint.setColor(mVivid);
                round(canvas, split - 2, 10, split + 2, 34, 2);
                return 44;
            }
            case SOFT: {
                final float knob = 34 + (WIDTH - 34) * .62f;
                sun(canvas, 12, 15, 0xB3EDEEF0);
                mPaint.setColor(0xFF110D0C);
                round(canvas, 34, 6, WIDTH, 24, 9);
                mPaint.setColor(mVivid);
                round(canvas, 36, 8, knob, 22, 7);
                mGlow.setColor(0xE60B0908);
                mGlow.setMaskFilter(new BlurMaskFilter(d(5), BlurMaskFilter.Blur.NORMAL));
                canvas.drawCircle(d(knob + 3), d(18), d(15), mGlow);
                mPaint.setColor(0xFF2A2321);
                canvas.drawCircle(d(knob), d(15), d(15), mPaint);
                mPaint.setColor(mVivid);
                canvas.drawCircle(d(knob), d(15), d(4), mPaint);
                return 30;
            }
            default:
                pill(canvas, 0, 0, WIDTH, 44, 22, SURFACE, mPrimary);
                return 44;
        }
    }

    /** A sun: a ring, as Classic draws it. */
    private void sun(Canvas canvas, float cx, float cy, int color) {
        mPaint.setColor(color);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(d(2.2f));
        canvas.drawCircle(d(cx), d(cy), d(5), mPaint);
        mPaint.setStyle(Paint.Style.FILL);
    }

    /** Fills a rounded rect in the current paint color. */
    private void round(Canvas canvas, float left, float top, float right, float bottom, float r) {
        mRect.set(d(left), d(top), d(right), d(bottom));
        canvas.drawRoundRect(mRect, d(r), d(r), mPaint);
    }

    /** Fills a Duo segment: radius start on the left corners, end on the right ones. */
    private void segment(Canvas canvas, float left, float top, float right, float bottom,
            float start, float end) {
        mRect.set(d(left), d(top), d(right), d(bottom));
        mPath.reset();
        mPath.addRoundRect(mRect, new float[] {d(start), d(start), d(end), d(end), d(end),
                d(end), d(start), d(start)}, android.graphics.Path.Direction.CW);
        canvas.drawPath(mPath, mPaint);
    }

    /** Sets mPath to a circle growing lobes, like a Shapes tile. */
    private void blob(float cx, float cy, float radius, int lobes, float depth) {
        mPath.reset();
        final float base = radius / (1 + depth);
        for (int i = 0; i < 96; i++) {
            final double angle = i * 2 * Math.PI / 96;
            final float r = (float) (base * (1 + depth * Math.cos(lobes * angle)));
            final float x = d(cx + r * (float) Math.sin(angle));
            final float y = d(cy - r * (float) Math.cos(angle));
            if (i == 0) {
                mPath.moveTo(x, y);
            } else {
                mPath.lineTo(x, y);
            }
        }
        mPath.close();
    }

    /** A slider track at 62%. */
    private void pill(Canvas canvas, float x, float y, float width, float height, float r,
            int track, int fill) {
        mPaint.setColor(track);
        mRect.set(d(x), d(y), d(x + width), d(y + height));
        canvas.drawRoundRect(mRect, d(r), d(r), mPaint);
        mPaint.setColor(fill);
        mRect.set(d(x), d(y), d(x + width * .62f), d(y + height));
        canvas.drawRoundRect(mRect, d(r), d(r), mPaint);
    }

    private void outline(Canvas canvas, float x, float y, float width, float height, float r,
            int color) {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(d(1.5f));
        mPaint.setColor(color);
        mRect.set(d(x), d(y), d(x + width), d(y + height));
        mRect.inset(d(.75f), d(.75f));
        canvas.drawRoundRect(mRect, d(r), d(r), mPaint);
        mPaint.setStyle(Paint.Style.FILL);
    }

    /** Colors of the lockscreen media card, matching SystemUI's QsStyleMedia. */
    private final class Card {
        int base = Color.TRANSPARENT;
        int bg = SURFACE;
        int border = Color.TRANSPARENT;
        int text = ON_SURFACE;
        int sub = 0xB8EDEEF0;
        int accent = mPrimary;
        int onAccent = mOnPrimary;
        int play = mPrimary;
        int playInk = mOnPrimary;
        int playBorder = Color.TRANSPARENT;
        int track = 0x47EDEEF0;
        float radius = 26;
        float artRadius = 14;
        boolean thumb = true;
        boolean art;

        Card() {
            switch (mStyle) {
                case STOCK:
                    thumb = false;
                    art = true;
                    bg = 0x9E140A1E;
                    radius = 32;
                    accent = play = 0xFFFFD9CC;
                    onAccent = playInk = 0xFF4A1B10;
                    track = 0x4DFFFFFF;
                    break;
                case REMIX:
                    bg = 0xFF1E2024;
                    radius = 28;
                    play = mAccent600;
                    playInk = Color.WHITE;
                    break;
                case COMPACT:
                    base = LOCK_BASE;
                    bg = 0x17FFFFFF;
                    radius = 32;
                    artRadius = 22;
                    break;
                case GLASS:
                    base = LOCK_BASE;
                    bg = 0x09FFFFFF;
                    border = 0x21FFFFFF;
                    radius = 24;
                    play = alpha(mPrimary, .2f);
                    playInk = mPrimary;
                    playBorder = mPrimary;
                    break;
                case CLASSIC:
                    base = LOCK_BASE;
                    bg = 0x0FFFFFFF;
                    radius = 16;
                    artRadius = 10;
                    play = Color.TRANSPARENT;
                    playInk = mPrimary;
                    playBorder = mPrimary;
                    break;
                case NEON:
                    bg = 0xFF0B080C;
                    border = alpha(mVivid, .9f);
                    radius = 24;
                    accent = playInk = playBorder = mVivid;
                    onAccent = mVividInk;
                    play = alpha(mVivid, .12f);
                    break;
                case BENTO:
                case SHAPES:
                case DUO:
                    base = LOCK_BASE;
                    bg = mStyle == DUO ? 0x12FFFFFF : SURFACE;
                    radius = mStyle == SHAPES ? 32 : mStyle == DUO ? 22 : 26;
                    artRadius = mStyle == BENTO ? 18 : 14;
                    accent = play = mVivid;
                    onAccent = playInk = mVividInk;
                    break;
                case BLUEPRINT:
                    base = LOCK_BASE;
                    bg = Color.TRANSPARENT;
                    border = 0x2EFFFFFF;
                    radius = 10;
                    artRadius = 6;
                    accent = playInk = playBorder = mVivid;
                    onAccent = mVividInk;
                    play = alpha(mVivid, .08f);
                    break;
                case SOFT:
                    bg = SOFT_SURFACE;
                    radius = 26;
                    artRadius = 16;
                    accent = playInk = mVivid;
                    onAccent = mVividInk;
                    play = SOFT_SURFACE;
                    playBorder = 0x14FFFFFF;
                    break;
                default:
                    base = LOCK_BASE;
                    break;
            }
        }
    }

    private void drawLockscreen(Canvas canvas, int w, int h) {
        final float big = Math.max(w, h);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setShader(new LinearGradient(0, 0, w * .4f, h, 0xFF3B2440, 0xFF101527,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mPaint);
        mPaint.setShader(new RadialGradient(w * .25f, h * .15f, big * .8f,
                alpha(mAccent500, .75f), Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mPaint);
        mPaint.setShader(new RadialGradient(w * .85f, h * .9f, big * .7f,
                0xCC2E5BD8, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mPaint);
        mPaint.setShader(new LinearGradient(0, 0, 0, h, 0x26060709, 0x8C060709,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mPaint);
        mPaint.setShader(null);

        final Card card = new Card();
        if (mMini) {
            if (mCardHidden) {
                return;
            }
            final float cardH = h * .5f;
            mRect.set(w * .12f, (h - cardH) / 2, w * .88f, (h + cardH) / 2);
            final float r = Math.min(card.radius * cardH / 150f * 2.2f, cardH / 2);
            cardFill(canvas, card, r);
            final float pillH = cardH * .4f;
            final float pillW = cardH * .56f;
            final float right = mRect.right - 4 * mDensity;
            mRect.set(right - pillW, (h - pillH) / 2, right, (h + pillH) / 2);
            pillFill(canvas, card.play, card.playBorder, pillH / 2, mDensity);
            return;
        }

        final float pad = 14 * mDensity;
        final float clear = h - LABEL_SPACE * mDensity;
        mScale = Math.min((w - pad * 2) / WIDTH, (clear - pad) / LOCK_HEIGHT);
        canvas.save();
        canvas.translate((w - d(WIDTH)) / 2, pad);
        final java.util.Date now = new java.util.Date();
        text(canvas, android.text.format.DateFormat.getTimeFormat(getContext()).format(now),
                WIDTH / 2, 64, WIDTH, 60, LIGHT, 0xFFF4F5F7, true);
        text(canvas, android.text.format.DateFormat.format(
                android.text.format.DateFormat.getBestDateTimePattern(
                        java.util.Locale.getDefault(), "EEEMMMd"), now).toString(),
                WIDTH / 2, 88, WIDTH, 14, MEDIUM, 0xDBF4F5F7, true);
        if (mCardHidden) {
            canvas.restore();
            return;
        }

        final float top = 108;
        mRect.set(0, d(top), d(WIDTH), d(top + 150));
        cardFill(canvas, card, d(card.radius));

        // Top row: app icon and output chip.
        lockIcon(canvas, L_MUSIC, 27, top + 25, 22, card.accent);
        final String device = getResources().getString(R.string.remixer_lock_preview_device);
        mText.setTextSize(d(13));
        mText.setTypeface(MEDIUM);
        final float chipW = mText.measureText(device) / mScale + 44;
        mRect.set(d(WIDTH - 14 - chipW), d(top + 12), d(WIDTH - 14), d(top + 42));
        pillFill(canvas, card.accent, Color.TRANSPARENT, d(15), 0);
        lockIcon(canvas, L_PHONE, WIDTH - 14 - chipW + 19, top + 27, 14, card.onAccent);
        text(canvas, device, WIDTH - 14 - chipW + 32, top + 32, chipW, 13, MEDIUM,
                card.onAccent, false);

        // Second row: artwork, title, play button.
        final float cy = top + 76;
        float x = 16;
        if (card.thumb) {
            mRect.set(d(x), d(cy - 22), d(x + 44), d(cy + 22));
            artFill(canvas, d(card.artRadius));
            x += 56;
        }
        final float playLeft = WIDTH - 16 - 64;
        text(canvas, getResources().getString(R.string.remixer_lock_preview_title), x, cy - 3,
                playLeft - x - 8, 16, MEDIUM, card.text, false);
        text(canvas, getResources().getString(R.string.remixer_lock_preview_artist), x, cy + 15,
                playLeft - x - 8, 13, Typeface.DEFAULT, card.sub, false);
        mRect.set(d(playLeft), d(cy - 21), d(WIDTH - 16), d(cy + 21));
        pillFill(canvas, card.play, card.playBorder, d(14), mScale);
        lockIcon(canvas, L_PAUSE, playLeft + 32, cy, 20, card.playInk);

        // Third row: previous, seek bar, next, like.
        final float cy3 = top + 125;
        lockIcon(canvas, L_PREV, 30, cy3, 20, card.text);
        final float barLeft = 54;
        final float barRight = WIDTH - 102;
        mPaint.setColor(card.track);
        mRect.set(d(barLeft), d(cy3 - 2), d(barRight), d(cy3 + 2));
        canvas.drawRoundRect(mRect, d(2), d(2), mPaint);
        final float progress = barLeft + (barRight - barLeft) * .38f;
        mPaint.setColor(card.accent);
        mRect.set(d(barLeft), d(cy3 - 2), d(progress), d(cy3 + 2));
        canvas.drawRoundRect(mRect, d(2), d(2), mPaint);
        mRect.set(d(progress - 2), d(cy3 - 8), d(progress + 2), d(cy3 + 8));
        canvas.drawRoundRect(mRect, d(2), d(2), mPaint);
        lockIcon(canvas, L_NEXT, WIDTH - 78, cy3, 20, card.text);
        lockIcon(canvas, L_LIKE, WIDTH - 34, cy3, 20, card.text);
        canvas.restore();
    }

    /** Fills mRect as the card: art or dark base, then the style fill and outline. */
    private void cardFill(Canvas canvas, Card card, float r) {
        if (card.art) {
            artFill(canvas, r);
        }
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);
        if (card.base != Color.TRANSPARENT) {
            mPaint.setColor(card.base);
            canvas.drawRoundRect(mRect, r, r, mPaint);
        }
        mPaint.setColor(card.bg);
        canvas.drawRoundRect(mRect, r, r, mPaint);
        if (card.border != Color.TRANSPARENT) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(1.5f * mDensity);
            mPaint.setColor(card.border);
            canvas.drawRoundRect(mRect, r, r, mPaint);
            mPaint.setStyle(Paint.Style.FILL);
        }
    }

    /** A stand-in for the album art over mRect. */
    private void artFill(Canvas canvas, float r) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setShader(new LinearGradient(mRect.left, mRect.top, mRect.right, mRect.bottom,
                0xFFFF7A59, 0xFF8C3BFF, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(mRect, r, r, mPaint);
        mPaint.setShader(null);
    }

    /** Fills mRect with an optional outline; strokeScale is px per dp of the outline. */
    private void pillFill(Canvas canvas, int fill, int border, float r, float strokeScale) {
        mPaint.setStyle(Paint.Style.FILL);
        if (fill != Color.TRANSPARENT) {
            mPaint.setColor(fill);
            canvas.drawRoundRect(mRect, r, r, mPaint);
        }
        if (border != Color.TRANSPARENT) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(1.5f * strokeScale);
            mPaint.setColor(border);
            canvas.drawRoundRect(mRect, r, r, mPaint);
            mPaint.setStyle(Paint.Style.FILL);
        }
    }

    private void lockIcon(Canvas canvas, int i, float cx, float cy, float size, int color) {
        final Drawable icon = mLockIcons[i];
        if (icon == null) {
            return;
        }
        final float half = d(size) / 2;
        icon.setBounds(Math.round(d(cx) - half), Math.round(d(cy) - half),
                Math.round(d(cx) + half), Math.round(d(cy) + half));
        icon.setTint(color);
        icon.draw(canvas);
    }

    private void text(Canvas canvas, String value, float x, float baseline, float width,
            float size, Typeface typeface, int color, boolean centered) {
        mText.setTextSize(d(size));
        mText.setTypeface(typeface);
        mText.setColor(color);
        final CharSequence fitted = TextUtils.ellipsize(value, mText, d(width),
                TextUtils.TruncateAt.END);
        final float textWidth = mText.measureText(fitted, 0, fitted.length());
        final float start = centered ? d(x) - textWidth / 2 : d(x);
        canvas.drawText(fitted, 0, fitted.length(), start, d(baseline), mText);
    }

    private void drawTiles(Canvas canvas, float top, float bottom) {
        final boolean uniform = mStyle == CLASSIC || mStyle == CARDS || mStyle == SHAPES;
        final int cols = (mStyle == CLASSIC || mStyle == SHAPES) ? 5 : mStyle == CARDS ? 3 : 4;
        final float gap;
        switch (mStyle) {
            case CLASSIC: gap = 4; break;
            case SHAPES: gap = 6; break;
            case SOFT: gap = 14; break;
            case CARDS:
            case GLASS:
            case NEON:
            case BENTO: gap = 10; break;
            default: gap = 8; break;
        }
        final float height = tileHeight();
        final float colWidth = (WIDTH - gap * (cols - 1)) / cols;
        float y = top;
        int col = 0;
        for (int i = 0; i < ICONS.length; i++) {
            final int span = !uniform && i < WIDE ? 2 : 1;
            if (col + span > cols) {
                col = 0;
                y += height + gap;
            }
            if (y >= bottom) {
                return;
            }
            final float x = col * (colWidth + gap);
            drawTile(canvas, i, x, y, span * colWidth + (span - 1) * gap, height, span > 1);
            col += span;
        }
    }

    private float tileHeight() {
        switch (mStyle) {
            case REMIX: return 60;
            case COMPACT: return 48;
            case GLASS: return 64;
            case CIRCLES: return 80;
            case CLASSIC: return 78;
            case CARDS: return 104;
            case NEON:
            case SOFT: return 64;
            case BENTO: return 80;
            case SHAPES: return 88;
            case BLUEPRINT: return 56;
            case DUO: return 60;
            default: return 72;
        }
    }

    private void drawTile(Canvas canvas, int i, float x, float y, float w, float h,
            boolean wide) {
        final boolean on = ON[i];
        mRect.set(d(x), d(y), d(x + w), d(y + h));
        switch (mStyle) {
            case CLASSIC: {
                icon(canvas, i, x + w / 2, y + h / 2 - 10, 26,
                        on ? mPrimary : 0x8CEDEEF0);
                label(canvas, mLabels[i], x + w / 2, y + h / 2 + 22, w - 4, 11.5f, false,
                        on ? ON_SURFACE : MUTED, true);
                return;
            }
            case CIRCLES:
                if (!wide) {
                    mPaint.setColor(on ? mPrimary : SURFACE);
                    canvas.drawCircle(d(x + w / 2), d(y + 30), d(28), mPaint);
                    icon(canvas, i, x + w / 2, y + 30, 24, on ? mOnPrimary : ON_SURFACE);
                    label(canvas, mLabels[i], x + w / 2, y + 74, w, 11.5f, true, ON_SURFACE,
                            true);
                    return;
                }
                break;
            case CARDS: {
                fill(canvas, on ? mPrimary : SURFACE, 26);
                mPaint.setColor(on ? alpha(mOnPrimary, .14f) : SURFACE);
                canvas.drawCircle(d(x + 14 + 19), d(y + 14 + 19), d(19), mPaint);
                icon(canvas, i, x + 33, y + 33, 20, on ? mOnPrimary : ON_SURFACE);
                label(canvas, mLabels[i], x + 14, y + h - 34, w - 28, 14, true,
                        on ? mOnPrimary : ON_SURFACE, false);
                label(canvas, on ? mOn : mOff, x + 14, y + h - 16, w - 28, 12, false,
                        on ? alpha(mOnPrimary, .8f) : MUTED, false);
                return;
            }
            case SHAPES: {
                final float cx = x + w / 2;
                final float cy = y + 31;
                blob(cx, cy, 29, LOBES[i % LOBES.length], on ? DEPTHS[i % DEPTHS.length] : 0);
                mPaint.setColor(on ? mVivid : SURFACE);
                canvas.drawPath(mPath, mPaint);
                icon(canvas, i, cx, cy, 24, on ? mVividInk : ON_SURFACE);
                label(canvas, mLabels[i], cx, y + 80, w, 11.5f, true, ON_SURFACE, true);
                return;
            }
            case BENTO:
                if (!wide) {
                    final int ink = on ? mVividInk : ON_SURFACE;
                    fill(canvas, on ? mVivid : SURFACE, 22);
                    icon(canvas, i, x + w / 2, y + h / 2 - 9, 22, ink);
                    label(canvas, mLabels[i], x + w / 2, y + h / 2 + 20, w - 6, 11, true, ink,
                            true);
                    return;
                }
                fill(canvas, on ? mVivid : SURFACE, 26);
                wideLabels(canvas, i, x, y, w, h, 8, 46, on ? alpha(mVividInk, .14f) : 0x14FFFFFF,
                        on ? mVividInk : ON_SURFACE, on ? alpha(mVividInk, .8f) : MUTED, false);
                return;
            case NEON:
                if (on) {
                    mGlow.setColor(alpha(mVivid, .55f));
                    mGlow.setMaskFilter(new BlurMaskFilter(d(10), BlurMaskFilter.Blur.NORMAL));
                    canvas.drawRoundRect(mRect, d(20), d(20), mGlow);
                }
                fill(canvas, on ? alpha(mVivid, .1f) : 0x08FFFFFF, 20);
                outline(canvas, x, y, w, h, 20, on ? mVivid : 0x1FFFFFFF);
                if (!wide) {
                    icon(canvas, i, x + w / 2, y + h / 2, 22, on ? mVivid : 0xB3EDEEF0);
                    return;
                }
                wideLabels(canvas, i, x, y, w, h, 2, 40, Color.TRANSPARENT,
                        on ? mVivid : 0xB3EDEEF0, on ? mVivid : MUTED, false);
                return;
            case BLUEPRINT: {
                if (on) {
                    fill(canvas, alpha(mVivid, .08f), 10);
                }
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(d(1));
                mPaint.setColor(on ? mVivid : 0x2EFFFFFF);
                mRect.inset(d(.5f), d(.5f));
                canvas.drawRoundRect(mRect, d(10), d(10), mPaint);
                mPaint.setStyle(Paint.Style.FILL);
                final int ink = on ? mVivid : 0xB3EDEEF0;
                if (!wide) {
                    icon(canvas, i, x + w / 2, y + h / 2, 20, ink);
                    return;
                }
                wideLabels(canvas, i, x, y, w, h, 2, 36, Color.TRANSPARENT, ink,
                        on ? mVivid : MUTED, true);
                return;
            }
            case DUO: {
                final int segment = on ? mVivid : 0x1AFFFFFF;
                final int ink = on ? mVividInk : ON_SURFACE;
                mPaint.setColor(segment);
                if (!wide) {
                    round(canvas, x, y, x + w, y + h, 20);
                    icon(canvas, i, x + w / 2, y + h / 2, 22, ink);
                    return;
                }
                segment(canvas, x, y, x + h, y + h, 20, 6);
                icon(canvas, i, x + h / 2, y + h / 2, 22, ink);
                mPaint.setColor(0x12FFFFFF);
                segment(canvas, x + h + 3, y, x + w, y + h, 6, 20);
                final float textLeft = x + h + 15;
                final float textWidth = x + w - textLeft - 10;
                label(canvas, mLabels[i], textLeft, y + h / 2 - 2, textWidth, 14, true,
                        ON_SURFACE, false);
                label(canvas, on ? mOn : mOff, textLeft, y + h / 2 + 14, textWidth, 12, false,
                        on ? mVivid : MUTED, false);
                return;
            }
            case SOFT: {
                if (on) {
                    // Pressed in: darker, shaded at the top left edge.
                    fill(canvas, 0xFF161211, 22);
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(d(3));
                    mPaint.setShader(new LinearGradient(mRect.left, mRect.top, mRect.right,
                            mRect.bottom, 0xCC0B0908, 0x402E2725, Shader.TileMode.CLAMP));
                    canvas.drawRoundRect(mRect, d(22), d(22), mPaint);
                    mPaint.setShader(null);
                    mPaint.setStyle(Paint.Style.FILL);
                } else {
                    mGlow.setMaskFilter(new BlurMaskFilter(d(7), BlurMaskFilter.Blur.NORMAL));
                    mGlow.setColor(0xE60B0908);
                    canvas.save();
                    canvas.translate(d(4), d(4));
                    canvas.drawRoundRect(mRect, d(22), d(22), mGlow);
                    canvas.restore();
                    mGlow.setColor(0xFF2E2725);
                    canvas.save();
                    canvas.translate(-d(3), -d(3));
                    canvas.drawRoundRect(mRect, d(22), d(22), mGlow);
                    canvas.restore();
                    fill(canvas, SOFT_SURFACE, 22);
                }
                final int ink = on ? mVivid : 0xBFEDEEF0;
                if (!wide) {
                    icon(canvas, i, x + w / 2, y + h / 2, 22, ink);
                    return;
                }
                wideLabels(canvas, i, x, y, w, h, 4, 40, Color.TRANSPARENT, ink,
                        on ? mVivid : MUTED, false);
                return;
            }
            default:
                break;
        }

        int bg;
        int chip = Color.TRANSPARENT;
        int ink;
        int sub;
        float r;
        float chipSize;
        float chipRadius;
        float labelSize = 14;
        float subSize = 12;
        switch (mStyle) {
            case REMIX:
                r = 20;
                bg = on ? Color.TRANSPARENT : 0xFF1E2024;
                if (on) {
                    mPaint.setShader(new LinearGradient(mRect.left, mRect.top, mRect.right,
                            mRect.bottom, mAccent500, mAccent700, Shader.TileMode.CLAMP));
                    canvas.drawRoundRect(mRect, d(r), d(r), mPaint);
                    mPaint.setShader(null);
                }
                chip = on ? 0x38FFFFFF : 0xFF2B2E33;
                ink = on ? Color.WHITE : 0xFFE9EAEC;
                sub = on ? 0xCCFFFFFF : 0xFF9DA1A7;
                chipSize = 40;
                chipRadius = 20;
                break;
            case COMPACT:
                r = 24;
                bg = on ? mPrimary : 0x17FFFFFF;
                chip = on ? alpha(mOnPrimary, .14f) : SURFACE;
                ink = on ? mOnPrimary : ON_SURFACE;
                sub = on ? alpha(mOnPrimary, .8f) : MUTED;
                chipSize = 36;
                chipRadius = 18;
                labelSize = 13;
                subSize = 11;
                break;
            case GLASS:
                r = 22;
                if (on) {
                    mGlow.setColor(alpha(mAccent500, .45f));
                    mGlow.setMaskFilter(new BlurMaskFilter(d(14), BlurMaskFilter.Blur.NORMAL));
                    canvas.drawRoundRect(mRect, d(r), d(r), mGlow);
                }
                bg = on ? alpha(mPrimary, .2f) : 0x09FFFFFF;
                ink = on ? mPrimary : ON_SURFACE;
                sub = on ? mPrimary : MUTED;
                chipSize = 44;
                chipRadius = 22;
                break;
            case CIRCLES:
                r = 26;
                bg = on ? mPrimary : SURFACE;
                chip = on ? alpha(mOnPrimary, .14f) : SURFACE;
                ink = on ? mOnPrimary : ON_SURFACE;
                sub = on ? alpha(mOnPrimary, .8f) : MUTED;
                chipSize = 48;
                chipRadius = 24;
                break;
            default:
                r = on ? 24 : h / 2;
                bg = wide ? SURFACE : (on ? mPrimary : SURFACE);
                chip = on ? mPrimary : 0x1FFFFFFF;
                ink = on ? mOnPrimary : ON_SURFACE;
                sub = MUTED;
                chipSize = 56;
                chipRadius = on ? 16 : 28;
                break;
        }
        fill(canvas, bg, r);
        if (mStyle == GLASS) {
            outline(canvas, x, y, w, h, r, on ? alpha(mPrimary, .95f) : 0x21FFFFFF);
        }
        if (!wide) {
            icon(canvas, i, x + w / 2, y + h / 2, mStyle == COMPACT ? 20 : 24, ink);
            return;
        }
        final float chipLeft = x + 8;
        final float cy = y + h / 2;
        if (chip != Color.TRANSPARENT) {
            mPaint.setColor(chip);
            mRect.set(d(chipLeft), d(cy - chipSize / 2), d(chipLeft + chipSize),
                    d(cy + chipSize / 2));
            canvas.drawRoundRect(mRect, d(chipRadius), d(chipRadius), mPaint);
        }
        int chipInk = ink;
        if (mStyle == STOCK) {
            chipInk = on ? mOnPrimary : ON_SURFACE;
        }
        icon(canvas, i, chipLeft + chipSize / 2, cy, mStyle == COMPACT ? 18 : 22, chipInk);
        final int labelInk = mStyle == STOCK ? ON_SURFACE : ink;
        final float textLeft = chipLeft + chipSize + 8;
        final float textWidth = x + w - textLeft - (mStyle == CIRCLES ? 26 : 10);
        label(canvas, mLabels[i], textLeft, cy - 2, textWidth, labelSize, true, labelInk, false);
        label(canvas, on ? mOn : mOff, textLeft, cy + subSize + 2, textWidth, subSize, false,
                sub, false);
        if (mStyle == CIRCLES && !mMini) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(d(2.2f));
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setColor(alpha(ink, .6f));
            final float cx = x + w - 16;
            canvas.drawLine(d(cx - 3), d(cy - 6), d(cx + 3), d(cy), mPaint);
            canvas.drawLine(d(cx + 3), d(cy), d(cx - 3), d(cy + 6), mPaint);
            mPaint.setStyle(Paint.Style.FILL);
        }
    }

    /** A wide tile's icon in an optional chip, then its name and state. */
    private void wideLabels(Canvas canvas, int i, float x, float y, float w, float h,
            float chipLeftPad, float chipSize, int chip, int ink, int sub, boolean caps) {
        final float chipLeft = x + chipLeftPad + 6;
        final float cy = y + h / 2;
        if (chip != Color.TRANSPARENT) {
            mPaint.setColor(chip);
            canvas.drawCircle(d(chipLeft + chipSize / 2), d(cy), d(chipSize / 2), mPaint);
        }
        icon(canvas, i, chipLeft + chipSize / 2, cy, 22, ink);
        final float textLeft = chipLeft + chipSize + 6;
        final float textWidth = x + w - textLeft - 10;
        final String name = caps ? mLabels[i].toUpperCase() : mLabels[i];
        final String state = on(i) ? mOn : mOff;
        if (caps) {
            mono(canvas, name, textLeft, cy - 1, textWidth, 11, ON_SURFACE);
            mono(canvas, state.toUpperCase(), textLeft, cy + 13, textWidth, 9.5f, sub);
        } else {
            label(canvas, name, textLeft, cy - 2, textWidth, 14, true,
                    mStyle == BENTO ? ink : ON_SURFACE, false);
            label(canvas, state, textLeft, cy + 14, textWidth, 12, false, sub, false);
        }
    }

    private static boolean on(int i) {
        return ON[i];
    }

    /** Blueprint's monospace caps. */
    private void mono(Canvas canvas, String text, float x, float baseline, float width,
            float size, int color) {
        if (mMini || text == null) {
            return;
        }
        mText.setTextSize(d(size));
        mText.setTypeface(Typeface.MONOSPACE);
        mText.setLetterSpacing(.06f);
        mText.setColor(color);
        final CharSequence fitted = TextUtils.ellipsize(text, mText, d(width),
                TextUtils.TruncateAt.END);
        canvas.drawText(fitted, 0, fitted.length(), d(x), d(baseline), mText);
        mText.setLetterSpacing(0);
    }

    /** Fills the tile rect set by drawTile. */
    private void fill(Canvas canvas, int color, float r) {
        if (color == Color.TRANSPARENT) {
            return;
        }
        mPaint.setColor(color);
        canvas.drawRoundRect(mRect, d(r), d(r), mPaint);
    }

    private void icon(Canvas canvas, int i, float cx, float cy, float size, int color) {
        final Drawable icon = mIcons[i];
        if (icon == null) {
            return;
        }
        final float half = d(size) / 2;
        icon.setBounds(Math.round(d(cx) - half), Math.round(d(cy) - half),
                Math.round(d(cx) + half), Math.round(d(cy) + half));
        icon.setTint(color);
        icon.draw(canvas);
    }

    /** One line of text starting at x, or centered on it. */
    private void label(Canvas canvas, String text, float x, float baseline, float width,
            float size, boolean bold, int color, boolean centered) {
        if (mMini || text == null) {
            return;
        }
        mText.setTextSize(d(size));
        mText.setTypeface(bold ? MEDIUM : Typeface.DEFAULT);
        mText.setColor(color);
        final CharSequence fitted = TextUtils.ellipsize(text, mText, d(width),
                TextUtils.TruncateAt.END);
        final float textWidth = mText.measureText(fitted, 0, fitted.length());
        final float start = centered ? d(x) - textWidth / 2 : d(x);
        canvas.drawText(fitted, 0, fitted.length(), start, d(baseline), mText);
    }

    private static int alpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(alpha * 255) << 24);
    }
}
