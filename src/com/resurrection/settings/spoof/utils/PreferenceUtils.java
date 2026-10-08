/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.spoof.utils;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.resurrection.settings.spoof.preferences.SystemSettingPrimarySwitchPreference;

import java.util.ArrayList;
import java.util.List;

public class PreferenceUtils {

    public static void reloadCustomPrimarySwitches(PreferenceGroup group) {
        for (Preference p : getAllPreferences(group)) {
            if (p instanceof SystemSettingPrimarySwitchPreference psp) {
                psp.loadValue();
            }
        }
    }

    public static List<Preference> getAllPreferences(PreferenceGroup group) {
        List<Preference> preferences = new ArrayList<>();
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            preferences.add(pref);
            if (pref instanceof PreferenceGroup) {
                preferences.addAll(getAllPreferences((PreferenceGroup) pref));
            }
        }
        return preferences;
    }
}
