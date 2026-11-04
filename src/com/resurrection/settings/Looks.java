/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.om.OverlayInfo;
import android.graphics.Color;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.internal.util.rr.Utils;
import com.android.settings.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads and applies Remix Looks, for the Looks page and for Remix Modes. */
public final class Looks {
    static final String KEY_LOOKS = "rr_looks";
    static final String PREFIX = "rrlook:";
    static final String LOOKS_DIR = "/product/etc/rr_looks";
    static final String QS_STYLE = "rr_qs_style";
    static final String CLOCK_LOOK = "status_bar_clock_rr_style";
    static final String CLOCK_CHIP = "statusbar_clock_chip";
    static final String CLOCK_FACE = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE;
    static final String CLOCK_COLOR_MODE = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_COLOR_MODE;
    static final String CLOCK_CUSTOM_COLOR = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_CUSTOM_COLOR;
    static final String EDGE_MODE = "edge_light_color_mode";
    static final String EDGE_COLOR = "edge_light_custom_color";
    static final String EDGE_STYLE = "edge_light_style";
    /** Written by the wallpaper page: which RR wallpaper is set, and its wallpaper id. */
    static final String WALLPAPER = "rr_wallpaper";
    static final String WALLPAPER_ID = "rr_wallpaper_id";
    static final String BOOT_PROP = "persist.sys.bootanimation_style";
    static final String HAPTIC_PATTERNS = "rr_haptic_patterns";
    static final String HAPTIC_ASSIGN = "rr_haptic_assign";
    /** The Look a Mode, the tile or the roulette put on last. */
    static final String CURRENT = "rr_look_current";
    /** Edge light moments with a color of their own, saved under "edge_moments". */
    static final String[] EDGE_MOMENTS = {"calls", "alarms", "plugged", "battery_low", "unlock"};

    static final int P_ACCENT = 0;
    static final int P_QS = 1;
    static final int P_FACE = 2;
    static final int P_FONT = 3;
    static final int P_CLOCK = 4;
    static final int P_EDGE = 5;
    static final int P_WALLPAPER = 6;
    static final int P_BOOT = 7;
    static final int P_HAPTICS = 8;
    static final int PARTS = 9;

    private Looks() {}

    /** Every part on, with or without the wallpaper; the boot animation is never a mode's. */
    public static boolean[] parts(boolean wallpaper) {
        boolean[] parts = new boolean[PARTS];
        java.util.Arrays.fill(parts, true);
        parts[P_WALLPAPER] = wallpaper;
        parts[P_BOOT] = false;
        return parts;
    }

    /** Your saved looks, then RR's own. */
    public static List<JSONObject> all(Context context) {
        List<JSONObject> looks = mine(context);
        looks.addAll(builtIn());
        return looks;
    }

    public static JSONObject find(Context context, String name) {
        if (name == null || name.isEmpty()) return null;
        for (JSONObject look : all(context)) {
            if (name.equals(look.optString("name"))) return look;
        }
        return null;
    }

    static List<JSONObject> mine(Context context) {
        List<JSONObject> looks = new ArrayList<>();
        try {
            String json = Settings.System.getStringForUser(context.getContentResolver(), KEY_LOOKS,
                    UserHandle.USER_CURRENT);
            JSONArray array = new JSONArray(json != null ? json : "[]");
            for (int i = 0; i < array.length(); i++) looks.add(array.getJSONObject(i));
        } catch (Exception e) {
            // Start over.
        }
        return looks;
    }

    static List<JSONObject> builtIn() {
        List<JSONObject> looks = new ArrayList<>();
        File[] files = new File(LOOKS_DIR).listFiles((dir, name) -> name.endsWith(".rrlook"));
        if (files == null) return looks;
        for (File file : files) {
            try (InputStream in = new FileInputStream(file)) {
                JSONObject look = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if (look != null) looks.add(look);
            } catch (Exception e) {
                // Skip a broken file.
            }
        }
        looks.sort((a, b) -> Integer.compare(a.optInt("order", 99), b.optInt("order", 99)));
        return looks;
    }

