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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import com.android.settings.R;
import com.android.settings.core.SubSettingLauncher;
import com.android.settingslib.utils.ThreadUtils;

/**
 * Remixer hub body: a row of quick shortcuts, then every section as a card with a live preview.
 */
public class RemixerHubView extends LinearLayout {
    /** Same value RemixerSettings reports; SubSettingLauncher requires one. */
    private static final int METRICS_CATEGORY = 46;

    private static final Section[] SECTIONS = {
        new Section("statusbar", R.string.remixer_statusbar, R.string.remixer_statusbar_summary,
                HubPreviewView.STATUS_BAR, 0),
        new Section("qs", R.string.remixer_qs, R.string.remixer_qs_summary,
                HubPreviewView.QUICK_SETTINGS, 32),
        new Section("notifications", R.string.remixer_notifications,
                R.string.remixer_notifications_summary, HubPreviewView.NOTIFICATIONS, -32),
        new Section("theme", R.string.remixer_theme, R.string.remixer_theme_summary,
                HubPreviewView.THEME, 64),
        new Section("lockscreen", R.string.remixer_lockscreen,
                R.string.remixer_lockscreen_summary, HubPreviewView.LOCK_SCREEN, -64),
        new Section("navigation", R.string.remixer_navigation,
                R.string.remixer_navigation_summary, HubPreviewView.NAVIGATION, 96),
    };
    private static final Section MISC = new Section("misc", R.string.remixer_misc,
            R.string.remixer_misc_summary, HubPreviewView.MISC, 128);

    private final boolean mNight;
    private final int mSurface;
    private final int mText;
    private final int mSubtext;
    private HubPreviewView mThemePreview;

    public RemixerHubView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mNight = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        mSurface = context.getColor(com.android.internal.R.color.materialColorSurfaceBright);
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
            post(() -> {
                aa.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                if (mThemePreview != null) {
                    mThemePreview.setFont(typeface);
                }
            });
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
            // MATCH_PARENT in a WRAP_CONTENT row keeps both cards of a row the same height.
            LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (i % 2 == 1) {
                lp.setMarginStart(dp(10));
            }
            bindCard(card, SECTIONS[i], accent);
            row.addView(card, lp);
        }
        View misc = inflater.inflate(R.layout.remixer_hub_wide, grid, false);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        bindCard(misc, MISC, accent);
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

    /** Colors the card from the accent, its hue turned by the section's offset. */
    private void bindCard(View card, Section section, int accent) {
        Context context = getContext();
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(accent, hsl);
        float hue = hsl[0] + section.hueOffset;
        // A grey accent keeps the cards grey instead of inventing a hue.
        float sat = hsl[1] < 0.12f ? 0 : 1;
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {hsl(hue, 0.68f * sat, 0.36f), hsl(hue + 14, 0.72f * sat, 0.17f)});
        fill.setCornerRadius(dp(26));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(26));
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), fill, mask));
        HubPreviewView preview = card.findViewById(R.id.card_preview);
        preview.setKind(section.kind);
        preview.setColors(hsl(hue, 0.95f * sat, 0.74f), hsl(hue, 0.8f * sat, 0.52f),
                hsl(hue, 0.7f * sat, 0.14f));
        if (section.kind == HubPreviewView.THEME) {
            mThemePreview = preview;
        }
        ((TextView) card.findViewById(R.id.card_title)).setText(section.title);
        ((TextView) card.findViewById(R.id.card_summary)).setText(section.summary);
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

    static int hsl(float hue, float saturation, float lightness) {
        return ColorUtils.HSLToColor(new float[] {(hue % 360 + 360) % 360, saturation, lightness});
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
        final int kind;
        final float hueOffset;

        Section(String key, int title, int summary, int kind, float hueOffset) {
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.kind = kind;
            this.hueOffset = hueOffset;
        }
    }
}
