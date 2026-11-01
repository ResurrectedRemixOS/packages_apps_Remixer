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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.stream.IntStream;

/**
 * Quick settings look, read by SystemUI from Settings.System {@link #SETTING}, or with
 * {@link #lockMediaIntent} the lockscreen media card look from {@link #LOCK_MEDIA_SETTING}.
 */
public class QsStyleActivity extends CollapsingToolbarBaseActivity {
    public static final String SETTING = "rr_qs_style";
    public static final String LOCK_MEDIA_SETTING = "rr_lockscreen_media_style";
    private static final String QS_TWEAKS_SETTING = "rr_qs_tweaks";
    private static final String QS_TWEAK_MEDIA_LOOK = "mediaFrom";
    private static final String EXTRA_LOCK_MEDIA = "lock_media";
    // Media value that follows the QS style.
    private static final int SAME_AS_QS = -1;
    // Media value for no player on the lockscreen.
    private static final int OFF = -2;

    // Indexed by the SystemUI QsStyle value.
    static final int[] NAMES = {
        R.string.remixer_qs_style_stock, R.string.remixer_qs_style_remix,
        R.string.remixer_qs_style_compact, R.string.remixer_qs_style_glass,
        R.string.remixer_qs_style_circles, R.string.remixer_qs_style_classic,
        R.string.remixer_qs_style_cards, 0,
        R.string.remixer_qs_style_neon, R.string.remixer_qs_style_bento,
        R.string.remixer_qs_style_shapes, R.string.remixer_qs_style_blueprint,
        R.string.remixer_qs_style_duo, R.string.remixer_qs_style_soft,
    };
    private static final int[] DESCRIPTIONS = {
        R.string.remixer_qs_style_stock_summary, R.string.remixer_qs_style_remix_summary,
        R.string.remixer_qs_style_compact_summary, R.string.remixer_qs_style_glass_summary,
        R.string.remixer_qs_style_circles_summary, R.string.remixer_qs_style_classic_summary,
        R.string.remixer_qs_style_cards_summary, 0,
        R.string.remixer_qs_style_neon_summary, R.string.remixer_qs_style_bento_summary,
        R.string.remixer_qs_style_shapes_summary, R.string.remixer_qs_style_blueprint_summary,
        R.string.remixer_qs_style_duo_summary, R.string.remixer_qs_style_soft_summary,
    };
    private static final int[] LOCK_DESCRIPTIONS = {
        R.string.remixer_lock_media_stock_summary, R.string.remixer_lock_media_remix_summary,
        R.string.remixer_lock_media_compact_summary, R.string.remixer_lock_media_glass_summary,
        R.string.remixer_lock_media_circles_summary, R.string.remixer_lock_media_classic_summary,
        R.string.remixer_lock_media_cards_summary, 0,
        R.string.remixer_lock_media_neon_summary, R.string.remixer_lock_media_bento_summary,
        R.string.remixer_lock_media_shapes_summary, R.string.remixer_lock_media_blueprint_summary,
        R.string.remixer_lock_media_duo_summary, R.string.remixer_lock_media_soft_summary,
    };

    private View[] mRows;
    private int[] mValues;
    private boolean mLockMedia;
    private QsStylePreviewView mPreview;
    private TextView mPreviewLabel;
    private int mSelected;

    static boolean isStyle(int value) {
        return value >= 0 && value < NAMES.length && NAMES[value] != 0;
    }

    static int[] styles() {
        return IntStream.range(0, NAMES.length).filter(QsStyleActivity::isStyle).toArray();
    }

    public static Intent lockMediaIntent(Context context) {
        return new Intent(context, QsStyleActivity.class).putExtra(EXTRA_LOCK_MEDIA, true);
    }

    /** Name of the current QS style, for the Remixer entry summary. */
    public static String currentName(Context context) {
        return context.getString(NAMES[qsStyle(context)]);
    }

    /** Name of the current lockscreen media style, for the Remixer entry summary. */
    public static String currentLockMediaName(Context context) {
        int value = lockMedia(context);
        if (value == OFF) {
            return context.getString(R.string.remixer_lock_media_off);
        }
        return value == SAME_AS_QS ? context.getString(R.string.remixer_lock_media_same)
                : context.getString(NAMES[value]);
    }

    private static int qsStyle(Context context) {
        int value = Settings.System.getIntForUser(context.getContentResolver(), SETTING, 0,
                UserHandle.USER_CURRENT);
        return isStyle(value) ? value : 0;
    }

