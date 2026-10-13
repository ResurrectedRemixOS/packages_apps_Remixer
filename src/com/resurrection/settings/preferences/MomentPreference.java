/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.preferences;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settingslib.Utils;

import lineageos.preference.SystemSettingSwitchPreference;

/**
 * An edge light moment: a switch, and a color dot that opens the color picker. The color is kept
 * in {@code <key>_color}; 0 means the moment's own default, the accent for most.
 */
public class MomentPreference extends SystemSettingSwitchPreference {
    private static final String NOTIFICATIONS = "edge_light_moment_notifications";

    public MomentPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        updateDot();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View frame = holder.findViewById(android.R.id.icon_frame);
        if (frame == null) {
            frame = holder.findViewById(androidx.preference.R.id.icon_frame);
        }
        if (frame == null || !hasColor()) {
            return;
        }
        frame.setOnClickListener(v -> SystemSettingColorPreference.showDialog(getContext(),
                getTitle(), color(), () -> store(0), this::store));
        frame.setContentDescription(getContext().getString(R.string.edge_light_moment_color));
    }

    /** Notifications keep the color chosen under Look. */
    private boolean hasColor() {
        return !NOTIFICATIONS.equals(getKey());
    }

    private int defaultColor() {
        switch (getKey()) {
            case "edge_light_moment_alarms":
                return 0xFFFFB02E;
            case "edge_light_moment_plugged":
                return 0xFF3DDC84;
            case "edge_light_moment_battery_low":
                return 0xFFFF3030;
            default:
                return Utils.getColorAccentDefaultColor(getContext());
        }
    }

    private int color() {
        int color = hasColor() ? Settings.System.getIntForUser(getContext().getContentResolver(),
                getKey() + "_color", 0, UserHandle.USER_CURRENT) : 0;
        return color != 0 ? color : defaultColor();
    }

    private void store(int color) {
        Settings.System.putIntForUser(getContext().getContentResolver(), getKey() + "_color",
                color, UserHandle.USER_CURRENT);
        updateDot();
    }

    private void updateDot() {
        GradientDrawable dot = SystemSettingColorPreference.dot(getContext(), color());
        int size = Math.round(18 * getContext().getResources().getDisplayMetrics().density);
        dot.setSize(size, size);
        setIcon(dot);
    }
}
