/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.about;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

/** The Resurrected Remix row at the top of the software card, with a button to the Updater. */
public class SoftwareHeaderPreference extends Preference {

    private static final ComponentName UPDATER = new ComponentName(
            "org.lineageos.updater", "org.lineageos.updater.UpdatesActivity");

    public SoftwareHeaderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.rr_about_software_header);
        setSelectable(false);
        setTitle(R.string.rr_about_rr);
        String variant = AboutProps.variantLabel(context);
        String release = AboutProps.releaseLabel(context);
        setSummary(variant == null ? release
                : context.getString(R.string.rr_about_model_line, release, variant));
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        Context context = getContext();
        View badge = holder.findViewById(R.id.rr_about_badge);
        badge.setBackground(AboutUi.round(context,
                AboutUi.color(context, android.R.color.system_accent1_600), 13));
        ((ImageView) holder.findViewById(R.id.rr_about_badge_mark))
                .setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));

        TextView updates = (TextView) holder.findViewById(R.id.rr_about_updates);
        Intent intent = new Intent().setComponent(UPDATER);
        if (context.getPackageManager().resolveActivity(intent, 0) == null) {
            updates.setVisibility(View.GONE);
            return;
        }
        int container = AboutUi.color(context,
                com.android.internal.R.color.materialColorSecondaryContainer);
        int onContainer = AboutUi.color(context,
                com.android.internal.R.color.materialColorOnSecondaryContainer);
        updates.setVisibility(View.VISIBLE);
        updates.setTextColor(onContainer);
        updates.setCompoundDrawableTintList(ColorStateList.valueOf(onContainer));
        updates.setBackground(AboutUi.pressable(context, container, 18, onContainer));
        updates.setOnClickListener(v -> {
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // The Updater went away.
            }
        });
    }
}
