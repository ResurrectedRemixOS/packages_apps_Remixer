/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.homepage;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;

import com.android.settings.core.SubSettingLauncher;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.support.SupportPreferenceController;
import com.android.settings.widget.HomepagePreference;
import com.android.settingslib.drawer.CategoryKey;
import com.android.settingslib.drawer.Tile;

/** A category page of the RR homepage: its homepage rows plus the entries other apps add. */
public class HomepageGroupFragment extends DashboardFragment {
    private static final String TAG = "HomepageGroup";
    private static final String ARG_GROUP = "rr_homepage_group";

    static void launch(Context context, String group, CharSequence title) {
        final Bundle args = new Bundle();
        args.putString(ARG_GROUP, group);
        new SubSettingLauncher(context)
                .setDestination(HomepageGroupFragment.class.getName())
                .setArguments(args)
                .setTitleText(title)
                .setSourceMetricsCategory(SettingsEnums.DASHBOARD_SUMMARY)
                .launch();
    }

    private String group() {
        final Bundle args = getArguments();
        return args != null ? args.getString(ARG_GROUP) : null;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        final SupportPreferenceController support = use(SupportPreferenceController.class);
        if (support != null) {
            support.setActivity(getActivity());
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return HomepageGroups.screenOf(group());
    }

    @Override
    public String getCategoryKey() {
        return CategoryKey.CATEGORY_HOMEPAGE;
    }

    @Override
    protected boolean displayTile(Tile tile) {
        final String group = group();
        return super.displayTile(tile) && group != null
                && group.equals(HomepageGroups.placeOf(tile));
    }

    @Override
    protected Preference createPreference(Tile tile) {
        return new HomepagePreference(getPrefContext());
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DASHBOARD_SUMMARY;
    }
}
