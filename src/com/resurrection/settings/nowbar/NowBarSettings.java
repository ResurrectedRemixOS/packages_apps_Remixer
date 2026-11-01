/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.nowbar;

import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;

import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/** Now Bar options; SystemUI's NowBarRepository and NowBarView read them. */
public class NowBarSettings extends SettingsPreferenceFragment {
    static final String MATERIAL = "now_bar_material";
    static final String COLOR = "now_bar_color";
    static final String OPACITY = "now_bar_opacity";
    static final String OUTLINE = "now_bar_outline";
    static final String SHADOW = "now_bar_shadow";
    static final String MORE = "now_bar_more";
    static final String HEIGHT = "now_bar_height";
    static final String WIDTH = "now_bar_width";
    static final String CORNERS = "now_bar_corners";
    static final String RAISE = "now_bar_raise";
    static final String ART = "now_bar_art";
    static final String BUTTONS = "now_bar_buttons";
    static final String TEXT = "now_bar_text";
    static final String TEXT_SIZE = "now_bar_text_size";
    static final String WAVE = "now_bar_wave";

    static final String[] KEYS = {MATERIAL, COLOR, OPACITY, OUTLINE, SHADOW, MORE, HEIGHT, WIDTH,
            CORNERS, RAISE, ART, BUTTONS, TEXT, TEXT_SIZE, WAVE};
    private static final int[] DEFAULTS = {0, 0, 94, 1, 0, 0, 64, 1, 100, 0, 0, 0, 0, 13, 1};

    /** The looks row, in order: each is the defaults plus these key and value pairs. */
    private static final Object[][] LOOKS = {
        {},
        {MATERIAL, 1, OPACITY, 25, OUTLINE, 0},
        {HEIGHT, 48, WIDTH, 0, BUTTONS, 1, WAVE, 0},
        {HEIGHT, 88, WIDTH, 2, TEXT, 1, ART, 1, CORNERS, 50, TEXT_SIZE, 15},
        {COLOR, 2, OPACITY, 100, OUTLINE, 2, SHADOW, 2},
        {MATERIAL, 1, OPACITY, 35, HEIGHT, 76, CORNERS, 45, ART, 2, TEXT, 1, OUTLINE, 0, WAVE, 0},
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        load();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // The preview stays on screen while the options scroll under it.
        setPinnedHeaderView(R.layout.remixer_now_bar_pinned);
        // Settings themes turn clipChildren off, which lets rows draw over the preview.
        final ViewGroup list = view.findViewById(android.R.id.list_container);
        if (list != null) {
            list.setClipChildren(true);
        }
    }

    private void load() {
        addPreferencesFromResource(R.xml.remixer_now_bar);
        final NowBarChipsPreference looks = findPreference("now_bar_look");
        looks.setOnChipClicked(this::applyLook);
    }

    private void applyLook(int index) {
        for (int i = 0; i < KEYS.length; i++) {
            put(KEYS[i], DEFAULTS[i]);
        }
        final Object[] look = LOOKS[index];
        for (int i = 0; i < look.length; i += 2) {
            put((String) look[i], (Integer) look[i + 1]);
        }
        // Every row below shows what the look just set.
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen != null) {
            screen.removeAll();
        }
        load();
    }

    private void put(String key, int value) {
        Settings.System.putIntForUser(getContentResolver(), key, value, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
