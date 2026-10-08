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

import android.app.UiModeManager;
import android.app.usage.StorageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settingslib.fuelgauge.BatterySaverLogging;
import com.android.settingslib.fuelgauge.BatterySaverUtils;
import com.android.settingslib.utils.ThreadUtils;

import java.io.IOException;
import java.util.Set;

/**
 * Device card, quick tiles and the Remixer card at the top of the Settings homepage.
 * Colors come from the system palette, so the header follows the accent and its shade.
 */
public class HomepageHeaderView extends LinearLayout {
    private static final String ACTION_REMIXER = "com.resurrection.settings.REMIXER";

    private final Meter mStorage;
    private final Meter mBattery;
    private final Tile mInternet;
    private final Tile mBluetooth;
    private final Tile mDark;
    private final Tile mSaver;
    private Intent mBatteryStatus;
    private boolean mRegistered;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                mBatteryStatus = intent;
            }
            updateBattery();
            updateTiles();
        }
    };

    public HomepageHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.rr_homepage_header, this, true);

        int container = color(com.android.internal.R.color.materialColorSecondaryContainer);
        int onContainer = color(com.android.internal.R.color.materialColorOnSecondaryContainer);
        int primary = color(com.android.internal.R.color.materialColorPrimary);
        int onPrimary = color(com.android.internal.R.color.materialColorOnPrimary);

        View card = findViewById(R.id.rr_device_card);
        card.setBackground(pressable(container, 28, onContainer));
        card.setOnClickListener(v -> launch(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
        findViewById(R.id.rr_device_badge).setBackground(round(primary, 16));
        ((ImageView) findViewById(R.id.rr_device_mark)).setImageTintList(
                ColorStateList.valueOf(onPrimary));
        TextView name = findViewById(R.id.rr_device_name);
        name.setTextColor(onContainer);
        name.setText(deviceName());
        TextView version = findViewById(R.id.rr_device_version);
        version.setTextColor(withAlpha(onContainer, 0.8f));
        version.setText(context.getString(R.string.remixer_version,
                SystemProperties.get("ro.rr.version"), Build.VERSION.RELEASE));

        mStorage = new Meter(findViewById(R.id.rr_storage_meter), onContainer, primary,
                R.string.rr_homepage_storage);
        mBattery = new Meter(findViewById(R.id.rr_battery_meter), onContainer, primary,
                R.string.rr_homepage_battery);

        mInternet = new Tile(findViewById(R.id.rr_tile_internet), R.string.rr_homepage_internet,
                R.drawable.ic_settings_wireless);
        mBluetooth = new Tile(findViewById(R.id.rr_tile_bluetooth),
                R.string.rr_homepage_bluetooth, R.drawable.ic_settings_bluetooth);
        mDark = new Tile(findViewById(R.id.rr_tile_dark), R.string.rr_homepage_dark_theme,
                R.drawable.ic_dark_ui);
        mSaver = new Tile(findViewById(R.id.rr_tile_saver), R.string.rr_homepage_battery_saver,
                R.drawable.ic_battery_saver_accent_24dp);

        mInternet.root.setOnClickListener(v -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        mBluetooth.root.setOnClickListener(v -> toggleBluetooth());
        mBluetooth.longPress(Settings.ACTION_BLUETOOTH_SETTINGS);
        mDark.root.setOnClickListener(v -> toggleDarkTheme());
        mDark.longPress(Settings.ACTION_DARK_THEME_SETTINGS);
        mSaver.root.setOnClickListener(v -> toggleBatterySaver());
        mSaver.longPress(Settings.ACTION_BATTERY_SAVER_SETTINGS);

        int remix = color(android.R.color.system_accent1_600);
        View remixer = findViewById(R.id.rr_remixer_card);
        remixer.setBackground(pressable(remix, 28, 0xFFFFFFFF));
        findViewById(R.id.rr_remixer_badge).setBackground(round(0x29FFFFFF, 16));
        remixer.setOnClickListener(v -> launch(new Intent(ACTION_REMIXER)
                .setPackage(getContext().getPackageName())));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        mBatteryStatus = getContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        getContext().registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        mRegistered = true;
        updateBattery();
        updateTiles();
        updateStorage();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mRegistered) {
            getContext().unregisterReceiver(mReceiver);
            mRegistered = false;
        }
        super.onDetachedFromWindow();
    }

    private String deviceName() {
        String name = Settings.Global.getString(getContext().getContentResolver(),
                Settings.Global.DEVICE_NAME);
        return TextUtils.isEmpty(name) ? Build.MODEL : name;
    }

    private void updateStorage() {
        Context context = getContext().getApplicationContext();
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
            int used = (int) Math.round((total - free) * 100d / total);
            String caption = context.getString(R.string.rr_homepage_free,
                    Formatter.formatShortFileSize(context, free));
            post(() -> mStorage.set(used, caption));
        });
    }

    private void updateBattery() {
        if (mBatteryStatus == null) {
            return;
        }
        int level = mBatteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = mBatteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            return;
        }
        int percent = Math.round(level * 100f / scale);
        int status = mBatteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        boolean plugged = mBatteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        int caption;
        if (plugged) {
            caption = status == BatteryManager.BATTERY_STATUS_FULL
                    ? R.string.rr_homepage_charged : R.string.rr_homepage_charging;
        } else {
            caption = isPowerSave() ? R.string.rr_homepage_saver_on : R.string.rr_homepage_on_battery;
        }
        mBattery.set(percent, getContext().getString(caption));
    }

    private void updateTiles() {
        Context context = getContext();

        WifiManager wifi = context.getSystemService(WifiManager.class);
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        Network network = cm != null ? cm.getActiveNetwork() : null;
        NetworkCapabilities caps = network != null ? cm.getNetworkCapabilities(network) : null;
        boolean onWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean onCell = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        String internet;
        if (onWifi) {
            internet = ssid(wifi);
        } else if (onCell) {
            internet = context.getString(R.string.rr_homepage_mobile_data);
        } else {
            internet = context.getString(wifi != null && wifi.isWifiEnabled()
                    ? R.string.rr_homepage_not_connected : R.string.rr_homepage_off);
        }
        mInternet.bind(onWifi || onCell, internet);

        BluetoothAdapter adapter = bluetooth();
        boolean btOn = adapter != null && adapter.isEnabled();
        int connected = btOn ? connectedDevices(adapter) : 0;
        mBluetooth.bind(btOn, connected > 0
                ? getResources().getQuantityString(R.plurals.rr_homepage_bt_devices,
                        connected, connected)
                : context.getString(btOn ? R.string.rr_homepage_on : R.string.rr_homepage_off));

        boolean dark = isDarkTheme();
        mDark.bind(dark, context.getString(dark ? R.string.rr_homepage_on : R.string.rr_homepage_off));

        boolean saver = isPowerSave();
        mSaver.bind(saver, context.getString(saver ? R.string.rr_homepage_on : R.string.rr_homepage_off));
    }

    private String ssid(WifiManager wifi) {
        WifiInfo info = wifi != null ? wifi.getConnectionInfo() : null;
        String ssid = info != null ? info.getSSID() : null;
        if (ssid != null && ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        return TextUtils.isEmpty(ssid) || WifiManager.UNKNOWN_SSID.equals(ssid)
                ? getContext().getString(R.string.rr_homepage_wifi) : ssid;
    }

    private BluetoothAdapter bluetooth() {
        BluetoothManager bm = getContext().getSystemService(BluetoothManager.class);
        return bm != null ? bm.getAdapter() : null;
    }

    private static int connectedDevices(BluetoothAdapter adapter) {
        int count = 0;
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice device : bonded) {
                    if (device.isConnected()) {
                        count++;
                    }
                }
            }
        } catch (SecurityException e) {
            return 0;
        }
        return count;
    }

    private boolean isDarkTheme() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean isPowerSave() {
        PowerManager pm = getContext().getSystemService(PowerManager.class);
        return pm != null && pm.isPowerSaveMode();
    }

    private void toggleBluetooth() {
        BluetoothAdapter adapter = bluetooth();
        if (adapter == null) {
            return;
        }
        try {
            if (adapter.isEnabled()) {
                adapter.disable();
            } else {
                adapter.enable();
            }
        } catch (SecurityException e) {
            launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        }
    }

    private void toggleDarkTheme() {
        UiModeManager ui = getContext().getSystemService(UiModeManager.class);
        if (ui != null) {
            ui.setNightModeActivated(!isDarkTheme());
        }
    }

    private void toggleBatterySaver() {
        BatterySaverUtils.setPowerSaveMode(getContext(), !isPowerSave(),
                false /* needFirstTimeWarning */, BatterySaverLogging.SAVER_ENABLED_SETTINGS);
    }

    private void launch(Intent intent) {
        try {
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // Page not present on this build.
        }
    }

    private int color(int res) {
        return getContext().getColor(res);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private Drawable pressable(int color, float radiusDp, int ink) {
        return new RippleDrawable(ColorStateList.valueOf(withAlpha(ink, 0.12f)),
                round(color, radiusDp), null);
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round(alpha * 255) << 24) | (color & 0x00FFFFFF);
    }

    /** Usage bar with a label, a percentage and a caption. */
    private final class Meter {
        private final TextView mValue;
        private final TextView mCaption;
        private final View mFill;
        private final View mRest;

        Meter(View root, int text, int fill, int label) {
            ((TextView) root.findViewById(R.id.rr_meter_label)).setText(label);
            ((TextView) root.findViewById(R.id.rr_meter_label)).setTextColor(text);
            mValue = root.findViewById(R.id.rr_meter_value);
            mValue.setTextColor(text);
            mCaption = root.findViewById(R.id.rr_meter_caption);
            mCaption.setTextColor(withAlpha(text, 0.8f));
            root.findViewById(R.id.rr_meter_track).setBackground(round(withAlpha(text, 0.15f), 4));
            mFill = root.findViewById(R.id.rr_meter_fill);
            mFill.setBackground(round(fill, 4));
            mRest = root.findViewById(R.id.rr_meter_rest);
        }

        void set(int percent, String caption) {
            int clamped = Math.max(0, Math.min(100, percent));
            mValue.setText(getContext().getString(R.string.rr_homepage_percent, clamped));
            mCaption.setText(caption);
            ((LayoutParams) mFill.getLayoutParams()).weight = clamped;
            ((LayoutParams) mRest.getLayoutParams()).weight = 100 - clamped;
            mFill.requestLayout();
        }
    }

    /** Quick tile: tap toggles, long press opens its page. */
    private final class Tile {
        final View root;
        private final View mChip;
        private final ImageView mIcon;
        private final TextView mTitle;
        private final TextView mSummary;

        Tile(View root, int title, int icon) {
            this.root = root;
            mChip = root.findViewById(R.id.rr_tile_chip);
            mIcon = root.findViewById(R.id.rr_tile_icon);
            mIcon.setImageResource(icon);
            mTitle = root.findViewById(R.id.rr_tile_title);
            mTitle.setText(title);
            mSummary = root.findViewById(R.id.rr_tile_summary);
        }

        void longPress(String action) {
            root.setOnLongClickListener(v -> {
                launch(new Intent(action));
                return true;
            });
        }

        void bind(boolean active, CharSequence summary) {
            int background;
            int text;
            int sub;
            int chip;
            int glyph;
            if (active) {
                background = color(com.android.internal.R.color.materialColorPrimaryContainer);
                text = color(com.android.internal.R.color.materialColorOnPrimaryContainer);
                sub = withAlpha(text, 0.85f);
                chip = color(com.android.internal.R.color.materialColorPrimary);
                glyph = color(com.android.internal.R.color.materialColorOnPrimary);
            } else {
                background = color(com.android.internal.R.color.materialColorSurfaceBright);
                text = color(com.android.internal.R.color.materialColorOnSurface);
                sub = color(com.android.internal.R.color.materialColorOnSurfaceVariant);
                chip = color(com.android.internal.R.color.materialColorSurfaceContainerHighest);
                glyph = text;
            }
            root.setBackground(pressable(background, 24, text));
            mChip.setBackground(round(chip, 17));
            mIcon.setImageTintList(ColorStateList.valueOf(glyph));
            mTitle.setTextColor(text);
            mSummary.setTextColor(sub);
            mSummary.setText(summary);
            root.setActivated(active);
            root.setContentDescription(mTitle.getText() + ", " + summary);
        }
    }
}
