package com.resurrection.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import com.android.settings.R;
import com.android.settings.core.SubSettingLauncher;
import com.android.settingslib.utils.ThreadUtils;

/**
 * Remixer hub body: a row of quick shortcuts, then every section as a colored card.
 */
public class RemixerHubView extends LinearLayout {
    /** Same value RemixerSettings reports; SubSettingLauncher requires one. */
    private static final int METRICS_CATEGORY = 46;

    private static final Section[] SECTIONS = {
        new Section("statusbar", R.string.remixer_statusbar, R.string.remixer_statusbar_summary,
                R.drawable.ic_remixer_statusbar_glyph, R.color.homepage_blue_bg,
                R.color.homepage_blue_fg),
        new Section("qs", R.string.remixer_qs, R.string.remixer_qs_summary,
                R.drawable.ic_remixer_qs_glyph, R.color.homepage_cyan_bg,
                R.color.homepage_cyan_fg),
        new Section("notifications", R.string.remixer_notifications,
                R.string.remixer_notifications_summary, R.drawable.ic_remixer_notifications_glyph,
                R.color.homepage_orange_bg, R.color.homepage_orange_fg),
        new Section("theme", R.string.remixer_theme, R.string.remixer_theme_summary,
                R.drawable.ic_remixer_theme_glyph, R.color.homepage_pink_bg,
                R.color.homepage_pink_fg),
        new Section("lockscreen", R.string.remixer_lockscreen,
                R.string.remixer_lockscreen_summary, R.drawable.ic_remixer_lockscreen_glyph,
                R.color.homepage_purple_bg, R.color.homepage_purple_fg),
        new Section("navigation", R.string.remixer_navigation,
                R.string.remixer_navigation_summary, R.drawable.ic_remixer_navigation_glyph,
                R.color.homepage_green_bg, R.color.homepage_green_fg),
    };
    private static final Section MISC = new Section("misc", R.string.remixer_misc,
            R.string.remixer_misc_summary, R.drawable.ic_remixer_misc_glyph,
            R.color.homepage_yellow_bg, R.color.homepage_yellow_fg);

    private final boolean mNight;
    private final int mSurface;
    private final int mContainer;
    private final int mText;
    private final int mSubtext;

    public RemixerHubView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mNight = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        mSurface = context.getColor(com.android.internal.R.color.materialColorSurfaceBright);
        mContainer = context.getColor(com.android.internal.R.color.materialColorSurfaceContainer);
        mText = context.getColor(com.android.internal.R.color.materialColorOnSurface);
        mSubtext = context.getColor(com.android.internal.R.color.materialColorOnSurfaceVariant);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Context context = getContext();
        ((TextView) findViewById(R.id.remixer_quick_title)).setTextColor(mText);
        ((TextView) findViewById(R.id.remixer_everything_title)).setTextColor(mText);

