/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.chargereport;

import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import org.json.JSONArray;
import org.json.JSONObject;

/** Charge report options and the last 7 days of charges, which SystemUI records. */
public class ChargeReportSettings extends SettingsPreferenceFragment {
    private static final String KEY_HISTORY = "charge_report_history";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_charge_report);
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceCategory history = findPreference("charge_report_history_list");
        history.removeAll();
        JSONArray sessions;
        try {
            String json = Settings.System.getStringForUser(getContentResolver(), KEY_HISTORY,
                    UserHandle.USER_CURRENT);
            sessions = new JSONArray(json != null ? json : "[]");
        } catch (Exception e) {
            sessions = new JSONArray();
        }
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.optJSONObject(i);
            if (s == null) continue;
            ChargeReportPreference row = new ChargeReportPreference(getPrefContext());
            row.setCurve(s.optJSONArray("curve"));
            row.setTitle(DateUtils.formatDateTime(getContext(), s.optLong("start"),
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_TIME
                            | DateUtils.FORMAT_ABBREV_WEEKDAY));
            row.setSummary(getString(R.string.charge_report_row,
                    s.optInt("to") - s.optInt("from"), s.optLong("minutes"),
                    s.optDouble("peak"), s.optDouble("avg"), s.optDouble("temp")));
            history.addPreference(row);
        }
        if (sessions.length() == 0) {
            Preference empty = new Preference(getPrefContext());
            empty.setSelectable(false);
            empty.setSummary(R.string.charge_report_empty);
            history.addPreference(empty);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
