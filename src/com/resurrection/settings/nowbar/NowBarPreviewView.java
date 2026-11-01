/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.nowbar;

import android.app.WallpaperColors;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.PathParser;

import com.android.settings.R;
import com.android.settingslib.utils.ThreadUtils;
import com.resurrection.settings.glass.GlassPreviewView;

import java.util.List;

/**
 * The bottom of the lock screen with the Now Bar on it, at the same size and in the same place,
 * following the options as they change. Mirrors NowBarView and NowBarSection in SystemUI. Shows the
 * song that's playing, or a sample when nothing is.
 */
public class NowBarPreviewView extends View {
    private static final String SYSTEM_GLASS = "rr_glass_material";
    private static final int GLASS_SOLID = 0;
    private static final int GLASS_LIQUID = 2;
    private static final int DARK_FILL = 0xFF0D0809;
    private static final int SAMPLE_FILL = 0xFF451C19;
    private static final float ART_RATIO = 0.6f;

    private static final String ICON_PREV = "M6,6h2v12H6zM9.5,12l8.5,6V6z";
    private static final String ICON_PAUSE = "M6,19h4V5H6v14zM14,5v14h4V5h-4z";
    private static final String ICON_NEXT = "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z";
    private static final String ICON_HOME = "M12,3l9,8h-3v9h-4v-6h-4v6H6v-9H3z";

