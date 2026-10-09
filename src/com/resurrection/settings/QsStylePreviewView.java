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
    public static final int MONO = 7;

    private static final float WIDTH = 340f;
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

    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final int SURFACE = 0x1AFFFFFF;
    private static final int ON_SURFACE = 0xFFEDEEF0;
    private static final int MUTED = 0xFFB4B8BE;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Drawable[] mIcons = new Drawable[ICONS.length];
    private final String[] mLabels;
    private final String mOn;
    private final String mOff;
    private final int mPrimary;
    private final int mOnPrimary;
    private final int mAccent500;
    private final int mAccent600;
    private final int mAccent700;
    private final int mAccent900;
    private final float mDensity;
    private int mStyle;
    private boolean mMini;
    private float mScale;

    public QsStylePreviewView(Context context) {
        this(context, null);
    }

    public QsStylePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDensity = getResources().getDisplayMetrics().density;
        mPrimary = context.getColor(android.R.color.system_accent1_200);
        mOnPrimary = context.getColor(android.R.color.system_accent1_800);
        mAccent500 = context.getColor(android.R.color.system_accent1_500);
        mAccent600 = context.getColor(android.R.color.system_accent1_600);
        mAccent700 = context.getColor(android.R.color.system_accent1_700);
        mAccent900 = context.getColor(android.R.color.system_accent1_900);
        mLabels = getResources().getStringArray(R.array.remixer_qs_preview_labels);
        mOn = getResources().getString(R.string.remixer_qs_preview_on);
        mOff = getResources().getString(R.string.remixer_qs_preview_off);
        for (int i = 0; i < ICONS.length; i++) {
            Drawable icon = context.getDrawable(ICONS[i]);
            mIcons[i] = icon != null ? icon.mutate() : null;
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
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mStyle == MONO ? 0xFF050505 : 0xFF0A0B0D);
        canvas.drawRect(0, 0, w, h, mPaint);
        final int glow = mStyle == MONO ? 0x1FFFFFFF : (mAccent500 & 0x00FFFFFF) | 0x66000000;
        mPaint.setShader(new RadialGradient(w * .2f, h * .08f, Math.max(w, h) * .7f,
                glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mPaint);
        mPaint.setShader(null);

        final float pad = (mMini ? 6 : 14) * mDensity;
        mScale = (w - pad * 2) / WIDTH;
        final float bottom = (h - pad * 2) / mScale;
        canvas.save();
        canvas.translate(pad, pad);
        float y = drawSlider(canvas) + 12;
        drawTiles(canvas, y, bottom);
        canvas.restore();
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
            case MONO:
                pill(canvas, 0, 0, WIDTH, 44, 14, 0xFF1C1C1C, 0xFFF2F2F2);
                return 44;
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
            default:
                pill(canvas, 0, 0, WIDTH, 44, 22, SURFACE, mPrimary);
                return 44;
        }
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

    private void drawTiles(Canvas canvas, float top, float bottom) {
        final boolean uniform = mStyle == CLASSIC || mStyle == CARDS;
        final int cols = mStyle == CLASSIC ? 5 : mStyle == CARDS ? 3 : 4;
        final float gap = mStyle == CLASSIC ? 4 : (mStyle == CARDS || mStyle == GLASS) ? 10 : 8;
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
            case GLASS:
            case MONO: return 64;
            case CIRCLES: return 80;
            case CLASSIC: return 78;
            case CARDS: return 104;
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
            case MONO:
                r = 18;
                bg = on ? 0xFFF2F2F2 : 0xFF161616;
                chip = on ? 0xFF111111 : 0xFF232323;
                ink = on ? 0xFF111111 : 0xFFEDEDED;
                sub = on ? 0xFF4A4A4A : 0xFF8A8A8A;
                chipSize = 40;
                chipRadius = 20;
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
            int iconInk = mStyle == MONO ? (on ? 0xFF111111 : 0xFFEDEDED) : ink;
            icon(canvas, i, x + w / 2, y + h / 2, mStyle == COMPACT ? 20 : 24, iconInk);
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
        } else if (mStyle == MONO) {
            chipInk = on ? 0xFFF2F2F2 : 0xFFEDEDED;
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