    private static int lockMedia(Context context) {
        if (Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN, 1, UserHandle.USER_CURRENT) == 0) {
            return OFF;
        }
        int value = Settings.System.getIntForUser(context.getContentResolver(),
                LOCK_MEDIA_SETTING, SAME_AS_QS, UserHandle.USER_CURRENT);
        return isStyle(value) ? value : SAME_AS_QS;
    }

    /** The look of the QS Media tile, picked in QS edit mode for each QS style. */
    private static int qsMediaLook(Context context) {
        int style = qsStyle(context);
        String tweaks = Settings.System.getStringForUser(context.getContentResolver(),
                QS_TWEAKS_SETTING, UserHandle.USER_CURRENT);
        if (tweaks == null) {
            return style;
        }
        try {
            JSONObject current = new JSONObject(tweaks).optJSONObject(String.valueOf(style));
            int value = current == null ? -1 : current.optInt(QS_TWEAK_MEDIA_LOOK, -1);
            return isStyle(value) ? value : style;
        } catch (JSONException e) {
            return style;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_qs_style);
        mLockMedia = getIntent().getBooleanExtra(EXTRA_LOCK_MEDIA, false);
        setTitle(mLockMedia ? R.string.remixer_lock_media_style : R.string.remixer_qs_style);

        mPreview = findViewById(R.id.qs_style_preview);
        mPreview.setLockscreen(mLockMedia);
        mPreviewLabel = findViewById(R.id.qs_style_preview_label);
        ((TextView) findViewById(R.id.qs_style_note)).setText(mLockMedia
                ? R.string.remixer_lock_media_note : R.string.remixer_qs_style_note);
        mSelected = mLockMedia ? lockMedia(this) : qsStyle(this);

        // The lockscreen list starts with following QS and off.
        final int extras = mLockMedia ? 2 : 0;
        final int[] styles = styles();
        mValues = new int[styles.length + extras];
        if (mLockMedia) {
            mValues[0] = SAME_AS_QS;
            mValues[1] = OFF;
        }
        System.arraycopy(styles, 0, mValues, extras, styles.length);
        mRows = new View[mValues.length];
        final String qsName = getString(NAMES[qsMediaLook(this)]);
        LinearLayout list = findViewById(R.id.qs_style_list);
        LayoutInflater inflater = LayoutInflater.from(list.getContext());
        for (int i = 0; i < mValues.length; i++) {
            final int value = mValues[i];
            View row = inflater.inflate(R.layout.remixer_qs_style_item, list, false);
            QsStylePreviewView thumb = row.findViewById(R.id.qs_style_thumb);
            thumb.setMini(true);
            thumb.setLockscreen(mLockMedia);
            thumb.setCardHidden(value == OFF);
            thumb.setStyle(look(value));
            TextView name = row.findViewById(R.id.qs_style_name);
            TextView desc = row.findViewById(R.id.qs_style_desc);
            if (value == SAME_AS_QS) {
                name.setText(R.string.remixer_lock_media_same);
                desc.setText(getString(R.string.remixer_lock_media_same_summary, qsName));
            } else if (value == OFF) {
                name.setText(R.string.remixer_lock_media_off);
                desc.setText(R.string.remixer_lock_media_off_summary);
            } else {
                name.setText(NAMES[value]);
                desc.setText(mLockMedia ? LOCK_DESCRIPTIONS[value] : DESCRIPTIONS[value]);
            }
            row.setOnClickListener(v -> select(value));
            list.addView(row);
            mRows[i] = row;
        }
        update();
    }

    /** The style a value draws as; following QS draws as the QS Media tile. */
    private int look(int value) {
        return value >= 0 ? value : qsMediaLook(this);
    }

    private void select(int value) {
        if (value == mSelected) {
            return;
        }
        if (mLockMedia) {
            Settings.Secure.putIntForUser(getContentResolver(),
                    Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN, value == OFF ? 0 : 1,
                    UserHandle.USER_CURRENT);
        }
        if (value != OFF) {
            Settings.System.putIntForUser(getContentResolver(),
                    mLockMedia ? LOCK_MEDIA_SETTING : SETTING, value, UserHandle.USER_CURRENT);
        }
        mSelected = value;
        update();
    }

    private void update() {
        mPreview.setCardHidden(mSelected == OFF);
        mPreview.setStyle(look(mSelected));
        mPreviewLabel.setText(getString(R.string.remixer_qs_style_preview,
                getString(mSelected == OFF ? R.string.remixer_lock_media_off
                        : NAMES[look(mSelected)])));
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
