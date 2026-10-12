package com.resurrection.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;
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
        STATUSBAR(R.xml.remixer_statusbar, R.string.remixer_statusbar,
                R.string.remixer_statusbar_summary, HubPreviewView.STATUS_BAR, 0),
        QS(R.xml.remixer_qs, R.string.remixer_qs, R.string.remixer_qs_summary,
                HubPreviewView.QUICK_SETTINGS, 32),
        NOTIFICATIONS(R.xml.remixer_notifications, R.string.remixer_notifications,
                R.string.remixer_notifications_summary, HubPreviewView.NOTIFICATIONS, -32),
        THEME(R.xml.remixer_theme, R.string.remixer_theme, R.string.remixer_theme_summary,
                HubPreviewView.THEME, 64),
        LOCKSCREEN(R.xml.remixer_lockscreen, R.string.remixer_lockscreen,
                R.string.remixer_lockscreen_summary, HubPreviewView.LOCK_SCREEN, -64),
        NAVIGATION(R.xml.remixer_navigation, R.string.remixer_navigation,
                R.string.remixer_navigation_summary, HubPreviewView.NAVIGATION, 96),
        NAVBAR(R.xml.remixer_navbar, R.string.remixer_navbar_category,
                R.string.remixer_navbar_summary, HubPreviewView.NAVIGATION, 96),
        WEATHER(R.xml.remixer_weather, R.string.remixer_weather,
                R.string.remixer_weather_page_summary, HubPreviewView.LOCK_SCREEN, -64),
        MISC(R.xml.remixer_misc, R.string.remixer_misc, R.string.remixer_misc_summary,
                HubPreviewView.MISC, 128);

        final int xml;
        final int title;
        final int summary;
        final int kind;
        // Same offsets as the hub cards, so a page keeps the color of the card that opened it.
        final int hueOffset;

        Section(int xml, int title, int summary, int kind, int hueOffset) {
            this.xml = xml;
            this.title = title;
            this.summary = summary;
            this.kind = kind;
            this.hueOffset = hueOffset;
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
        final LayoutPreference banner =
                new LayoutPreference(context, R.layout.remixer_section_header);
        banner.setKey("remixer_section_header");
        banner.setOrder(-1);
        banner.setSelectable(false);
        bindStage(banner, section, context);
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

    /** The live stage: a glow in the hub card's hue under the section's own scene. */
    private static void bindStage(LayoutPreference banner, Section section, Context context) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(context.getColor(android.R.color.system_accent1_500), hsl);
        float hue = hsl[0] + section.hueOffset;
        // A grey accent keeps the stage grey, like the hub cards.
        float sat = hsl[1] < 0.12f ? 0 : 1;
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.5f, 1.1f);
        glow.setGradientRadius(300 * density);
        glow.setColors(new int[] {RemixerHubView.hsl(hue, 0.75f * sat, 0.38f),
                RemixerHubView.hsl(hue, 0.4f * sat, 0.09f)}, new float[] {0f, 0.7f});
        glow.setCornerRadius(26 * density);
        View card = banner.findViewById(R.id.remixer_section_card);
        card.setBackground(glow);
        card.setClipToOutline(true);
        HubPreviewView scene = banner.findViewById(R.id.remixer_section_stage);
        scene.setBackdrop(false);
        scene.setKind(section.kind);
        scene.setColors(RemixerHubView.hsl(hue, 0.95f * sat, 0.74f),
                RemixerHubView.hsl(hue, 0.8f * sat, 0.52f),
                RemixerHubView.hsl(hue, 0.7f * sat, 0.14f));
        ((TextView) banner.findViewById(R.id.remixer_section_title)).setText(section.title);
        ((TextView) banner.findViewById(R.id.remixer_section_summary)).setText(section.summary);
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
            case "remixer_weather_service":
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
