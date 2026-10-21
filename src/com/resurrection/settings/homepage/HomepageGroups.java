/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.homepage;

import com.android.settings.R;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.drawer.Tile;

/** The category pages of the RR homepage, and where entries from other apps land. */
public final class HomepageGroups {
    public static final String NETWORK = "network";
    public static final String APPS = "apps";
    public static final String DISPLAY = "display";
    public static final String SECURITY = "security";
    public static final String ACCOUNTS = "accounts";
    public static final String SYSTEM = "system";
    /** The device bar under the Remixer bar. */
    public static final String DEVICE = "device";

    static final String DEVICE_TILE = "top_level_device_settings";

    private HomepageGroups() {}

    static int screenOf(String group) {
        if (group == null) {
            return 0;
        }
        return switch (group) {
            case NETWORK -> R.xml.rr_homepage_group_network;
            case APPS -> R.xml.rr_homepage_group_apps;
            case DISPLAY -> R.xml.rr_homepage_group_display;
            case SECURITY -> R.xml.rr_homepage_group_security;
            case ACCOUNTS -> R.xml.rr_homepage_group_accounts;
            case SYSTEM -> R.xml.rr_homepage_group_system;
            default -> 0;
        };
    }

    /** A group, {@link #DEVICE}, or null to stay in the homepage list. */
    public static String placeOf(Tile tile) {
        final String key = FeatureFactory.getFeatureFactory().getDashboardFeatureProvider()
                .getDashboardKeyForTile(tile);
        if (DEVICE_TILE.equals(key)) {
            return DEVICE;
        }
        if ("top_level_wellbeing".equals(key)) {
            return ACCOUNTS;
        }
        final String groupKey = tile.getGroupKey();
        if (groupKey == null) {
            return null;
        }
        return switch (groupKey) {
            case "top_level_account_category" -> ACCOUNTS;
            case "top_level_security_privacy_category" -> SECURITY;
            case "top_level_connectivity_category" -> NETWORK;
            case "top_level_personalize_category" -> DISPLAY;
            case "top_level_system_info_category", "top_level_support_category" -> SYSTEM;
            default -> null;
        };
    }
}
