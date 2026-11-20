/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.volume;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import com.resurrection.settings.charging.ChargingStageView;
import com.resurrection.settings.charging.ChargingUi;

import lineageos.providers.LineageSettings;

public class VolumePanelActivity extends CollapsingToolbarBaseActivity {
    private static final String STYLE = "rr_volume_style";
    private static final String RINGER = "rr_volume_ringer";
    private static final String TICKS = "rr_volume_tick";
    private static final String PERCENT = "rr_volume_percent";
    private static final String TIMEOUT = "volume_dialog_dismiss_timeout";
    private static final int LEFT_CHIP = 0;
    private static final int[] TIMEOUT_IDS = {
        R.id.volume_timeout_0, R.id.volume_timeout_1, R.id.volume_timeout_2, R.id.volume_timeout_3,
    };

    private final TextView[] mChips = new TextView[4];
    private final VolumeLook[] mLooks = new VolumeLook[VolumeLook.COUNT];
    private ChargingUi mUi;
    private VolumePanelPreview mPreview;
    private TextView mName;
    private TextView mDescription;
    private MaterialButton mApply;
    private RecyclerView.Adapter<Holder> mAdapter;
    private String[] mNames;
    private String[] mDescriptions;
    private int[] mTimeouts;
    private int mLook;
    private int mTimeout;
    private boolean mLeft;
    private boolean mRinger;
    private boolean mTicks;
    private boolean mPercent;
    private String mApplied;

    public static String currentName(Context context) {
        String[] names = context.getResources().getStringArray(R.array.volume_style_entries);
        return names[style(context.getContentResolver(), names.length)];
    }

