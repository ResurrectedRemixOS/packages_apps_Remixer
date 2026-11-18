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
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.util.rr.Utils;
import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class IconStyleActivity extends CollapsingToolbarBaseActivity {
    public static final String KIND_SIGNAL = "signal";
    public static final String KIND_WIFI = "wifi";
    private static final String EXTRA_KIND = "kind";
    private static final String TARGET = "com.android.systemui";
    private static final String PREFIX = "com.resurrection.theme.";
    private static final String MATCH_SETTING = "rr_icon_style_match";
    private static final List<String> ORIGINALS =
            Arrays.asList("remix", "glass", "neon", "orbit", "bento");
    private static final String[] SIGNAL_THUMBS =
            {"ic_mobile_1_5_bar", "ic_mobile_3_5_bar", "ic_mobile_5_5_bar"};
    private static final String[] WIFI_THUMBS = {"ic_wifi_1", "ic_wifi_2", "ic_wifi_3"};

    private IOverlayManager mOverlayManager;
    private String mKind;
    private List<String> mValues;
    private View[] mRows;
    private String mApplied;
    private String mSelected;
    private String mOtherApplied;
    private MaterialSwitch mMatch;
    private MaterialButton mApply;
    private TextView mPreviewLabel;

    public static Intent intent(Context context, String kind) {
        return new Intent(context, IconStyleActivity.class).putExtra(EXTRA_KIND, kind);
    }

    public static String currentName(Context context, String kind) {
        String pkg = applied(overlayManager(), kind);
        return pkg == null ? context.getString(R.string.remixer_icon_style_stock)
                : label(context, pkg);
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

    private static String label(Context context, String pkg) {
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
        mKind = KIND_WIFI.equals(getIntent().getStringExtra(EXTRA_KIND)) ? KIND_WIFI : KIND_SIGNAL;
        setTitle(isSignal() ? R.string.remixer_signal_icon : R.string.remixer_wifi_icon);
        mOverlayManager = overlayManager();
        mApplied = applied(mOverlayManager, mKind);
        mOtherApplied = applied(mOverlayManager, isSignal() ? KIND_WIFI : KIND_SIGNAL);
        mSelected = mApplied;

        List<OverlayInfo> packs = overlays(mOverlayManager, mKind);
        packs.sort(Comparator.comparingInt((OverlayInfo info) -> {
            int index = ORIGINALS.indexOf(key(info.packageName));
            return index >= 0 ? index : ORIGINALS.size();
        }).thenComparing(info -> label(this, info.packageName), String.CASE_INSENSITIVE_ORDER));
        mValues = new ArrayList<>();
        mValues.add(null);
        for (OverlayInfo info : packs) {
            mValues.add(info.packageName);
        }

        mPreviewLabel = findViewById(R.id.icon_style_preview_label);
        mMatch = findViewById(R.id.icon_style_match);
        mMatch.setVisibility(isSignal() ? View.VISIBLE : View.GONE);
        mMatch.setChecked(Settings.System.getIntForUser(getContentResolver(), MATCH_SETTING, 1,
                UserHandle.USER_CURRENT) != 0);
        mMatch.setOnCheckedChangeListener((v, checked) -> {
            Settings.System.putIntForUser(getContentResolver(), MATCH_SETTING, checked ? 1 : 0,
                    UserHandle.USER_CURRENT);
            update();
        });
        mApply = findViewById(R.id.icon_style_apply);
        mApply.setOnClickListener(v -> confirmApply());

        LinearLayout list = findViewById(R.id.icon_style_list);
        LayoutInflater inflater = LayoutInflater.from(list.getContext());
        mRows = new View[mValues.size()];
        final String[] thumbs = isSignal() ? SIGNAL_THUMBS : WIFI_THUMBS;
        for (int i = 0; i < mValues.size(); i++) {
            final String pkg = mValues.get(i);
            if (i == 1 || (i > 1 && ORIGINALS.contains(key(mValues.get(i - 1)))
                    && !ORIGINALS.contains(key(pkg)))) {
                TextView header = (TextView) inflater.inflate(
                        R.layout.remixer_icon_style_header, list, false);
                header.setText(ORIGINALS.contains(key(pkg))
                        ? R.string.remixer_icon_style_originals
                        : R.string.remixer_icon_style_classics);
                list.addView(header);
            }
            View row = inflater.inflate(R.layout.remixer_icon_style_item, list, false);
            LinearLayout thumb = row.findViewById(R.id.icon_style_thumb);
            for (int t = 0; t < thumb.getChildCount(); t++) {
                ImageView image = (ImageView) thumb.getChildAt(t);
                image.setImageDrawable(drawable(pkg, thumbs[t]));
                image.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            }
            ((TextView) row.findViewById(R.id.icon_style_name)).setText(
                    pkg == null ? getString(R.string.remixer_icon_style_stock) : label(this, pkg));
            row.setOnClickListener(v -> {
                mSelected = pkg;
                update();
            });
            list.addView(row);
            mRows[i] = row;
        }
        update();
    }

    private boolean isSignal() {
        return KIND_SIGNAL.equals(mKind);
    }

    private String matchingWifi(String signalPkg) {
        if (signalPkg == null) {
            return null;
        }
        String wifi = PREFIX + KIND_WIFI + "." + key(signalPkg);
        for (OverlayInfo info : overlays(mOverlayManager, KIND_WIFI)) {
            if (info.packageName.equals(wifi)) {
                return wifi;
            }
        }
        return null;
    }

    private boolean matching() {
        return isSignal() && mMatch.isChecked();
    }

    private String previewSignal() {
        return isSignal() ? mSelected : mOtherApplied;
    }

    private String previewWifi() {
        if (!isSignal()) {
            return mSelected;
        }
        return matching() ? matchingWifi(mSelected) : mOtherApplied;
    }

    private Drawable drawable(String pkg, String name) {
        try {
            Resources res = pkg == null ? getResources()
                    : getPackageManager().getResourcesForApplication(pkg);
            int id = res.getIdentifier(name, "drawable", pkg == null ? getPackageName() : pkg);
            return id == 0 ? null : res.getDrawable(id, null);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
            return null;
        }
    }

    private void preview(int barId, int tint, String signal, String wifi, boolean error) {
        View bar = findViewById(barId);
        ImageView signalView = bar.findViewWithTag("signal");
        ImageView wifiView = bar.findViewWithTag("wifi");
        signalView.setImageDrawable(drawable(signal,
                error ? "ic_mobile_3_5_bar_error" : "ic_mobile_5_5_bar"));
        wifiView.setImageDrawable(drawable(wifi, error ? "ic_wifi_2_error" : "ic_wifi_3"));
        signalView.setImageTintList(ColorStateList.valueOf(tint));
        wifiView.setImageTintList(ColorStateList.valueOf(tint));
    }

    private void update() {
        String signal = previewSignal();
        String wifi = previewWifi();
        preview(R.id.icon_style_bar_dark, Color.WHITE, signal, wifi, false);
        preview(R.id.icon_style_bar_light, 0xFF1B1B1F, signal, wifi, true);
        mPreviewLabel.setText(mSelected == null ? getString(R.string.remixer_icon_style_stock)
                : label(this, mSelected));
        for (int i = 0; i < mRows.length; i++) {
            MaterialCardView card = (MaterialCardView) mRows[i];
            boolean selected = Objects.equals(mValues.get(i), mSelected);
            int primary = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
            card.setStrokeColor(selected ? primary : Color.TRANSPARENT);
            card.setCardBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(card,
                    selected ? com.google.android.material.R.attr.colorSecondaryContainer
                            : com.google.android.material.R.attr.colorSurfaceContainerHigh)));
            ((RadioButton) card.findViewById(R.id.icon_style_radio)).setChecked(selected);
            card.setSelected(selected);
        }
        boolean pending = !Objects.equals(mSelected, mApplied)
                || (matching() && !Objects.equals(matchingWifi(mSelected), mOtherApplied));
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
            enable(mKind, mSelected);
            if (matching()) {
                enable(KIND_WIFI, matchingWifi(mSelected));
            }
        } catch (RemoteException | IllegalStateException | SecurityException e) {
            Toast.makeText(this, R.string.remixer_icon_style_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Utils.restartSystemUI();
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
}
