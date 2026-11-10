/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.internal.util.catalyxx.GlassSettings;
import com.android.settingslib.utils.ThreadUtils;

/**
 * A chip of glass over the wallpaper in one of Liquid Glass's materials, for the material cards
 * and, larger, for Liquid's two edge styles. The page's live preview is the system's own glass
 * ({@link GlassPreview}).
 */
public class GlassPreviewView extends View {
    private static final String TAG = "GlassPreviewView";

    private static final float LIGHT_X = -0.5f;
    private static final float LIGHT_Y = -0.866f;
    private static final float REFRACTION = 0.6f;

    // A lens like the rim SurfaceFlinger draws, enough for a chip.
    private static final String SHADER = ""
            + "uniform shader blurred;\n"
            + "uniform shader sharp;\n"
            + "uniform float4 rect;\n"
            + "uniform float cornerRadius;\n"
            + "uniform float refraction;\n"
            + "uniform float lens;\n"
            + "uniform float2 lightDir;\n"
            + "float boxSdf(float2 p, float2 halfSize, float r) {\n"
            + "    float2 q = abs(p) - halfSize + float2(r);\n"
            + "    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;\n"
            + "}\n"
            + "half4 main(float2 xy) {\n"
            + "    float2 halfSize = max((rect.zw - rect.xy) * 0.5, float2(1.0));\n"
            + "    float2 p = xy - (rect.xy + rect.zw) * 0.5;\n"
            + "    float r = min(cornerRadius, min(halfSize.x, halfSize.y));\n"
            + "    float d = boxSdf(p, halfSize, r);\n"
            + "    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);\n"
            + "    float2 a = clamp(abs(p) / halfSize, float2(0.0), float2(1.0));\n"
            + "    float2 n = normalize(s * pow(a, float2(6.0)) + float2(0.00001));\n"
            + "    float band = clamp(min(halfSize.x, halfSize.y) * 0.5, 6.0, 96.0);\n"
            + "    float edge = 1.0 - clamp(-d / band, 0.0, 1.0);\n"
            + "    float bend = edge * edge * edge;\n"
            + "    float2 uv = xy - n * (bend * band * refraction * 0.9);\n"
            + "    float split = bend * refraction * 4.0;\n"
            + "    float3 soft = float3(blurred.eval(uv).rgb);\n"
            + "    soft.r = float(blurred.eval(uv + n * split).r);\n"
            + "    soft.b = float(blurred.eval(uv - n * split).b);\n"
            + "    float3 crisp = float3(sharp.eval(uv).rgb);\n"
            + "    crisp.r = float(sharp.eval(uv + n * split).r);\n"
            + "    crisp.b = float(sharp.eval(uv - n * split).b);\n"
            + "    float3 color = mix(soft, crisp, smoothstep(0.35, 1.0, edge) * 0.8 * lens);\n"
            + "    float luma = dot(color, float3(0.2126, 0.7152, 0.0722));\n"
            + "    color = mix(float3(luma), color, 1.0 + 0.2 * lens) * (1.0 + 0.03 * lens);\n"
            + "    float rim = 1.0 - smoothstep(0.0, 1.5 + 3.5 * refraction, -d);\n"
            + "    float light = dot(n, lightDir);\n"
            + "    color += rim * (0.08 + 0.5 * max(light, 0.0) + 0.18 * max(-light, 0.0)) *\n"
            + "            (0.5 + 0.5 * refraction) + edge * edge * (0.04 + 0.1 * max(light, 0.0));\n"
            + "    return half4(half3(clamp(color, float3(0.0), float3(1.0))), 1.0);\n"
            + "}\n";

    private static Bitmap sWallpaper;
    private static int sWallpaperId = -1;

    private final float mDp;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mWallMatrix = new Matrix();
    private final Path mClip = new Path();
    private final RectF mShape = new RectF();
    private final RenderNode mNode = new RenderNode("glass");
    private RuntimeShader mShader;
    private boolean mShaderFailed;
    private boolean mNodeDirty = true;
    private Bitmap mWallpaper;
    private float mCorner;
    private int mMaterial = GlassSettings.MATERIAL_FROSTED;
    private boolean mLens = true;
    private boolean mLarge;

