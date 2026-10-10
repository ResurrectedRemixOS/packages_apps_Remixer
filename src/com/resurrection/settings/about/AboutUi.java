/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.about;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/** Small drawing helpers shared by the About phone views. */
final class AboutUi {

    private AboutUi() {
    }

    static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    static int color(Context context, int id) {
        return context.getColor(id);
    }

    static GradientDrawable round(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** A rounded fill with a ripple in the content color. */
    static Drawable pressable(Context context, int color, float radiusDp, int contentColor) {
        int ripple = (contentColor & 0x00FFFFFF) | 0x33000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple),
                round(context, color, radiusDp), null);
    }
}