    /** The text of a .rrlook file or QR code, or null if it isn't a look. */
    static JSONObject parse(String text) {
        if (text == null) return null;
        String body = text.trim();
        if (body.startsWith(PREFIX)) body = body.substring(PREFIX.length());
        try {
            JSONObject look = new JSONObject(body);
            return look.has("name") ? look : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Applies the chosen [parts] of [look] and restarts SystemUI when the clock face moves
     * between the system clock and a custom face. True unless something failed.
     */
    public static boolean apply(Context context, JSONObject look, boolean[] parts) {
        boolean ok = true;
        boolean restart = false;
        try {
            if (parts[P_ACCENT] && look.has("accent")) {
                AccentController accent = new AccentController(context);
                accent.apply(look.getString("accent"));
                if (look.has("saturation")) accent.setSaturation(look.getInt("saturation"));
            }
            if (parts[P_QS] && look.has("qs_style")) {
                putSystem(context, QS_STYLE, look.getInt("qs_style"));
            }
            if (parts[P_FACE] && look.has("clock_face")) {
                int face = faceIndex(context, look.getString("clock_face"));
                int old = Settings.Secure.getIntForUser(context.getContentResolver(), CLOCK_FACE,
                        0, UserHandle.USER_CURRENT);
                if (face >= 0 && face != old) {
                    Settings.Secure.putIntForUser(context.getContentResolver(), CLOCK_FACE, face,
                            UserHandle.USER_CURRENT);
                    restart = (face == 0) != (old == 0);
                }
            }
            if (parts[P_FACE] && look.has("clock_color")) {
                String value = look.getString("clock_color");
                boolean custom = value.startsWith("#");
                Settings.Secure.putStringForUser(context.getContentResolver(), CLOCK_COLOR_MODE,
                        custom ? "custom" : value, UserHandle.USER_CURRENT);
                if (custom) {
                    Settings.Secure.putIntForUser(context.getContentResolver(), CLOCK_CUSTOM_COLOR,
                            color(value, Color.WHITE), UserHandle.USER_CURRENT);
                }
            }
            if (parts[P_FONT] && look.has("font")) {
                applyFont(context, look.getString("font"));
            }
            if (parts[P_CLOCK] && look.has("clock_look")) {
                putSystem(context, CLOCK_LOOK, look.getInt("clock_look"));
            }
            if (parts[P_CLOCK] && look.has("clock_chip")) {
                putSystem(context, CLOCK_CHIP, look.getInt("clock_chip"));
            }
            if (parts[P_EDGE] && look.has("edge")) {
                String edge = look.getString("edge");
                if ("accent".equals(edge)) {
                    Settings.System.putStringForUser(context.getContentResolver(), EDGE_MODE,
                            "accent", UserHandle.USER_CURRENT);
                } else {
                    Settings.System.putStringForUser(context.getContentResolver(), EDGE_MODE,
                            "custom", UserHandle.USER_CURRENT);
                    putSystem(context, EDGE_COLOR, color(edge, Color.WHITE));
                }
            }
            if (parts[P_EDGE] && look.has("edge_style")) {
                Settings.System.putStringForUser(context.getContentResolver(), EDGE_STYLE,
                        look.getString("edge_style"), UserHandle.USER_CURRENT);
            }
            if (parts[P_EDGE] && look.has("edge_moments")) {
                JSONObject moments = look.getJSONObject("edge_moments");
                for (String moment : EDGE_MOMENTS) {
                    String value = moments.optString(moment);
                    putSystem(context, "edge_light_moment_" + moment + "_color",
                            value.startsWith("#") ? color(value, 0) : 0);
                }
            }
            if (parts[P_HAPTICS] && look.has("haptic_patterns")) {
                Settings.System.putStringForUser(context.getContentResolver(), HAPTIC_PATTERNS,
                        look.getJSONArray("haptic_patterns").toString(), UserHandle.USER_CURRENT);
                Settings.System.putStringForUser(context.getContentResolver(), HAPTIC_ASSIGN,
                        look.optJSONObject("haptic_assign") != null
                                ? look.getJSONObject("haptic_assign").toString() : "{}",
                        UserHandle.USER_CURRENT);
            }
            Settings.System.putStringForUser(context.getContentResolver(), CURRENT,
                    look.optString("name"), UserHandle.USER_CURRENT);
            if (parts[P_BOOT] && look.has("boot")) {
                SystemProperties.set(BOOT_PROP, String.valueOf(look.getInt("boot")));
            }
            if (parts[P_WALLPAPER] && look.has("wallpaper")) {
                String wallpaper = look.getString("wallpaper");
                Context app = context.getApplicationContext();
                new Thread(() -> applyWallpaper(app, wallpaper), "LooksWallpaper").start();
            }
        } catch (Exception e) {
            ok = false;
        }
        if (restart) Utils.restartSystemUI();
        return ok;
    }

    /** A look names its font by package, or by part of it in older looks. */
    static String fontPackage(Context context, String font) {
        if (FontController.CATEGORY.equals(font) || "android".equals(font)) return "android";
        for (OverlayInfo info : new FontController(context).fonts()) {
            if (info.packageName.equals(font)) return info.packageName;
        }
        for (OverlayInfo info : new FontController(context).fonts()) {
            if (info.packageName.toLowerCase().endsWith("." + font.toLowerCase())) {
                return info.packageName;
            }
        }
        return font;
    }

    private static void applyFont(Context context, String font) throws Exception {
        String pkg = fontPackage(context, font);
        FontController fonts = new FontController(context);
        if ("android".equals(pkg)) {
            fonts.apply("android");
            return;
        }
        for (OverlayInfo info : fonts.fonts()) {
            if (info.packageName.equals(pkg)) {
                fonts.apply(pkg);
                return;
            }
        }
    }

    private static void applyWallpaper(Context context, String value) {
        WallpaperManager wallpapers = WallpaperManager.getInstance(context);
        try {
            int res = context.getResources().getIdentifier(value, "drawable",
                    context.getPackageName());
            if (res == 0) return;
            try (InputStream in = context.getResources().openRawResource(res)) {
                int id = wallpapers.setStream(in, null, true,
                        WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
                Settings.System.putStringForUser(context.getContentResolver(), WALLPAPER, value,
                        UserHandle.USER_CURRENT);
                Settings.System.putIntForUser(context.getContentResolver(), WALLPAPER_ID, id,
                        UserHandle.USER_CURRENT);
            }
        } catch (Exception e) {
            // The rest of the look still applies.
        }
    }

    static int faceIndex(Context context, String name) {
        String[] names = context.getResources().getStringArray(R.array.clock_face_names);
        if ("System".equals(name)) return 0;
        for (int i = 0; i < names.length; i++) {
            if (names[i].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    static int color(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void putSystem(Context context, String key, int value) {
        Settings.System.putIntForUser(context.getContentResolver(), key, value,
                UserHandle.USER_CURRENT);
    }
}