        int accent = context.getColor(android.R.color.system_accent1_500);
        LinearLayout quick = findViewById(R.id.remixer_quick);
        View accents = quickTile(quick, R.string.remixer_accents, R.string.remixer_quick_accents,
                AccentActivity.class);
        LinearLayout dots = new LinearLayout(context);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        int[] swatches = {accent, 0xFF4285F4, 0xFFFCC900, 0};
        for (int i = 0; i < swatches.length; i++) {
            View dot = new View(context);
            LayoutParams lp = new LayoutParams(dp(34), dp(34));
            if (i > 0) {
                lp.setMarginStart(-dp(10));
            }
            dot.setLayoutParams(lp);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            if (swatches[i] == 0) {
                shape.setGradientType(GradientDrawable.SWEEP_GRADIENT);
                shape.setColors(new int[] {0xFFE02437, 0xFFFCC900, 0xFF3DDC84, 0xFF4285F4,
                        0xFF8C5CFF, 0xFFE02437});
            } else {
                shape.setColor(swatches[i]);
            }
            shape.setStroke(dp(3), mSurface);
            dot.setBackground(shape);
            dots.addView(dot);
        }
        art(accents).addView(dots, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.TOP));

        View fonts = quickTile(quick, R.string.remixer_fonts, R.string.remixer_quick_fonts,
                FontActivity.class);
        TextView aa = new TextView(context);
        aa.setText("Aa");
        aa.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        aa.setTypeface(Typeface.DEFAULT_BOLD);
        aa.setIncludeFontPadding(false);
        aa.setTextColor(context.getColor(mNight
                ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600));
        art(fonts).addView(aa);
        ThreadUtils.postOnBackgroundThread(() -> {
            FontController controller = new FontController(context);
            Typeface typeface = controller.typeface(controller.enabledPackage());
            post(() -> aa.setTypeface(Typeface.create(typeface, Typeface.BOLD)));
        });

        View walls = quickTile(quick, R.string.remixer_wallpapers, R.string.remixer_quick_walls,
                WallpaperActivity.class);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.25f, 0.2f);
        glow.setGradientRadius(dp(140));
        glow.setColors(new int[] {context.getColor(android.R.color.system_accent1_300),
                context.getColor(android.R.color.system_accent1_800), 0xFF050506});
        glow.setCornerRadius(dp(18));
        art(walls).setBackground(glow);

        View header = quickTile(quick, R.string.remixer_quick_header,
                R.string.remixer_quick_header_summary, HeaderActivity.class);
        GradientDrawable sky = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF1B2A49, 0xFF3A5F8F, 0xFFF2A65A});
        sky.setCornerRadius(dp(18));
        art(header).setBackground(sky);

        LinearLayout grid = findViewById(R.id.remixer_grid);
        LayoutInflater inflater = LayoutInflater.from(context);
        LinearLayout row = null;
        for (int i = 0; i < SECTIONS.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(context);
                LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    lp.topMargin = dp(10);
                }
                grid.addView(row, lp);
            }
            View card = inflater.inflate(R.layout.remixer_hub_card, row, false);
            LayoutParams lp = new LayoutParams(0, dp(132), 1);
            if (i % 2 == 1) {
                lp.setMarginStart(dp(10));
            }
            bindCard(card, SECTIONS[i]);
            row.addView(card, lp);
        }
        View misc = inflater.inflate(R.layout.remixer_hub_wide, grid, false);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76));
        lp.topMargin = dp(10);
        bindCard(misc, MISC);
        ((ImageView) misc.findViewById(R.id.card_chevron)).setImageTintList(
                ColorStateList.valueOf(mSubtext));
        grid.addView(misc, lp);
    }

    private View quickTile(LinearLayout parent, int title, int summary, Class<?> activity) {
        View tile = LayoutInflater.from(getContext())
                .inflate(R.layout.remixer_quick_tile, parent, false);
        LayoutParams lp = new LayoutParams(dp(150), dp(150));
        if (parent.getChildCount() > 0) {
            lp.setMarginStart(dp(10));
        }
        TextView titleView = tile.findViewById(R.id.quick_title);
        titleView.setText(title);
        titleView.setTextColor(mText);
        TextView summaryView = tile.findViewById(R.id.quick_summary);
        summaryView.setText(summary);
        summaryView.setTextColor(mSubtext);
        tile.setBackground(pressable(mSurface, 26));
        tile.setContentDescription(getContext().getString(title));
        tile.setOnClickListener(v -> start(new Intent(getContext(), activity)));
        parent.addView(tile, lp);
        return tile;
    }

    private static FrameLayout art(View tile) {
        return tile.findViewById(R.id.quick_art);
    }

    private void bindCard(View card, Section section) {
        Context context = getContext();
        int bg = context.getColor(section.bg);
        card.setBackground(pressable(
                ColorUtils.blendARGB(bg, mContainer, mNight ? 0.84f : 0.5f), 26));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(bg);
        chip.setCornerRadius(dp(14));
        card.findViewById(R.id.card_chip).setBackground(chip);
        ImageView glyph = card.findViewById(R.id.card_glyph);
        glyph.setImageResource(section.glyph);
        glyph.setImageTintList(ColorStateList.valueOf(context.getColor(section.fg)));
        TextView title = card.findViewById(R.id.card_title);
        title.setText(section.title);
        title.setTextColor(mText);
        TextView summary = card.findViewById(R.id.card_summary);
        summary.setText(section.summary);
        summary.setTextColor(mSubtext);
        card.setContentDescription(context.getString(section.title));
        card.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("section", section.key);
            new SubSettingLauncher(context)
                    .setDestination(RemixerSection.class.getName())
                    .setArguments(args)
                    .setTitleRes(section.title)
                    .setSourceMetricsCategory(METRICS_CATEGORY)
                    .launch();
        });
    }

    private void start(Intent intent) {
        try {
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), R.string.missing_app, Toast.LENGTH_SHORT).show();
        }
    }

    private Drawable pressable(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(mText, 0x1F)), shape, mask);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Section {
        final String key;
        final int title;
        final int summary;
        final int glyph;
        final int bg;
        final int fg;

        Section(String key, int title, int summary, int glyph, int bg, int fg) {
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.glyph = glyph;
            this.bg = bg;
            this.fg = fg;
        }
    }
}
