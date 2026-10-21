/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.homepage;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

/** RR homepage colors, from the accent palette so they follow accent, shade and saturation. */
final class HomepagePalette {
    static final int NETWORK = 0;
    static final int APPS = 18;
    static final int DISPLAY = -18;
    static final int SECURITY = 36;
    static final int ACCOUNTS = -36;
    static final int DEVICE = 54;
    static final int ACCESSIBILITY = -54;
    static final int SYSTEM = 72;

    final boolean night;
    final boolean hues;

    final int text;
    final int subtext;

    final int heroTop;
    final int heroBottom;
    final int heroGlow;
    final int edge;
    final int neon;
    final int track;
    final int badge;
    final int watermark;
    final int on;
    final int onInk;
    final int offFill;
    final int offStroke;
    final int offInk;

    final int barBottom = 0xFF0C0C0C;
    final int barText = Color.WHITE;
    final int barSubtext;
    final int pill = Color.WHITE;
    final int pillInk = 0xFF141012;

    private final Context mContext;

    HomepagePalette(Context context) {
        mContext = context;
        night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        hues = HomepageStyle.tileHues(context);
        barSubtext = color(android.R.color.system_neutral2_200);
        if (night) {
            text = color(android.R.color.system_neutral1_50);
            subtext = color(android.R.color.system_neutral2_200);
            heroTop = ColorUtils.blendARGB(color(android.R.color.system_accent1_900),
                    color(android.R.color.system_accent1_800), 0.2f);
            heroBottom = ColorUtils.blendARGB(color(android.R.color.system_accent1_900),
                    Color.BLACK, 0.6f);
            heroGlow = alpha(color(android.R.color.system_accent1_400), 0x6B);
            edge = alpha(color(android.R.color.system_accent1_400), 0x40);
            neon = color(android.R.color.system_accent1_400);
            track = alpha(color(android.R.color.system_accent1_300), 0x33);
            badge = 0x61000000;
            watermark = alpha(Color.WHITE, 0x12);
            on = neon;
            onInk = color(android.R.color.system_accent1_900);
            offFill = 0x14FFFFFF;
            offStroke = 0x1FFFFFFF;
            offInk = color(android.R.color.system_neutral1_100);
        } else {
            text = color(android.R.color.system_neutral1_900);
            subtext = color(android.R.color.system_neutral2_700);
            heroTop = color(android.R.color.system_accent1_100);
            heroBottom = ColorUtils.blendARGB(color(android.R.color.system_accent1_50),
                    Color.WHITE, 0.5f);
            heroGlow = alpha(color(android.R.color.system_accent1_300), 0x59);
            edge = alpha(color(android.R.color.system_accent1_600), 0x29);
            neon = color(android.R.color.system_accent1_600);
            track = alpha(color(android.R.color.system_accent1_600), 0x29);
            badge = 0x99FFFFFF;
            watermark = alpha(Color.BLACK, 0x0D);
            on = neon;
            onInk = Color.WHITE;
            offFill = 0x0F000000;
            offStroke = 0x1A000000;
            offInk = color(android.R.color.system_neutral1_800);
        }
    }

    Tile tile(int offset) {
        return new Tile(hues ? offset : 0);
    }

    Bar bar(int offset) {
        return new Bar(hues ? offset : 0);
    }

    private int color(int res) {
        return mContext.getColor(res);
    }

    private int accent(int res, int offset) {
        return turn(color(res), offset);
    }

    static int alpha(int color, int alpha) {
        return ColorUtils.setAlphaComponent(color, alpha);
    }

    static int turn(int color, float degrees) {
        if (degrees == 0) {
            return color;
        }
        final float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[0] = ((hsl[0] + degrees) % 360f + 360f) % 360f;
        return ColorUtils.HSLToColor(hsl);
    }

    /** One category tile. */
    final class Tile {
        final int top;
        final int bottom;
        final int edge;
        final int ring;
        final int dim;
        final int chipBg;
        final int chipText;
        final int photoA;
        final int photoB;

        private Tile(int offset) {
            if (night) {
                top = accent(android.R.color.system_accent1_800, offset);
                bottom = ColorUtils.blendARGB(
                        accent(android.R.color.system_accent1_900, offset), Color.BLACK, 0.5f);
                edge = alpha(accent(android.R.color.system_accent1_400, offset), 0x38);
                ring = accent(android.R.color.system_accent1_400, offset);
                dim = alpha(accent(android.R.color.system_accent1_200, offset), 0x47);
                chipBg = alpha(ring, 0x33);
                chipText = accent(android.R.color.system_accent1_200, offset);
                photoA = turn(accent(android.R.color.system_accent1_500, offset), 20);
                photoB = turn(accent(android.R.color.system_accent1_800, offset), -20);
            } else {
                top = accent(android.R.color.system_accent1_50, offset);
                bottom = accent(android.R.color.system_accent1_100, offset);
                edge = alpha(accent(android.R.color.system_accent1_600, offset), 0x24);
                ring = accent(android.R.color.system_accent1_600, offset);
                dim = alpha(accent(android.R.color.system_accent1_700, offset), 0x2E);
                chipBg = alpha(ring, 0x24);
                chipText = accent(android.R.color.system_accent1_800, offset);
                photoA = turn(accent(android.R.color.system_accent1_400, offset), 20);
                photoB = turn(accent(android.R.color.system_accent1_700, offset), -20);
            }
        }
    }

    /** The Remixer and device bars: dark in both modes. */
    final class Bar {
        final int top;
        final int edge;
        final int mark;
        final int glow;

        private Bar(int offset) {
            top = accent(android.R.color.system_accent1_900, offset);
            mark = accent(android.R.color.system_accent1_400, offset);
            edge = alpha(mark, 0x40);
            glow = alpha(mark, 0x8C);
        }
    }
}
