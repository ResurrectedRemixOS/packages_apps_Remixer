/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.preferences;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;

import lineageos.preference.SelfRemovingListPreference;

/** A list stored as a Settings.System value for the current user. */
public class SystemSettingListPreference extends SelfRemovingListPreference {
    public SystemSettingListPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SystemSettingListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean isPersisted() {
        return Settings.System.getStringForUser(getContext().getContentResolver(), getKey(),
                UserHandle.USER_CURRENT) != null;
    }

    @Override
    protected void putString(String key, String value) {
        Settings.System.putStringForUser(getContext().getContentResolver(), key, value,
                UserHandle.USER_CURRENT);
    }

    @Override
    protected String getString(String key, String defaultValue) {
        String value = Settings.System.getStringForUser(getContext().getContentResolver(), key,
                UserHandle.USER_CURRENT);
        return value != null ? value : defaultValue;
    }
}
