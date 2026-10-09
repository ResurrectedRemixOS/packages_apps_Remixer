package com.resurrection.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settingslib.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Remixer hub hero: the accent card with the current look and a button that shuffles the
 * accent through the presets.
 */
public class RemixerHeroView extends LinearLayout {
    private final AccentController mAccents;
    private TextView mFontChip;

    public RemixerHeroView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAccents = new AccentController(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Context context = getContext();
        ((TextView) findViewById(R.id.remixer_version)).setText(
                context.getString(R.string.remixer_version, Build.VERSION.RELEASE));

        RemixerChipRow chips = findViewById(R.id.remixer_chips);
        TextView accent = chip(chips, accentName());
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(context.getColor(android.R.color.system_accent1_400));
        dot.setStroke(dp(2), 0xD9FFFFFF);
        dot.setSize(dp(18), dp(18));
        accent.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
        accent.setCompoundDrawablePadding(dp(8));
        accent.setPaddingRelative(dp(8), 0, dp(12), 0);
        mFontChip = chip(chips, fontLabel(context.getString(R.string.font_default)));
        chip(chips, shadeLabel());
        ThreadUtils.postOnBackgroundThread(() -> {
            FontController fonts = new FontController(context);
            String label = fonts.label(fonts.enabledPackage());
            post(() -> mFontChip.setText(fontLabel(label)));
        });

        TextView remix = findViewById(R.id.remixer_remix);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.WHITE);
        pill.setCornerRadius(dp(24));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(24));
        remix.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1F000000), pill, mask));
        Drawable shuffle = context.getDrawable(R.drawable.remixer_ic_shuffle).mutate();
        shuffle.setTint(context.getColor(android.R.color.system_accent1_600));
        remix.setCompoundDrawablesRelativeWithIntrinsicBounds(shuffle, null, null, null);
        remix.setOnClickListener(v -> remix());
    }

    private TextView chip(RemixerChipRow row, CharSequence text) {
        TextView chip = new TextView(getContext());
        chip.setText(text);
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setSingleLine(true);
        chip.setMinHeight(dp(32));
        chip.setPaddingRelative(dp(12), 0, dp(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1FFFFFFF);
        bg.setCornerRadius(dp(16));
        chip.setBackground(bg);
        row.addView(chip);
        return chip;
    }

    private CharSequence fontLabel(String label) {
        SpannableString text = new SpannableString("Aa  " + label);
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private String accentName() {
        String current = mAccents.current();
        String[] names = getResources().getStringArray(R.array.accent_names);
        String[] colors = getResources().getStringArray(R.array.accent_colors);
        if (current == null) {
            return names[0];
        }
        for (int i = 1; i < colors.length; i++) {
            if (current.equals(String.format("%06X", Color.parseColor(colors[i]) & 0xFFFFFF))) {
                return names[i];
            }
        }
        return getContext().getString(R.string.accent_custom);
    }

    private String shadeLabel() {
        int shade = mAccents.shade();
        if (shade > 0) {
            return getContext().getString(R.string.accent_shade_lighter_value, shade);
        }
        if (shade < 0) {
            return getContext().getString(R.string.accent_shade_darker_value, -shade);
        }
        return getContext().getString(R.string.remixer_chip_shade_default);
    }

    /** Applies a random preset other than the current accent. */
    private void remix() {
        String[] names = getResources().getStringArray(R.array.accent_names);
        String[] colors = getResources().getStringArray(R.array.accent_colors);
        String current = mAccents.current();
        List<Integer> choices = new ArrayList<>();
        for (int i = 1; i < colors.length; i++) {
            if (!String.format("%06X", Color.parseColor(colors[i]) & 0xFFFFFF).equals(current)) {
                choices.add(i);
            }
        }
        int pick = choices.get(new Random().nextInt(choices.size()));
        try {
            mAccents.apply(colors[pick]);
        } catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(getContext(), R.string.accent_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(), getContext().getString(R.string.remixer_remixed,
                names[pick]), Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
