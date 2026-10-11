package com.resurrection.settings.preferences;

import android.content.ContentResolver;
import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;

import androidx.preference.PreferenceDataStore;
import androidx.preference.SeekBarPreference;

/** A seek bar kept as an int in Settings.System. */
public class SystemSettingSeekBarPreference extends SeekBarPreference {

    public SystemSettingSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        ContentResolver resolver = context.getContentResolver();
        setPreferenceDataStore(new PreferenceDataStore() {
            @Override
            public void putInt(String key, int value) {
                Settings.System.putIntForUser(resolver, key, value, UserHandle.USER_CURRENT);
            }

            @Override
            public int getInt(String key, int defValue) {
                return Settings.System.getIntForUser(resolver, key, defValue,
                        UserHandle.USER_CURRENT);
            }
        });
    }
}