    private static int style(ContentResolver resolver, int count) {
        int style = Settings.System.getIntForUser(resolver, STYLE, 0, UserHandle.USER_CURRENT);
        return style >= 0 && style < count ? style : 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_volume_panel);
        findViewById(R.id.volume_scroll).setNestedScrollingEnabled(false);
        setTitle(R.string.volume_page_title);
        mUi = new ChargingUi(this);
        mNames = getResources().getStringArray(R.array.volume_style_entries);
        mDescriptions = getResources().getStringArray(R.array.volume_style_descriptions);
        String[] values = getResources().getStringArray(R.array.volume_timeout_values);
        mTimeouts = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            mTimeouts[i] = Integer.parseInt(values[i]);
        }
        for (int i = 0; i < mLooks.length; i++) {
            mLooks[i] = VolumeLook.of(this, i);
        }
        read();
        mApplied = state();

        View stage = findViewById(R.id.volume_stage);
        stage.setBackground(mUi.stage());
        stage.setClipToOutline(true);
        ChargingStageView phone = findViewById(R.id.volume_phone);
        mPreview = new VolumePanelPreview(this);
        phone.setEffect(mPreview);
        stepButton(R.id.volume_prev, -1);
        stepButton(R.id.volume_next, 1);

        LinearLayout chips = findViewById(R.id.volume_chips);
        int[] labels = {
            R.string.volume_chip_left, R.string.volume_chip_ringer, R.string.volume_chip_ticks,
            R.string.volume_chip_percent,
        };
        for (int i = 0; i < labels.length; i++) {
            int which = i;
            TextView chip = mUi.chip(this, getString(labels[i]), true);
            chip.setOnClickListener(v -> toggle(which));
            chips.addView(chip);
            mChips[i] = chip;
        }

        mName = findViewById(R.id.volume_name);
        mDescription = findViewById(R.id.volume_description);
        MaterialButton tryIt = findViewById(R.id.volume_try);
        tryIt.setStrokeColor(ColorStateList.valueOf(mUi.accent));
        tryIt.setTextColor(mUi.accent);
        tryIt.setOnClickListener(v -> tryIt());

        RecyclerView grid = findViewById(R.id.volume_grid);
        grid.setLayoutManager(new GridLayoutManager(this, 5));
        mAdapter = new Adapter();
        grid.setAdapter(mAdapter);

        String[] timeoutLabels = getResources().getStringArray(R.array.volume_timeout_entries);
        MaterialButtonToggleGroup timeouts = findViewById(R.id.volume_timeouts);
        for (int i = 0; i < TIMEOUT_IDS.length; i++) {
            MaterialButton button = findViewById(TIMEOUT_IDS[i]);
            button.setText(timeoutLabels[i]);
            if (mTimeouts[i] == mTimeout) {
                timeouts.check(TIMEOUT_IDS[i]);
            }
        }
        timeouts.addOnButtonCheckedListener((group, id, checked) -> {
            for (int i = 0; checked && i < TIMEOUT_IDS.length; i++) {
                if (TIMEOUT_IDS[i] == id) {
                    mTimeout = mTimeouts[i];
                    update();
                }
            }
        });

        mApply = findViewById(R.id.volume_apply);
        mApply.setOnClickListener(v -> apply());
        update();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPreview.setRunning(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.setRunning(false);
    }

    private void read() {
        ContentResolver resolver = getContentResolver();
        mLook = style(resolver, VolumeLook.COUNT);
        mLeft = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.VOLUME_PANEL_ON_LEFT, 0, UserHandle.USER_CURRENT) != 0;
        mRinger = Settings.System.getIntForUser(resolver, RINGER, 1, UserHandle.USER_CURRENT) == 1;
        mTicks = Settings.System.getIntForUser(resolver, TICKS, 1, UserHandle.USER_CURRENT) == 1;
        mPercent = Settings.System.getIntForUser(resolver, PERCENT, 1, UserHandle.USER_CURRENT) == 1;
        int timeout = Settings.Secure.getIntForUser(resolver, TIMEOUT, 3000, UserHandle.USER_CURRENT);
        mTimeout = mTimeouts[0];
        for (int value : mTimeouts) {
            if (Math.abs(value - timeout) < Math.abs(mTimeout - timeout)) {
                mTimeout = value;
            }
        }
    }

    private String state() {
        return mLook + "|" + mLeft + "|" + mRinger + "|" + mTicks + "|" + mPercent + "|" + mTimeout;
    }

    private void stepButton(int id, int dir) {
        ImageButton button = findViewById(id);
        button.setBackground(mUi.shape(0x1FFFFFFF, 0, 18, 0));
        button.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        button.setOnClickListener(v -> {
            mLook = Math.floorMod(mLook + dir, VolumeLook.COUNT);
            update();
        });
    }

    private void toggle(int chip) {
        if (chip == LEFT_CHIP) {
            mLeft = !mLeft;
        } else if (chip == 1) {
            mRinger = !mRinger;
        } else if (chip == 2) {
            mTicks = !mTicks;
        } else {
            mPercent = !mPercent;
        }
        update();
    }

    private void update() {
        boolean classic = mLook == VolumeLook.CLASSIC;
        mPreview.set(mLooks[mLook], mLeft && !classic, mRinger, mTicks, mPercent);
        mName.setText(mNames[mLook]);
        mDescription.setText(mDescriptions[mLook]);
        boolean[] on = {mLeft, mRinger, mTicks, mPercent};
        for (int i = 0; i < mChips.length; i++) {
            mUi.setStageChip(mChips[i], on[i]);
        }
        mChips[LEFT_CHIP].setEnabled(!classic);
        mChips[LEFT_CHIP].setAlpha(classic ? 0.45f : 1f);
        mAdapter.notifyDataSetChanged();
        boolean pending = !state().equals(mApplied);
        mApply.setEnabled(pending);
        mApply.setText(pending ? R.string.charging_apply : R.string.charging_applied);
    }

    private void apply() {
        ContentResolver resolver = getContentResolver();
        Settings.System.putIntForUser(resolver, STYLE, mLook, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver, LineageSettings.Secure.VOLUME_PANEL_ON_LEFT,
                mLeft ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, RINGER, mRinger ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, TICKS, mTicks ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, PERCENT, mPercent ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, TIMEOUT, mTimeout, UserHandle.USER_CURRENT);
        mApplied = state();
        update();
    }

    private void tryIt() {
        apply();
        AudioManager audio = getSystemService(AudioManager.class);
        if (audio != null) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME,
                    AudioManager.FLAG_SHOW_UI);
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_volume_tile, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            boolean selected = position == mLook;
            MaterialCardView card = (MaterialCardView) holder.itemView;
            holder.glyph.setImageDrawable(new VolumeGlyph(mLooks[position]));
            holder.name.setText(mNames[position]);
            card.setStrokeColor(selected ? mUi.accent : Color.TRANSPARENT);
            card.setCardBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(card,
                    selected ? com.google.android.material.R.attr.colorSecondaryContainer
                            : com.google.android.material.R.attr.colorSurfaceContainerHigh)));
            card.setSelected(selected);
            card.setContentDescription(mNames[position]);
            card.setOnClickListener(v -> {
                mLook = position;
                update();
            });
        }

        @Override
        public int getItemCount() {
            return VolumeLook.COUNT;
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView glyph;
        final TextView name;

        Holder(View view) {
            super(view);
            glyph = view.findViewById(R.id.volume_tile_glyph);
            name = view.findViewById(R.id.volume_tile_name);
        }
    }
}
