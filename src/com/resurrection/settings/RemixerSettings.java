package com.resurrection.settings;

import android.os.Bundle;
import android.view.View;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;

import com.google.android.material.appbar.AppBarLayout;

/**
 * Remixer hub hosted by Settings as a dashboard page: {@link RemixerHeroView} and
 * {@link RemixerHubView}, whose cards open a {@link RemixerSection}.
 * Not search-indexed, so Settings startup does not load this class.
 */
public class RemixerSettings extends DashboardFragment {

    private static final String TAG = "RemixerSettings";

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.remixer_settings;
    }

    @Override
    public int getMetricsCategory() {
        // Existing SettingsEnums.DISPLAY value. A new enum needs a framework
        // rebuild, and a mismatch here crashes Settings while it is starting.
        return 46;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // The remixer_header logo is the page title: keep the toolbar collapsed.
        AppBarLayout appBar = getActivity().findViewById(R.id.app_bar);
        if (appBar != null) {
            appBar.setExpanded(false, false);
            getListView().setNestedScrollingEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle("");
    }
}
