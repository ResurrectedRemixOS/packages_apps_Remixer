/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.homepage;

import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MIN;
import static com.android.settingslib.display.BrightnessUtils.convertLinearToGammaFloat;

import android.app.NotificationManager;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.app.usage.StorageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Display;
import android.view.View;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settingslib.utils.ThreadUtils;

import java.io.IOException;
import java.text.NumberFormat;

/** Keeps one homepage entry's neon ring and readout in step with the device. */
final class LiveHomepageEntry {
    private static final int NONE = 0;
    private static final int NETWORK = 1;
    private static final int BLUETOOTH = 2;
    private static final int VOLUME = 3;
    private static final int DND = 4;
    private static final int BRIGHTNESS = 5;
    private static final int WALLPAPER = 6;
    private static final int STORAGE = 7;
    private static final int BATTERY = 8;
    private static final int LOCATION = 9;

    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final long STORAGE_REFRESH_MS = 30_000;

    private final Preference mPreference;
    private final Runnable mNotifyChanged;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private NeonRingDrawable mIcon;
    private int mKind;
    private CharSequence mChip;
    private boolean mChipNeutral;
    private int mChipBg;
    private int mChipFg;
    private int mNeutralBg;
    private int mNeutralFg;
    private BroadcastReceiver mReceiver;
    private DisplayManager.DisplayListener mDisplayListener;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private WallpaperManager.OnColorsChangedListener mColorsListener;
    private long mStorageAt;

    LiveHomepageEntry(Preference preference, Runnable notifyChanged) {
        mPreference = preference;
        mNotifyChanged = notifyChanged;
    }

    void attach() {
        Context context = mPreference.getContext();
        mKind = kindOf(mPreference.getKey());
        if (mIcon == null) {
            Drawable icon = mPreference.getIcon();
            if (icon == null) {
                return;
            }
            // The RR icons are a colored square with the glyph layered on top.
            Drawable glyph = icon instanceof LayerDrawable layers && layers.getNumberOfLayers() > 1
                    ? layers.getDrawable(1) : icon;
            Drawable.ConstantState state = glyph.getConstantState();
            glyph = state != null ? state.newDrawable(context.getResources()).mutate()
                    : glyph.mutate();
            Resources res = context.getResources();
            mIcon = new NeonRingDrawable(glyph,
                    res.getDimensionPixelSize(R.dimen.dashboard_tile_image_size),
                    res.getDisplayMetrics().density);
            applyColors(context);
            mPreference.setIcon(mIcon);
        }
        register(context.getApplicationContext());
        refresh();
    }

    void detach() {
        unregister(mPreference.getContext().getApplicationContext());
    }

    void bind(PreferenceViewHolder holder) {
        TextView chip = (TextView) holder.findViewById(R.id.rr_live_chip);
        if (chip != null) {
            chip.setVisibility(mChip == null ? View.GONE : View.VISIBLE);
            if (mChip != null) {
                chip.setText(mChip);
                chip.setTextColor(mChipNeutral ? mNeutralFg : mChipFg);
                GradientDrawable pill = new GradientDrawable();
                pill.setCornerRadius(chip.getResources().getDisplayMetrics().density * 13);
                pill.setColor(mChipNeutral ? mNeutralBg : mChipBg);
                chip.setBackground(pill);
            }
        }
        if (mKind == STORAGE && SystemClock.elapsedRealtime() - mStorageAt > STORAGE_REFRESH_MS) {
            refreshStorage();
        }
    }

    private void applyColors(Context context) {
        boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int a100 = context.getColor(android.R.color.system_accent1_100);
        int a200 = context.getColor(android.R.color.system_accent1_200);
        int a300 = context.getColor(android.R.color.system_accent1_300);
        int a400 = context.getColor(android.R.color.system_accent1_400);
        int a500 = context.getColor(android.R.color.system_accent1_500);
        int a600 = context.getColor(android.R.color.system_accent1_600);
        int a700 = context.getColor(android.R.color.system_accent1_700);
        int a800 = context.getColor(android.R.color.system_accent1_800);
        if (night) {
            mIcon.setColors(a300, a200, 0x61FFFFFF, ColorUtils.setAlphaComponent(a400, 0x29),
                    ColorUtils.setAlphaComponent(a500, 0x47), 0xFF0E0E0F);
            mChipBg = ColorUtils.setAlphaComponent(a400, 0x29);
            mChipFg = a100;
            mNeutralBg = 0x12FFFFFF;
            mNeutralFg = 0xFFA39B9C;
        } else {
            mIcon.setColors(a600, a700, 0x61000000, ColorUtils.setAlphaComponent(a600, 0x2E),
                    ColorUtils.setAlphaComponent(a200, 0x66),
                    context.getColor(android.R.color.system_neutral1_50));
            mChipBg = ColorUtils.setAlphaComponent(a600, 0x24);
            mChipFg = a800;
            mNeutralBg = 0x12000000;
            mNeutralFg = 0xFF6B6466;
        }
    }

