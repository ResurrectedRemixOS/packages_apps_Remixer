/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.about;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.os.BatteryManager;
import android.os.Build;
import android.os.storage.StorageManager;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.os.PowerProfile;
import com.android.settings.R;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;
import com.android.settingslib.utils.ThreadUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/** The About phone hardware grid: processor, memory, storage, display, battery and kernel. */
public class AboutSpecsView extends LinearLayout {

    private static final long GB = 1000L * 1000 * 1000;
    private static final long GIB = 1024L * 1024 * 1024;
    private static final int[] RAM_SIZES = {1, 2, 3, 4, 6, 8, 10, 12, 16, 18, 20, 24, 32, 48, 64};
    private static final Map<String, String> SOCS = Map.ofEntries(
            Map.entry("SM8850", "Snapdragon 8 Elite Gen 5"),
            Map.entry("SM8750", "Snapdragon 8 Elite"),
            Map.entry("SM8650", "Snapdragon 8 Gen 3"),
            Map.entry("SM8635", "Snapdragon 8s Gen 3"),
            Map.entry("SM8550", "Snapdragon 8 Gen 2"),
            Map.entry("SM8475", "Snapdragon 8+ Gen 1"),
            Map.entry("SM8450", "Snapdragon 8 Gen 1"),
            Map.entry("SM8350", "Snapdragon 888"),
            Map.entry("SM8250", "Snapdragon 865"),
            Map.entry("SM8150", "Snapdragon 855"),
            Map.entry("SM7675", "Snapdragon 7+ Gen 3"),
            Map.entry("SM7550", "Snapdragon 7 Gen 3"));

    private Tile mStorage;
    private Tile mBattery;
    private double mBatteryCapacity;
    private boolean mRegistered;

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    public AboutSpecsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        LinearLayout grid = findViewById(R.id.rr_about_specs_grid);
        Tile processor = new Tile(grid, R.drawable.rr_about_ic_cpu, R.string.rr_about_processor);
        Tile memory = new Tile(grid, R.drawable.rr_about_ic_ram, R.string.rr_about_memory);
        mStorage = new Tile(grid, R.drawable.rr_about_ic_storage, R.string.rr_about_storage);
        Tile display = new Tile(grid, R.drawable.rr_about_ic_display, R.string.rr_about_display);
        mBattery = new Tile(grid, R.drawable.rr_about_ic_battery, R.string.rr_about_battery);
        Tile kernel = new Tile(grid, R.drawable.rr_about_ic_kernel, R.string.rr_about_kernel);
        addRow(grid, processor, memory);
        addRow(grid, mStorage, display);
        addRow(grid, mBattery, kernel);

        Context context = getContext();
        mBatteryCapacity = new PowerProfile(context).getBatteryCapacity();
        int cores = Runtime.getRuntime().availableProcessors();
        long maxKhz = maxCpuKhz(cores);
        processor.set(socName(), 18, maxKhz > 0
                ? context.getString(R.string.rr_about_processor_sub, cores, ghz(maxKhz))
                : context.getString(R.string.rr_about_processor_cores, cores));
        memory.set(Formatter.formatShortFileSize(context, ramSize() * GB), 28,
                context.getString(R.string.rr_about_ram));
        updateDisplay(display);
        updateKernel(kernel);
        mStorage.set(" ", 28, " ");
        ThreadUtils.postOnBackgroundThread(() -> {
            PrivateStorageInfo info = PrivateStorageInfo.getPrivateStorageInfo(
                    new StorageManagerVolumeProvider(
                            context.getSystemService(StorageManager.class)));
            ThreadUtils.postOnMainThread(() -> updateStorage(info));
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Intent sticky = getContext().registerReceiver(mBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        mRegistered = true;
        updateBattery(sticky);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mRegistered) {
            getContext().unregisterReceiver(mBatteryReceiver);
            mRegistered = false;
        }
        super.onDetachedFromWindow();
    }

    private void addRow(LinearLayout grid, Tile first, Tile second) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        if (grid.getChildCount() > 0) {
            params.topMargin = Math.round(AboutUi.dp(context, 10));
        }
        row.addView(first.root);
        LayoutParams secondParams = (LayoutParams) second.root.getLayoutParams();
        secondParams.setMarginStart(Math.round(AboutUi.dp(context, 10)));
        row.addView(second.root);
        grid.addView(row, params);
    }

    private void updateStorage(PrivateStorageInfo info) {
        if (info == null || info.totalBytes <= 0) {
            mStorage.set("—", 28, " ");
            return;
        }
        Context context = getContext();
        long used = Math.max(0, info.totalBytes - info.freeBytes);
        mStorage.set(Formatter.formatShortFileSize(context, info.totalBytes), 28,
                context.getString(R.string.rr_about_storage_used,
                        Formatter.formatShortFileSize(context, used)));
        mStorage.setBar((int) (used * 100 / info.totalBytes));
    }

    private void updateBattery(Intent status) {
        Context context = getContext();
        double capacity = mBatteryCapacity;
        int level = -1;
        boolean charging = false;
        if (status != null) {
            int raw = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = raw >= 0 && scale > 0 ? raw * 100 / scale : -1;
            int state = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = state == BatteryManager.BATTERY_STATUS_CHARGING;
        }
        String percent = level >= 0 ? NumberFormat.getPercentInstance().format(level / 100.0) : "";
        String capacityText = capacity > 0 ? context.getString(R.string.rr_about_battery_capacity,
                NumberFormat.getIntegerInstance().format(Math.round(capacity))) : percent;
        mBattery.set(capacityText, 22, charging
                ? context.getString(R.string.rr_about_battery_charging, percent) : percent);
        if (level >= 0) {
            mBattery.setBar(level);
        }
    }

