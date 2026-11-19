package com.resurrection.settings.charging;

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class ChargingEffectsSettings extends SettingsPreferenceFragment {
    private static final int IMAGES = 9;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_charging_effects);

        ListPreference image = findPreference("charging_image_flow_style");
        CharSequence[] entries = new CharSequence[IMAGES];
        CharSequence[] values = new CharSequence[IMAGES];
        for (int i = 0; i < IMAGES; i++) {
            entries[i] = getString(R.string.charging_effects_image_n, i + 1);
            values[i] = String.valueOf(i);
        }
        image.setEntries(entries);
        image.setEntryValues(values);

        ListPreference style = findPreference("charging_animation_style");
        applyStyle(style.getValue());
        style.setOnPreferenceChangeListener((preference, value) -> {
            applyStyle((String) value);
            return true;
        });
    }

    private void applyStyle(String value) {
        int style = value != null ? Integer.parseInt(value) : 0;
        boolean images = style >= 8;
        show("charging_color_mode", !images);
        show("charging_ripple_opacity", style == 0);
        show("charging_glow_intensity", style == 1 || style == 3 || style == 5);
        show("charging_image_flow_style", images);
        show("charging_image_flow_opacity", images);
        show("charging_image_flow_strength", images);
    }

    private void show(String key, boolean visible) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setVisible(visible);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
