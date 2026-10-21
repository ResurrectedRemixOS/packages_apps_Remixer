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

import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MIN;
import static com.android.settingslib.display.BrightnessUtils.convertLinearToGammaFloat;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.UiModeManager;
import android.app.settings.SettingsEnums;
import android.app.usage.StorageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.core.CategoryMixin;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.Utils;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.drawer.CategoryKey;
import com.android.settingslib.drawer.DashboardCategory;
import com.android.settingslib.drawer.Tile;
import com.android.settingslib.fuelgauge.BatterySaverLogging;
import com.android.settingslib.fuelgauge.BatterySaverUtils;
import com.android.settingslib.utils.ThreadUtils;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Top of the RR Settings homepage: the device card with its quick toggles, the Remixer and
 * device bars, and the category tiles. Colors come from {@link HomepagePalette}.
 */
public class HomepageHeaderView extends LinearLayout {
    private static final String ACTION_REMIXER = "com.resurrection.settings.REMIXER";
    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String GOOGLE_ACCOUNT = "com.google";
    private static final String GMS = "com.google.android.gms";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final View mCard;
    private final Meter mStorage;
    private final Meter mBattery;
    private final Toggle mInternet;
    private final Toggle mBluetooth;
    private final Toggle mDark;
    private final Toggle mSaver;
    private final View mRemixer;
    private final View mDeviceBar;
    private final Group mNetwork;
    private final Group mApps;
    private final Group mDisplay;
    private final Group mSecurity;
    private final Group mAccounts;
    private final Group mAccessibility;
    private final Group mSystem;
    private final View mAvatarFrame;
    private final ImageView mAvatar;
    private final TextView mInitial;
    private final TextView mBadge;
    private final TextView mAccountName;
    private final TextView mAccountStatus;
    private HomepagePalette mPalette;
    private Tile mDeviceTile;
    private Intent mBatteryStatus;
    private CategoryMixin mCategoryMixin;
    private boolean mRegistered;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (VOLUME_CHANGED.equals(action)) {
                updateLevels();
                return;
            }
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryStatus = intent;
            }
            updateBattery();
            updateToggles();
            updateNetwork();
        }
    };

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    updateLevels();
                }
            };

    private final CategoryMixin.CategoryListener mCategoryListener = categories -> {
        if (categories == null || categories.contains(CategoryKey.CATEGORY_HOMEPAGE)) {
            updateDeviceBar();
        }
    };

    public HomepageHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.rr_homepage_header, this, true);

        mCard = findViewById(R.id.rr_device_card);
        mCard.setClipToOutline(true);
        mCard.setOnClickListener(v -> launch(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
        ((TextView) findViewById(R.id.rr_device_name)).setText(deviceName());
        ((TextView) findViewById(R.id.rr_device_version)).setText(
                context.getString(R.string.remixer_version, Build.VERSION.RELEASE));

        mStorage = new Meter(findViewById(R.id.rr_storage_meter), R.string.rr_homepage_storage,
                Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        mBattery = new Meter(findViewById(R.id.rr_battery_meter), R.string.rr_homepage_battery,
                Intent.ACTION_POWER_USAGE_SUMMARY);

        mInternet = new Toggle(findViewById(R.id.rr_toggle_internet),
                R.string.rr_homepage_internet, R.drawable.ic_settings_wireless);
        mBluetooth = new Toggle(findViewById(R.id.rr_toggle_bluetooth),
                R.string.rr_homepage_bluetooth, R.drawable.ic_settings_bluetooth);
        mDark = new Toggle(findViewById(R.id.rr_toggle_dark), R.string.rr_homepage_dark_theme,
                R.drawable.ic_dark_ui);
        mSaver = new Toggle(findViewById(R.id.rr_toggle_saver), R.string.rr_homepage_saver,
                R.drawable.ic_battery_saver_accent_24dp);

        mInternet.root.setOnClickListener(v -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        mBluetooth.root.setOnClickListener(v -> toggleBluetooth());
        mBluetooth.longPress(Settings.ACTION_BLUETOOTH_SETTINGS);
        mDark.root.setOnClickListener(v -> toggleDarkTheme());
        mDark.longPress(Settings.ACTION_DARK_THEME_SETTINGS);
        mSaver.root.setOnClickListener(v -> toggleBatterySaver());
        mSaver.longPress(Settings.ACTION_BATTERY_SAVER_SETTINGS);

        mRemixer = findViewById(R.id.rr_remixer_card);
        mRemixer.setContentDescription(context.getString(R.string.remixer_app_name) + ", "
                + context.getString(R.string.rr_homepage_remixer_tagline));
        mRemixer.setOnClickListener(v -> launch(new Intent(ACTION_REMIXER)
                .setPackage(getContext().getPackageName())));

        mDeviceBar = findViewById(R.id.rr_device_bar);
        mDeviceBar.setOnClickListener(v -> openDeviceTile());

        mNetwork = new Group(R.id.rr_group_network, HomepageGroups.NETWORK,
                R.string.rr_group_network, R.string.rr_group_network_summary,
                R.drawable.ic_homepage_network, HomepagePalette.NETWORK, TilePreviewView.SIGNAL);
        mApps = new Group(R.id.rr_group_apps, HomepageGroups.APPS, R.string.rr_group_apps,
                R.string.rr_group_apps_summary, R.drawable.ic_homepage_apps,
                HomepagePalette.APPS, TilePreviewView.PILLS);
        mDisplay = new Group(R.id.rr_group_display, HomepageGroups.DISPLAY,
                R.string.rr_group_display, R.string.rr_group_display_summary,
                R.drawable.ic_homepage_display, HomepagePalette.DISPLAY, TilePreviewView.SLIDERS);
        mSecurity = new Group(R.id.rr_group_security, HomepageGroups.SECURITY,
                R.string.rr_group_security, R.string.rr_group_security_summary,
                R.drawable.ic_homepage_security, HomepagePalette.SECURITY, TilePreviewView.DOTS);
        mAccounts = new Group(R.id.rr_group_accounts, HomepageGroups.ACCOUNTS,
                R.string.rr_group_accounts, R.string.rr_group_accounts_summary, 0,
                HomepagePalette.ACCOUNTS, -1);
        mAccessibility = new Group(R.id.rr_group_accessibility, null,
                R.string.accessibility_settings, R.string.rr_group_accessibility_summary,
                R.drawable.ic_homepage_accessibility, HomepagePalette.ACCESSIBILITY,
                TilePreviewView.TYPE);
        mSystem = new Group(R.id.rr_group_system, HomepageGroups.SYSTEM,
                R.string.header_category_system, R.string.rr_group_system_summary,
                R.drawable.ic_homepage_system_dashboard, HomepagePalette.SYSTEM, -1);
        mAccessibility.root.setOnClickListener(
                v -> launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        mAccounts.root.findViewById(R.id.rr_tile_top).setVisibility(GONE);
        mAccounts.root.findViewById(R.id.rr_tile_account).setVisibility(VISIBLE);
        mAvatarFrame = mAccounts.root.findViewById(R.id.rr_tile_avatar_frame);
        mAvatar = mAccounts.root.findViewById(R.id.rr_tile_avatar);
        mAvatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        mAvatar.setClipToOutline(true);
        mInitial = mAccounts.root.findViewById(R.id.rr_tile_initial);
        mBadge = mAccounts.root.findViewById(R.id.rr_tile_badge);
        mAccountName = mAccounts.root.findViewById(R.id.rr_tile_account_name);
        mAccountStatus = mAccounts.root.findViewById(R.id.rr_tile_account_status);

        applyColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Size the tiles, then stretch the short one in each pair to match.
        for (View tile : tiles()) {
            ((LayoutParams) tile.getLayoutParams()).height = LayoutParams.WRAP_CONTENT;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (stretchShortTiles()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private View[] tiles() {
        return new View[] {mNetwork.root, mApps.root, mDisplay.root, mSecurity.root,
                mAccounts.root, mAccessibility.root};
    }

    private boolean stretchShortTiles() {
        return stretch(mNetwork.root, mApps.root)
                | stretch(mDisplay.root, mSecurity.root)
                | stretch(mAccounts.root, mAccessibility.root);
    }

    private boolean stretch(View a, View b) {
        if (a.getMeasuredHeight() == b.getMeasuredHeight()) {
            return false;
        }
        final int height = Math.max(a.getMeasuredHeight(), b.getMeasuredHeight());
        ((LayoutParams) a.getLayoutParams()).height = height;
        ((LayoutParams) b.getLayoutParams()).height = height;
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final Context context = getContext();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(VOLUME_CHANGED);
        mBatteryStatus = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        context.registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        final DisplayManager displays = context.getSystemService(DisplayManager.class);
        if (displays != null) {
            displays.registerDisplayListener(mDisplayListener, mHandler,
                    DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS);
        }
        mCategoryMixin = categoryMixin();
        if (mCategoryMixin != null) {
            mCategoryMixin.addCategoryListener(mCategoryListener);
        }
        mRegistered = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mRegistered) {
            final Context context = getContext();
            context.unregisterReceiver(mReceiver);
            final DisplayManager displays = context.getSystemService(DisplayManager.class);
            if (displays != null) {
                displays.unregisterDisplayListener(mDisplayListener);
            }
            if (mCategoryMixin != null) {
                mCategoryMixin.removeCategoryListener(mCategoryListener);
                mCategoryMixin = null;
            }
            mRegistered = false;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE || !mRegistered) {
            return;
        }
        if (mPalette.hues != HomepageStyle.tileHues(getContext())) {
            applyColors();
        }
        updateBattery();
        updateToggles();
        updateNetwork();
        updateLevels();
        updateStorage();
        updateAccount();
        updateDeviceBar();
    }

    private void applyColors() {
        final HomepagePalette p = new HomepagePalette(getContext());
        mPalette = p;

        final GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {p.heroTop, p.heroBottom});
        base.setCornerRadius(dp(28));
        base.setStroke(dp(1), p.edge);
        final GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[] {p.heroGlow, HomepagePalette.alpha(p.heroGlow, 0)});
        glow.setGradientCenter(1f, 0f);
        glow.setGradientRadius(dp(220));
        glow.setCornerRadius(dp(28));
        mCard.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(p.text, 0.12f)),
                new LayerDrawable(new Drawable[] {base, glow}), null));
        final GradientDrawable badge = round(p.badge, 16);
        badge.setStroke(dp(1), p.edge);
        findViewById(R.id.rr_device_badge).setBackground(badge);
        tint(R.id.rr_device_mark, p.neon);
        tint(R.id.rr_device_watermark, p.watermark);
        tint(R.id.rr_device_chevron, p.subtext);
        ((TextView) findViewById(R.id.rr_device_name)).setTextColor(p.text);
        ((TextView) findViewById(R.id.rr_device_version)).setTextColor(p.subtext);
        mStorage.applyColors(p);
        mBattery.applyColors(p);

        final HomepagePalette.Bar remix = p.bar(0);
        mRemixer.setBackground(bar(p, remix));
        tint(R.id.rr_remixer_mark, remix.mark);
        ((TextView) findViewById(R.id.rr_remixer_word)).setTextColor(p.barText);
        ((TextView) findViewById(R.id.rr_remixer_tagline)).setTextColor(p.barSubtext);
        pill(findViewById(R.id.rr_remixer_action), p);

        final HomepagePalette.Bar device = p.bar(HomepagePalette.DEVICE);
        mDeviceBar.setBackground(bar(p, device));
        final TextView mark = findViewById(R.id.rr_device_bar_mark);
        mark.setTextColor(device.mark);
        mark.setShadowLayer(dp(8), 0, 0, device.glow);
        ((TextView) findViewById(R.id.rr_device_bar_word)).setTextColor(p.barText);
        ((TextView) findViewById(R.id.rr_device_bar_tagline)).setTextColor(p.barSubtext);
        pill(findViewById(R.id.rr_device_bar_action), p);

        for (Group group : new Group[] {mNetwork, mApps, mDisplay, mSecurity, mAccounts,
                mAccessibility, mSystem}) {
            group.applyColors(p);
        }
        final HomepagePalette.Tile account = mAccounts.colors;
        final GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(2.5f), account.ring);
        mAvatarFrame.setBackground(ring);
        final GradientDrawable photo = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {account.photoA, account.photoB});
        photo.setShape(GradientDrawable.OVAL);
        mInitial.setBackground(photo);
        final GradientDrawable chip = new GradientDrawable();
        chip.setShape(GradientDrawable.OVAL);
        chip.setColor(Color.WHITE);
        chip.setStroke(dp(2), account.bottom);
        mBadge.setBackground(chip);
        mAccountName.setTextColor(p.text);
        mAccountStatus.setTextColor(p.subtext);

        for (Toggle toggle : new Toggle[] {mInternet, mBluetooth, mDark, mSaver}) {
            toggle.applyColors();
        }
    }

    private Drawable bar(HomepagePalette p, HomepagePalette.Bar bar) {
        final GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {bar.top, p.barBottom});
        shape.setCornerRadius(dp(28));
        shape.setStroke(dp(1), bar.edge);
        return new RippleDrawable(ColorStateList.valueOf(0x1FFFFFFF), shape, null);
    }

    private void pill(TextView view, HomepagePalette p) {
        view.setBackground(round(p.pill, 100));
        view.setTextColor(p.pillInk);
        view.setCompoundDrawableTintList(ColorStateList.valueOf(p.pillInk));
    }

    private void tint(int id, int color) {
        ((ImageView) findViewById(id)).setImageTintList(ColorStateList.valueOf(color));
    }

    private String deviceName() {
        final String name = Settings.Global.getString(getContext().getContentResolver(),
                Settings.Global.DEVICE_NAME);
        return TextUtils.isEmpty(name) ? Build.MODEL : name;
    }

    private void updateStorage() {
        final Context context = getContext().getApplicationContext();
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
            final int used = (int) Math.round((total - free) * 100d / total);
            final String caption = context.getString(R.string.rr_homepage_free,
                    Formatter.formatShortFileSize(context, free));
            post(() -> mStorage.set(used, caption));
        });
    }

    private void updateBattery() {
        if (mBatteryStatus == null) {
            return;
        }
        final int level = mBatteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = mBatteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            return;
        }
        final int percent = Math.round(level * 100f / scale);
        final int status = mBatteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        final boolean plugged = mBatteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        final int caption;
        if (plugged) {
            caption = status == BatteryManager.BATTERY_STATUS_FULL
                    ? R.string.rr_homepage_charged : R.string.rr_homepage_charging;
        } else {
            caption = isPowerSave() ? R.string.rr_homepage_saver_on : R.string.rr_homepage_on_battery;
        }
        mBattery.set(percent, getContext().getString(caption));
    }

    private void updateToggles() {
        final Context context = getContext();
        final BluetoothAdapter adapter = bluetooth();
        final boolean btOn = adapter != null && adapter.isEnabled();
        final int connected = btOn ? connectedDevices(adapter) : 0;
        mBluetooth.bind(btOn, connected > 0
                ? getResources().getQuantityString(R.plurals.rr_homepage_bt_devices,
                        connected, connected)
                : context.getString(btOn ? R.string.rr_homepage_on : R.string.rr_homepage_off));

        final boolean dark = isDarkTheme();
        mDark.bind(dark, context.getString(dark ? R.string.rr_homepage_on : R.string.rr_homepage_off));

        final boolean saver = isPowerSave();
        mSaver.bind(saver, context.getString(saver ? R.string.rr_homepage_on : R.string.rr_homepage_off));
    }

    private void updateNetwork() {
        final Context context = getContext();
        final WifiManager wifi = context.getSystemService(WifiManager.class);
        final ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        final Network network = cm != null ? cm.getActiveNetwork() : null;
        final NetworkCapabilities caps = network != null ? cm.getNetworkCapabilities(network) : null;
        final boolean onWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        final boolean onCell = caps != null
                && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        final String internet;
        final float level;
        final int chip;
        if (onWifi && wifi != null) {
            internet = ssid(wifi);
            level = (wifi.calculateSignalLevel(caps.getSignalStrength()) + 1f)
                    / (wifi.getMaxSignalLevel() + 1f);
            chip = R.string.rr_live_wifi;
        } else if (onCell) {
            internet = context.getString(R.string.rr_homepage_mobile_data);
            final TelephonyManager telephony = context.getSystemService(TelephonyManager.class);
            final SignalStrength signal = telephony != null ? telephony.getSignalStrength() : null;
            level = signal != null ? (signal.getLevel() + 1f) / 5f : 1f;
            chip = R.string.rr_live_mobile;
        } else {
            internet = context.getString(wifi != null && wifi.isWifiEnabled()
                    ? R.string.rr_homepage_not_connected : R.string.rr_homepage_off);
            level = 0f;
            chip = R.string.rr_live_offline;
        }
        mInternet.bind(onWifi || onCell, internet);
        mNetwork.preview.setLevels(level, 0f);
        mNetwork.setChip(context.getString(chip));
    }

    private void updateLevels() {
        final Context context = getContext();
        float brightness = 0.5f;
        final DisplayManager displays = context.getSystemService(DisplayManager.class);
        final Display display = displays != null
                ? displays.getDisplay(Display.DEFAULT_DISPLAY) : null;
        final BrightnessInfo info = display != null ? display.getBrightnessInfo() : null;
        if (info != null) {
            final int value = convertLinearToGammaFloat(info.brightness,
                    info.brightnessMinimum, info.brightnessMaximum);
            brightness = (value - GAMMA_SPACE_MIN) / (float) (GAMMA_SPACE_MAX - GAMMA_SPACE_MIN);
        }
        final AudioManager audio = context.getSystemService(AudioManager.class);
        final int max = audio != null ? audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 0;
        final float volume = max > 0
                ? audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max : 0f;
        mDisplay.preview.setLevels(brightness, volume);
    }

    private void updateAccount() {
        final Context context = getContext();
        final boolean gms = hasGms();
        mAccounts.title.setText(gms ? R.string.rr_group_accounts : R.string.rr_group_accounts_plain);
        mAccounts.summary.setText(gms
                ? R.string.rr_group_accounts_summary : R.string.rr_group_accounts_plain_summary);
        mBadge.setVisibility(gms ? VISIBLE : GONE);

        final UserManager um = context.getSystemService(UserManager.class);
        final UserInfo info = um != null ? um.getUserInfo(UserHandle.myUserId()) : null;
        final String name = info != null && !TextUtils.isEmpty(info.name)
                ? info.name : context.getString(R.string.rr_homepage_account);
        mAccountName.setText(name);
        if (info != null && info.iconPath != null) {
            mAvatar.setImageDrawable(Utils.getUserIcon(context, um, info));
            mAvatar.setVisibility(VISIBLE);
            mInitial.setVisibility(GONE);
        } else {
            mInitial.setText(name.substring(0, name.offsetByCodePoints(0, 1))
                    .toUpperCase(Locale.getDefault()));
            mInitial.setVisibility(VISIBLE);
            mAvatar.setVisibility(GONE);
        }

        final AccountManager accounts = AccountManager.get(context);
        final Account[] signedIn = gms
                ? accounts.getAccountsByType(GOOGLE_ACCOUNT) : accounts.getAccounts();
        mAccountStatus.setText(signedIn.length > 0
                ? R.string.rr_homepage_signed_in : R.string.rr_homepage_signed_out);
        mAccounts.root.setContentDescription(mAccounts.title.getText() + ", " + name + ", "
                + mAccountStatus.getText());
    }

    private void updateDeviceBar() {
        final DashboardCategory category = FeatureFactory.getFeatureFactory()
                .getDashboardFeatureProvider().getTilesForCategory(CategoryKey.CATEGORY_HOMEPAGE);
        Tile found = null;
        if (category != null) {
            for (Tile tile : category.getTiles()) {
                if (HomepageGroups.DEVICE.equals(HomepageGroups.placeOf(tile))) {
                    found = tile;
                    break;
                }
            }
        }
        mDeviceTile = found;
        mDeviceBar.setVisibility(found != null ? VISIBLE : GONE);
        if (found != null) {
            mDeviceBar.setContentDescription(found.getTitle(getContext()) + ", "
                    + getContext().getString(R.string.rr_homepage_device_tagline));
        }
    }

    private void openDeviceTile() {
        if (mDeviceTile != null) {
            launch(new Intent(mDeviceTile.getIntent()).putExtra(
                    MetricsFeatureProvider.EXTRA_SOURCE_METRICS_CATEGORY,
                    SettingsEnums.DASHBOARD_SUMMARY));
        }
    }

    private CategoryMixin categoryMixin() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof CategoryMixin.CategoryHandler handler) {
                return handler.getCategoryMixin();
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private boolean hasGms() {
        try {
            getContext().getPackageManager().getPackageInfo(GMS, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String ssid(WifiManager wifi) {
        final WifiInfo info = wifi.getConnectionInfo();
        String ssid = info != null ? info.getSSID() : null;
        if (ssid != null && ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        return TextUtils.isEmpty(ssid) || WifiManager.UNKNOWN_SSID.equals(ssid)
                ? getContext().getString(R.string.rr_homepage_wifi) : ssid;
    }

    private BluetoothAdapter bluetooth() {
        final BluetoothManager bm = getContext().getSystemService(BluetoothManager.class);
        return bm != null ? bm.getAdapter() : null;
    }

    private static int connectedDevices(BluetoothAdapter adapter) {
        int count = 0;
        try {
            final Set<BluetoothDevice> bonded = adapter.getBondedDevices();
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
        final PowerManager pm = getContext().getSystemService(PowerManager.class);
        return pm != null && pm.isPowerSaveMode();
    }

    private void toggleBluetooth() {
        final BluetoothAdapter adapter = bluetooth();
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
        final UiModeManager ui = getContext().getSystemService(UiModeManager.class);
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

    private Drawable glyph(int res) {
        final Drawable icon = getContext().getDrawable(res);
        final Drawable glyph = icon instanceof LayerDrawable layers
                && layers.getNumberOfLayers() > 1 ? layers.getDrawable(1) : icon;
        final Drawable.ConstantState state = glyph.getConstantState();
        return state != null ? state.newDrawable(getResources()).mutate() : glyph.mutate();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, float radiusDp) {
        final GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round(alpha * 255) << 24) | (color & 0x00FFFFFF);
    }

    /** Usage bar with a label, a percentage and a caption; a tap opens its page. */
    private final class Meter {
        private final View mRoot;
        private final TextView mLabel;
        private final TextView mValue;
        private final TextView mCaption;
        private final View mTrack;
        private final View mFill;
        private final View mRest;

        Meter(View root, int label, String action) {
            mRoot = root;
            mLabel = root.findViewById(R.id.rr_meter_label);
            mLabel.setText(getContext().getString(R.string.rr_homepage_link,
                    getContext().getString(label)));
            mValue = root.findViewById(R.id.rr_meter_value);
            mCaption = root.findViewById(R.id.rr_meter_caption);
            mTrack = root.findViewById(R.id.rr_meter_track);
            mFill = root.findViewById(R.id.rr_meter_fill);
            mRest = root.findViewById(R.id.rr_meter_rest);
            root.setOnClickListener(v -> launch(new Intent(action)));
        }

        void applyColors(HomepagePalette p) {
            mLabel.setTextColor(p.text);
            mValue.setTextColor(p.text);
            mCaption.setTextColor(p.subtext);
            mTrack.setBackground(round(p.track, 3));
            mFill.setBackground(round(p.neon, 3));
            mRoot.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(withAlpha(p.text, 0.12f)), null,
                    round(Color.WHITE, 10)));
        }

        void set(int percent, String caption) {
            final int clamped = Math.max(0, Math.min(100, percent));
            mValue.setText(getContext().getString(R.string.rr_homepage_percent, clamped));
            mCaption.setText(caption);
            ((LayoutParams) mFill.getLayoutParams()).weight = clamped;
            ((LayoutParams) mRest.getLayoutParams()).weight = 100 - clamped;
            mFill.requestLayout();
        }
    }

    /** Round quick toggle in the device card: tap toggles, long press opens its page. */
    private final class Toggle {
        final View root;
        private final View mCircle;
        private final ImageView mIcon;
        private final TextView mLabel;
        private boolean mActive;

        Toggle(View root, int label, int icon) {
            this.root = root;
            mCircle = root.findViewById(R.id.rr_toggle_circle);
            mCircle.setDuplicateParentStateEnabled(true);
            mIcon = root.findViewById(R.id.rr_toggle_icon);
            mIcon.setImageResource(icon);
            mLabel = root.findViewById(R.id.rr_toggle_label);
            mLabel.setText(label);
            root.setContentDescription(mLabel.getText());
        }

        void longPress(String action) {
            root.setOnLongClickListener(v -> {
                launch(new Intent(action));
                return true;
            });
        }

        void bind(boolean active, CharSequence state) {
            mActive = active;
            root.setActivated(active);
            root.setStateDescription(state);
            applyColors();
        }

        void applyColors() {
            final HomepagePalette p = mPalette;
            final GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(mActive ? p.on : p.offFill);
            if (!mActive) {
                circle.setStroke(dp(1), p.offStroke);
            }
            final int ink = mActive ? p.onInk : p.offInk;
            mCircle.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(ink, 0.16f)),
                    circle, null));
            mIcon.setImageTintList(ColorStateList.valueOf(ink));
            mLabel.setTextColor(p.offInk);
        }
    }

    /** A category tile, or the wide System row; a tap opens its page. */
    private final class Group {
        final View root;
        final TextView title;
        final TextView summary;
        final TilePreviewView preview;
        private final ImageView mIcon;
        private final TextView mChip;
        private final Drawable mGlyph;
        private final int mOffset;
        HomepagePalette.Tile colors;

        Group(int id, String group, int titleRes, int summaryRes, int glyphRes, int offset,
                int kind) {
            root = findViewById(id);
            title = root.findViewById(R.id.rr_tile_title);
            summary = root.findViewById(R.id.rr_tile_summary);
            preview = root.findViewById(R.id.rr_tile_preview);
            mIcon = root.findViewById(R.id.rr_tile_icon);
            mChip = root.findViewById(R.id.rr_tile_chip);
            mGlyph = glyphRes != 0 ? glyph(glyphRes) : null;
            mOffset = offset;
            title.setText(titleRes);
            summary.setText(summaryRes);
            if (preview != null) {
                if (kind < 0) {
                    preview.setVisibility(GONE);
                } else {
                    preview.setKind(kind);
                }
            }
            if (group != null) {
                root.setOnClickListener(v -> HomepageGroupFragment.launch(getContext(), group,
                        title.getText()));
            }
        }

        void setChip(CharSequence text) {
            mChip.setText(text);
            mChip.setVisibility(VISIBLE);
        }

        void applyColors(HomepagePalette p) {
            colors = p.tile(mOffset);
            final GradientDrawable shape = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[] {colors.top, colors.bottom});
            shape.setCornerRadius(dp(24));
            shape.setStroke(dp(1), colors.edge);
            root.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(withAlpha(colors.ring, 0.16f)), shape, null));
            if (mGlyph != null) {
                final NeonRingDrawable ring = new NeonRingDrawable(mGlyph, dp(38),
                        getResources().getDisplayMetrics().density);
                ring.setColors(colors.ring, colors.ring, colors.ring,
                        HomepagePalette.alpha(colors.ring, 0x29), 0, 0);
                ring.setState(1f, true);
                mIcon.setImageDrawable(ring);
            }
            title.setTextColor(p.text);
            summary.setTextColor(p.subtext);
            if (preview != null) {
                preview.setColors(colors.ring, colors.dim);
            }
            if (mChip != null) {
                mChip.setTextColor(colors.chipText);
                mChip.setBackground(round(colors.chipBg, 100));
            }
            final ImageView chevron = root.findViewById(R.id.rr_tile_chevron);
            if (chevron != null) {
                chevron.setImageTintList(ColorStateList.valueOf(p.subtext));
            }
        }
    }
}