    private final float mDp;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mWallMatrix = new Matrix();
    private final Path mClip = new Path();
    private final RectF mBar = new RectF();
    private final RectF mRect = new RectF();
    private final RenderNode mBlurNode = new RenderNode("nowBarBlur");
    private final Path mIconPrev = PathParser.createPathFromPathData(ICON_PREV);
    private final Path mIconPause = PathParser.createPathFromPathData(ICON_PAUSE);
    private final Path mIconNext = PathParser.createPathFromPathData(ICON_NEXT);
    private final Path mIconHome = PathParser.createPathFromPathData(ICON_HOME);
    private final Path mWave = new Path();
    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            readSettings();
            invalidate();
        }
    };

    private Bitmap mWallpaper;
    private boolean mBlurDirty = true;
    private Bitmap mArt;
    private int mArtFill = SAMPLE_FILL;
    private String mSong;
    private String mArtist;

    private boolean mGlass;
    private int mSystemGlass;
    private int mColor;
    private int mOpacity;
    private int mOutline;
    private int mShadow;
    private int mMore;
    private int mHeight;
    private int mWidth;
    private int mCorners;
    private int mRaise;
    private int mArtShape;
    private int mButtons;
    private int mTextMode;
    private int mTextSize;
    private boolean mShowWave;

    public NowBarPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDp = getResources().getDisplayMetrics().density;
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
        mText.setTypeface(Typeface.create(Typeface.DEFAULT, 500, false));
        readSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final ContentResolver resolver = getContext().getContentResolver();
        for (String key : NowBarSettings.KEYS) {
            resolver.registerContentObserver(Settings.System.getUriFor(key), false, mObserver);
        }
        resolver.registerContentObserver(Settings.System.getUriFor(SYSTEM_GLASS), false, mObserver);
        readSettings();
        readSong();
        loadWallpaper();
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onDetachedFromWindow();
    }

    private int get(String key, int def) {
        return Settings.System.getIntForUser(getContext().getContentResolver(), key, def,
                UserHandle.USER_CURRENT);
    }

    private void readSettings() {
        mGlass = get(NowBarSettings.MATERIAL, 0) == 1;
        mSystemGlass = get(SYSTEM_GLASS, 1);
        mColor = get(NowBarSettings.COLOR, 0);
        mOpacity = clamp(get(NowBarSettings.OPACITY, 94), 0, 100);
        mOutline = get(NowBarSettings.OUTLINE, 1);
        mShadow = get(NowBarSettings.SHADOW, 0);
        mMore = get(NowBarSettings.MORE, 0);
        mHeight = clamp(get(NowBarSettings.HEIGHT, 64), 48, 96);
        mWidth = get(NowBarSettings.WIDTH, 1);
        mCorners = clamp(get(NowBarSettings.CORNERS, 100), 20, 100);
        mRaise = clamp(get(NowBarSettings.RAISE, 0), 0, 40);
        mArtShape = get(NowBarSettings.ART, 0);
        mButtons = get(NowBarSettings.BUTTONS, 0);
        mTextMode = get(NowBarSettings.TEXT, 0);
        mTextSize = clamp(get(NowBarSettings.TEXT_SIZE, 13), 11, 17);
        mShowWave = get(NowBarSettings.WAVE, 1) != 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void readSong() {
        mSong = getResources().getString(R.string.remixer_lock_preview_title);
        mArtist = getResources().getString(R.string.remixer_lock_preview_artist);
        mArt = null;
        mArtFill = SAMPLE_FILL;
        final MediaSessionManager sessions =
                getContext().getSystemService(MediaSessionManager.class);
        if (sessions == null) {
            return;
        }
        final List<MediaController> controllers;
        try {
            controllers = sessions.getActiveSessions(null);
        } catch (SecurityException e) {
            return;
        }
        MediaController picked = null;
        for (MediaController controller : controllers) {
            if (controller.getMetadata() == null) {
                continue;
            }
            final PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                picked = controller;
                break;
            }
            if (picked == null) {
                picked = controller;
            }
        }
        if (picked == null) {
            return;
        }
        final MediaMetadata metadata = picked.getMetadata();
        final String song = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (!TextUtils.isEmpty(song)) {
            mSong = song;
            mArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        }
        mArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (mArt == null) {
            mArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        if (mArt != null) {
            try {
                final float[] hsl = new float[3];
                ColorUtils.colorToHSL(WallpaperColors.fromBitmap(mArt).getPrimaryColor().toArgb(),
                        hsl);
                hsl[1] = Math.min(hsl[1], 0.5f);
                hsl[2] = 0.17f;
                mArtFill = ColorUtils.HSLToColor(hsl);
            } catch (RuntimeException e) {
                // Hardware or odd bitmaps: the sample tint.
            }
        }
    }

    private void loadWallpaper() {
        final Context context = getContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            final Bitmap bitmap = GlassPreviewView.wallpaper(context);
            ThreadUtils.postOnMainThread(() -> {
                mWallpaper = bitmap;
                placeWallpaper(getWidth(), getHeight());
                invalidate();
            });
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        placeWallpaper(w, h);
    }

    private void placeWallpaper(int w, int h) {
        mBlurDirty = true;
        mWallMatrix.reset();
        if (mWallpaper == null || w == 0 || h == 0) {
            return;
        }
        // The bottom of the wallpaper, as it sits on the lock screen.
        final float scale = Math.max((float) w / mWallpaper.getWidth(),
                (float) h / mWallpaper.getHeight());
        mWallMatrix.setScale(scale, scale);
        mWallMatrix.postTranslate((w - mWallpaper.getWidth() * scale) / 2,
                h - mWallpaper.getHeight() * scale);
    }

    private void drawWallpaper(Canvas canvas, int w, int h) {
        if (mWallpaper != null) {
            canvas.drawBitmap(mWallpaper, mWallMatrix, null);
            return;
        }
        mFill.setShader(new LinearGradient(0, 0, 0, h, Color.BLACK,
                getContext().getColor(android.R.color.system_accent1_700), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mFill);
        mFill.setShader(null);
    }

    private float dp(float value) {
        return value * mDp;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        canvas.save();
        mClip.reset();
        final float corner = dp(28);
        mClip.addRoundRect(0, 0, w, h, new float[] {0, 0, 0, 0, corner, corner, corner, corner},
                Path.Direction.CW);
        canvas.clipPath(mClip);
        drawWallpaper(canvas, w, h);

        // NowBarSection's numbers.
        final float side = mWidth == 2 ? dp(16) : mWidth == 0 ? dp(102) : dp(76);
        final float barHeight = dp(mHeight);
        final float bottom = Math.max(dp(14), dp(56) - barHeight / 2) + dp(mRaise);
        mBar.set(side, h - bottom - barHeight, w - side, h - bottom);
        final float radius = Math.max(dp(8), barHeight / 2 * mCorners / 100f);

        final float shortcutBottom = mWidth == 2 ? h - mBar.top + dp(10) : dp(32);
        drawShortcut(canvas, h - shortcutBottom - dp(24));
        if (mMore == 2) {
            drawPeek(canvas, radius);
        }
        drawCapsule(canvas, radius, w, h);
        drawMusic(canvas);
        if (mMore == 1) {
            mFill.setColor(Color.WHITE);
            canvas.drawCircle(mBar.centerX() - dp(4.5f), mBar.bottom - dp(6.5f), dp(2.5f), mFill);
            mFill.setColor(0x66FFFFFF);
            canvas.drawCircle(mBar.centerX() + dp(4.5f), mBar.bottom - dp(6.5f), dp(2.5f), mFill);
        }
        canvas.restore();
    }

    private void drawShortcut(Canvas canvas, float cy) {
        final float cx = dp(16 + 24);
        mFill.setColor(0x99000000);
        canvas.drawCircle(cx, cy, dp(24), mFill);
        drawIcon(canvas, mIconHome, cx, cy, dp(20), 0xFFFFE0E2);
    }

    private void drawPeek(Canvas canvas, float radius) {
        mRect.set(mBar.left + dp(14), mBar.top - dp(7), mBar.right - dp(14),
                mBar.bottom - dp(7));
        mFill.setColor(ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(fill(), Color.WHITE, 0.08f), 0xC0));
        canvas.drawRoundRect(mRect, radius, radius, mFill);
    }

    private int fill() {
        switch (mColor) {
            case 1: return getContext().getColor(android.R.color.system_accent1_800);
            case 2: return DARK_FILL;
            default: return mArtFill;
        }
    }

    private void drawCapsule(Canvas canvas, float radius, int w, int h) {
        final boolean glass = mGlass && mSystemGlass != GLASS_SOLID
                && canvas.isHardwareAccelerated();
        final float opacity = glass ? mOpacity * 0.7f
                : mGlass ? Math.max(mOpacity, 85) : mOpacity;
        final int accent = getContext().getColor(android.R.color.system_accent1_400);
        if (mShadow != 0) {
            mFill.setShadowLayer(dp(mShadow == 2 ? 14 : 10), 0, mShadow == 2 ? 0 : dp(4),
                    mShadow == 2 ? accent : 0x99000000);
            canvas.save();
            mClip.reset();
            mClip.addRoundRect(mBar, radius, radius, Path.Direction.CW);
            canvas.clipOutPath(mClip);
            mFill.setColor(Color.BLACK);
            canvas.drawRoundRect(mBar, radius, radius, mFill);
            canvas.restore();
            mFill.clearShadowLayer();
        }
        if (glass) {
            if (mBlurDirty) {
                mBlurDirty = false;
                mBlurNode.setPosition(0, 0, w, h);
                final RecordingCanvas recording = mBlurNode.beginRecording(w, h);
                drawWallpaper(recording, w, h);
                mBlurNode.endRecording();
                mBlurNode.setRenderEffect(RenderEffect.createBlurEffect(dp(15), dp(15),
                        Shader.TileMode.CLAMP));
            }
            canvas.save();
            mClip.reset();
            mClip.addRoundRect(mBar, radius, radius, Path.Direction.CW);
            canvas.clipPath(mClip);
            canvas.drawRenderNode(mBlurNode);
            canvas.restore();
        }
        mFill.setColor(ColorUtils.setAlphaComponent(fill(), Math.round(opacity * 2.55f)));
        canvas.drawRoundRect(mBar, radius, radius, mFill);
        if (glass && mSystemGlass == GLASS_LIQUID) {
            mStroke.setShader(new LinearGradient(mBar.left, mBar.top, mBar.right, mBar.bottom,
                    0xB3FFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP));
            mStroke.setStrokeWidth(dp(1.5f));
            canvas.drawRoundRect(mBar, radius, radius, mStroke);
            mStroke.setShader(null);
        }
        if (mOutline != 0) {
            mStroke.setColor(mOutline == 2 ? accent : 0x14FFFFFF);
            mStroke.setStrokeWidth(mOutline == 2 ? dp(1.5f) : dp(1));
            canvas.drawRoundRect(mBar, radius, radius, mStroke);
        }
    }

    private void drawMusic(Canvas canvas) {
        final boolean compact = mHeight < 60;
        final float art = Math.round(mHeight * ART_RATIO) * mDp;
        final float inset = dp(compact ? 8 : 12);
        final float cy = mBar.centerY();
        float start = mBar.left + dp(16);
        if (mArtShape != 3) {
            drawArt(canvas, mBar.left + inset, cy - art / 2, art);
            start = mBar.left + inset + art + dp(12);
        }
        final boolean buttons = mButtons != 2;
        final boolean row = buttons && !compact;
        final float end = mBar.right - dp(buttons && compact ? 46 : 16);
        final boolean twoLines = mTextMode == 1 && mHeight >= 70 && !TextUtils.isEmpty(mArtist);
        final String line = twoLines || mTextMode == 2 || TextUtils.isEmpty(mArtist)
                ? mSong : mSong + " · " + mArtist;

        mText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, mTextSize,
                getResources().getDisplayMetrics()));
        final float titleHeight = mText.getFontSpacing();
        final Paint.FontMetrics titleMetrics = mText.getFontMetrics();
        final float small = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12,
                getResources().getDisplayMetrics());
        final float total = titleHeight + (twoLines ? small * 1.3f : 0) + (row ? dp(34) : 0);
        float y = cy - total / 2;

        mText.setColor(Color.WHITE);
        canvas.drawText(TextUtils.ellipsize(line, mText, end - start,
                TextUtils.TruncateAt.END).toString(), start, y - titleMetrics.ascent, mText);
        y += titleHeight;
        if (twoLines) {
            mText.setTextSize(small);
            mText.setColor(0xB8FFFFFF);
            canvas.drawText(TextUtils.ellipsize(mArtist, mText, end - start,
                    TextUtils.TruncateAt.END).toString(), start, y + small, mText);
            y += small * 1.3f;
        }
        if (row) {
            final float iconY = y + dp(17);
            final float width = mBar.right - dp(16) - start;
            if (mButtons == 0) {
                drawIcon(canvas, mIconPrev, start + width / 6, iconY, dp(18), Color.WHITE);
                drawIcon(canvas, mIconNext, start + width * 5 / 6, iconY, dp(18), Color.WHITE);
            }
            drawIcon(canvas, mIconPause, start + width / 2, iconY, dp(18), Color.WHITE);
        }
        if (buttons && compact) {
            drawIcon(canvas, mIconPause, mBar.right - dp(25), cy, dp(18), Color.WHITE);
        }
        if (mShowWave && !compact) {
            drawWave(canvas);
        }
    }

    private void drawArt(Canvas canvas, float left, float top, float size) {
        mRect.set(left, top, left + size, top + size);
        final float corner = mArtShape == 1 ? size * 0.22f : mArtShape == 2 ? size * 0.08f
                : size / 2;
        if (mArt != null) {
            final BitmapShader shader = new BitmapShader(mArt, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
            final float scale = size / Math.min(mArt.getWidth(), mArt.getHeight());
            final Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(left - (mArt.getWidth() * scale - size) / 2,
                    top - (mArt.getHeight() * scale - size) / 2);
            shader.setLocalMatrix(matrix);
            mFill.setShader(shader);
        } else {
            mFill.setShader(new RadialGradient(left + size * 0.35f, top + size * 0.3f, size,
                    new int[] {0xFFD8B48C, 0xFF7A4A35, 0xFF241412}, new float[] {0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(mRect, corner, corner, mFill);
        mFill.setShader(null);
    }

    private void drawWave(Canvas canvas) {
        final float left = mBar.left + dp(44);
        final float right = mBar.right - dp(26);
        final float mid = mBar.bottom - dp(5);
        final float amp = dp(2.5f);
        mWave.reset();
        mWave.moveTo(left, mid);
        final float step = (right - left) / 72;
        for (int i = 1; i <= 72; i++) {
            mWave.lineTo(left + step * i, mid + amp * (float) Math.sin(i * Math.PI / 18));
        }
        mStroke.setColor(0x80FFC8AA);
        mStroke.setStrokeWidth(dp(2));
        canvas.drawPath(mWave, mStroke);
    }

    private void drawIcon(Canvas canvas, Path icon, float cx, float cy, float size, int color) {
        canvas.save();
        canvas.translate(cx - size / 2, cy - size / 2);
        canvas.scale(size / 24f, size / 24f);
        mFill.setColor(color);
        canvas.drawPath(icon, mFill);
        canvas.restore();
    }
}
