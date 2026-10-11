/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.homepage;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.widget.HomepagePreference;

/** RR homepage entry: a neon ring icon that follows the device, plus a readout. */
public class LiveHomepagePreference extends HomepagePreference {
    private final LiveHomepageEntry mEntry = new LiveHomepageEntry(this, this::notifyChanged);

    public LiveHomepagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.rr_homepage_preference_live);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mEntry.attach();
    }

    @Override
    public void onDetached() {
        mEntry.detach();
        super.onDetached();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mEntry.bind(holder);
    }
}
