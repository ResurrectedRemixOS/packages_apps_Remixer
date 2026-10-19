/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.widget.LayoutPreference;

/**
 * Liquid Glass: the material SurfaceFlinger draws behind blurred surfaces, how it looks, and
 * which surfaces use it. system_server's BlurController applies the material.
 */
public class LiquidGlassSettings extends SettingsPreferenceFragment {
    static final String MATERIAL = "rr_glass_material";
    static final String BLUR = "rr_glass_blur";
    static final String TINT = "rr_glass_tint";
    static final String REFRACTION = "rr_glass_refraction";
    static final String ACCENT_TINT = "rr_glass_accent_tint";
    static final String TILT = "rr_glass_tilt";

    static final int SOLID = 0;
    static final int FROSTED = 1;
    static final int LIQUID = 2;

    private static final int[] CARDS = {R.id.liquid_glass_solid, R.id.liquid_glass_frosted,
            R.id.liquid_glass_liquid};
    private static final int[] NAMES = {R.string.liquid_glass_solid,
            R.string.liquid_glass_frosted, R.string.liquid_glass_liquid};
    private static final int[] SUMMARIES = {R.string.liquid_glass_solid_summary,
            R.string.liquid_glass_frosted_summary, R.string.liquid_glass_liquid_summary};

    private final View[] mCards = new View[CARDS.length];
    private int mMaterial;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_liquid_glass);
        mMaterial = Settings.System.getIntForUser(getContentResolver(), MATERIAL, FROSTED,
                UserHandle.USER_CURRENT);

        final LayoutPreference header = findPreference("liquid_glass_header");
        for (int i = 0; i < CARDS.length; i++) {
            final int material = i;
            final View card = header.findViewById(CARDS[i]);
            ((GlassPreviewView) card.findViewById(R.id.liquid_glass_swatch)).setSwatch(material);
            ((TextView) card.findViewById(R.id.liquid_glass_label)).setText(NAMES[i]);
            ((TextView) card.findViewById(R.id.liquid_glass_desc)).setText(SUMMARIES[i]);
            card.setOnClickListener(v -> select(material));
            card.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host,
                        AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(RadioButton.class.getName());
                    info.setCheckable(true);
                    info.setChecked(host.isSelected());
                }
            });
            mCards[i] = card;
        }
        update();
    }

    private void select(int material) {
        if (material == mMaterial) {
            return;
        }
        mMaterial = material;
        Settings.System.putIntForUser(getContentResolver(), MATERIAL, material,
                UserHandle.USER_CURRENT);
        update();
    }

    private void update() {
        final Context context = getPrefContext();
        final float density = context.getResources().getDisplayMetrics().density;
        final int primary = context.getColor(com.android.internal.R.color.materialColorPrimary);
        final int selectedFill = context.getColor(
                com.android.internal.R.color.materialColorSecondaryContainer);
        final int fill = context.getColor(
                com.android.internal.R.color.materialColorSurfaceContainerHigh);
        final int ripple = context.getColor(
                com.android.internal.R.color.materialColorOnSurface) & 0x1FFFFFFF;
        for (int i = 0; i < mCards.length; i++) {
            final boolean selected = i == mMaterial;
            final GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(22 * density);
            shape.setColor(selected ? selectedFill : fill);
            shape.setStroke(Math.round(2 * density), selected ? primary : Color.TRANSPARENT);
            mCards[i].setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), shape,
                    null));
            mCards[i].setSelected(selected);
        }
        setEnabled("liquid_glass_look", mMaterial != SOLID);
        setEnabled("liquid_glass_liquid", mMaterial == LIQUID);
        setEnabled("liquid_glass_surfaces", mMaterial != SOLID);
    }

    private void setEnabled(String key, boolean enabled) {
        final Preference preference = findPreference(key);
        if (preference != null) {
            preference.setEnabled(enabled);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