    public GlassPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDp = getResources().getDisplayMetrics().density;
    }

    /** The material the chip is drawn in, one of GlassSettings' MATERIAL_ values. */
    public void setSwatch(int material) {
        setSwatch(material, mLens, mLarge);
    }

    /**
     * The material the chip is drawn in. For Liquid, {@code lens} is RR's lens edge, which keeps
     * the rim clear around the frosted middle; without it the frost goes to the edge, as
     * Catalyxx's does. A {@code large} chip fills most of the card, so its edge shows.
     */
    public void setSwatch(int material, boolean lens, boolean large) {
        mMaterial = material;
        mLens = lens;
        if (mLarge != large) {
            mLarge = large;
            layoutShape(getWidth(), getHeight());
        }
        mNodeDirty = true;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loadWallpaper();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutShape(w, h);
    }

    private void loadWallpaper() {
        final Context context = getContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            final Bitmap bitmap = wallpaper(context);
            ThreadUtils.postOnMainThread(() -> {
                mWallpaper = bitmap;
                layoutShape(getWidth(), getHeight());
                invalidate();
            });
        });
    }

    /** The home wallpaper, scaled down, or the built-in one where it can't be read. */
    public static synchronized Bitmap wallpaper(Context context) {
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

    private void layoutShape(int w, int h) {
        if (w == 0 || h == 0) {
            return;
        }
        final float pw = w * (mLarge ? 0.8f : 0.62f);
        final float ph = mLarge ? h * 0.64f : Math.min(h * 0.46f, 34 * mDp);
        mShape.set((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2);
        mCorner = Math.min(ph / 2, 22 * mDp);
        mWallMatrix.reset();
        if (mWallpaper != null) {
            final float scale = Math.max((float) w / mWallpaper.getWidth(),
                    (float) h / mWallpaper.getHeight());
            mWallMatrix.setScale(scale, scale);
            mWallMatrix.postTranslate((w - mWallpaper.getWidth() * scale) / 2,
                    (h - mWallpaper.getHeight() * scale) * 0.4f);
        }
        mNodeDirty = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        final float corner = 14 * mDp;
        canvas.save();
        mClip.reset();
        mClip.addRoundRect(0, 0, w, h, corner, corner, Path.Direction.CW);
        canvas.clipPath(mClip);
        drawWallpaper(canvas, w, h);
        if (mNodeDirty) {
            recordNode(w, h);
        }
        drawGlass(canvas);
        canvas.restore();
    }

    private void drawWallpaper(Canvas canvas, int w, int h) {
        if (mWallpaper != null) {
            canvas.drawBitmap(mWallpaper, mWallMatrix, null);
            return;
        }
        mFill.setShader(new LinearGradient(0, 0, w, h, accent(), Color.BLACK,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mFill);
        mFill.setShader(null);
    }

    private void recordNode(int w, int h) {
        mNodeDirty = false;
        if (mMaterial == GlassSettings.MATERIAL_SOLID) {
            return;
        }
        final float radius = 18 * mDp
                * (mMaterial == GlassSettings.MATERIAL_LIQUID ? 0.65f : 1f);
        mNode.setPosition(0, 0, w, h);
        final RecordingCanvas recording = mNode.beginRecording(w, h);
        drawWallpaper(recording, w, h);
        mNode.endRecording();
        mNode.setRenderEffect(effect(radius, sharpShader(w, h)));
    }

    // The unblurred wallpaper, which the lens bends at the rim.
    private Shader sharpShader(int w, int h) {
        if (mWallpaper != null) {
            final BitmapShader shader = new BitmapShader(mWallpaper, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
            shader.setLocalMatrix(mWallMatrix);
            return shader;
        }
        return new LinearGradient(0, 0, w, h, accent(), Color.BLACK, Shader.TileMode.CLAMP);
    }

    private RenderEffect effect(float radius, Shader sharp) {
        final RenderEffect blur = RenderEffect.createBlurEffect(radius, radius,
                Shader.TileMode.CLAMP);
        if (mMaterial != GlassSettings.MATERIAL_LIQUID) {
            return blur;
        }
        final RuntimeShader shader = shader();
        if (shader == null) {
            return blur;
        }
        shader.setInputShader("sharp", sharp);
        shader.setFloatUniform("rect", mShape.left, mShape.top, mShape.right, mShape.bottom);
        shader.setFloatUniform("cornerRadius", mCorner);
        shader.setFloatUniform("refraction", REFRACTION);
        shader.setFloatUniform("lens", mLens ? 1f : 0f);
        shader.setFloatUniform("lightDir", LIGHT_X, LIGHT_Y);
        return RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "blurred"), blur);
    }

    private RuntimeShader shader() {
        if (mShader == null && !mShaderFailed) {
            try {
                mShader = new RuntimeShader(SHADER);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Glass shader failed to compile", e);
                mShaderFailed = true;
            }
        }
        return mShader;
    }

    private void drawGlass(Canvas canvas) {
        if (mMaterial == GlassSettings.MATERIAL_SOLID) {
            mFill.setColor(0xFF2B2930);
            canvas.drawRoundRect(mShape, mCorner, mCorner, mFill);
            return;
        }
        if (canvas.isHardwareAccelerated()) {
            canvas.save();
            mClip.reset();
            mClip.addRoundRect(mShape, mCorner, mCorner, Path.Direction.CW);
            canvas.clipPath(mClip);
            canvas.drawRenderNode(mNode);
            canvas.restore();
        }
        // The panel's own color over the blur
        if (mMaterial == GlassSettings.MATERIAL_FROSTED) {
            mFill.setColor(0x5C241E24);
        } else {
            mFill.setShader(new LinearGradient(mShape.left, mShape.top, mShape.right,
                    mShape.bottom, new int[] {0x38FFFFFF, 0x0FFFFFFF, 0x2E10080C}, null,
                    Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(mShape, mCorner, mCorner, mFill);
        mFill.setShader(null);
    }

    private int accent() {
        return getContext().getColor(android.R.color.system_accent1_500);
    }
}
