package com.resurrection.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;

import com.google.android.material.appbar.AppBarLayout;

/**
 * Remixer hub hosted by Settings, the same way Evolver is a dashboard page.
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

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        Intent intent = intentFor(preference.getKey());
        if (intent == null) {
            return super.onPreferenceTreeClick(preference);
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), R.string.missing_app, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private Intent intentFor(String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case "remixer_fonts":
                return new Intent(getContext(), FontActivity.class);
            case "remixer_wallpapers":
                return new Intent(getContext(), WallpaperActivity.class);
            case "remixer_accents":
                return new Intent(getContext(), AccentActivity.class);
            case "remixer_pulse":
                return new Intent(getContext(), PulseActivity.class);
            case "remixer_slim":
                return new Intent(getContext(), SlimActivity.class);
            case "remixer_headers":
                return new Intent(getContext(), HeaderActivity.class);
            case "remixer_doze":
                return external("com.resurrection.doze", "com.resurrection.doze.DozeActivity");
            case "remixer_style_walls":
                return external("org.omnirom.omnistyle",
                        "org.omnirom.omnistyle.BrowseWallsActivity");
            case "remixer_weather":
                return external("org.omnirom.omnijaws", "org.omnirom.omnijaws.SettingsActivity");
            default:
                return null;
        }
    }

    private static Intent external(String pkg, String cls) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(pkg, cls);
        return intent;
    }
}
