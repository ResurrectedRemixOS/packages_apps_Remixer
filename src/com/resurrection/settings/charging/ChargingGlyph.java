package com.resurrection.settings.charging;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.PathParser;

import java.util.HashMap;
import java.util.Map;

final class ChargingGlyph extends Drawable {
    private static final float SIZE = 40f;
    private static final Map<String, Part[]> GLYPHS = new HashMap<>();

    static {
        GLYPHS.put("none", new Part[] {
                new Part("M6,20 A14,14 0 1 0 34,20 A14,14 0 1 0 6,20 Z", 0, 0xFFD6C1C4, 1.6f, 1f, 1f),
                new Part("M10,10 L30,30", 0, 0xFFD6C1C4, 1.6f, 1f, 1f)});
        GLYPHS.put("rr_neon", new Part[] {
                new Part("M 23.46 7.50 L 36.43 7.50 A 0.58 0.58 0 0 1 36.98 8.27 C 36.18 10.62 34.78 13.11 33.07 13.11 L 23.79 13.11 C 20.69 13.11 18.30 15.76 18.29 15.78 L 9.54 26.43 C 9.30 26.71 7.02 28.52 3.18 26.08 A 0.38 0.38 0 0 1 3.09 25.52 L 13.32 13.07 C 16.13 9.65 19.94 7.50 23.46 7.50 Z", 0xFF840009, 0xFFFF1F55, 1.3f, 1f, 1f),
                new Part("M 25.99 16.39 L 36.17 16.39 A 0.53 0.53 0 0 1 36.67 17.10 C 35.42 20.54 34.12 20.96 33.49 20.96 L 25.96 20.96 C 24.59 20.96 22.70 21.85 21.44 23.39 L 14.67 31.62 C 14.66 31.64 12.90 33.42 9.62 31.37 A 0.39 0.39 0 0 1 9.52 30.79 L 17.60 20.96 C 20.20 17.79 23.29 16.39 25.99 16.39 Z", 0xFF840009, 0xFFFF1F55, 1.3f, 1f, 1f)});
        GLYPHS.put("rr_defib", new Part[] {
                new Part("M2,22 H11 L13,18 L15,22 L17,22 L19,8 L22,32 L24,22 H38", 0, 0xFFFF1F4F, 1.8f, 1f, 1f)});
        GLYPHS.put("rr_pulse", new Part[] {
                new Part("M7,20 A13,13 0 1 0 33,20 A13,13 0 1 0 7,20 Z", 0, 0xFFFFFFFF, 3f, 1f, 0.18f),
                new Part("M20,7 A13,13 0 0 1 22.5,32.8", 0, 0xFFFF1F4F, 3f, 1f, 1f),
                new Part("M13,21 H17 L18.5,17 L20.5,25 L22,21 H27", 0, 0xFFFF1F4F, 1.2f, 1f, 1f)});
        GLYPHS.put("pixel", new Part[] {
                new Part("M6,36 A14,14 0 0 1 34,36", 0, 0xFF9EC3FF, 1.6f, 1f, 0.45f),
                new Part("M11,36 A9,9 0 0 1 29,36", 0, 0xFF9EC3FF, 1.6f, 1f, 0.75f),
                new Part("M16,36 A4,4 0 0 1 24,36", 0, 0xFFFFFFFF, 1.6f, 1f, 1f)});
        GLYPHS.put("oneplus", new Part[] {
                new Part("M7,20 A13,13 0 1 0 33,20 A13,13 0 1 0 7,20 Z", 0, 0xFFFFFFFF, 3f, 1f, 0.15f),
                new Part("M20,7 A13,13 0 0 1 33,20", 0, 0xFFFF5A36, 3f, 1f, 1f)});
        GLYPHS.put("oneui", new Part[] {
                new Part("M8,14 H32 A6,6 0 0 1 32,26 H8 A6,6 0 0 1 8,14 Z", 0xFF4E5871, 0, 0f, 1f, 1f),
                new Part("M8,14 H21 V26 H8 A6,6 0 0 1 8,14 Z", 0xFF3DDC84, 0, 0f, 1f, 1f),
                new Part("M5,20 A4,4 0 1 0 13,20 A4,4 0 1 0 5,20 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M9.6,17 L7.6,20.4 H9.4 L8.6,23 L10.8,19.6 H9 Z", 0xFF3DDC84, 0, 0f, 1f, 1f)});
        GLYPHS.put("hyperos", new Part[] {
                new Part("M11,15 A9,9 0 1 0 29,15 A9,9 0 1 0 11,15 Z", 0xFF19C08A, 0, 0f, 1f, 1f),
                new Part("M17.8,29 A2.2,2.2 0 1 0 22.2,29 A2.2,2.2 0 1 0 17.8,29 Z", 0xFF1AA3FF, 0, 0f, 1f, 1f),
                new Part("M15.4,34 A1.6,1.6 0 1 0 18.6,34 A1.6,1.6 0 1 0 15.4,34 Z", 0xFF1AA3FF, 0, 0f, 1f, 1f),
                new Part("M21.8,37 A1.2,1.2 0 1 0 24.2,37 A1.2,1.2 0 1 0 21.8,37 Z", 0xFF1AA3FF, 0, 0f, 1f, 1f)});
        GLYPHS.put("originos", new Part[] {
                new Part("M12,20 A8,8 0 1 0 28,20 A8,8 0 1 0 12,20 Z", 0xFF4AA8FF, 0, 0f, 1f, 1f),
                new Part("M3,20 A17,5 0 1 0 37,20 A17,5 0 1 0 3,20 Z", 0, 0xFFA0D2FF, 1f, 1f, 1f)});
        GLYPHS.put("supercharge", new Part[] {
                new Part("M8,20 A12,12 0 1 0 32,20 A12,12 0 1 0 8,20 Z", 0, 0xFF36E0A0, 1.8f, 1f, 1f),
                new Part("M22,10 L14,22 H19.5 L17.5,30 L26,18 H20.5 Z", 0xFF36E0A0, 0, 0f, 1f, 1f),
                new Part("M1,20 H6", 0, 0xFF36E0A0, 1.2f, 1f, 0.7f),
                new Part("M34,20 H39", 0, 0xFF36E0A0, 1.2f, 1f, 0.7f)});
        GLYPHS.put("moto", new Part[] {
                new Part("M14.5,31.5 A13,13 0 1 1 25.5,31.5", 0, 0xFF38B6FF, 2f, 1f, 1f),
                new Part("M20,40 V27", 0, 0xFF9FE0FF, 2f, 1f, 1f)});
        GLYPHS.put("nothing", new Part[] {
                new Part("M11.12,34.61 A1,1 0 1 0 13.12,34.61 A1,1 0 1 0 11.12,34.61 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M12.07,32.0 A1,1 0 1 0 14.07,32.0 A1,1 0 1 0 12.07,32.0 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M13.86,29.87 A1,1 0 1 0 15.86,29.87 A1,1 0 1 0 13.86,29.87 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M16.26,28.48 A1,1 0 1 0 18.26,28.48 A1,1 0 1 0 16.26,28.48 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M19.0,28.0 A1,1 0 1 0 21.0,28.0 A1,1 0 1 0 19.0,28.0 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M21.74,28.48 A1,1 0 1 0 23.74,28.48 A1,1 0 1 0 21.74,28.48 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M24.14,29.87 A1,1 0 1 0 26.14,29.87 A1,1 0 1 0 24.14,29.87 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M25.93,32.0 A1,1 0 1 0 27.93,32.0 A1,1 0 1 0 25.93,32.0 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M26.88,34.61 A1,1 0 1 0 28.88,34.61 A1,1 0 1 0 26.88,34.61 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M6.2,33.74 A1,1 0 1 0 8.2,33.74 A1,1 0 1 0 6.2,33.74 Z", 0xFFFFFFFF, 0, 0f, 0.7f, 1f),
                new Part("M7.74,29.5 A1,1 0 1 0 9.74,29.5 A1,1 0 1 0 7.74,29.5 Z", 0xFFFFFFFF, 0, 0f, 0.7f, 1f),
                new Part("M10.64,26.04 A1,1 0 1 0 12.64,26.04 A1,1 0 1 0 10.64,26.04 Z", 0xFFFFFFFF, 0, 0f, 0.7f, 1f),
                new Part("M14.55,23.78 A1,1 0 1 0 16.55,23.78 A1,1 0 1 0 14.55,23.78 Z", 0xFFFFFFFF, 0, 0f, 0.7f, 1f),
                new Part("M19.0,23.0 A1,1 0 1 0 21.0,23.0 A1,1 0 1 0 19.0,23.0 Z", 0xFFFFFFFF, 0, 0f, 0.7f, 1f),
                new Part("M23.45,23.78 A1,1 0 1 0 25.45,23.78 A1,1 0 1 0 23.45,23.78 Z", 0xFFFFFFFF, 0, 0f, 0.7f, 1f),
                new Part("M27.36,26.04 A1,1 0 1 0 29.36,26.04 A1,1 0 1 0 27.36,26.04 Z", 0xFFFFFFFF, 0, 0f, 0.7f, 1f),
                new Part("M30.26,29.5 A1,1 0 1 0 32.260000000000005,29.5 A1,1 0 1 0 30.26,29.5 Z", 0xFFFFFFFF, 0, 0f, 0.7f, 1f),
                new Part("M31.799999999999997,33.74 A1,1 0 1 0 33.8,33.74 A1,1 0 1 0 31.799999999999997,33.74 Z", 0xFFFFFFFF, 0, 0f, 0.7f, 1f),
                new Part("M1.27,32.87 A1,1 0 1 0 3.27,32.87 A1,1 0 1 0 1.27,32.87 Z", 0xFFFFFFFF, 0, 0f, 0.4f, 1f),
                new Part("M3.41,27.0 A1,1 0 1 0 5.41,27.0 A1,1 0 1 0 3.41,27.0 Z", 0xFFFFFFFF, 0, 0f, 0.4f, 1f),
                new Part("M7.43,22.21 A1,1 0 1 0 9.43,22.21 A1,1 0 1 0 7.43,22.21 Z", 0xFFFFFFFF, 0, 0f, 0.4f, 1f),
                new Part("M12.84,19.09 A1,1 0 1 0 14.84,19.09 A1,1 0 1 0 12.84,19.09 Z", 0xFFFFFFFF, 0, 0f, 0.4f, 1f),
                new Part("M19.0,18.0 A1,1 0 1 0 21.0,18.0 A1,1 0 1 0 19.0,18.0 Z", 0xFFFFFFFF, 0, 0f, 0.4f, 1f),
                new Part("M25.16,19.09 A1,1 0 1 0 27.16,19.09 A1,1 0 1 0 25.16,19.09 Z", 0xFFFFFFFF, 0, 0f, 0.4f, 1f),
                new Part("M30.57,22.21 A1,1 0 1 0 32.57,22.21 A1,1 0 1 0 30.57,22.21 Z", 0xFFFFFFFF, 0, 0f, 0.4f, 1f),
                new Part("M34.59,27.0 A1,1 0 1 0 36.59,27.0 A1,1 0 1 0 34.59,27.0 Z", 0xFFFFFFFF, 0, 0f, 0.4f, 1f),
                new Part("M36.73,32.87 A1,1 0 1 0 38.73,32.87 A1,1 0 1 0 36.73,32.87 Z", 0xFFFFFFFF, 0, 0f, 0.4f, 1f)});
        GLYPHS.put("bliss_flash", new Part[] {
                new Part("M15,20 A5,5 0 1 0 25,20 A5,5 0 1 0 15,20 Z", 0xFFFFFFFF, 0, 0f, 0.9f, 1f),
                new Part("M28.0,20.0 L35.0,20.0", 0, 0xFFFFFFFF, 1.6f, 1f, 0.6f),
                new Part("M25.7,25.7 L30.6,30.6", 0, 0xFFFFFFFF, 1.6f, 1f, 0.6f),
                new Part("M20.0,28.0 L20.0,35.0", 0, 0xFFFFFFFF, 1.6f, 1f, 0.6f),
                new Part("M14.3,25.7 L9.4,30.6", 0, 0xFFFFFFFF, 1.6f, 1f, 0.6f),
                new Part("M12.0,20.0 L5.0,20.0", 0, 0xFFFFFFFF, 1.6f, 1f, 0.6f),
                new Part("M14.3,14.3 L9.4,9.4", 0, 0xFFFFFFFF, 1.6f, 1f, 0.6f),
                new Part("M20.0,12.0 L20.0,5.0", 0, 0xFFFFFFFF, 1.6f, 1f, 0.6f),
                new Part("M25.7,14.3 L30.6,9.4", 0, 0xFFFFFFFF, 1.6f, 1f, 0.6f)});
        GLYPHS.put("bliss_glow", new Part[] {
                new Part("M7,4 H33 A4,4 0 0 1 37,8 V32 A4,4 0 0 1 33,36 H7 A4,4 0 0 1 3,32 V8 A4,4 0 0 1 7,4 Z", 0, 0xFFFFB347, 5f, 1f, 0.35f),
                new Part("M7,4 H33 A4,4 0 0 1 37,8 V32 A4,4 0 0 1 33,36 H7 A4,4 0 0 1 3,32 V8 A4,4 0 0 1 7,4 Z", 0, 0xFFFFB347, 1.6f, 1f, 1f)});
        GLYPHS.put("bliss_battery", new Part[] {
                new Part("M14,9 H26 V35 H14 Z", 0, 0xFFFFFFFF, 1.6f, 1f, 1f),
                new Part("M17,6 H23 V9 H17 Z", 0xFFFFFFFF, 0, 0f, 1f, 1f),
                new Part("M15.6,23 H24.4 V33.4 H15.6 Z", 0xFFFF8A3D, 0, 0f, 1f, 1f)});
    }

    private final Part[] mParts;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    ChargingGlyph(String key) {
        Part[] parts = GLYPHS.get(key);
        mParts = parts != null ? parts : GLYPHS.get("none");
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / SIZE, getBounds().height() / SIZE);
        for (Part part : mParts) {
            if (part.fill != 0) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(part.fill);
                mPaint.setAlpha(Math.round(255 * part.fillAlpha));
                canvas.drawPath(part.path, mPaint);
            }
            if (part.stroke != 0) {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(part.width);
                mPaint.setColor(part.stroke);
                mPaint.setAlpha(Math.round(255 * part.strokeAlpha));
                canvas.drawPath(part.path, mPaint);
            }
        }
        canvas.restore();
    }

    @Override
    public int getIntrinsicWidth() {
        return -1;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private static final class Part {
        final Path path;
        final int fill;
        final int stroke;
        final float width;
        final float fillAlpha;
        final float strokeAlpha;

        Part(String d, int fill, int stroke, float width, float fillAlpha, float strokeAlpha) {
            path = PathParser.createPathFromPathData(d);
            this.fill = fill;
            this.stroke = stroke;
            this.width = width;
            this.fillAlpha = fillAlpha;
            this.strokeAlpha = strokeAlpha;
        }
    }
}
