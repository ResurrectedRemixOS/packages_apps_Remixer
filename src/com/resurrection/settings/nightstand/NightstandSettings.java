/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.nightstand;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.resurrection.settings.stage.LockStageActivity;

import java.util.Calendar;

/** Remixer › Lock screen › Nightstand: when it runs, the night window, how dim, which face. */
public class NightstandSettings extends SettingsPreferenceFragment {
    static final String KEY_FROM = "rr_nightstand_from";
    static final String KEY_TO = "rr_nightstand_to";
    static final String KEY_FACE = "rr_nightstand_face";
    static final String KEY_LAYOUT = "rr_nightstand_layout";
    private static final int DEFAULT_FROM = 22 * 60;
    private static final int DEFAULT_TO = 7 * 60;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_nightstand);
        timeRow(KEY_FROM, DEFAULT_FROM);
        timeRow(KEY_TO, DEFAULT_TO);

        ListPreference face = findPreference(KEY_FACE);
        String[] names = getResources().getStringArray(R.array.clock_face_names);
        CharSequence[] entries = new CharSequence[names.length];
        CharSequence[] values = new CharSequence[names.length];
        entries[0] = getString(R.string.nightstand_face_keep);
        values[0] = "-1";
        for (int i = 1; i < names.length; i++) {
            entries[i] = names[i];
            values[i] = String.valueOf(i);
        }
        face.setEntries(entries);
        face.setEntryValues(values);
        if (face.getValue() == null) face.setValue("-1");

        findPreference(KEY_LAYOUT).setOnPreferenceClickListener(p -> {
            startActivity(new Intent(getContext(), LockStageActivity.class)
                    .putExtra(LockStageActivity.EXTRA_NIGHT, true));
            return true;
        });
    }

    /** A time of day kept as minutes since midnight. */
    private void timeRow(String key, int def) {
        Preference row = findPreference(key);
        row.setSummary(format(minutes(key, def)));
        row.setOnPreferenceClickListener(p -> {
            int now = minutes(key, def);
            new TimePickerDialog(getContext(), (view, hour, minute) -> {
                Settings.System.putIntForUser(getContentResolver(), key, hour * 60 + minute,
                        UserHandle.USER_CURRENT);
                row.setSummary(format(hour * 60 + minute));
            }, now / 60, now % 60, DateFormat.is24HourFormat(getContext())).show();
            return true;
        });
    }

    private int minutes(String key, int def) {
        return Settings.System.getIntForUser(getContentResolver(), key, def,
                UserHandle.USER_CURRENT);
    }

    private CharSequence format(int minutes) {
        Calendar at = Calendar.getInstance();
        at.set(Calendar.HOUR_OF_DAY, minutes / 60);
        at.set(Calendar.MINUTE, minutes % 60);
        return DateFormat.getTimeFormat(getContext()).format(at.getTime());
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
