/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.about;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;

import com.android.settings.R;

/** Build and device names shown on About phone. */
final class AboutProps {

    private AboutProps() {
    }

    /** The device's market name, e.g. "OnePlus 13". */
    static String deviceName() {
        String name = SystemProperties.get("ro.product.marketname");
        return TextUtils.isEmpty(name) ? Build.MODEL : name;
    }

    /** Model and codename, e.g. "CPH2655 · dodge". */
    static String modelLine(Context context) {
        String codename = SystemProperties.get("ro.rr.device");
        if (TextUtils.isEmpty(codename)) {
            codename = Build.DEVICE;
        }
        return context.getString(R.string.rr_about_model_line, Build.MODEL, codename);
    }

    static boolean isOfficial() {
        String type = SystemProperties.get("ro.rr.releasetype");
        if (TextUtils.isEmpty(type)) {
            String[] parts = SystemProperties.get("ro.rr.display.version").split("-");
            type = parts.length > 2 ? parts[2] : "";
        }
        return "OFFICIAL".equalsIgnoreCase(type);
    }

    static String releaseLabel(Context context) {
        return context.getString(isOfficial() ? R.string.rr_about_official
                : R.string.rr_about_unofficial);
    }

    /** "GApps" or "Vanilla", or null when the build doesn't say. */
    static String variantLabel(Context context) {
        String variant = SystemProperties.get("ro.rr.build.variant");
        if ("gapps".equalsIgnoreCase(variant)) {
            return context.getString(R.string.rr_about_gapps);
        }
        if ("vanilla".equalsIgnoreCase(variant)) {
            return context.getString(R.string.rr_about_vanilla);
        }
        return null;
    }
}
