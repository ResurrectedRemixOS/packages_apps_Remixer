package com.resurrection.settings.charging;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.PreferenceCategory;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChargingAnimationSettings extends SettingsPreferenceFragment
        implements SelectorWithWidgetPreference.OnClickListener {
    private static final String SETTING = "rr_charging_animation";
    private static final String PREVIEW = "rr_charging_animation_preview";
    private static final String LEGACY_RING = "rr_charging_ring";
    private static final String RING_LIBRARY = "/system_ext/lib64/libnativeChargingRing.so";
    private static final String OVERLAY_PREFIX = "com.android.systemui.charging_animation.";

    private static final Object[][] STYLES = {
        {"rr_neon", "rr", R.string.charging_style_rr_neon, R.string.charging_style_rr_neon_summary},
        {"rr_defib", "rr", R.string.charging_style_rr_defib, R.string.charging_style_rr_defib_summary},
        {"rr_pulse", "rr", R.string.charging_style_rr_pulse, R.string.charging_style_rr_pulse_summary},
        {"pixel", "makers", R.string.charging_style_pixel, R.string.charging_style_pixel_summary},
        {"oneplus", "makers", R.string.charging_style_oneplus, R.string.charging_style_oneplus_summary},
        {"oneui", "makers", R.string.charging_style_oneui, R.string.charging_style_oneui_summary},
        {"hyperos", "makers", R.string.charging_style_hyperos, R.string.charging_style_hyperos_summary},
        {"originos", "makers", R.string.charging_style_originos, R.string.charging_style_originos_summary},
        {"supercharge", "makers", R.string.charging_style_supercharge,
                R.string.charging_style_supercharge_summary},
        {"moto", "makers", R.string.charging_style_moto, R.string.charging_style_moto_summary},
        {"nothing", "makers", R.string.charging_style_nothing, R.string.charging_style_nothing_summary},
        {"bliss_flash", "roms", R.string.charging_style_bliss_flash,
                R.string.charging_style_bliss_flash_summary},
        {"bliss_glow", "roms", R.string.charging_style_bliss_glow,
                R.string.charging_style_bliss_glow_summary},
        {"bliss_battery", "roms", R.string.charging_style_bliss_battery,
                R.string.charging_style_bliss_battery_summary},
        {"none", "off", R.string.charging_animation_none, R.string.charging_animation_none_summary},
    };

    private final List<SelectorWithWidgetPreference> mItems = new ArrayList<>();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_charging_animation);
        final Context context = getPrefContext();
        final String selected = selected(context);
        for (Object[] style : STYLES) {
            final String key = (String) style[0];
            if (!isAvailable(context, key)) {
                continue;
            }
            final SelectorWithWidgetPreference item = new SelectorWithWidgetPreference(context);
            item.setKey(key);
            item.setTitle((int) style[2]);
            item.setSummary((int) style[3]);
            item.setPersistent(false);
            item.setChecked(key.equals(selected));
            item.setOnClickListener(this);
            final PreferenceCategory category = findPreference((String) style[1]);
            category.addPreference(item);
            mItems.add(item);
        }
    }

    @Override
    public void onRadioButtonClicked(SelectorWithWidgetPreference clicked) {
        final String key = clicked.getKey();
        final Context context = getPrefContext();
        Settings.System.putStringForUser(context.getContentResolver(), SETTING, key,
                UserHandle.USER_CURRENT);
        Settings.System.putStringForUser(context.getContentResolver(), PREVIEW,
                key + ":" + SystemClock.uptimeMillis(), UserHandle.USER_CURRENT);
        for (SelectorWithWidgetPreference item : mItems) {
            item.setChecked(item == clicked);
        }
    }

    static String selected(Context context) {
        final String style = Settings.System.getStringForUser(context.getContentResolver(),
                SETTING, UserHandle.USER_CURRENT);
        if (style != null && !style.isEmpty()) {
            return style;
        }
        if (Settings.System.getIntForUser(context.getContentResolver(), LEGACY_RING, 1,
                UserHandle.USER_CURRENT) == 0) {
            return "pixel";
        }
        return isAvailable(context, "oneplus") ? "oneplus" : "rr_neon";
    }

    private static boolean isAvailable(Context context, String key) {
        switch (key) {
            case "oneplus":
                return new File(RING_LIBRARY).exists();
            case "moto":
            case "nothing":
                try {
                    context.getPackageManager().getPackageInfo(OVERLAY_PREFIX + key, 0);
                    return true;
                } catch (PackageManager.NameNotFoundException e) {
                    return false;
                }
            default:
                return true;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
