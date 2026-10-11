package com.resurrection.settings.preferences;

import android.content.Context;
import android.os.SystemProperties;
import android.util.AttributeSet;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceDataStore;

/** A list kept in a system property, hidden when the device doesn't set that property. */
public class SystemPropertyListPreference extends ListPreference {

    public SystemPropertyListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPreferenceDataStore(new PreferenceDataStore() {
            @Override
            public void putString(String key, String value) {
                SystemProperties.set(key, value);
            }

            @Override
            public String getString(String key, String defValue) {
                return SystemProperties.get(key, defValue);
            }
        });
        setVisible(!SystemProperties.get(getKey()).isEmpty());
    }
}
