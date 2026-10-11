package com.resurrection.settings.edgelight;

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/** Edge lighting: a live preview, then the options SystemUI's EdgeLightView reads. */
public class EdgeLightSettings extends SettingsPreferenceFragment {
    private static final String KEY_COLOR_MODE = "edge_light_color_mode";
    private static final String KEY_CUSTOM_COLOR = "edge_light_custom_color";
    private static final String COLOR_MODE_CUSTOM = "custom";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_edge_light);

        ListPreference mode = findPreference(KEY_COLOR_MODE);
        Preference custom = findPreference(KEY_CUSTOM_COLOR);
        custom.setVisible(COLOR_MODE_CUSTOM.equals(mode.getValue()));
        mode.setOnPreferenceChangeListener((preference, value) -> {
            custom.setVisible(COLOR_MODE_CUSTOM.equals(value));
            return true;
        });
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
