/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.util.rr.Utils;
import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class IconStyleActivity extends CollapsingToolbarBaseActivity {
    public static final String KIND_SIGNAL = "signal";
    public static final String KIND_WIFI = "wifi";
    private static final String EXTRA_KIND = "kind";
    private static final String TARGET = "com.android.systemui";
    private static final String PREFIX = "com.resurrection.theme.";
    private static final String MATCH_SETTING = "rr_icon_style_match";
    private static final String SIMS_SETTING = "rr_separate_sim_icons";
    private static final List<String> ORIGINALS = Arrays.asList("remix", "glass", "neon", "orbit",
            "bento", "pulse", "duo", "blueprint", "soft", "shapes", "retro", "crystal", "comet");
    private static final int[] WIFI_ICONS = {0, 1, 2, 2, 3};

    private final Map<String, Resources> mResources = new HashMap<>();
    private final Map<String, String> mLabels = new HashMap<>();
    private final Random mRandom = new Random();
    private IOverlayManager mOverlayManager;
    private List<String> mSignals;
    private List<String> mWifis;
    private String mSignal;
    private String mWifi;
    private String mAppliedSignal;
    private String mAppliedWifi;
    private boolean mWifiDeck;
    private boolean mLinked;
    private boolean mNoInternet;
    private int mLevel = 4;
    private int mGlow;
    private int mAccent;
    private float mDensity;
    private IconDeckView mSignalPlatter;
    private IconDeckView mWifiPlatter;
    private TextView mSignalName;
    private TextView mWifiName;
    private ImageButton mLink;
    private TextView mMode;
    private ImageView mStripSignal;
    private ImageView mStripWifi;
    private ImageView mStripSignal2;
    private MaterialSwitch mSims;
    private boolean mDualSim;
    private TextView mLevelText;
    private TextView mNoInternetToggle;
    private MaterialButtonToggleGroup mTabs;
    private MaterialButton mApply;
    private Adapter mAdapter;

    public static Intent intent(Context context, String kind) {
        return new Intent(context, IconStyleActivity.class).putExtra(EXTRA_KIND, kind);
    }

    public static String currentName(Context context, String kind) {
        String pkg = applied(overlayManager(), kind);
        return pkg == null ? context.getString(R.string.remixer_icon_style_stock)
                : labelOf(context, pkg);
    }

    private static IOverlayManager overlayManager() {
        return IOverlayManager.Stub.asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE));
    }

    private static String category(String kind) {
        return "android.theme.customization." + kind + "_icon";
    }

    private static List<OverlayInfo> overlays(IOverlayManager om, String kind) {
        List<OverlayInfo> list = new ArrayList<>();
        if (om == null) {
            return list;
        }
        try {
            for (OverlayInfo info : om.getOverlayInfosForTarget(TARGET, UserHandle.USER_SYSTEM)) {
                if (category(kind).equals(info.category)) {
                    list.add(info);
                }
            }
        } catch (RemoteException ignored) {
        }
        return list;
    }

    private static String applied(IOverlayManager om, String kind) {
        for (OverlayInfo info : overlays(om, kind)) {
            if (info.isEnabled()) {
                return info.packageName;
            }
        }
        return null;
    }

    private static String labelOf(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static String key(String pkg) {
        return pkg == null ? null : pkg.substring(pkg.lastIndexOf('.') + 1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_icon_style);
        setTitle(R.string.sb_icons_category);
        mDensity = getResources().getDisplayMetrics().density;
        mWifiDeck = KIND_WIFI.equals(getIntent().getStringExtra(EXTRA_KIND));
        mOverlayManager = overlayManager();
        mSignals = styles(KIND_SIGNAL);
        mWifis = styles(KIND_WIFI);
        mAppliedSignal = applied(mOverlayManager, KIND_SIGNAL);
        mAppliedWifi = applied(mOverlayManager, KIND_WIFI);
        mSignal = mAppliedSignal;
        mWifi = mAppliedWifi;
        mLinked = Settings.System.getIntForUser(getContentResolver(), MATCH_SETTING, 1,
                UserHandle.USER_CURRENT) != 0;

        float[] hsl = new float[3];
        ColorUtils.colorToHSL(getColor(android.R.color.system_accent1_500), hsl);
        float sat = hsl[1] < 0.12f ? 0 : 1;
        mGlow = RemixerHubView.hsl(hsl[0], 0.75f * sat, 0.38f);
        mAccent = RemixerHubView.hsl(hsl[0], 0.95f * sat, 0.74f);
        GradientDrawable stageBg = new GradientDrawable();
        stageBg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        stageBg.setGradientCenter(0.5f, 1.1f);
        stageBg.setGradientRadius(300 * mDensity);
        stageBg.setColors(new int[] {mGlow, RemixerHubView.hsl(hsl[0], 0.4f * sat, 0.09f)},
                new float[] {0f, 0.7f});
        stageBg.setCornerRadius(26 * mDensity);
        View stage = findViewById(R.id.icon_style_stage);
        stage.setBackground(stageBg);
        stage.setClipToOutline(true);
        findViewById(R.id.icon_style_strip).setBackground(shape(0x9E000000, 0, 16));

        mSignalPlatter = findViewById(R.id.icon_style_platter_signal);
        mWifiPlatter = findViewById(R.id.icon_style_platter_wifi);
        int disc = ColorUtils.setAlphaComponent(mGlow, 0x8C);
        mSignalPlatter.setColors(mAccent, disc);
        mWifiPlatter.setColors(mAccent, disc);
        mSignalPlatter.setOnClickListener(v -> focus(false));
        mWifiPlatter.setOnClickListener(v -> focus(true));
        mSignalName = findViewById(R.id.icon_style_name_signal);
        mWifiName = findViewById(R.id.icon_style_name_wifi);
        deckButton(R.id.icon_style_prev_signal, v -> step(false, -1));
        deckButton(R.id.icon_style_next_signal, v -> step(false, 1));
        deckButton(R.id.icon_style_prev_wifi, v -> step(true, -1));
        deckButton(R.id.icon_style_next_wifi, v -> step(true, 1));
        mLink = findViewById(R.id.icon_style_link);
        mLink.setOnClickListener(v -> setLinked(!mLinked));
        mMode = findViewById(R.id.icon_style_mode);
        mStripSignal = findViewById(R.id.icon_style_strip_signal);
        mStripWifi = findViewById(R.id.icon_style_strip_wifi);
        mStripSignal2 = findViewById(R.id.icon_style_strip_signal2);
        TelephonyManager telephony = getSystemService(TelephonyManager.class);
        mDualSim = telephony != null && telephony.getSupportedModemCount() > 1;
        View sims = findViewById(R.id.icon_style_sims);
        sims.setVisibility(mDualSim ? View.VISIBLE : View.GONE);
        mSims = findViewById(R.id.icon_style_sims_switch);
        sims.setOnClickListener(v -> mSims.toggle());
        mSims.setOnCheckedChangeListener((button, checked) -> {
            if (checked != separateSims()) {
                Settings.System.putIntForUser(getContentResolver(), SIMS_SETTING, checked ? 1 : 0,
                        UserHandle.USER_CURRENT);
                update();
            }
        });

        MaterialButton remix = findViewById(R.id.icon_style_remix);
        remix.setIconResource(R.drawable.remixer_ic_shuffle);
        remix.setOnClickListener(v -> remix());
        int outline = MaterialColors.getColor(remix, com.google.android.material.R.attr.colorOutline);
        TextView weaker = findViewById(R.id.icon_style_weaker);
        TextView stronger = findViewById(R.id.icon_style_stronger);
        weaker.setBackground(shape(Color.TRANSPARENT, outline, 18));
        stronger.setBackground(shape(Color.TRANSPARENT, outline, 18));
        weaker.setOnClickListener(v -> setLevel(mLevel - 1));
        stronger.setOnClickListener(v -> setLevel(mLevel + 1));
        mLevelText = findViewById(R.id.icon_style_level);
        mNoInternetToggle = findViewById(R.id.icon_style_no_internet);
        mNoInternetToggle.setOnClickListener(v -> {
            mNoInternet = !mNoInternet;
            update();
        });

        mTabs = findViewById(R.id.icon_style_tabs);
        mTabs.check(mWifiDeck ? R.id.icon_style_tab_wifi : R.id.icon_style_tab_signal);
        mTabs.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                focus(id == R.id.icon_style_tab_wifi);
            }
        });
        RecyclerView grid = findViewById(R.id.icon_style_grid);
        grid.setLayoutManager(new GridLayoutManager(this, 4));
        mAdapter = new Adapter();
        grid.setAdapter(mAdapter);
        mApply = findViewById(R.id.icon_style_apply);
        mApply.setOnClickListener(v -> confirmApply());
        update();
    }

    private GradientDrawable shape(int fill, int stroke, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        if (stroke != 0) {
            shape.setStroke(Math.round(mDensity), stroke);
        }
        shape.setCornerRadius(radiusDp * mDensity);
        return shape;
    }

    private void deckButton(int id, View.OnClickListener listener) {
        ImageButton button = findViewById(id);
        button.setBackground(shape(0x1FFFFFFF, 0, 16));
        button.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        button.setOnClickListener(listener);
    }

    private List<String> styles(String kind) {
        List<String> packages = new ArrayList<>();
        for (OverlayInfo info : overlays(mOverlayManager, kind)) {
            packages.add(info.packageName);
        }
        packages.sort(Comparator.comparingInt((String pkg) -> {
            int index = ORIGINALS.indexOf(key(pkg));
            return index >= 0 ? index : ORIGINALS.size();
        }).thenComparing(this::label, String.CASE_INSENSITIVE_ORDER));
        packages.add(0, null);
        return packages;
    }

    private String label(String pkg) {
        if (pkg == null) {
            return getString(R.string.remixer_icon_style_stock);
        }
        return mLabels.computeIfAbsent(pkg, p -> labelOf(this, p));
    }

    private Resources resources(String pkg) {
        if (pkg == null) {
            return getResources();
        }
        return mResources.computeIfAbsent(pkg, p -> {
            try {
                return getPackageManager().getResourcesForApplication(p);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        });
    }

    private Drawable icon(boolean wifi, String pkg) {
        return icon(wifi, pkg, mLevel);
    }

    private Drawable icon(boolean wifi, String pkg, int level) {
        String name = wifi ? "ic_wifi_" + WIFI_ICONS[Math.round(level * 4 / 5f)]
                : "ic_mobile_" + level + "_5_bar";
        if (mNoInternet) {
            name += "_error";
        }
        Resources res = resources(pkg);
        if (res == null) {
            return null;
        }
        int id = res.getIdentifier(name, "drawable", pkg == null ? getPackageName() : pkg);
        try {
            return id == 0 ? null : res.getDrawable(id, null);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    private String partner(String pkg, boolean wifi) {
        if (pkg == null) {
            return null;
        }
        String want = PREFIX + (wifi ? KIND_WIFI : KIND_SIGNAL) + "." + key(pkg);
        return (wifi ? mWifis : mSignals).contains(want) ? want : null;
    }

    private void focus(boolean wifi) {
        if (mWifiDeck != wifi) {
            mWifiDeck = wifi;
            mTabs.check(wifi ? R.id.icon_style_tab_wifi : R.id.icon_style_tab_signal);
        }
        update();
    }

    private void choose(boolean wifi, String pkg, int direction) {
        boolean other = false;
        if (wifi) {
            mWifi = pkg;
        } else {
            mSignal = pkg;
        }
        String partner = partner(pkg, !wifi);
        if (mLinked && (pkg == null || partner != null)) {
            other = !Objects.equals(wifi ? mSignal : mWifi, partner);
            if (wifi) {
                mSignal = partner;
            } else {
                mWifi = partner;
            }
        }
        (wifi ? mWifiPlatter : mSignalPlatter).spin(direction);
        if (other) {
            (wifi ? mSignalPlatter : mWifiPlatter).spin(direction);
        }
        update();
    }

    private void step(boolean wifi, int direction) {
        List<String> list = wifi ? mWifis : mSignals;
        int index = Math.max(0, list.indexOf(wifi ? mWifi : mSignal));
        focus(wifi);
        choose(wifi, list.get((index + direction + list.size()) % list.size()), direction);
    }

    private void remix() {
        String signal = mSignals.get(mRandom.nextInt(mSignals.size()));
        String wifi = null;
        if (!mLinked || signal != null) {
            String partner = mLinked ? partner(signal, true) : null;
            wifi = partner != null ? partner : mWifis.get(mRandom.nextInt(mWifis.size()));
        }
        mSignal = signal;
        mWifi = wifi;
        mSignalPlatter.spin(1);
        mWifiPlatter.spin(1);
        update();
    }

    private void setLinked(boolean linked) {
        mLinked = linked;
        Settings.System.putIntForUser(getContentResolver(), MATCH_SETTING, linked ? 1 : 0,
                UserHandle.USER_CURRENT);
        String partner = partner(mSignal, true);
        if (linked && (mSignal == null || partner != null)) {
            mWifi = partner;
            mWifiPlatter.spin(1);
        }
        update();
    }

    private boolean separateSims() {
        return Settings.System.getIntForUser(getContentResolver(), SIMS_SETTING,
                mAppliedSignal != null ? 1 : 0, UserHandle.USER_CURRENT) == 1;
    }

    private void setLevel(int level) {
        mLevel = Math.max(0, Math.min(5, level));
        update();
    }

    private void update() {
        mSignalPlatter.setIcon(icon(false, mSignal));
        mWifiPlatter.setIcon(icon(true, mWifi));
        mSignalPlatter.setActive(!mWifiDeck);
        mWifiPlatter.setActive(mWifiDeck);
        mSignalName.setText(label(mSignal));
        mWifiName.setText(label(mWifi));
        mSignalPlatter.setContentDescription(getString(R.string.remixer_icon_style_deck_description,
                getString(R.string.remixer_icon_deck_signal), label(mSignal)));
        mWifiPlatter.setContentDescription(getString(R.string.remixer_icon_style_deck_description,
                getString(R.string.remixer_wifi_icon), label(mWifi)));
        mStripSignal.setImageDrawable(icon(false, mSignal));
        mStripWifi.setImageDrawable(icon(true, mWifi));
        mStripSignal.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        mSims.setChecked(separateSims());
        boolean separate = mDualSim && mSims.isChecked();
        mStripSignal2.setVisibility(separate ? View.VISIBLE : View.GONE);
        mStripSignal2.setImageDrawable(separate ? icon(false, mSignal, Math.max(0, mLevel - 2)) : null);
        mStripSignal2.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        mStripWifi.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        mMode.setText(mLinked ? R.string.remixer_icon_style_matched
                : R.string.remixer_icon_style_mixed);
        mLink.setImageResource(mLinked ? R.drawable.remixer_ic_link : R.drawable.remixer_ic_link_off);
        mLink.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        mLink.setBackground(mLinked ? shape(mGlow, mAccent, 20) : shape(0x59000000, 0x40FFFFFF, 20));
        mLink.setContentDescription(getString(mLinked ? R.string.remixer_icon_style_unlink
                : R.string.remixer_icon_style_link));
        mLevelText.setText(getString(R.string.remixer_icon_style_level, mLevel));
        int outline = MaterialColors.getColor(mNoInternetToggle,
                com.google.android.material.R.attr.colorOutline);
        int container = MaterialColors.getColor(mNoInternetToggle,
                com.google.android.material.R.attr.colorSecondaryContainer);
        mNoInternetToggle.setBackground(mNoInternet ? shape(container, mAccent, 18)
                : shape(Color.TRANSPARENT, outline, 18));
        mNoInternetToggle.setSelected(mNoInternet);
        mAdapter.notifyDataSetChanged();
        boolean pending = !Objects.equals(mSignal, mAppliedSignal)
                || !Objects.equals(mWifi, mAppliedWifi);
        mApply.setEnabled(pending);
        mApply.setText(pending ? R.string.remixer_icon_style_apply
                : R.string.remixer_icon_style_applied);
    }

    private void confirmApply() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remixer_icon_style_restart_title)
                .setMessage(R.string.remixer_icon_style_restart_message)
                .setPositiveButton(R.string.remixer_icon_style_restart, (d, w) -> apply())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void apply() {
        try {
            enable(KIND_SIGNAL, mSignal);
            enable(KIND_WIFI, mWifi);
        } catch (RemoteException | IllegalStateException | SecurityException e) {
            Toast.makeText(this, R.string.remixer_icon_style_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> all = new HashSet<>();
        Set<String> want = new HashSet<>();
        for (String kind : new String[] {KIND_SIGNAL, KIND_WIFI}) {
            for (OverlayInfo info : overlays(mOverlayManager, kind)) {
                all.add(info.baseCodePath);
                if (info.packageName.equals(mSignal) || info.packageName.equals(mWifi)) {
                    want.add(info.baseCodePath);
                }
            }
        }
        mAppliedSignal = mSignal;
        mAppliedWifi = mWifi;
        update();
        PackageManager pm = getPackageManager();
        new Thread(() -> {
            long until = SystemClock.uptimeMillis() + 3000;
            while (!loaded(pm, all, want) && SystemClock.uptimeMillis() < until) {
                SystemClock.sleep(50);
            }
            Utils.restartSystemUI();
        }).start();
    }

    private static boolean loaded(PackageManager pm, Set<String> all, Set<String> want) {
        Set<String> have = new HashSet<>();
        try {
            String[] paths = pm.getApplicationInfoAsUser(TARGET, 0, UserHandle.USER_SYSTEM)
                    .overlayPaths;
            if (paths != null) {
                for (String path : paths) {
                    if (all.contains(path)) {
                        have.add(path);
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
        return have.equals(want);
    }

    private void enable(String kind, String pkg) throws RemoteException {
        if (mOverlayManager == null) {
            throw new RemoteException("overlay service missing");
        }
        if (pkg != null) {
            mOverlayManager.setEnabledExclusiveInCategory(pkg, UserHandle.USER_SYSTEM);
            return;
        }
        for (OverlayInfo info : overlays(mOverlayManager, kind)) {
            if (info.isEnabled()) {
                mOverlayManager.setEnabled(info.packageName, false, UserHandle.USER_SYSTEM);
            }
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_icon_style_tile, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String pkg = (mWifiDeck ? mWifis : mSignals).get(position);
            boolean selected = Objects.equals(pkg, mWifiDeck ? mWifi : mSignal);
            MaterialCardView card = (MaterialCardView) holder.itemView;
            holder.icon.setImageDrawable(icon(mWifiDeck, pkg));
            holder.icon.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(card,
                    com.google.android.material.R.attr.colorOnSurface)));
            holder.name.setText(label(pkg));
            int primary = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
            card.setStrokeColor(selected ? primary : Color.TRANSPARENT);
            card.setCardBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(card,
                    selected ? com.google.android.material.R.attr.colorSecondaryContainer
                            : com.google.android.material.R.attr.colorSurfaceContainerHigh)));
            card.setSelected(selected);
            card.setContentDescription(label(pkg));
            card.setOnClickListener(v -> choose(mWifiDeck, pkg, 1));
        }

        @Override
        public int getItemCount() {
            return (mWifiDeck ? mWifis : mSignals).size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;

        Holder(View view) {
            super(view);
            icon = view.findViewById(R.id.icon_style_tile_icon);
            name = view.findViewById(R.id.icon_style_tile_name);
        }
    }
}