    private void updateDisplay(Tile tile) {
        Context context = getContext();
        Display display = context.getDisplay();
        if (display == null) {
            tile.set("—", 18, " ");
            return;
        }
        Display.Mode mode = display.getMode();
        float maxHz = 0;
        for (Display.Mode supported : display.getSupportedModes()) {
            maxHz = Math.max(maxHz, supported.getRefreshRate());
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = mode.getPhysicalWidth();
        int height = mode.getPhysicalHeight();
        double inches = Math.hypot(width / metrics.xdpi, height / metrics.ydpi);
        String size = String.format(Locale.getDefault(), "%.2f", inches)
                .replaceAll("([.,]\\d)0$", "$1");
        tile.set(context.getString(R.string.rr_about_display_value, size, Math.round(maxHz)), 18,
                context.getString(R.string.rr_about_resolution, width, height));
    }

    private void updateKernel(Tile tile) {
        String full = System.getProperty("os.version", "");
        int dash = full.indexOf('-');
        String version = dash > 0 ? full.substring(0, dash) : full;
        String name = dash > 0 ? full.substring(dash + 1) : "";
        name = name.replaceAll("-g[0-9a-f]{7,}.*$", "").replaceAll("[+-]+$", "");
        tile.set(version, 22, name.isEmpty()
                ? getContext().getString(R.string.rr_about_kernel_linux) : name);
    }

    private static String socName() {
        String model = Build.SOC_MODEL;
        String name = SOCS.get(model);
        if (name != null) {
            return name;
        }
        String maker = "QTI".equals(Build.SOC_MANUFACTURER) ? "Qualcomm" : Build.SOC_MANUFACTURER;
        return Build.UNKNOWN.equals(model) ? Build.HARDWARE : maker + " " + model;
    }

    private long ramSize() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        getContext().getSystemService(ActivityManager.class).getMemoryInfo(info);
        for (int size : RAM_SIZES) {
            if (size * GIB >= info.totalMem) {
                return size;
            }
        }
        return Math.round((double) info.totalMem / GIB);
    }

    private static long maxCpuKhz(int cores) {
        long max = 0;
        for (int i = 0; i < cores; i++) {
            String path = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq";
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                max = Math.max(max, Long.parseLong(reader.readLine().trim()));
            } catch (IOException | NumberFormatException | NullPointerException e) {
                // Core offline or no cpufreq.
            }
        }
        return max;
    }

    private static String ghz(long khz) {
        return String.format(Locale.getDefault(), "%.2f", khz / 1_000_000.0)
                .replaceAll("([.,]\\d)0$", "$1");
    }

    /** One spec tile. */
    private static final class Tile {
        final View root;
        final TextView value;
        final TextView sub;
        final View bar;
        final View fill;
        final View rest;

        Tile(LinearLayout parent, int icon, int label) {
            Context context = parent.getContext();
            root = LayoutInflater.from(context).inflate(R.layout.rr_about_spec_tile, parent, false);
            value = root.findViewById(R.id.rr_spec_value);
            sub = root.findViewById(R.id.rr_spec_sub);
            bar = root.findViewById(R.id.rr_spec_bar);
            fill = root.findViewById(R.id.rr_spec_bar_fill);
            rest = root.findViewById(R.id.rr_spec_bar_rest);

            int surface = AboutUi.color(context,
                    com.android.internal.R.color.materialColorSurfaceBright);
            int onSurface = AboutUi.color(context,
                    com.android.internal.R.color.materialColorOnSurface);
            int variant = AboutUi.color(context,
                    com.android.internal.R.color.materialColorOnSurfaceVariant);
            int chip = AboutUi.color(context,
                    com.android.internal.R.color.materialColorSecondaryContainer);
            int primary = AboutUi.color(context,
                    com.android.internal.R.color.materialColorPrimary);
            int track = AboutUi.color(context,
                    com.android.internal.R.color.materialColorSurfaceContainerHighest);

            root.setBackground(AboutUi.round(context, surface, 24));
            ImageView iconView = root.findViewById(R.id.rr_spec_icon);
            iconView.setImageResource(icon);
            iconView.setImageTintList(ColorStateList.valueOf(primary));
            iconView.setBackground(AboutUi.round(context, chip, 11));
            TextView labelView = root.findViewById(R.id.rr_spec_label);
            labelView.setText(label);
            labelView.setTextColor(variant);
            value.setTextColor(onSurface);
            sub.setTextColor(variant);
            bar.setBackground(AboutUi.round(context, track, 3));
            fill.setBackground(AboutUi.round(context, primary, 3));
        }

        void set(CharSequence text, int sizeSp, CharSequence subText) {
            value.setText(text);
            value.setTextSize(sizeSp);
            sub.setText(subText);
        }

        void setBar(int percent) {
            int clamped = Math.max(2, Math.min(100, percent));
            bar.setVisibility(View.VISIBLE);
            ((LinearLayout.LayoutParams) fill.getLayoutParams()).weight = clamped;
            ((LinearLayout.LayoutParams) rest.getLayoutParams()).weight = 100 - clamped;
            bar.requestLayout();
        }
    }
}
