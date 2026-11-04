/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.resurrection.settings.homepage.HomepageStyle;

/** Remixer › Theme › Settings style: the home page's look, previewed before it is picked. */
public class SettingsStyleActivity extends CollapsingToolbarBaseActivity {
    // Indexed by the SettingsStylePreviewView style.
    private static final int[] NAMES = {
        R.string.remixer_settings_style_rr_v2, R.string.remixer_settings_style_rr_v2_hues,
        R.string.remixer_settings_style_rr_v1, R.string.remixer_settings_style_original,
    };
    private static final int[] DESCRIPTIONS = {
        R.string.remixer_settings_style_rr_v2_summary,
        R.string.remixer_settings_style_rr_v2_hues_summary,
        R.string.remixer_settings_style_rr_v1_summary,
        R.string.remixer_settings_style_original_summary,
    };

    private View[] mRows;
    private SettingsStylePreviewView mPreview;
    private TextView mPreviewLabel;
    private int mSelected;

    /** The style on now, as a preview value. */
    public static int current(Context context) {
        if (HomepageStyle.isOriginal(context)) return SettingsStylePreviewView.ORIGINAL;
        if (HomepageStyle.isV1(context)) return SettingsStylePreviewView.CLASSIC;
        return HomepageStyle.tileHues(context)
                ? SettingsStylePreviewView.RR_HUES : SettingsStylePreviewView.RR;
    }

    /** Name of the current style, for the Remixer entry summary. */
    public static String currentName(Context context) {
        return context.getString(NAMES[current(context)]);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_settings_style);
        setTitle(R.string.remixer_settings_style);
        mPreview = findViewById(R.id.settings_style_preview);
        mPreviewLabel = findViewById(R.id.settings_style_preview_label);
        mSelected = current(this);
        mRows = new View[NAMES.length];
        LinearLayout list = findViewById(R.id.settings_style_list);
        LayoutInflater inflater = LayoutInflater.from(list.getContext());
        for (int i = 0; i < NAMES.length; i++) {
            final int value = i;
            View row = inflater.inflate(R.layout.remixer_settings_style_item, list, false);
            SettingsStylePreviewView thumb = row.findViewById(R.id.settings_style_thumb);
            thumb.setMini(true);
            thumb.setStyle(value);
            ((TextView) row.findViewById(R.id.settings_style_name)).setText(NAMES[i]);
            ((TextView) row.findViewById(R.id.settings_style_desc)).setText(DESCRIPTIONS[i]);
            row.setOnClickListener(v -> select(value));
            list.addView(row);
            mRows[i] = row;
        }
        update();
    }

    private void select(int value) {
        if (value == mSelected) return;
        switch (value) {
            case SettingsStylePreviewView.ORIGINAL:
                HomepageStyle.set(this, HomepageStyle.ORIGINAL);
                break;
            case SettingsStylePreviewView.CLASSIC:
                HomepageStyle.set(this, HomepageStyle.V1);
                break;
            default:
                HomepageStyle.set(this, HomepageStyle.RR);
                HomepageStyle.setTileHues(this, value == SettingsStylePreviewView.RR_HUES);
        }
        mSelected = value;
        update();
    }

    private void update() {
        mPreview.setStyle(mSelected);
        mPreviewLabel.setText(getString(R.string.remixer_qs_style_preview,
                getString(NAMES[mSelected])));
        for (int i = 0; i < mRows.length; i++) {
            MaterialCardView card = (MaterialCardView) mRows[i];
            boolean selected = i == mSelected;
            int primary = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary);
            card.setStrokeColor(selected ? primary : Color.TRANSPARENT);
            card.setCardBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(card,
                    selected ? com.google.android.material.R.attr.colorSecondaryContainer
                            : com.google.android.material.R.attr.colorSurfaceContainerHigh)));
            ((RadioButton) card.findViewById(R.id.settings_style_radio)).setChecked(selected);
            card.setSelected(selected);
        }
    }
}
