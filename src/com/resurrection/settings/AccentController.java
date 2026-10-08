package com.resurrection.settings;

import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Writes a Material You seed color the way ThemePicker writes a preset.
 */
public final class AccentController {

    static final String PALETTE = "android.theme.customization.system_palette";
    static final String ACCENT = "android.theme.customization.accent_color";
    static final String SOURCE = "android.theme.customization.color_source";
    static final String STYLE = "android.theme.customization.theme_style";
    static final String INDEX = "android.theme.customization.color_index";
    static final String BOTH = "android.theme.customization.color_both";
    static final String TIMESTAMP = "_applied_timestamp";
    static final String PRESET = "preset";
    static final String WALLPAPER = "home_wallpaper";
    /** Read by SystemUI's ThemeOverlayController; -SHADE_MAX darker to SHADE_MAX lighter. */
    static final String SHADE = "rr_accent_shade";
    static final int SHADE_MAX = 5;
    /** Tone curve exponent per shade step; matches ThemeOverlayController. */
    static final double SHADE_STEP = 0.2;

    private final Context mContext;

    public AccentController(Context context) {
        mContext = context;
    }

    public String current() {
        String raw = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES);
        if (raw == null) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(raw);
            if (!PRESET.equals(object.optString(SOURCE))) {
                return null;
            }
            String color = object.optString(PALETTE, "");
            if (color.isEmpty()) {
                return null;
            }
            if (!color.startsWith("#")) {
                color = "#" + color;
            }
            return String.format("%06X", Color.parseColor(color) & 0xFFFFFF);
        } catch (JSONException | IllegalArgumentException e) {
            return null;
        }
    }

    public int shade() {
        int shade = Settings.Secure.getInt(mContext.getContentResolver(), SHADE, 0);
        return Math.max(-SHADE_MAX, Math.min(SHADE_MAX, shade));
    }

    public void setShade(int shade) {
        if (!Settings.Secure.putInt(mContext.getContentResolver(), SHADE,
                Math.max(-SHADE_MAX, Math.min(SHADE_MAX, shade)))) {
            throw new SecurityException("secure setting rejected");
        }
    }

    public void apply(String color) {
        String raw = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES);
        JSONObject object;
        try {
            object = raw == null ? new JSONObject() : new JSONObject(raw);
        } catch (JSONException e) {
            object = new JSONObject();
        }
        try {
            if (color == null) {
                object.remove(PALETTE);
                object.remove(ACCENT);
                object.put(SOURCE, WALLPAPER);
            } else {
                String hex = String.format("%06X", Color.parseColor(color) & 0xFFFFFF);
                object.put(PALETTE, hex);
                object.put(ACCENT, hex);
                object.put(SOURCE, PRESET);
                object.put(STYLE, "TONAL_SPOT");
                object.put(INDEX, "1");
                object.put(BOTH, "1");
            }
            object.put(TIMESTAMP, System.currentTimeMillis());
            if (!Settings.Secure.putString(mContext.getContentResolver(),
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, object.toString())) {
                throw new SecurityException("secure setting rejected");
            }
        } catch (JSONException e) {
            throw new SecurityException(e);
        }
    }
}
