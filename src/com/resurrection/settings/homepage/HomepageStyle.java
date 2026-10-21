/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.homepage;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings homepage look: the Resurrected Remix homepage, or the original Lineage one. */
public final class HomepageStyle {
    public static final String RR = "rr";
    public static final String ORIGINAL = "original";

    private static final String PREFS = "rr_homepage";
    private static final String KEY = "style";
    private static final String KEY_TILE_HUES = "tile_hues";

    private HomepageStyle() {}

    public static String get(Context context) {
        return prefs(context).getString(KEY, RR);
    }

    public static boolean isOriginal(Context context) {
        return ORIGINAL.equals(get(context));
    }

    public static void set(Context context, String style) {
        prefs(context).edit().putString(KEY, ORIGINAL.equals(style) ? ORIGINAL : RR).apply();
    }

    /** Category tiles take hues around the accent; false gives them all the accent. */
    public static boolean tileHues(Context context) {
        return prefs(context).getBoolean(KEY_TILE_HUES, true);
    }

    public static void setTileHues(Context context, boolean hues) {
        prefs(context).edit().putBoolean(KEY_TILE_HUES, hues).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
