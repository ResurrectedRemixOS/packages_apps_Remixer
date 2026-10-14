/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.chargereport;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Charge report options and the recent charges, which SystemUI records. */
public class ChargeReportSettings extends SettingsPreferenceFragment {
    private static final String KEY_HISTORY = "charge_report_history";
    private static final String KEY_KEEP = "charge_report_keep";
    /** From the report notification: the charge to open. */
    private static final String EXTRA_START = "charge_report_start";

    private long mOpenStart;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_charge_report);
        if (icicle == null && getActivity() != null && getActivity().getIntent() != null) {
            mOpenStart = getActivity().getIntent().getLongExtra(EXTRA_START, 0);
        }
        findPreference(KEY_KEEP).setOnPreferenceChangeListener((p, value) -> {
            getView().post(this::fill);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        fill();
    }

    private void fill() {
        PreferenceCategory history = findPreference("charge_report_history_list");
        history.removeAll();
        int keep = Settings.System.getIntForUser(getContentResolver(), KEY_KEEP, 7,
                UserHandle.USER_CURRENT);
        history.setTitle(getString(R.string.charge_report_history_days, keep));
        JSONArray sessions;
        try {
            String json = Settings.System.getStringForUser(getContentResolver(), KEY_HISTORY,
                    UserHandle.USER_CURRENT);
            sessions = new JSONArray(json != null ? json : "[]");
        } catch (Exception e) {
            sessions = new JSONArray();
        }
        long cutoff = System.currentTimeMillis() - keep * DateUtils.DAY_IN_MILLIS;
        int shown = 0;
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.optJSONObject(i);
            if (s == null || s.optLong("start") < cutoff) continue;
            ChargeReportPreference row = new ChargeReportPreference(getPrefContext());
            row.setCurve(s.optJSONArray("curve"));
            row.setTitle(when(getContext(), s.optLong("start")));
            row.setSummary(getString(R.string.charge_report_row,
                    s.optInt("to") - s.optInt("from"), duration(s.optLong("minutes")),
                    s.optDouble("peak")));
            row.setOnPreferenceClickListener(p -> {
                showDetail(s);
                return true;
            });
            history.addPreference(row);
            shown++;
            if (mOpenStart != 0 && s.optLong("start") == mOpenStart) {
                mOpenStart = 0;
                showDetail(s);
            }
        }
        if (shown == 0) {
            Preference empty = new Preference(getPrefContext());
            empty.setSelectable(false);
            empty.setSummary(R.string.charge_report_empty);
            history.addPreference(empty);
        }
    }

    /** "Today, 10:34", "Yesterday, 23:40" or "Friday, 18:12". */
    private static String when(Context context, long start) {
        String time = DateFormat.getTimeFormat(context).format(new Date(start));
        if (DateUtils.isToday(start)) {
            return context.getString(R.string.charge_report_today, time);
        }
        if (DateUtils.isToday(start + DateUtils.DAY_IN_MILLIS)) {
            return context.getString(R.string.charge_report_yesterday, time);
        }
        String day = new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date(start));
        return context.getString(R.string.charge_report_day_time, day, time);
    }

    private String duration(long minutes) {
        return minutes >= 60
                ? getString(R.string.charge_report_hours, minutes / 60, minutes % 60)
                : getString(R.string.charge_report_minutes, minutes);
    }

    private String charger(int plugged) {
        switch (plugged) {
            case 1: return getString(R.string.charge_report_wired);
            case 2: return getString(R.string.charge_report_usb);
            case 4: return getString(R.string.charge_report_wireless);
            case 8: return getString(R.string.charge_report_dock);
            default: return null;
        }
    }

    private void showDetail(JSONObject s) {
        Context context = getPrefContext();
        int pad = dp(24);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(4), pad, 0);

        String charger = charger(s.optInt("plugged"));
        TextView sub = text(context, (charger != null ? charger + " · " : "")
                + getString(R.string.charge_report_range, s.optInt("from"), s.optInt("to")),
                14, true);
        box.addView(sub);

        ChargeCurveView chart = new ChargeCurveView(context, null);
        chart.setShowTemperature(true);
        chart.setCurve(s.optJSONArray("curve"));
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(-1, dp(160));
        chartLp.topMargin = dp(16);
        box.addView(chart, chartLp);

        LinearLayout legend = new LinearLayout(context);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setPadding(0, dp(10), 0, dp(6));
        legend.addView(key(context, ChargeCurveView.wattsColor(context),
                R.string.charge_report_watts));
        legend.addView(key(context, ChargeCurveView.levelColor(context),
                R.string.charge_report_battery));
        legend.addView(key(context, ChargeCurveView.TEMP_COLOR, R.string.charge_report_temp));
        box.addView(legend);

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(3);
        stat(grid, R.string.charge_report_added, "+" + (s.optInt("to") - s.optInt("from")) + "%");
        stat(grid, R.string.charge_report_time, duration(s.optLong("minutes")));
        stat(grid, R.string.charge_report_peak, String.format(Locale.getDefault(), "%.1f W",
                s.optDouble("peak")));
        stat(grid, R.string.charge_report_average, String.format(Locale.getDefault(), "%.1f W",
                s.optDouble("avg")));
        stat(grid, R.string.charge_report_max_temp, String.format(Locale.getDefault(), "%.0f °C",
                s.optDouble("temp")));
        stat(grid, charger != null ? R.string.charge_report_charger
                : R.string.charge_report_started, charger != null ? charger
                : s.optInt("from") + "%");
        box.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        new AlertDialog.Builder(context)
                .setTitle(when(context, s.optLong("start")))
                .setView(box)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private TextView text(Context context, String value, int sp, boolean secondary) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (secondary) {
            TypedValue tv = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, tv, true);
            view.setTextColor(tv.resourceId != 0 ? context.getColorStateList(tv.resourceId)
                    : android.content.res.ColorStateList.valueOf(tv.data));
        }
        return view;
    }

    private LinearLayout key(Context context, int color, int label) {
        LinearLayout item = new LinearLayout(context);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(0, 0, dp(14), 0);
        android.view.View dot = new android.view.View(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        dot.setBackground(shape);
        item.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView text = text(context, getString(label), 12, true);
        text.setPadding(dp(6), 0, 0, 0);
        item.addView(text);
        return item;
    }

    private void stat(GridLayout grid, int label, String value) {
        Context context = grid.getContext();
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(0, dp(10), dp(8), 0);
        cell.addView(text(context, getString(label), 12, true));
        cell.addView(text(context, value, 18, false));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        grid.addView(cell, lp);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
