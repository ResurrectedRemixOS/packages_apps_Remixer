/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass;

import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.PathParser;

import com.android.settings.R;
import com.android.settingslib.utils.ThreadUtils;

/** Sample glass over the wallpaper, drawn the way SurfaceFlinger draws Liquid Glass. */
public class GlassPreviewView extends View {
    private static final String TAG = "GlassPreviewView";

    private static final String[] KEYS = {LiquidGlassSettings.MATERIAL, LiquidGlassSettings.BLUR,
            LiquidGlassSettings.TINT, LiquidGlassSettings.REFRACTION,
            LiquidGlassSettings.ACCENT_TINT};

    // The lens from frameworks/native's BlurFilter, so the preview matches the system.
    private static final String SHADER = ""
            + "uniform shader blurred;\n"
            + "uniform float4 rect;\n"
            + "uniform float cornerRadius;\n"
            + "uniform float liquid;\n"
            + "uniform float refraction;\n"
            + "uniform float dim;\n"
            + "uniform float4 tint;\n"
            + "float boxSdf(float2 p, float2 halfSize, float r) {\n"
            + "    float2 q = abs(p) - halfSize + float2(r);\n"
            + "    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;\n"
            + "}\n"
            + "half4 main(float2 xy) {\n"
            + "    float3 color = float3(0.0);\n"
            + "    if (liquid > 0.5) {\n"
            + "        float2 halfSize = max((rect.zw - rect.xy) * 0.5, float2(1.0));\n"
            + "        float2 p = xy - (rect.xy + rect.zw) * 0.5;\n"
            + "        float r = min(cornerRadius, min(halfSize.x, halfSize.y));\n"
            + "        float d = boxSdf(p, halfSize, r);\n"
            + "        float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);\n"
            + "        float2 a = clamp(abs(p) / halfSize, float2(0.0), float2(1.0));\n"
            + "        float2 n = normalize(s * pow(a, float2(6.0)) + float2(0.00001));\n"
            + "        float band = clamp(min(halfSize.x, halfSize.y) * 0.5, 6.0, 96.0);\n"
            + "        float edge = 1.0 - clamp(-d / band, 0.0, 1.0);\n"
            + "        float bend = edge * edge * edge;\n"
            + "        float2 uv = xy - n * (bend * band * refraction * 0.9);\n"
            + "        float split = bend * refraction * 4.0;\n"
            + "        color = float3(blurred.eval(uv).rgb);\n"
            + "        color.r = float(blurred.eval(uv + n * split).r);\n"
            + "        color.b = float(blurred.eval(uv - n * split).b);\n"
            + "        float luma = dot(color, float3(0.2126, 0.7152, 0.0722));\n"
            + "        color = mix(float3(luma), color, 1.2) * 1.03;\n"
            + "        color = mix(color, tint.rgb, tint.a) * (1.0 - dim);\n"
            + "        float rim = 1.0 - smoothstep(0.0, 1.5 + 2.5 * refraction, -d);\n"
            + "        float light = dot(n, float2(-0.5, -0.866));\n"
            + "        color += rim * (0.06 + 0.34 * max(light, 0.0) + 0.14 * max(-light, 0.0)) *\n"
            + "                (0.5 + 0.5 * refraction) + edge * edge * 0.04;\n"
            + "    } else {\n"
            + "        color = float3(blurred.eval(xy).rgb);\n"
            + "        color = mix(color, tint.rgb, tint.a) * (1.0 - dim);\n"
            + "    }\n"
            + "    return half4(half3(clamp(color, float3(0.0), float3(1.0))), 1.0);\n"
            + "}\n";

    private static final String ICON_WIFI = "M2.5 8.5a14 14 0 0 1 19 0 M5.5 12a9.5 9.5 0 0 1 13 0"
            + " M8.7 15.4a5 5 0 0 1 6.6 0 M12 19h.01";
    private static final String ICON_BLUETOOTH = "M7 7l10 10-5 5V2l5 5L7 17";
    private static final String ICON_SUN = "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M12 2.5v2"
            + " M12 19.5v2 M4.6 4.6l1.4 1.4 M18 18l1.4 1.4 M2.5 12h2 M19.5 12h2 M4.6 19.4L6 18"
            + " M18 6l1.4-1.4";

    private static Bitmap sWallpaper;
    private static int sWallpaperId = -1;

