package com.resurrection.settings;

import android.content.Context;

import com.android.settings.core.BasePreferenceController;

/** Remixer is always shown at the top of Settings. */
public class TopLevelRemixerSettingsPreferenceController extends BasePreferenceController {

    public TopLevelRemixerSettingsPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }
}
