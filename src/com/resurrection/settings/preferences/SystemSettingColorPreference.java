package com.resurrection.settings.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.resurrection.settings.ColorPadView;
import com.resurrection.settings.HueBarView;

/** A color kept as an int in Settings.System, picked on the accent page's pad and hue bar. */
public class SystemSettingColorPreference extends Preference {
    private int mDefault = Color.WHITE;

    public SystemSettingColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
        setWidgetLayoutResource(R.layout.remixer_color_swatch);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        String value = a.getString(index);
        return value != null ? Color.parseColor(value) : Color.WHITE;
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        if (defaultValue instanceof Integer) {
            mDefault = (Integer) defaultValue;
        }
        setSummary(hex(color()));
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View swatch = holder.findViewById(R.id.color_swatch);
        if (swatch != null) {
            swatch.setBackground(dot(color()));
        }
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.remixer_color_dialog, null);
        ColorPadView pad = view.findViewById(R.id.color_pad);
        HueBarView hue = view.findViewById(R.id.color_hue);
        View preview = view.findViewById(R.id.color_preview);
        TextView label = view.findViewById(R.id.color_hex);
        float[] hsv = new float[3];
        Color.colorToHSV(color(), hsv);
        Runnable update = () -> {
            int color = Color.HSVToColor(hsv);
            pad.setColor(hsv[0], hsv[1], hsv[2]);
            hue.setValue(hsv[0]);
            preview.setBackground(dot(color));
            label.setText(hex(color));
        };
        pad.setOnChangeListener((saturation, value) -> {
            hsv[1] = saturation;
            hsv[2] = value;
            update.run();
        });
        hue.setOnChangeListener(value -> {
            hsv[0] = value;
            update.run();
        });
        update.run();
        new AlertDialog.Builder(context)
                .setTitle(getTitle())
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int color = Color.HSVToColor(hsv);
                    if (callChangeListener(color)) {
                        Settings.System.putIntForUser(context.getContentResolver(), getKey(),
                                color, UserHandle.USER_CURRENT);
                        setSummary(hex(color));
                        notifyChanged();
                    }
                })
                .show();
    }

    private int color() {
        return Settings.System.getIntForUser(getContext().getContentResolver(), getKey(),
                mDefault, UserHandle.USER_CURRENT);
    }

    private GradientDrawable dot(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        shape.setStroke(Math.round(getContext().getResources().getDisplayMetrics().density),
                0x40808080);
        return shape;
    }

    private static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }
}
