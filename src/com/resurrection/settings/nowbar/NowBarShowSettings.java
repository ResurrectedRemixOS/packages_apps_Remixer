/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.nowbar;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** What the Now Bar shows, and where else it appears. */
public class NowBarShowSettings extends SettingsPreferenceFragment {
    private static final String KEY_LIVE_APPS = "now_bar_live_apps";
    private static final String KEY_PIN = "now_bar_pin";
    private static final String KEY_GLANCE = "now_bar_glance_settings";
    /** The Google app's At a Glance settings, where Sports is switched on and teams followed. */
    private static final Intent GLANCE_SETTINGS =
            new Intent("com.google.android.apps.gsa.smartspace.SETTINGS")
                    .setPackage("com.google.android.googlequicksearchbox");

    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_now_bar_show);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummaries();
        Preference glance = findPreference(KEY_GLANCE);
        if (glance != null) {
            glance.setVisible(GLANCE_SETTINGS.resolveActivity(getContext().getPackageManager()) != null);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (KEY_LIVE_APPS.equals(preference.getKey())) {
            pickApps();
            return true;
        }
        if (KEY_PIN.equals(preference.getKey())) {
            if (pinned() == null) pickPin(); else unpin();
            return true;
        }
        if (KEY_GLANCE.equals(preference.getKey())) {
            try {
                startActivity(GLANCE_SETTINGS);
            } catch (Exception e) {
                // Hidden when it does not resolve; a race at most.
            }
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }

    private void updateSummaries() {
        Preference apps = findPreference(KEY_LIVE_APPS);
        if (apps != null) {
            int count = liveApps().size();
            apps.setSummary(count == 0 ? getString(R.string.now_bar_live_apps_all)
                    : getString(R.string.now_bar_live_apps_count, count));
        }
        Preference pin = findPreference(KEY_PIN);
        if (pin != null) {
            String pinned = pinned();
            pin.setSummary(pinned == null ? getString(R.string.now_bar_pin_none) : label(pinned));
        }
    }

    private Set<String> liveApps() {
        String csv = Settings.System.getStringForUser(getContentResolver(), KEY_LIVE_APPS,
                UserHandle.USER_CURRENT);
        Set<String> apps = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(csv)) {
            for (String pkg : csv.split(",")) {
                if (!pkg.isEmpty()) apps.add(pkg);
            }
        }
        return apps;
    }

    private String pinned() {
        String pkg = Settings.System.getStringForUser(getContentResolver(), KEY_PIN,
                UserHandle.USER_CURRENT);
        return TextUtils.isEmpty(pkg) ? null : pkg;
    }

    private CharSequence label(String pkg) {
        PackageManager pm = getContext().getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private Context themed() {
        return new ContextThemeWrapper(getContext(),
                com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight);
    }

    /** Every launchable app, checked when its Live Updates may show. */
    private void pickApps() {
        loadApps(this::showApps);
    }

    /** Every launchable app; the one picked leads the bar and holds the always-on display. */
    private void pickPin() {
        loadApps(this::showPin);
    }

    private void showPin(String[] packages, CharSequence[] labels) {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(themed())
                .setTitle(R.string.now_bar_pin)
                .setItems(labels, (d, which) -> {
                    Settings.System.putStringForUser(getContentResolver(), KEY_PIN,
                            packages[which], UserHandle.USER_CURRENT);
                    updateSummaries();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadApps(java.util.function.BiConsumer<String[], CharSequence[]> then) {
        Context context = getContext();
        if (context == null) return;
        mExecutor.execute(() -> {
            PackageManager pm = context.getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<String> packages = new ArrayList<>();
            List<CharSequence> labels = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
                String pkg = info.activityInfo.packageName;
                if (seen.add(pkg)) {
                    packages.add(pkg);
                    labels.add(info.loadLabel(pm));
                }
            }
            Integer[] order = new Integer[packages.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Collator collator = Collator.getInstance();
            Arrays.sort(order, (a, b) ->
                    collator.compare(labels.get(a).toString(), labels.get(b).toString()));
            String[] sortedPackages = new String[order.length];
            CharSequence[] sortedLabels = new CharSequence[order.length];
            for (int i = 0; i < order.length; i++) {
                sortedPackages[i] = packages.get(order[i]);
                sortedLabels[i] = labels.get(order[i]);
            }
            mHandler.post(() -> then.accept(sortedPackages, sortedLabels));
        });
    }

    private void showApps(String[] packages, CharSequence[] labels) {
        if (!isAdded()) return;
        Set<String> chosen = liveApps();
        boolean[] checked = new boolean[packages.length];
        for (int i = 0; i < packages.length; i++) checked[i] = chosen.contains(packages[i]);
        new MaterialAlertDialogBuilder(themed())
                .setTitle(R.string.now_bar_live_apps)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    if (isChecked) chosen.add(packages[which]); else chosen.remove(packages[which]);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Settings.System.putStringForUser(getContentResolver(), KEY_LIVE_APPS,
                            TextUtils.join(",", chosen), UserHandle.USER_CURRENT);
                    updateSummaries();
                })
                .setNeutralButton(R.string.now_bar_live_apps_all, (d, w) -> {
                    Settings.System.putStringForUser(getContentResolver(), KEY_LIVE_APPS, "",
                            UserHandle.USER_CURRENT);
                    updateSummaries();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void unpin() {
        String pinned = pinned();
        if (pinned == null) return;
        new MaterialAlertDialogBuilder(themed())
                .setMessage(getString(R.string.now_bar_unpin_question, label(pinned)))
                .setPositiveButton(R.string.now_bar_unpin, (d, w) -> {
                    Settings.System.putStringForUser(getContentResolver(), KEY_PIN, "",
                            UserHandle.USER_CURRENT);
                    updateSummaries();
                })
                .setNeutralButton(R.string.now_bar_pin_change, (d, w) -> pickPin())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
