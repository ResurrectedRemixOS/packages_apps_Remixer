/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings;

import android.content.Context;
import android.content.Intent;
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

/**
 * Quick settings look, read by SystemUI from Settings.System {@link #SETTING}, or with
 * {@link #lockMediaIntent} the lockscreen media card look from {@link #LOCK_MEDIA_SETTING},
 * or with {@link #qsMediaIntent} the QS media card look from {@link #QS_MEDIA_SETTING}.
 */
public class QsStyleActivity extends CollapsingToolbarBaseActivity {
    public static final String SETTING = "rr_qs_style";
    public static final String LOCK_MEDIA_SETTING = "rr_lockscreen_media_style";
    public static final String QS_MEDIA_SETTING = "rr_qs_media_style";
    private static final String EXTRA_LOCK_MEDIA = "lock_media";
    private static final String EXTRA_QS_MEDIA = "qs_media";
    // Media value that follows the QS style.
    private static final int SAME_AS_QS = -1;

    // Indexed by the SystemUI QsStyle value.
    static final int[] NAMES = {
        R.string.remixer_qs_style_stock, R.string.remixer_qs_style_remix,
        R.string.remixer_qs_style_compact, R.string.remixer_qs_style_glass,
        R.string.remixer_qs_style_circles, R.string.remixer_qs_style_classic,
        R.string.remixer_qs_style_cards, R.string.remixer_qs_style_mono,
        R.string.remixer_qs_style_neon, R.string.remixer_qs_style_bento,
        R.string.remixer_qs_style_shapes, R.string.remixer_qs_style_blueprint,
        R.string.remixer_qs_style_duo, R.string.remixer_qs_style_soft,
    };
    private static final int[] DESCRIPTIONS = {
        R.string.remixer_qs_style_stock_summary, R.string.remixer_qs_style_remix_summary,
        R.string.remixer_qs_style_compact_summary, R.string.remixer_qs_style_glass_summary,
        R.string.remixer_qs_style_circles_summary, R.string.remixer_qs_style_classic_summary,
        R.string.remixer_qs_style_cards_summary, R.string.remixer_qs_style_mono_summary,
        R.string.remixer_qs_style_neon_summary, R.string.remixer_qs_style_bento_summary,
        R.string.remixer_qs_style_shapes_summary, R.string.remixer_qs_style_blueprint_summary,
        R.string.remixer_qs_style_duo_summary, R.string.remixer_qs_style_soft_summary,
    };
    private static final int[] LOCK_DESCRIPTIONS = {
        R.string.remixer_lock_media_stock_summary, R.string.remixer_lock_media_remix_summary,
        R.string.remixer_lock_media_compact_summary, R.string.remixer_lock_media_glass_summary,
        R.string.remixer_lock_media_circles_summary, R.string.remixer_lock_media_classic_summary,
        R.string.remixer_lock_media_cards_summary, R.string.remixer_lock_media_mono_summary,
        R.string.remixer_lock_media_neon_summary, R.string.remixer_lock_media_bento_summary,
        R.string.remixer_lock_media_shapes_summary, R.string.remixer_lock_media_blueprint_summary,
        R.string.remixer_lock_media_duo_summary, R.string.remixer_lock_media_soft_summary,
    };

    private View[] mRows;
    private int[] mValues;
    private boolean mLockMedia;
    private boolean mQsMedia;
    private QsStylePreviewView mPreview;
    private TextView mPreviewLabel;
    private int mSelected;

    public static Intent lockMediaIntent(Context context) {
        return new Intent(context, QsStyleActivity.class).putExtra(EXTRA_LOCK_MEDIA, true);
    }

    public static Intent qsMediaIntent(Context context) {
        return new Intent(context, QsStyleActivity.class).putExtra(EXTRA_QS_MEDIA, true);
    }

    /** Name of the current QS style, for the Remixer entry summary. */
    public static String currentName(Context context) {
        return context.getString(NAMES[qsStyle(context)]);
    }

    /** Name of the current lockscreen media style, for the Remixer entry summary. */
    public static String currentLockMediaName(Context context) {
        int value = mediaStyle(context, LOCK_MEDIA_SETTING);
        return value == SAME_AS_QS ? context.getString(R.string.remixer_lock_media_same)
                : context.getString(NAMES[value]);
    }

