/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.about;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

/** An About phone category with a Show/Hide button for the private values in it. */
public class RevealCategory extends PreferenceCategory implements AboutPrivacy.Listener {

    public RevealCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.rr_about_category_reveal);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        AboutPrivacy.addListener(this);
    }

    @Override
    public void onDetached() {
        AboutPrivacy.removeListener(this);
        super.onDetached();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView button = (TextView) holder.findViewById(R.id.rr_about_reveal);
        if (button == null) {
            return;
        }
        Context context = getContext();
        boolean revealed = AboutPrivacy.isRevealed();
        int container = AboutUi.color(context,
                com.android.internal.R.color.materialColorSecondaryContainer);
        int onContainer = AboutUi.color(context,
                com.android.internal.R.color.materialColorOnSecondaryContainer);
        button.setText(revealed ? R.string.rr_about_hide : R.string.rr_about_show);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(
                revealed ? R.drawable.rr_about_ic_eye_off : R.drawable.rr_about_ic_eye, 0, 0, 0);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(onContainer));
        button.setTextColor(onContainer);
        button.setBackground(AboutUi.pressable(context, container, 17, onContainer));
        button.setOnClickListener(v -> AboutPrivacy.toggle());
        // Categories are never enabled, so super disabled every view in the row, button included.
        button.setEnabled(true);
    }

    @Override
    public void onRevealChanged(boolean revealed) {
        notifyChanged();
    }
}
