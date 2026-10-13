/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.gestures;

import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import org.json.JSONArray;

/** Gesture anywhere: the strip, the built-in shapes and your own. */
public class GestureAnywhereSettings extends SettingsPreferenceFragment {
    static final String KEY_CUSTOM = "gesture_anywhere_custom";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_gesture_anywhere);
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceCategory list = findPreference("gesture_anywhere_custom_list");
        list.removeAll();
        JSONArray custom = custom();
        for (int i = 0; i < custom.length(); i++) {
            final int index = i;
            String action = custom.optJSONObject(i).optString("action");
            Preference row = new Preference(getPrefContext());
            row.setTitle(getString(R.string.gesture_anywhere_yours, i + 1));
            row.setSummary(GestureRecordActivity.label(getContext(), action));
            row.setOnPreferenceClickListener(p -> {
                new AlertDialog.Builder(getContext())
                        .setTitle(row.getTitle())
                        .setPositiveButton(R.string.gesture_anywhere_remove, (d, w) -> {
                            JSONArray all = custom();
                            all.remove(index);
                            Settings.System.putStringForUser(getContentResolver(), KEY_CUSTOM,
                                    all.toString(), UserHandle.USER_CURRENT);
                            onResume();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
            list.addPreference(row);
        }
    }

    private JSONArray custom() {
        try {
            String json = Settings.System.getStringForUser(getContentResolver(), KEY_CUSTOM,
                    UserHandle.USER_CURRENT);
            return new JSONArray(json != null ? json : "[]");
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
