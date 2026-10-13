/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.chargingglow;

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/** Charging glow options; SystemUI's ChargingGlowView reads them. */
public class ChargingGlowSettings extends SettingsPreferenceFragment {
    private static final String KEY_COLOR = "charging_glow_color";
    private static final String KEY_CUSTOM_COLOR = "charging_glow_custom_color";
    private static final String COLOR_CUSTOM = "3";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_charging_glow);

        ListPreference color = findPreference(KEY_COLOR);
        Preference custom = findPreference(KEY_CUSTOM_COLOR);
        custom.setVisible(COLOR_CUSTOM.equals(color.getValue()));
        color.setOnPreferenceChangeListener((preference, value) -> {
            custom.setVisible(COLOR_CUSTOM.equals(value));
            return true;
        });
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