    public static String currentQsMediaName(Context context) {
        int value = mediaStyle(context, QS_MEDIA_SETTING);
        return value == SAME_AS_QS ? context.getString(R.string.remixer_lock_media_same)
                : context.getString(NAMES[value]);
    }

    private static int qsStyle(Context context) {
        int value = Settings.System.getIntForUser(context.getContentResolver(), SETTING, 0,
                UserHandle.USER_CURRENT);
        return value >= 0 && value < NAMES.length ? value : 0;
    }

    private static int mediaStyle(Context context, String setting) {
        int value = Settings.System.getIntForUser(context.getContentResolver(),
                setting, SAME_AS_QS, UserHandle.USER_CURRENT);
        return value >= 0 && value < NAMES.length ? value : SAME_AS_QS;
    }

    private static int qsMediaLook(Context context) {
        int value = mediaStyle(context, QS_MEDIA_SETTING);
        return value == SAME_AS_QS ? qsStyle(context) : value;
    }

    private boolean media() {
        return mLockMedia || mQsMedia;
    }

    private String setting() {
        return mLockMedia ? LOCK_MEDIA_SETTING : mQsMedia ? QS_MEDIA_SETTING : SETTING;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_qs_style);
        mLockMedia = getIntent().getBooleanExtra(EXTRA_LOCK_MEDIA, false);
        mQsMedia = !mLockMedia && getIntent().getBooleanExtra(EXTRA_QS_MEDIA, false);
        setTitle(media() ? R.string.remixer_lock_media_style : R.string.remixer_qs_style);

        mPreview = findViewById(R.id.qs_style_preview);
        mPreview.setLockscreen(media());
        mPreview.setPanel(mQsMedia);
        mPreviewLabel = findViewById(R.id.qs_style_preview_label);
        ((TextView) findViewById(R.id.qs_style_note)).setText(mLockMedia
                ? R.string.remixer_lock_media_note
                : mQsMedia ? R.string.remixer_qs_media_note : R.string.remixer_qs_style_note);
        mSelected = media() ? mediaStyle(this, setting()) : qsStyle(this);

        mValues = new int[NAMES.length + (media() ? 1 : 0)];
        for (int i = 0; i < mValues.length; i++) {
            mValues[i] = media() ? i - 1 : i;
        }
        mRows = new View[mValues.length];
        final String qsName = getString(NAMES[mLockMedia ? qsMediaLook(this) : qsStyle(this)]);
        LinearLayout list = findViewById(R.id.qs_style_list);
        LayoutInflater inflater = LayoutInflater.from(list.getContext());
        for (int i = 0; i < mValues.length; i++) {
            final int value = mValues[i];
            View row = inflater.inflate(R.layout.remixer_qs_style_item, list, false);
            QsStylePreviewView thumb = row.findViewById(R.id.qs_style_thumb);
            thumb.setMini(true);
            thumb.setLockscreen(media());
            thumb.setPanel(mQsMedia);
            thumb.setStyle(look(value));
            TextView name = row.findViewById(R.id.qs_style_name);
            TextView desc = row.findViewById(R.id.qs_style_desc);
            if (value == SAME_AS_QS) {
                name.setText(R.string.remixer_lock_media_same);
                desc.setText(getString(R.string.remixer_lock_media_same_summary, qsName));
            } else {
                name.setText(NAMES[value]);
                desc.setText(media() ? LOCK_DESCRIPTIONS[value] : DESCRIPTIONS[value]);
            }
            row.setOnClickListener(v -> select(value));
            list.addView(row);
            mRows[i] = row;
        }
        update();
    }

    /** The style a value draws as; following QS draws as the QS style. */
    private int look(int value) {
        if (value != SAME_AS_QS) {
            return value;
        }
        return mLockMedia ? qsMediaLook(this) : qsStyle(this);
    }

    private void select(int value) {
        if (value == mSelected) {
            return;
        }
        Settings.System.putIntForUser(getContentResolver(), setting(), value,
                UserHandle.USER_CURRENT);
        mSelected = value;
        update();
    }

    private void update() {
        mPreview.setStyle(look(mSelected));
        mPreviewLabel.setText(getString(R.string.remixer_qs_style_preview,
                getString(NAMES[look(mSelected)])));
        for (int i = 0; i < mRows.length; i++) {
            MaterialCardView card = (MaterialCardView) mRows[i];
            boolean selected = mValues[i] == mSelected;
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
