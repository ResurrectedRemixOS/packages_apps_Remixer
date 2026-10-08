/*
 * Copyright (C) 2026 The Resurrected Remix Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.resurrection.settings.homepage;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

/**
 * Homepage section for entries a device injects by group key. Hidden while it has none.
 */
public class HomepageExtrasCategory extends PreferenceCategory {
    public HomepageExtrasCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVisible(false);
    }

    @Override
    protected boolean onPrepareAddPreference(Preference preference) {
        setVisible(true);
        return super.onPrepareAddPreference(preference);
    }

    @Override
    public boolean removePreference(Preference preference) {
        boolean removed = super.removePreference(preference);
        if (getPreferenceCount() == 0) {
            setVisible(false);
        }
        return removed;
    }
}
