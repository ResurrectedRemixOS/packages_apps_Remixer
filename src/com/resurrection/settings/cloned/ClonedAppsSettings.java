/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.cloned;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.applications.manageapplications.CloneBackend;
import com.android.settingslib.widget.LayoutPreference;

import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Stage layout for choosing which installed apps get a second account. */
public class ClonedAppsSettings extends SettingsPreferenceFragment {
    private final ExecutorService mLoader = Executors.newSingleThreadExecutor();
    private final Set<String> mBusy = new HashSet<>();

    private LayoutPreference mPage;
    private List<AppRow> mApps;
    private int mGeneration;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mPage = new LayoutPreference(getPrefContext(), R.layout.rr_cloned_apps);
        mPage.setSelectable(false);
        mPage.setKey("rr_cloned_apps_page");
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getPrefContext());
        screen.addPreference(mPage);
        setPreferenceScreen(screen);
        paintHero();
        mPage.findViewById(R.id.clone_delete_all).setOnClickListener(v -> confirmDeleteAll());
        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mApps != null) {
            reload();
        }
    }

    @Override
    public void onDestroy() {
        mGeneration++;
        mLoader.shutdownNow();
        super.onDestroy();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }

    private void reload() {
        final int generation = ++mGeneration;
        final Context context = getContext().getApplicationContext();
        final Map<String, Drawable> icons = new HashMap<>();
        if (mApps != null) {
            for (AppRow row : mApps) {
                icons.put(row.pkg, row.icon);
            }
        }
        mLoader.execute(() -> {
            List<AppRow> rows = load(context, icons);
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded()) {
                    return;
                }
                mApps = rows;
                render();
            });
        });
    }

    private static List<AppRow> load(Context context, Map<String, Drawable> icons) {
        PackageManager pm = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(launcher, 0);
        Map<String, AppRow> byPackage = new HashMap<>();
        for (ResolveInfo info : activities) {
            if (info.activityInfo == null) {
                continue;
            }
            ApplicationInfo app = info.activityInfo.applicationInfo;
            if (app == null || byPackage.containsKey(app.packageName)) {
                continue;
            }
            boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updated = (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (system && !updated) {
                continue;
            }
            AppRow row = new AppRow();
            row.pkg = app.packageName;
            CharSequence label = pm.getApplicationLabel(app);
            row.label = label == null ? app.packageName : label.toString();
            row.icon = icons.get(app.packageName);
            if (row.icon == null) {
                row.icon = pm.getApplicationIcon(app);
            }
            byPackage.put(row.pkg, row);
        }
        Set<String> cloned = clonedPackages(context);
        for (AppRow row : byPackage.values()) {
            row.cloned = cloned.contains(row.pkg);
        }
        List<AppRow> rows = new ArrayList<>(byPackage.values());
        rows.sort(Comparator.comparing(row -> row.label, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static Set<String> clonedPackages(Context context) {
        int cloneUser = Utils.getCloneUserId(context);
        if (cloneUser == -1) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        for (android.content.pm.PackageInfo info : context.getPackageManager()
                .getInstalledPackagesAsUser(0, cloneUser)) {
            names.add(info.packageName);
        }
        return names;
    }

    private void render() {
        List<AppRow> cloned = new ArrayList<>();
        List<AppRow> ready = new ArrayList<>();
        for (AppRow row : mApps) {
            (row.cloned ? cloned : ready).add(row);
        }
        int total = Math.max(1, mApps.size());
        TextView countOn = mPage.findViewById(R.id.clone_count_on);
        TextView countOff = mPage.findViewById(R.id.clone_count_off);
        countOn.setText(getString(R.string.cloned_apps_count_on, cloned.size()));
        countOff.setText(getString(R.string.cloned_apps_count_off, ready.size()));
        ((ProgressBar) mPage.findViewById(R.id.clone_bar_on))
                .setProgress(cloned.isEmpty() ? 0 : Math.max(8, cloned.size() * 100 / total));
        ((ProgressBar) mPage.findViewById(R.id.clone_bar_off))
                .setProgress(ready.isEmpty() ? 0 : Math.max(8, ready.size() * 100 / total));

        mPage.findViewById(R.id.clone_loading).setVisibility(View.GONE);
        fill((LinearLayout) mPage.findViewById(R.id.clone_list_on),
                (TextView) mPage.findViewById(R.id.clone_header_on), cloned, true);
        fill((LinearLayout) mPage.findViewById(R.id.clone_list_off),
                (TextView) mPage.findViewById(R.id.clone_header_off), ready, false);
        mPage.findViewById(R.id.clone_delete_all)
                .setVisibility(cloned.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void fill(LinearLayout list, TextView header, List<AppRow> rows, boolean cloned) {
        list.removeAllViews();
        header.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        float radius = dp(24);
        float tight = dp(6);
        for (int i = 0; i < rows.size(); i++) {
            AppRow row = rows.get(i);
            View view = getLayoutInflater().inflate(R.layout.rr_cloned_app_row, list, false);
            view.setBackground(rowBackground(i, rows.size(), radius, tight));
            ImageView icon = view.findViewById(R.id.clone_icon);
            icon.setImageDrawable(row.icon);
            icon.setClipToOutline(true);
            icon.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10));
                }
            });
            ((TextView) view.findViewById(R.id.clone_name)).setText(row.label);
            TextView status = view.findViewById(R.id.clone_status);
            Button action = view.findViewById(R.id.clone_action);
            boolean busy = mBusy.contains(row.pkg);
            if (cloned) {
                status.setText(R.string.cloned_apps_status_on);
                status.setTextColor(accent());
                action.setText(R.string.cloned_apps_action_remove);
                styleGhost(action);
                action.setOnClickListener(v -> remove(row.pkg));
            } else {
                status.setText(busy ? R.string.cloned_apps_working : R.string.cloned_apps_status_off);
                action.setText(busy ? R.string.cloned_apps_working : R.string.cloned_apps_action_clone);
                stylePill(action);
                action.setEnabled(!busy);
                action.setOnClickListener(v -> clone(row));
            }
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
            params.bottomMargin = i == rows.size() - 1 ? 0 : (int) dp(3);
            list.addView(view, params);
        }
    }

    private void clone(AppRow row) {
        if (!mBusy.add(row.pkg)) {
            return;
        }
        render();
        final Context context = getContext().getApplicationContext();
        mLoader.execute(() -> {
            int result = CloneBackend.getInstance(context).installCloneApp(row.pkg);
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(() -> {
                mBusy.remove(row.pkg);
                if (!isAdded()) {
                    return;
                }
                if (result != CloneBackend.SUCCESS) {
                    Toast.makeText(getContext(),
                            getString(R.string.cloned_apps_failed, row.label),
                            Toast.LENGTH_LONG).show();
                }
                reload();
            });
        });
    }

    private void remove(String packageName) {
        int cloneUser = Utils.getCloneUserId(getContext());
        if (cloneUser == -1 || getActivity() == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                Uri.parse("package:" + packageName));
        intent.putExtra(Intent.EXTRA_USER, UserHandle.of(cloneUser));
        getActivity().startActivityAsUser(intent, UserHandle.of(cloneUser));
    }

    private void confirmDeleteAll() {
        int cloneUser = Utils.getCloneUserId(getContext());
        if (cloneUser == -1) {
            return;
        }
        new AlertDialog.Builder(getContext())
                .setMessage(R.string.cloned_apps_delete_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.cloned_apps_delete_all, (dialog, which) -> {
                    Context context = getContext().getApplicationContext();
                    mLoader.execute(() -> {
                        boolean removed = false;
                        try {
                            removed = context.getSystemService(UserManager.class)
                                    .removeUser(cloneUser);
                        } catch (RuntimeException ignored) {
                            removed = false;
                        }
                        if (removed) {
                            CloneBackend.getInstance(context).resetCloneUserId();
                        }
                        if (getActivity() == null) {
                            return;
                        }
                        boolean ok = removed;
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) {
                                return;
                            }
                            if (!ok) {
                                Toast.makeText(getContext(), R.string.delete_all_app_clones_failure,
                                        Toast.LENGTH_LONG).show();
                            }
                            reload();
                        });
                    });
                })
                .show();
    }

    private void paintHero() {
        int accent = accent();
        GradientDrawable hero = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {withAlpha(accent, 0x66), withAlpha(accent, 0x22)});
        hero.setCornerRadius(dp(28));
        hero.setStroke((int) dp(1), withAlpha(accent, 0x55));
        mPage.findViewById(R.id.clone_hero).setBackground(hero);
    }

    private int accent() {
        return getContext().getColor(android.R.color.system_accent1_400);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private GradientDrawable rowBackground(int index, int count, float radius, float tight) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF171717);
        float top = index == 0 ? radius : tight;
        float bottom = index == count - 1 ? radius : tight;
        background.setCornerRadii(new float[] {
                top, top, top, top, bottom, bottom, bottom, bottom});
        return background;
    }

    private void stylePill(Button button) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(999));
        background.setColor(0xFFFFFFFF);
        button.setBackground(background);
        button.setTextColor(0xFF141012);
        button.setStateListAnimator(null);
    }

    private void styleGhost(Button button) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(999));
        background.setColor(0x00000000);
        background.setStroke((int) dp(1), 0x47FFFFFF);
        button.setBackground(background);
        button.setTextColor(0xFFF4EEF0);
        button.setStateListAnimator(null);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static final class AppRow {
        String pkg;
        String label;
        Drawable icon;
        boolean cloned;
    }
}
