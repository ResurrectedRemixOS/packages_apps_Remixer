/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.volume;

import android.content.Context;

import androidx.core.graphics.ColorUtils;

final class VolumeLook {
    static final int COUNT = 10;
    static final int CLASSIC = 4;

    int accent;
    int panel;
    int panelStroke;
    float panelStrokeDp = 1f;
    boolean glow;
    int ringerArea;
    int ringerBg;
    int ringerStroke;
    int ringerFg;
    int trackBg;
    int trackStroke;
    float trackStrokeDp = 1f;
    int active;
    int thumb;
    int iconFg;
    int percentFg;
    boolean mono;
    int tick = 0x4D000000;
    boolean pill;
    boolean classic;

    static VolumeLook of(Context context, int style) {
        int accent = context.getColor(android.R.color.system_primary_dark);
        int highest = context.getColor(android.R.color.system_surface_container_highest_dark);
        VolumeLook l = new VolumeLook();
        l.accent = accent;
        l.panel = context.getColor(android.R.color.system_surface_dark);
        l.ringerBg = highest;
        l.ringerFg = context.getColor(android.R.color.system_on_surface_dark);
        l.trackBg = highest;
        l.active = accent;
        l.thumb = accent;
        l.iconFg = context.getColor(android.R.color.system_on_primary_dark);
        l.percentFg = context.getColor(android.R.color.system_on_surface_variant_dark);
        switch (style) {
            case 1:
                l.panel = 0xE6120A0C;
                l.panelStroke = accent;
                l.panelStrokeDp = 1.5f;
                l.glow = true;
                l.ringerBg = 0x14FFFFFF;
                l.ringerFg = accent;
                l.trackBg = 0x1AFFFFFF;
                l.trackStroke = accent;
                l.trackStrokeDp = 1.5f;
                l.iconFg = 0xFF120A0C;
                l.percentFg = accent;
                break;
            case 2:
                l.panel = 0x40FFFFFF;
                l.panelStroke = 0x44FFFFFF;
                l.ringerBg = 0x38FFFFFF;
                l.ringerFg = 0xFFFFFFFF;
                l.trackBg = 0x33FFFFFF;
                l.active = 0xD9FFFFFF;
                l.thumb = 0xFFFFFFFF;
                l.iconFg = 0xFF2B2123;
                l.percentFg = 0xFFFFFFFF;
                l.tick = 0x40000000;
                break;
            case 3:
                l.panel = 0xCC1A1416;
                l.pill = true;
                break;
            case CLASSIC:
                l.panel = 0xF21D1517;
                l.classic = true;
                break;
            case 5:
                l.panel = 0xF2181211;
                l.ringerBg = 0x14FFFFFF;
                l.trackBg = 0x1AFFFFFF;
                break;
            case 6:
                l.panel = 0xF21E1715;
                l.ringerBg = 0x14FFFFFF;
                l.trackBg = 0x2EFFFFFF;
                break;
            case 7:
                l.panel = 0xF20C0B0B;
                l.panelStroke = 0x2EFFFFFF;
                l.ringerBg = 0;
                l.ringerStroke = 0x66FFFFFF;
                l.ringerFg = accent;
                l.trackBg = 0;
                l.trackStroke = 0x47FFFFFF;
                l.percentFg = accent;
                l.mono = true;
                l.tick = 0x4DFFFFFF;
                break;
            case 8:
                l.panel = 0xF2181211;
                l.ringerArea = 0xF22A2120;
                l.ringerBg = 0x14FFFFFF;
                l.trackBg = 0x1AFFFFFF;
                l.active = ColorUtils.setAlphaComponent(accent, 0x59);
                l.iconFg = 0xFFF0DEE0;
                l.tick = 0x40FFFFFF;
                break;
            case 9:
                l.panel = 0xFF1C1716;
                l.panelStroke = 0x14FFFFFF;
                l.ringerBg = 0x14FFFFFF;
                l.trackBg = 0xFF110D0C;
                l.trackStroke = 0x14FFFFFF;
                break;
            default:
                break;
        }
        return l;
    }
}
