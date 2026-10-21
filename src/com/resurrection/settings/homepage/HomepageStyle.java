/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.homepage;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings homepage look. "rr" stays v2 so an existing choice does not change. */
public final class HomepageStyle {
    public static final String V1 = "rr_v1";
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

    public static boolean isV1(Context context) {
        return V1.equals(get(context));
    }

    /** Category-tile homepage. Anything unrecognized stays here. */
    public static boolean isV2(Context context) {
        return !isOriginal(context) && !isV1(context);
    }

    public static void set(Context context, String style) {
        String stored = V1.equals(style) || ORIGINAL.equals(style) ? style : RR;
        prefs(context).edit().putString(KEY, stored).apply();
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
