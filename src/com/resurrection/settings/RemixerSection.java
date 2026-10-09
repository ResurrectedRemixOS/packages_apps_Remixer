package com.resurrection.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.widget.LayoutPreference;
import com.resurrection.settings.homepage.HomepageStyle;

import java.util.Locale;

/**
 * One Remixer section, picked by the "section" extra of its hub entry: a banner in the
 * section's homepage colors, then its entries.
 */
public class RemixerSection extends SettingsPreferenceFragment {

    private enum Section {
        STATUSBAR(R.xml.remixer_statusbar, R.string.remixer_statusbar_summary,
                R.drawable.ic_remixer_statusbar_glyph,
                R.color.homepage_blue_bg, R.color.homepage_blue_fg),
        QS(R.xml.remixer_qs, R.string.remixer_qs_summary,
                R.drawable.ic_remixer_qs_glyph,
                R.color.homepage_cyan_bg, R.color.homepage_cyan_fg),
        NOTIFICATIONS(R.xml.remixer_notifications, R.string.remixer_notifications_summary,
                R.drawable.ic_remixer_notifications_glyph,
                R.color.homepage_orange_bg, R.color.homepage_orange_fg),
        THEME(R.xml.remixer_theme, R.string.remixer_theme_summary,
                R.drawable.ic_remixer_theme_glyph,
                R.color.homepage_pink_bg, R.color.homepage_pink_fg),
        LOCKSCREEN(R.xml.remixer_lockscreen, R.string.remixer_lockscreen_summary,
                R.drawable.ic_remixer_lockscreen_glyph,
                R.color.homepage_purple_bg, R.color.homepage_purple_fg),
        NAVIGATION(R.xml.remixer_navigation, R.string.remixer_navigation_summary,
                R.drawable.ic_remixer_navigation_glyph,
                R.color.homepage_green_bg, R.color.homepage_green_fg),
        MISC(R.xml.remixer_misc, R.string.remixer_misc_summary,
                R.drawable.ic_remixer_misc_glyph,
                R.color.homepage_yellow_bg, R.color.homepage_yellow_fg);

        final int xml;
        final int summary;
        final int glyph;
        final int bg;
        final int fg;

        Section(int xml, int summary, int glyph, int bg, int fg) {
            this.xml = xml;
            this.summary = summary;
            this.glyph = glyph;
            this.bg = bg;
            this.fg = fg;
        }
    }

    private Section section() {
        return Section.valueOf(requireArguments().getString("section").toUpperCase(Locale.ROOT));
    }

    @Override
    protected int getPreferenceScreenResId() {
        return section().xml;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Section section = section();
        final Context context = getPrefContext();
        final ColorStateList fg = ColorStateList.valueOf(context.getColor(section.fg));
        final LayoutPreference banner =
                new LayoutPreference(context, R.layout.remixer_section_header);
        banner.setKey("remixer_section_header");
        banner.setOrder(-1);
        banner.setSelectable(false);
        banner.findViewById(R.id.remixer_section_card).setBackgroundTintList(
                ColorStateList.valueOf(context.getColor(section.bg)));
        final ImageView icon = banner.findViewById(R.id.remixer_section_icon);
        icon.setImageResource(section.glyph);
        icon.setImageTintList(fg);
        final TextView summary = banner.findViewById(R.id.remixer_section_summary);
        summary.setText(section.summary);
        summary.setTextColor(fg);
        getPreferenceScreen().addPreference(banner);

        removeUnless("remixer_udfps_icon", UdfpsActivity.hasIcons(context));
        removeUnless("remixer_udfps_animation", UdfpsActivity.hasAnimations(context));

        final ListPreference settingsStyle = findPreference("remixer_settings_style");
        if (settingsStyle != null) {
            settingsStyle.setValue(HomepageStyle.get(context));
            settingsStyle.setOnPreferenceChangeListener((preference, value) -> {
                HomepageStyle.set(context, (String) value);
                return true;
            });
        }
    }

    private void removeUnless(String key, boolean keep) {
        final Preference preference = findPreference(key);
        if (preference != null && !keep) {
            getPreferenceScreen().removePreference(preference);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final Preference qsStyle = findPreference("remixer_qs_style");
        if (qsStyle != null) {
            qsStyle.setSummary(QsStyleActivity.currentName(getContext()));
        }
        final Preference lockMedia = findPreference("remixer_lock_media_style");
        if (lockMedia != null) {
            lockMedia.setSummary(QsStyleActivity.currentLockMediaName(getContext()));
        }
        // Entries launched from XML get no title from SubSettingLauncher.
        getActivity().setTitle(getPreferenceScreen().getTitle());
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final Intent intent = intentFor(preference.getKey());
        if (intent == null) {
            return super.onPreferenceTreeClick(preference);
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), R.string.missing_app, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private Intent intentFor(String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case "remixer_fonts":
                return new Intent(getContext(), FontActivity.class);
            case "remixer_wallpapers":
                return new Intent(getContext(), WallpaperActivity.class);
            case "remixer_accents":
                return new Intent(getContext(), AccentActivity.class);
            case "remixer_pulse":
                return new Intent(getContext(), PulseActivity.class);
            case "remixer_qs_style":
                return new Intent(getContext(), QsStyleActivity.class);
            case "remixer_lock_media_style":
                return QsStyleActivity.lockMediaIntent(getContext());
            case "remixer_headers":
                return new Intent(getContext(), HeaderActivity.class);
            case "remixer_udfps_icon":
                return UdfpsActivity.intent(getContext(), UdfpsActivity.KIND_ICON);
            case "remixer_udfps_animation":
                return UdfpsActivity.intent(getContext(), UdfpsActivity.KIND_ANIMATION);
            case "remixer_weather":
                return external("org.omnirom.omnijaws", "org.omnirom.omnijaws.SettingsActivity");
            default:
                return null;
        }
    }

    private static Intent external(String pkg, String cls) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(pkg, cls);
        return intent;
    }
}
