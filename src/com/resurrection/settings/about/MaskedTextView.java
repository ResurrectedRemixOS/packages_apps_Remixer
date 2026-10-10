/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.about;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.settings.R;

/** A preference summary that shows dots until About phone's Show button is pressed. */
public class MaskedTextView extends TextView implements AboutPrivacy.Listener {

    private static final String MASK = "•••• ••••";

    // Not initialized here: TextView's constructor calls setText before field initializers run.
    private CharSequence mReal;

    public MaskedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        mReal = text;
        super.setText(shown(text), type);
        updateDescription();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AboutPrivacy.addListener(this);
        onRevealChanged(AboutPrivacy.isRevealed());
    }

    @Override
    protected void onDetachedFromWindow() {
        AboutPrivacy.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onRevealChanged(boolean revealed) {
        super.setText(shown(mReal), BufferType.NORMAL);
        updateDescription();
    }

    private CharSequence shown(CharSequence text) {
        return TextUtils.isEmpty(text) || !TextUtils.isGraphic(text) || AboutPrivacy.isRevealed()
                ? text : MASK;
    }

    private void updateDescription() {
        boolean masked = MASK.contentEquals(getText());
        setContentDescription(masked ? getContext().getString(R.string.rr_about_hidden) : null);
    }
}
