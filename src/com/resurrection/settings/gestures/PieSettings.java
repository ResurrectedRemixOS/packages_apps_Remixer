/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.gestures;

import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.MultiSelectListPreference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/** Pie controls; SystemUI's PieController reads these. */
public class PieSettings extends SettingsPreferenceFragment {
    private static final String KEY_ITEMS = "pie_items";
    private static final String DEFAULT_ITEMS =
            "back,home,recents,notifications,flashlight,screenshot,power";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_pie);
        MultiSelectListPreference picker = findPreference("pie_items_picker");
        String current = Settings.System.getStringForUser(getContentResolver(), KEY_ITEMS,
                UserHandle.USER_CURRENT);
        picker.setValues(new HashSet<>(Arrays.asList(
                (current != null ? current : DEFAULT_ITEMS).split(","))));
        updateSummary(picker);
        picker.setOnPreferenceChangeListener((preference, value) -> {
            @SuppressWarnings("unchecked")
            Set<String> chosen = (Set<String>) value;
            if (chosen.isEmpty()) return false;
            // Saved in the list's order: the first three make the inner ring.
            List<String> ordered = new ArrayList<>();
            for (CharSequence item : picker.getEntryValues()) {
                if (chosen.contains(item.toString())) ordered.add(item.toString());
            }
            Settings.System.putStringForUser(getContentResolver(), KEY_ITEMS,
                    TextUtils.join(",", ordered), UserHandle.USER_CURRENT);
            picker.setValues(chosen);
            updateSummary(picker);
            return false;
        });
    }

    private static void updateSummary(MultiSelectListPreference picker) {
        List<String> names = new ArrayList<>();
        CharSequence[] values = picker.getEntryValues();
        for (int i = 0; i < values.length; i++) {
            if (picker.getValues().contains(values[i].toString())) {
                names.add(picker.getEntries()[i].toString());
            }
        }
        picker.setSummary(TextUtils.join(", ", names));
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
