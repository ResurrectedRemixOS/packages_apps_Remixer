/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/** Quick settings look, read by SystemUI from Settings.System {@link #SETTING}. */
public class QsStyleActivity extends CollapsingToolbarBaseActivity {
    public static final String SETTING = "rr_qs_style";

    // Indexed by the SystemUI QsStyle value.
    private static final int[] NAMES = {
        R.string.remixer_qs_style_stock, R.string.remixer_qs_style_remix,
        R.string.remixer_qs_style_compact, R.string.remixer_qs_style_glass,
        R.string.remixer_qs_style_circles, R.string.remixer_qs_style_classic,
        R.string.remixer_qs_style_cards, R.string.remixer_qs_style_mono,
    };
    private static final int[] DESCRIPTIONS = {
        R.string.remixer_qs_style_stock_summary, R.string.remixer_qs_style_remix_summary,
        R.string.remixer_qs_style_compact_summary, R.string.remixer_qs_style_glass_summary,
        R.string.remixer_qs_style_circles_summary, R.string.remixer_qs_style_classic_summary,
        R.string.remixer_qs_style_cards_summary, R.string.remixer_qs_style_mono_summary,
    };

    private final View[] mRows = new View[NAMES.length];
    private QsStylePreviewView mPreview;
    private TextView mPreviewLabel;
    private int mSelected;

    /** Name of the current style, for the Remixer entry summary. */
    public static String currentName(Context context) {
        return context.getString(NAMES[current(context)]);
    }

    private static int current(Context context) {
        int value = Settings.System.getIntForUser(context.getContentResolver(), SETTING, 0,
                UserHandle.USER_CURRENT);
        return value >= 0 && value < NAMES.length ? value : 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_qs_style);
        setTitle(R.string.remixer_qs_style);

        mPreview = findViewById(R.id.qs_style_preview);
        mPreviewLabel = findViewById(R.id.qs_style_preview_label);
        mSelected = current(this);

        LinearLayout list = findViewById(R.id.qs_style_list);
        LayoutInflater inflater = LayoutInflater.from(list.getContext());
        for (int i = 0; i < NAMES.length; i++) {
            final int style = i;
            View row = inflater.inflate(R.layout.remixer_qs_style_item, list, false);
            QsStylePreviewView thumb = row.findViewById(R.id.qs_style_thumb);
            thumb.setMini(true);
            thumb.setStyle(style);
            ((TextView) row.findViewById(R.id.qs_style_name)).setText(NAMES[i]);
            ((TextView) row.findViewById(R.id.qs_style_desc)).setText(DESCRIPTIONS[i]);
            row.setOnClickListener(v -> select(style));
            list.addView(row);
            mRows[i] = row;
        }
        update();
    }

    private void select(int style) {
        if (style == mSelected) {
            return;
        }
        Settings.System.putIntForUser(getContentResolver(), SETTING, style,
                UserHandle.USER_CURRENT);
        mSelected = style;
        update();
    }

    private void update() {
        mPreview.setStyle(mSelected);
        mPreviewLabel.setText(getString(R.string.remixer_qs_style_preview,
                getString(NAMES[mSelected])));
        for (int i = 0; i < mRows.length; i++) {
            MaterialCardView card = (MaterialCardView) mRows[i];
            boolean selected = i == mSelected;
            int primary = MaterialColors.getColor(card,
                    androidx.appcompat.R.attr.colorPrimary);
            card.setStrokeColor(selected ? primary : Color.TRANSPARENT);
            card.setCardBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(card,
                    selected ? com.google.android.material.R.attr.colorSecondaryContainer
                            : com.google.android.material.R.attr.colorSurfaceContainerHigh)));
            ((RadioButton) card.findViewById(R.id.qs_style_radio)).setChecked(selected);
            card.setSelected(selected);
        }
    }
}
