/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.modes;

import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.notification.modes.ZenMode;
import com.android.settingslib.notification.modes.ZenModesBackend;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.resurrection.settings.Looks;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A Look for each Android Mode: Sleep, Work, Driving and the ones you made. */
public class RemixModesSettings extends SettingsPreferenceFragment {
    /** JSON object: mode id (or "everyday") to the name of a Look. */
    static final String KEY_MAPPING = "rr_mode_looks";
    static final String KEY_WALLPAPER = "rr_mode_looks_wallpaper";
    static final String EVERYDAY = "everyday";

    private JSONObject mMapping = new JSONObject();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
        build();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }

    private void build() {
        PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();
        Context context = getContext();
        mMapping = readMapping();
        List<String> names = new ArrayList<>();
        for (JSONObject look : Looks.all(context)) names.add(look.optString("name"));
        if (names.isEmpty()) {
            Preference none = new Preference(context);
            none.setTitle(R.string.remix_modes_none);
            none.setSelectable(false);
            screen.addPreference(none);
            return;
        }

        Preference everyday = new Preference(context);
        everyday.setTitle(R.string.remix_modes_everyday);
        everyday.setSummary(summary(EVERYDAY, R.string.remix_modes_everyday_summary));
        everyday.setOnPreferenceClickListener(p -> {
            pick(EVERYDAY, getString(R.string.remix_modes_everyday), names);
            return true;
        });
        screen.addPreference(everyday);

        SwitchPreferenceCompat wallpaper = new SwitchPreferenceCompat(context);
        wallpaper.setTitle(R.string.remix_modes_wallpaper);
        wallpaper.setSummary(R.string.remix_modes_wallpaper_summary);
        wallpaper.setChecked(Settings.System.getIntForUser(getContentResolver(), KEY_WALLPAPER, 1,
                UserHandle.USER_CURRENT) == 1);
        wallpaper.setOnPreferenceChangeListener((p, value) -> {
            Settings.System.putIntForUser(getContentResolver(), KEY_WALLPAPER,
                    Boolean.TRUE.equals(value) ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        });
        screen.addPreference(wallpaper);

        PreferenceCategory modes = new PreferenceCategory(context);
        modes.setTitle(R.string.remix_modes_modes);
        screen.addPreference(modes);
        for (ZenMode mode : ZenModesBackend.getInstance(context).getModes()) {
            Preference row = new Preference(context);
            row.setTitle(mode.getName());
            row.setSummary(summary(mode.getId(), R.string.remix_modes_no_change));
            row.setOnPreferenceClickListener(p -> {
                pick(mode.getId(), mode.getName(), names);
                return true;
            });
            modes.addPreference(row);
        }
    }

    private CharSequence summary(String id, int none) {
        String name = mMapping.optString(id);
        return TextUtils.isEmpty(name) ? getString(none) : name;
    }

    private void pick(String id, String title, List<String> names) {
        List<String> choices = new ArrayList<>(names);
        choices.add(0, getString(R.string.remix_modes_no_change));
        int current = choices.indexOf(mMapping.optString(id));
        new MaterialAlertDialogBuilder(new ContextThemeWrapper(getContext(),
                com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight),
                R.style.ThemeOverlay_Remixer_AlertDialog)
                .setTitle(getString(R.string.remix_modes_pick, title))
                .setSingleChoiceItems(choices.toArray(new CharSequence[0]),
                        Math.max(current, 0), (d, which) -> {
                            try {
                                if (which == 0) mMapping.remove(id);
                                else mMapping.put(id, choices.get(which));
                            } catch (Exception e) {
                                // A name is always a valid value.
                            }
                            Settings.System.putStringForUser(getContentResolver(), KEY_MAPPING,
                                    mMapping.toString(), UserHandle.USER_CURRENT);
                            d.dismiss();
                            build();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private JSONObject readMapping() {
        try {
            String json = Settings.System.getStringForUser(getContentResolver(), KEY_MAPPING,
                    UserHandle.USER_CURRENT);
            return new JSONObject(json != null ? json : "{}");
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