    private final float mDp;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mWallMatrix = new Matrix();
    private final Path mClip = new Path();
    private final RectF mFillRect = new RectF();
    private final RectF[] mShapes = {new RectF(), new RectF(), new RectF()};
    private final float[] mCorners = new float[3];
    private final RenderNode[] mNodes = {new RenderNode("glass0"), new RenderNode("glass1"),
            new RenderNode("glass2")};
    private final RuntimeShader[] mShaders = new RuntimeShader[3];
    private final Path mIconWifi = PathParser.createPathFromPathData(ICON_WIFI);
    private final Path mIconBluetooth = PathParser.createPathFromPathData(ICON_BLUETOOTH);
    private final Path mIconSun = PathParser.createPathFromPathData(ICON_SUN);
    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            readSettings();
        }
    };

    private Bitmap mWallpaper;
    private boolean mShaderFailed;
    private boolean mNodesDirty = true;
    // A picker swatch shows one chip in a fixed material; -1 follows the settings.
    private int mSwatch = -1;
    private int mMaterial = LiquidGlassSettings.FROSTED;
    private int mBlur = 50;
    private int mTint = 0;
    private int mRefraction = 60;
    private boolean mAccentTint;

    public GlassPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDp = getResources().getDisplayMetrics().density;
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
        mStroke.setStrokeJoin(Paint.Join.ROUND);
        mText.setTypeface(Typeface.create(Typeface.DEFAULT, 500, false));
    }

    /** Shows a single chip in {@code material} instead of following the settings. */
    public void setSwatch(int material) {
        mSwatch = material;
        layoutShapes(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSwatch < 0) {
            final ContentResolver resolver = getContext().getContentResolver();
            for (String key : KEYS) {
                resolver.registerContentObserver(Settings.System.getUriFor(key), false,
                        mObserver);
            }
            readSettings();
        }
        loadWallpaper();
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutShapes(w, h);
    }

    private void readSettings() {
        final ContentResolver resolver = getContext().getContentResolver();
        mMaterial = get(resolver, LiquidGlassSettings.MATERIAL, LiquidGlassSettings.FROSTED);
        mBlur = get(resolver, LiquidGlassSettings.BLUR, 50);
        mTint = get(resolver, LiquidGlassSettings.TINT, 0);
        mRefraction = get(resolver, LiquidGlassSettings.REFRACTION, 60);
        mAccentTint = get(resolver, LiquidGlassSettings.ACCENT_TINT, 0) != 0;
        mNodesDirty = true;
        invalidate();
    }

    private static int get(ContentResolver resolver, String key, int def) {
        return Settings.System.getIntForUser(resolver, key, def, UserHandle.USER_CURRENT);
    }

    private void loadWallpaper() {
        final Context context = getContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            final Bitmap bitmap = wallpaper(context);
            ThreadUtils.postOnMainThread(() -> {
                mWallpaper = bitmap;
                layoutShapes(getWidth(), getHeight());
                invalidate();
            });
        });
    }

    private static synchronized Bitmap wallpaper(Context context) {
        final WallpaperManager manager = WallpaperManager.getInstance(context);
        Drawable drawable = null;
        int id = -1;
        try {
            id = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
            if (sWallpaper != null && id == sWallpaperId) {
                return sWallpaper;
            }
            drawable = manager.getDrawable();
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't read the wallpaper", e);
        }
        if (drawable == null) {
            drawable = manager.getBuiltInDrawable();
        }
        if (drawable == null) {
            return null;
        }
        final int width = 720;
        final int intrinsicW = drawable.getIntrinsicWidth();
        final int intrinsicH = drawable.getIntrinsicHeight();
        final int height = intrinsicW > 0 && intrinsicH > 0
                ? Math.max(1, Math.round(width * (float) intrinsicH / intrinsicW)) : 1584;
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(new Canvas(bitmap));
        sWallpaper = bitmap;
        sWallpaperId = id;
        return bitmap;
    }

    private void layoutShapes(int w, int h) {
        if (w == 0 || h == 0) {
            return;
        }
        if (mSwatch >= 0) {
            final float pw = w * 0.62f;
            final float ph = Math.min(h * 0.46f, 34 * mDp);
            mShapes[0].set((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2);
            mCorners[0] = ph / 2;
        } else {
            final float pad = 16 * mDp;
            final float tileW = (w - 3 * pad) / 2;
            final float tileH = 66 * mDp;
            final float sliderH = 56 * mDp;
            final float top = (h - tileH - 22 * mDp - sliderH) / 2;
            mShapes[0].set(pad, top, pad + tileW, top + tileH);
            mShapes[1].set(2 * pad + tileW, top, w - pad, top + tileH);
            final float sliderTop = top + tileH + 22 * mDp;
            mShapes[2].set(pad, sliderTop, w - pad, sliderTop + sliderH);
            mCorners[0] = mCorners[1] = 24 * mDp;
            mCorners[2] = 28 * mDp;
        }
        mWallMatrix.reset();
        if (mWallpaper != null) {
            final float scale = Math.max((float) w / mWallpaper.getWidth(),
                    (float) h / mWallpaper.getHeight());
            mWallMatrix.setScale(scale, scale);
            mWallMatrix.postTranslate((w - mWallpaper.getWidth() * scale) / 2,
                    (h - mWallpaper.getHeight() * scale) * 0.4f);
        }
        mNodesDirty = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        final int material = mSwatch >= 0 ? mSwatch : mMaterial;
        final int count = mSwatch >= 0 ? 1 : 3;
        final float corner = (mSwatch >= 0 ? 14 : 28) * mDp;

        canvas.save();
        mClip.reset();
        mClip.addRoundRect(0, 0, w, h, corner, corner, Path.Direction.CW);
        canvas.clipPath(mClip);
        drawWallpaper(canvas, w, h);
        if (mNodesDirty) {
            recordNodes(material, count, w, h);
        }
        for (int i = 0; i < count; i++) {
            drawGlass(canvas, i, material, mSwatch < 0 && i == 1);
        }
        if (mSwatch < 0) {
            drawContent(canvas, material);
        }
        canvas.restore();
    }

    private void drawWallpaper(Canvas canvas, int w, int h) {
        if (mWallpaper != null) {
            canvas.drawBitmap(mWallpaper, mWallMatrix, null);
            return;
        }
        mFill.setShader(new LinearGradient(0, 0, w, h, accent(500), Color.BLACK,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mFill);
        mFill.setShader(null);
    }

    private void recordNodes(int material, int count, int w, int h) {
        mNodesDirty = false;
        if (material == LiquidGlassSettings.SOLID) {
            return;
        }
        final float radius = 18 * mDp * (0.5f + mBlur / 100f)
                * (material == LiquidGlassSettings.LIQUID ? 0.65f : 1f);
        for (int i = 0; i < count; i++) {
            final RenderNode node = mNodes[i];
            node.setPosition(0, 0, w, h);
            final RecordingCanvas recording = node.beginRecording(w, h);
            drawWallpaper(recording, w, h);
            node.endRecording();
            node.setRenderEffect(effect(i, material, radius));
        }
    }

    private RenderEffect effect(int i, int material, float radius) {
        final RenderEffect blur = RenderEffect.createBlurEffect(radius, radius,
                Shader.TileMode.CLAMP);
        final RuntimeShader shader = shader(i);
        if (shader == null) {
            return blur;
        }
        final RectF r = mShapes[i];
        final int tint = accent(500);
        final boolean accentTint = mSwatch < 0 && mAccentTint;
        shader.setFloatUniform("rect", r.left, r.top, r.right, r.bottom);
        shader.setFloatUniform("cornerRadius", mCorners[i]);
        shader.setFloatUniform("liquid", material == LiquidGlassSettings.LIQUID ? 1f : 0f);
        shader.setFloatUniform("refraction", (mSwatch < 0 ? mRefraction : 60) / 100f);
        shader.setFloatUniform("dim", (mSwatch < 0 ? mTint : 0) / 200f);
        shader.setFloatUniform("tint", Color.red(tint) / 255f, Color.green(tint) / 255f,
                Color.blue(tint) / 255f, accentTint ? 0.16f : 0f);
        return RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "blurred"), blur);
    }

    private RuntimeShader shader(int i) {
        if (mShaders[i] == null && !mShaderFailed) {
            try {
                mShaders[i] = new RuntimeShader(SHADER);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Glass shader failed to compile", e);
                mShaderFailed = true;
            }
        }
        return mShaders[i];
    }

    private void drawGlass(Canvas canvas, int i, int material, boolean active) {
        final RectF r = mShapes[i];
        final float c = mCorners[i];
        if (material == LiquidGlassSettings.SOLID) {
            mFill.setColor(active ? accent(200) : 0xFF2B2930);
            canvas.drawRoundRect(r, c, c, mFill);
            return;
        }
        if (canvas.isHardwareAccelerated()) {
            canvas.save();
            mClip.reset();
            mClip.addRoundRect(r, c, c, Path.Direction.CW);
            canvas.clipPath(mClip);
            canvas.drawRenderNode(mNodes[i]);
            canvas.restore();
        }
        // The surface's own color, which SystemUI draws over the blur.
        if (material == LiquidGlassSettings.FROSTED) {
            mFill.setColor(active ? ColorUtils.setAlphaComponent(accent(200), 235) : 0x5C241E24);
        } else {
            final int accent = accent(500);
            mFill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, active
                    ? new int[] {withAlpha(accent(300), 0.62f), withAlpha(accent, 0.42f),
                            withAlpha(accent(700), 0.5f)}
                    : new int[] {0x38FFFFFF, 0x0FFFFFFF, 0x2E10080C},
                    null, Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(r, c, c, mFill);
        mFill.setShader(null);
    }

    private void drawContent(Canvas canvas, int material) {
        final boolean darkInk = material != LiquidGlassSettings.LIQUID;
        final int ink = 0xFFFFFFFF;
        final int activeInk = darkInk ? 0xFF2B0008 : 0xFFFFFFFF;

        // Brightness: an accent fill in the slider.
        final RectF slider = mShapes[2];
        final float inset = 4 * mDp;
        mFillRect.set(slider.left + inset, slider.top + inset,
                slider.left + inset + (slider.width() - 2 * inset) * 0.6f, slider.bottom - inset);
        final float fillCorner = mFillRect.height() / 2;
        if (material == LiquidGlassSettings.LIQUID) {
            mFill.setShader(new LinearGradient(mFillRect.left, mFillRect.top, mFillRect.right,
                    mFillRect.bottom, new int[] {withAlpha(accent(300), 0.8f),
                            withAlpha(accent(500), 0.6f), withAlpha(accent(700), 0.7f)},
                    null, Shader.TileMode.CLAMP));
        } else {
            mFill.setColor(accent(200));
        }
        canvas.drawRoundRect(mFillRect, fillCorner, fillCorner, mFill);
        mFill.setShader(null);
        if (material == LiquidGlassSettings.LIQUID) {
            mStroke.setColor(0x66FFFFFF);
            mStroke.setStrokeWidth(mDp);
            canvas.drawRoundRect(mFillRect, fillCorner, fillCorner, mStroke);
        }
        drawIcon(canvas, mIconSun, mFillRect.left + 26 * mDp, mFillRect.centerY(), 22 * mDp,
                activeInk);

        drawTile(canvas, mShapes[0], mIconWifi, R.string.liquid_glass_preview_internet,
                R.string.liquid_glass_preview_wifi, ink, material);
        drawTile(canvas, mShapes[1], mIconBluetooth, R.string.liquid_glass_preview_bluetooth,
                R.string.liquid_glass_preview_earbuds, activeInk, material);
    }

    private void drawTile(Canvas canvas, RectF tile, Path icon, int title, int subtitle, int ink,
            int material) {
        final float chip = 38 * mDp;
        final float cx = tile.left + 12 * mDp + chip / 2;
        final float cy = tile.centerY();
        mFill.setColor(material == LiquidGlassSettings.SOLID && ink == 0xFFFFFFFF
                ? 0xFF3A3740 : ColorUtils.setAlphaComponent(ink, 40));
        canvas.drawCircle(cx, cy, chip / 2, mFill);
        drawIcon(canvas, icon, cx, cy, 20 * mDp, ink);

        final float x = cx + chip / 2 + 10 * mDp;
        mText.setColor(ink);
        mText.setTextSize(14 * mDp);
        canvas.drawText(getResources().getString(title), x, cy - 2 * mDp, mText);
        mText.setColor(ColorUtils.setAlphaComponent(ink, 200));
        mText.setTextSize(12 * mDp);
        canvas.drawText(getResources().getString(subtitle), x, cy + 14 * mDp, mText);
    }

    private void drawIcon(Canvas canvas, Path icon, float cx, float cy, float size, int color) {
        canvas.save();
        canvas.translate(cx - size / 2, cy - size / 2);
        canvas.scale(size / 24f, size / 24f);
        mStroke.setColor(color);
        mStroke.setStrokeWidth(2f);
        canvas.drawPath(icon, mStroke);
        canvas.restore();
    }

    private int accent(int tone) {
        final int res;
        switch (tone) {
            case 200: res = android.R.color.system_accent1_200; break;
            case 300: res = android.R.color.system_accent1_300; break;
            case 700: res = android.R.color.system_accent1_700; break;
            default: res = android.R.color.system_accent1_500; break;
        }
        return getContext().getColor(res);
    }

    private static int withAlpha(int color, float alpha) {
        return ColorUtils.setAlphaComponent(color, Math.round(alpha * 255));
    }
}
