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
    /** Read by SystemUI: draw the strip while this page is open. */
    private static final String KEY_SHOW_STRIP = "gesture_anywhere_show_strip";
    private static final String KEY_PREVIEW = "gesture_anywhere_preview";
    private static final String[][] BUILT_IN = {{"gesture_anywhere_c", "camera"},
            {"gesture_anywhere_m", "music"}, {"gesture_anywhere_down", "flashlight"},
            {"gesture_anywhere_o", "screenshot"}};

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_gesture_anywhere);
        for (String[] shape : BUILT_IN) {
            Preference row = findPreference(shape[0]);
            row.setOnPreferenceClickListener(p -> {
                GestureRecordActivity.pickAction(getPrefContext(), action -> {
                    Settings.System.putStringForUser(getContentResolver(), shape[0], action,
                            UserHandle.USER_CURRENT);
                    row.setSummary(GestureRecordActivity.label(getContext(), action));
                });
                return true;
            });
        }
        findPreference(KEY_PREVIEW).setOnPreferenceChangeListener((p, value) -> {
            showStrip((Boolean) value);
            return true;
        });
        findPreference("gesture_anywhere").setOnPreferenceChangeListener((p, value) -> {
            getView().post(() -> showStrip(previewOn()));
            return true;
        });
    }

    private boolean previewOn() {
        return Settings.System.getIntForUser(getContentResolver(), KEY_PREVIEW, 1,
                UserHandle.USER_CURRENT) == 1;
    }

    private void showStrip(boolean show) {
        Settings.System.putIntForUser(getContentResolver(), KEY_SHOW_STRIP, show ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    @Override
    public void onPause() {
        super.onPause();
        showStrip(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        showStrip(previewOn());
        for (String[] shape : BUILT_IN) {
            String action = Settings.System.getStringForUser(getContentResolver(), shape[0],
                    UserHandle.USER_CURRENT);
            findPreference(shape[0]).setSummary(
                    GestureRecordActivity.label(getContext(), action != null ? action : shape[1]));
        }
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
