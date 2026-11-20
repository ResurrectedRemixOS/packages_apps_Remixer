package com.resurrection.settings.charging;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

public final class ChargingUi {
    public final float density;
    public final int accent;
    public final int glow;
    public final int deep;

    public ChargingUi(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(context.getColor(android.R.color.system_accent1_500), hsl);
        float sat = hsl[1] < 0.12f ? 0 : 1;
        glow = hsl(hsl[0], 0.75f * sat, 0.38f);
        accent = hsl(hsl[0], 0.95f * sat, 0.74f);
        deep = hsl(hsl[0], 0.4f * sat, 0.09f);
    }

    private static int hsl(float hue, float saturation, float lightness) {
        return ColorUtils.HSLToColor(new float[] {hue, saturation, lightness});
    }

    public GradientDrawable stage() {
        GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientCenter(0.5f, 1.12f);
        bg.setGradientRadius(320 * density);
        bg.setColors(new int[] {glow, deep}, new float[] {0f, 0.72f});
        bg.setCornerRadius(26 * density);
        return bg;
    }

    public GradientDrawable shape(int fill, int stroke, float radiusDp, float strokeDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        if (stroke != 0) {
            shape.setStroke(Math.round(strokeDp * density), stroke);
        }
        shape.setCornerRadius(radiusDp * density);
        return shape;
    }

    public TextView chip(Context context, CharSequence text, boolean onStage) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, onStage ? 12 : 13);
        chip.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        int pad = Math.round((onStage ? 12 : 14) * density);
        chip.setPadding(pad, 0, pad, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Math.round((onStage ? 28 : 36) * density));
        lp.setMarginEnd(Math.round(6 * density));
        chip.setLayoutParams(lp);
        chip.setClickable(true);
        chip.setFocusable(true);
        return chip;
    }

    public void setStageChip(TextView chip, boolean on) {
        chip.setSelected(on);
        chip.setBackground(on ? shape(ColorUtils.setAlphaComponent(glow, 0x8C), accent, 14, 1)
                : shape(0x4D000000, 0x40FFFFFF, 14, 1));
    }

    void setChip(TextView chip, boolean on, int surface) {
        chip.setSelected(on);
        chip.setBackground(on ? shape(ColorUtils.setAlphaComponent(glow, 0x52), accent, 18, 2)
                : shape(surface, 0, 18, 0));
    }

    static int batteryLevel(Context context) {
        BatteryManager bm = context.getSystemService(BatteryManager.class);
        int level = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        return level >= 0 && level <= 100 ? level : 47;
    }
}