    private void refresh() {
        if (mIcon == null) {
            return;
        }
        Context context = mPreference.getContext();
        switch (mKind) {
            case NETWORK -> refreshNetwork(context);
            case BLUETOOTH -> {
                BluetoothManager manager = context.getSystemService(BluetoothManager.class);
                BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
                onOff(context, adapter != null && adapter.isEnabled());
            }
            case VOLUME -> {
                AudioManager audio = context.getSystemService(AudioManager.class);
                int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                float level = max > 0
                        ? audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max : 0;
                mIcon.setState(level, true);
                setChip(percent(level), false);
            }
            case DND -> {
                int filter = context.getSystemService(NotificationManager.class)
                        .getCurrentInterruptionFilter();
                onOff(context, filter != NotificationManager.INTERRUPTION_FILTER_ALL
                        && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN);
            }
            case BRIGHTNESS -> {
                Display display = context.getSystemService(DisplayManager.class)
                        .getDisplay(Display.DEFAULT_DISPLAY);
                BrightnessInfo info = display != null ? display.getBrightnessInfo() : null;
                if (info != null) {
                    int value = convertLinearToGammaFloat(info.brightness,
                            info.brightnessMinimum, info.brightnessMaximum);
                    float level = Math.max(0f, Math.min(1f,
                            (value - GAMMA_SPACE_MIN) / (float) (GAMMA_SPACE_MAX - GAMMA_SPACE_MIN)));
                    mIcon.setState(level, true);
                    setChip(percent(level), false);
                }
            }
            case WALLPAPER -> {
                WallpaperColors colors = context.getSystemService(WallpaperManager.class)
                        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                int primary = colors != null ? colors.getPrimaryColor().toArgb()
                        : context.getColor(android.R.color.system_accent1_300);
                int secondary = colors != null && colors.getSecondaryColor() != null
                        ? colors.getSecondaryColor().toArgb()
                        : context.getColor(android.R.color.system_accent2_300);
                int tertiary = colors != null && colors.getTertiaryColor() != null
                        ? colors.getTertiaryColor().toArgb()
                        : context.getColor(android.R.color.system_accent3_300);
                mIcon.setSweep(new int[] {primary, secondary, tertiary, primary});
            }
            case STORAGE -> refreshStorage();
            case BATTERY -> {
                Intent battery = context.registerReceiver(null,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (battery != null) {
                    int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    float level = scale > 0
                            ? battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / (float) scale
                            : 0;
                    mIcon.setState(level, true);
                    setChip(percent(level), false);
                }
            }
            case LOCATION -> onOff(context,
                    context.getSystemService(LocationManager.class).isLocationEnabled());
            default -> mIcon.setState(NeonRingDrawable.NO_LEVEL, true);
        }
    }

    private void refreshNetwork(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        Network network = connectivity.getActiveNetwork();
        NetworkCapabilities caps = network != null
                ? connectivity.getNetworkCapabilities(network) : null;
        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiManager wifi = context.getSystemService(WifiManager.class);
            float level = (wifi.calculateSignalLevel(caps.getSignalStrength()) + 1f)
                    / (wifi.getMaxSignalLevel() + 1f);
            mIcon.setState(level, true);
            setChip(context.getString(R.string.rr_live_wifi), false);
        } else if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            SignalStrength signal = context.getSystemService(TelephonyManager.class)
                    .getSignalStrength();
            float level = signal != null ? (signal.getLevel() + 1f) / 5f : 1f;
            mIcon.setState(level, true);
            setChip(context.getString(R.string.rr_live_mobile), false);
        } else {
            mIcon.setState(NeonRingDrawable.NO_LEVEL, false);
            setChip(context.getString(R.string.rr_live_offline), true);
        }
    }

    private void refreshStorage() {
        Context context = mPreference.getContext().getApplicationContext();
        mStorageAt = SystemClock.elapsedRealtime();
        ThreadUtils.postOnBackgroundThread(() -> {
            long total;
            long free;
            try {
                StorageStatsManager stats = context.getSystemService(StorageStatsManager.class);
                total = stats.getTotalBytes(StorageManager.UUID_DEFAULT);
                free = stats.getFreeBytes(StorageManager.UUID_DEFAULT);
            } catch (IOException | RuntimeException e) {
                return;
            }
            if (total <= 0) {
                return;
            }
            float used = (total - free) / (float) total;
            mHandler.post(() -> {
                mIcon.setState(used, true);
                setChip(percent(used), false);
            });
        });
    }

    private void onOff(Context context, boolean on) {
        mIcon.setState(on ? 1f : NeonRingDrawable.NO_LEVEL, on);
        setChip(context.getString(on ? R.string.rr_live_on : R.string.rr_live_off), !on);
    }

    private void setChip(CharSequence text, boolean neutral) {
        if (text.equals(mChip) && neutral == mChipNeutral) {
            return;
        }
        mChip = text;
        mChipNeutral = neutral;
        mNotifyChanged.run();
    }

    private static String percent(float fraction) {
        return NumberFormat.getPercentInstance().format(fraction);
    }

    private void register(Context context) {
        IntentFilter filter = new IntentFilter();
        switch (mKind) {
            case NETWORK -> {
                mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onCapabilitiesChanged(Network network,
                            NetworkCapabilities caps) {
                        mHandler.post(LiveHomepageEntry.this::refresh);
                    }

                    @Override
                    public void onLost(Network network) {
                        mHandler.post(LiveHomepageEntry.this::refresh);
                    }
                };
                context.getSystemService(ConnectivityManager.class)
                        .registerDefaultNetworkCallback(mNetworkCallback, mHandler);
            }
            case BLUETOOTH -> filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            case VOLUME -> {
                filter.addAction(VOLUME_CHANGED);
                filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            }
            case DND -> filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
            case BATTERY -> filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            case LOCATION -> filter.addAction(LocationManager.MODE_CHANGED_ACTION);
            case BRIGHTNESS -> {
                mDisplayListener = new DisplayManager.DisplayListener() {
                    @Override
                    public void onDisplayAdded(int displayId) {
                    }

                    @Override
                    public void onDisplayRemoved(int displayId) {
                    }

                    @Override
                    public void onDisplayChanged(int displayId) {
                        refresh();
                    }
                };
                context.getSystemService(DisplayManager.class).registerDisplayListener(
                        mDisplayListener, mHandler, DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS);
            }
            case WALLPAPER -> {
                mColorsListener = (colors, which) -> refresh();
                context.getSystemService(WallpaperManager.class)
                        .addOnColorsChangedListener(mColorsListener, mHandler);
            }
            default -> {
            }
        }
        if (filter.countActions() > 0) {
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    refresh();
                }
            };
            context.registerReceiver(mReceiver, filter, null, mHandler,
                    Context.RECEIVER_NOT_EXPORTED);
        }
    }

    private void unregister(Context context) {
        if (mReceiver != null) {
            context.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        if (mDisplayListener != null) {
            context.getSystemService(DisplayManager.class)
                    .unregisterDisplayListener(mDisplayListener);
            mDisplayListener = null;
        }
        if (mNetworkCallback != null) {
            context.getSystemService(ConnectivityManager.class)
                    .unregisterNetworkCallback(mNetworkCallback);
            mNetworkCallback = null;
        }
        if (mColorsListener != null) {
            context.getSystemService(WallpaperManager.class)
                    .removeOnColorsChangedListener(mColorsListener);
            mColorsListener = null;
        }
    }

    private static int kindOf(String key) {
        if (key == null) {
            return NONE;
        }
        return switch (key) {
            case "top_level_network" -> NETWORK;
            case "top_level_connected_devices" -> BLUETOOTH;
            case "top_level_sound" -> VOLUME;
            case "top_level_priority_modes" -> DND;
            case "top_level_display" -> BRIGHTNESS;
            case "top_level_wallpaper" -> WALLPAPER;
            case "top_level_storage" -> STORAGE;
            case "top_level_battery" -> BATTERY;
            case "top_level_location" -> LOCATION;
            default -> NONE;
        };
    }
}
