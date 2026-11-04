/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings;

import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.om.OverlayInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.FileProvider;
import androidx.core.widget.NestedScrollView;

import com.android.internal.util.rr.Utils;
import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.qrcode.QrCodeGenerator;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Remix Looks: accent, QS style, clock face, font, status bar clock, edge lighting, wallpaper and
 * boot animation saved together. RR's own looks come from /product/etc/rr_looks; yours are kept
 * in rr_looks and travel as a QR code or a small .rrlook file holding the same text.
 */
public class LooksActivity extends CollapsingToolbarBaseActivity {
    private static final String KEY_LOOKS = "rr_looks";
    static final String PREFIX = "rrlook:";
    private static final String LOOKS_DIR = "/product/etc/rr_looks";
    private static final String QS_STYLE = Looks.QS_STYLE;
    private static final String CLOCK_LOOK = Looks.CLOCK_LOOK;
    private static final String CLOCK_CHIP = Looks.CLOCK_CHIP;
    private static final String CLOCK_FACE = Looks.CLOCK_FACE;
    private static final String CLOCK_COLOR_MODE = Looks.CLOCK_COLOR_MODE;
    private static final String CLOCK_CUSTOM_COLOR = Looks.CLOCK_CUSTOM_COLOR;
    private static final String EDGE_MODE = Looks.EDGE_MODE;
    private static final String EDGE_COLOR = Looks.EDGE_COLOR;
    private static final String EDGE_STYLE = Looks.EDGE_STYLE;
    /** Written by the wallpaper page: which RR wallpaper is set, and its wallpaper id. */
    static final String WALLPAPER = Looks.WALLPAPER;
    static final String WALLPAPER_ID = Looks.WALLPAPER_ID;
    private static final String BOOT_PROP = Looks.BOOT_PROP;

    private static final int P_ACCENT = 0;
    private static final int P_QS = 1;
    private static final int P_FACE = 2;
    private static final int P_FONT = 3;
    private static final int P_CLOCK = 4;
    private static final int P_EDGE = 5;
    private static final int P_WALLPAPER = 6;
    private static final int P_BOOT = 7;
    private static final int[] PART_LABELS = {R.string.looks_part_accent, R.string.looks_part_qs,
            R.string.looks_part_clock_face, R.string.looks_part_font,
            R.string.looks_part_clock_look, R.string.looks_part_edge,
            R.string.looks_part_wallpaper, R.string.looks_part_boot};
    /** The look keys each part covers. */
    private static final String[][] PART_KEYS = {{"accent", "saturation"}, {"qs_style"},
            {"clock_face", "clock_color"}, {"font"}, {"clock_look", "clock_chip"},
            {"edge", "edge_style"}, {"wallpaper"}, {"boot"}};

    private Context mThemed;
    private LinearLayout mList;
    private ActivityResultLauncher<String[]> mOpener;
    private ActivityResultLauncher<String> mSaver;
    private ActivityResultLauncher<Intent> mScanner;
    private ActivityResultLauncher<Intent> mEditor;
    private JSONObject mSaving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.looks_title);
        mThemed = new ContextThemeWrapper(this,
                com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight);
        NestedScrollView scroll = new NestedScrollView(mThemed);
        mList = new LinearLayout(mThemed);
        mList.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        mList.setPadding(pad, 0, pad, dp(24));
        scroll.addView(mList);
        setContentView(scroll);
        mOpener = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) importFrom(read(uri), R.string.looks_from_file);
        });
        mSaver = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                    if (uri != null && mSaving != null) saveFile(uri, mSaving);
                    mSaving = null;
                });
        mScanner = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (data != null && data.getStringExtra(LookScanActivity.EXTRA_TEXT) != null) {
                        importFrom(data.getStringExtra(LookScanActivity.EXTRA_TEXT),
                                R.string.looks_from_qr);
                    }
                });
        mEditor = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        onEdited(result.getData());
                    }
                });
        build();
        Intent intent = getIntent();
        if (savedInstanceState == null && intent != null
                && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            importFrom(intent.getDataString(), R.string.looks_from_qr);
        }
    }

    private void build() {
        mList.removeAllViews();
        List<JSONObject> rr = builtIn();
        if (!rr.isEmpty()) {
            section(R.string.looks_rr);
            grid(rr, false);
        }
        section(R.string.looks_yours);
        grid(mine(), true);
        LinearLayout actions = new LinearLayout(mThemed);
        actions.addView(button(R.string.looks_create, v -> edit(current(""), -1)));
        actions.addView(button(R.string.looks_save_button, v -> saveCurrent()));
        actions.addView(button(R.string.looks_import, v -> chooseImport()));
        mList.addView(actions);
    }

    /** RR's looks, from vendor/extras/themes/looks. */
    private List<JSONObject> builtIn() {
        List<JSONObject> looks = new ArrayList<>();
        File[] files = new File(LOOKS_DIR).listFiles((dir, name) -> name.endsWith(".rrlook"));
        if (files == null) return looks;
        for (File file : files) {
            try (InputStream in = new java.io.FileInputStream(file)) {
                JSONObject look = parse(readAll(in));
                if (look != null) looks.add(look);
            } catch (Exception e) {
                // Skip a broken file.
            }
        }
        looks.sort((a, b) -> Integer.compare(a.optInt("order", 99), b.optInt("order", 99)));
        return looks;
    }

    private void section(int title) {
        TextView text = new TextView(mThemed);
        text.setText(title);
        text.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        text.setPadding(dp(4), dp(24), dp(4), dp(8));
        mList.addView(text);
    }

    private void grid(List<JSONObject> looks, boolean mine) {
        if (looks.isEmpty()) {
            TextView none = new TextView(mThemed);
            none.setText(R.string.looks_none);
            none.setPadding(dp(4), 0, dp(4), dp(8));
            mList.addView(none);
            return;
        }
        GridLayout grid = new GridLayout(mThemed);
        grid.setColumnCount(2);
        for (JSONObject look : looks) {
            MaterialCardView card = new MaterialCardView(mThemed);
            card.setRadius(dp(18));
            LinearLayout box = new LinearLayout(mThemed);
            box.setOrientation(LinearLayout.VERTICAL);
            FrameLayout preview = new FrameLayout(mThemed);
            int accent = Looks.color(look.optString("accent", "#FF003F"), 0xFFFF003F);
            GradientDrawable bg = new GradientDrawable();
            bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            bg.setGradientCenter(0.5f, 1.2f);
            bg.setGradientRadius(dp(150));
            bg.setColors(new int[] {accent, 0xFF0A0506});
            preview.setBackground(bg);
            TextView time = new TextView(mThemed);
            time.setText("10:42");
            time.setTextColor(Color.WHITE);
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            int clockLook = look.optInt("clock_look");
            if (clockLook == 1) time.setShadowLayer(dp(6), 0, 0, accent);
            if (clockLook == 4) time.setTypeface(Typeface.MONOSPACE);
            preview.addView(time, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            box.addView(preview, new LinearLayout.LayoutParams(-1, dp(84)));
            TextView name = new TextView(mThemed);
            name.setText(look.optString("name"));
            name.setPadding(dp(12), dp(10), dp(12), dp(12));
            name.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            box.addView(name);
            card.addView(box);
            card.setOnClickListener(v -> open(look, mine));
            if (mine) {
                card.setOnLongClickListener(v -> {
                    confirmDelete(look);
                    return true;
                });
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(card, lp);
        }
        mList.addView(grid);
    }

    private View button(int label, View.OnClickListener onClick) {
        com.google.android.material.button.MaterialButton button =
                new com.google.android.material.button.MaterialButton(mThemed);
        button.setText(label);
        button.setSingleLine(true);
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(4), dp(12), dp(4), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private static boolean has(JSONObject look, int part) {
        for (String key : PART_KEYS[part]) {
            if (look.has(key)) return true;
        }
        return false;
    }

    /** Every part the look sets, with its value; untick what you'd rather keep. */
    private void open(JSONObject look, boolean mine) {
        List<Integer> parts = new ArrayList<>();
        for (int part = 0; part < PART_LABELS.length; part++) {
            if (has(look, part)) parts.add(part);
        }
        CharSequence[] items = new CharSequence[parts.size()];
        boolean[] chosen = new boolean[parts.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = getString(PART_LABELS[parts.get(i)]) + " · " + describe(look, parts.get(i));
            chosen[i] = true;
        }
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(mThemed)
                .setTitle(look.optString("name"))
                .setMultiChoiceItems(items, chosen, (d, which, checked) -> chosen[which] = checked)
                .setPositiveButton(R.string.looks_apply, (d, w) -> {
                    boolean[] apply = new boolean[PART_LABELS.length];
                    for (int i = 0; i < chosen.length; i++) apply[parts.get(i)] = chosen[i];
                    apply(look, apply);
                })
                .setNeutralButton(R.string.looks_share, (d, w) -> share(look));
        if (mine) {
            dialog.setNegativeButton(R.string.looks_edit, (d, w) -> edit(look, indexOf(look)));
        } else {
            // RR's looks are a starting point for your own.
            dialog.setNegativeButton(R.string.looks_customize, (d, w) -> {
                JSONObject copy = copy(look);
                copy.remove("order");
                edit(copy, -1);
            });
        }
        dialog.show();
    }

    private String describe(JSONObject look, int part) {
        StringBuilder text = new StringBuilder();
        switch (part) {
            case P_ACCENT:
                if (look.has("accent")) text.append(look.optString("accent").toUpperCase());
                if (look.has("saturation") && look.optInt("saturation") != 100) {
                    join(text, getString(R.string.looks_saturation, look.optInt("saturation")));
                }
                break;
            case P_QS: {
                int qs = look.optInt("qs_style");
                text.append(QsStyleActivity.isStyle(qs)
                        ? getString(QsStyleActivity.NAMES[qs]) : String.valueOf(qs));
                break;
            }
            case P_FACE:
                if (look.has("clock_face")) text.append(look.optString("clock_face"));
                if (look.has("clock_color")) join(text, colorName(look.optString("clock_color")));
                break;
            case P_FONT:
                text.append(new FontController(this).label(Looks.fontPackage(this, look.optString("font"))));
                break;
            case P_CLOCK:
                if (look.has("clock_look")) {
                    text.append(entry(R.array.status_bar_clock_rr_style_entries,
                            look.optInt("clock_look")));
                }
                if (look.optInt("clock_chip") > 0) {
                    join(text, entry(R.array.sb_clock_chip_entries, look.optInt("clock_chip")));
                }
                break;
            case P_EDGE:
                if (look.has("edge")) text.append(colorName(look.optString("edge")));
                if (look.has("edge_style")) {
                    join(text, getString("rounded".equals(look.optString("edge_style"))
                            ? R.string.edge_light_style_frame : R.string.edge_light_style_default));
                }
                break;
            case P_WALLPAPER:
                text.append(wallpaperName(look.optString("wallpaper")));
                break;
            case P_BOOT:
                text.append(entry(R.array.boot_animation_names, look.optInt("boot")));
                break;
        }
        return text.toString();
    }

    private static void join(StringBuilder text, String more) {
        if (text.length() > 0) text.append(" · ");
        text.append(more);
    }

    private String entry(int array, int index) {
        String[] entries = getResources().getStringArray(array);
        return index >= 0 && index < entries.length ? entries[index] : String.valueOf(index);
    }

    private String colorName(String value) {
        if ("accent".equals(value)) return getString(R.string.looks_color_accent);
        if ("default".equals(value)) return getString(R.string.looks_color_default);
        return value.toUpperCase();
    }

    private String wallpaperName(String value) {
        return wallpaperName(this, value);
    }

    /** "RR wallpaper 3" for the third of RR's own. */
    static String wallpaperName(Context context, String value) {
        String[] names = context.getResources().getStringArray(
                com.rr.wallpapers.R.array.partner_wallpapers);
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(value)) return context.getString(R.string.looks_rr_wallpaper, i + 1);
        }
        return value;
    }

    private void apply(JSONObject look, boolean[] parts) {
        boolean ok = Looks.apply(this, look, parts);
        Toast.makeText(this, ok ? getString(R.string.looks_applied, look.optString("name"))
                : getString(R.string.looks_failed), Toast.LENGTH_SHORT).show();
    }

    /** The RR wallpaper that's set now, if it's one: your own photos stay out of looks. */
    private String currentWallpaper() {
        WallpaperManager wallpapers = WallpaperManager.getInstance(this);
        if (wallpapers.getWallpaperInfo() != null) return null;
        String name = Settings.System.getStringForUser(getContentResolver(), WALLPAPER,
                UserHandle.USER_CURRENT);
        int id = Settings.System.getIntForUser(getContentResolver(), WALLPAPER_ID, -1,
                UserHandle.USER_CURRENT);
        return name != null && id >= 0 && id == wallpapers.getWallpaperId(
                WallpaperManager.FLAG_SYSTEM) ? name : null;
    }

    private List<JSONObject> mine() {
        List<JSONObject> looks = new ArrayList<>();
        try {
            String json = Settings.System.getStringForUser(getContentResolver(), KEY_LOOKS,
                    UserHandle.USER_CURRENT);
            JSONArray array = new JSONArray(json != null ? json : "[]");
            for (int i = 0; i < array.length(); i++) looks.add(array.getJSONObject(i));
        } catch (Exception e) {
            // Start over.
        }
        return looks;
    }

    private void store(List<JSONObject> looks) {
        JSONArray array = new JSONArray();
        for (JSONObject look : looks) array.put(look);
        Settings.System.putStringForUser(getContentResolver(), KEY_LOOKS, array.toString(),
                UserHandle.USER_CURRENT);
        build();
    }

    private void saveCurrent() {
        EditText name = new EditText(mThemed);
        name.setHint(R.string.looks_name);
        FrameLayout box = new FrameLayout(mThemed);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(name);
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(R.string.looks_save)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String label = name.getText().toString().trim();
                    List<JSONObject> looks = mine();
                    looks.add(current(label.isEmpty() ? getString(R.string.looks_mine) : label));
                    store(looks);
                })
                .show();
    }

    private JSONObject current(String name) {
        try {
            JSONObject look = new JSONObject().put("name", name);
            String theme = Settings.Secure.getStringForUser(getContentResolver(),
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, UserHandle.USER_CURRENT);
            if (theme != null) {
                JSONObject json = new JSONObject(theme);
                String accent = json.optString("android.theme.customization.accent_color", null);
                if (accent != null) {
                    look.put("accent", accent.startsWith("#") ? accent : "#" + accent);
                }
            }
            look.put("saturation", new AccentController(this).saturation());
            look.put("qs_style", Settings.System.getIntForUser(getContentResolver(), QS_STYLE, 0,
                    UserHandle.USER_CURRENT));
            int face = Settings.Secure.getIntForUser(getContentResolver(), CLOCK_FACE, 0,
                    UserHandle.USER_CURRENT);
            String[] names = getResources().getStringArray(R.array.clock_face_names);
            look.put("clock_face", face == 0 || face >= names.length ? "System" : names[face]);
            String mode = Settings.Secure.getStringForUser(getContentResolver(), CLOCK_COLOR_MODE,
                    UserHandle.USER_CURRENT);
            if ("custom".equals(mode)) {
                look.put("clock_color", String.format("#%06X", Settings.Secure.getIntForUser(
                        getContentResolver(), CLOCK_CUSTOM_COLOR, Color.WHITE,
                        UserHandle.USER_CURRENT) & 0xFFFFFF));
            } else {
                look.put("clock_color", mode != null ? mode : "default");
            }
            look.put("font", new FontController(this).enabledPackage());
            look.put("clock_look", Settings.System.getIntForUser(getContentResolver(), CLOCK_LOOK,
                    0, UserHandle.USER_CURRENT));
            look.put("clock_chip", Settings.System.getIntForUser(getContentResolver(), CLOCK_CHIP,
                    0, UserHandle.USER_CURRENT));
            String edgeMode = Settings.System.getStringForUser(getContentResolver(), EDGE_MODE,
                    UserHandle.USER_CURRENT);
            if ("custom".equals(edgeMode)) {
                int color = Settings.System.getIntForUser(getContentResolver(), EDGE_COLOR,
                        Color.WHITE, UserHandle.USER_CURRENT);
                look.put("edge", String.format("#%06X", color & 0xFFFFFF));
            } else {
                look.put("edge", "accent");
            }
            String edgeStyle = Settings.System.getStringForUser(getContentResolver(), EDGE_STYLE,
                    UserHandle.USER_CURRENT);
            look.put("edge_style", edgeStyle != null ? edgeStyle : "default");
            String wallpaper = currentWallpaper();
            if (wallpaper != null) look.put("wallpaper", wallpaper);
            int boot = SystemProperties.getInt(BOOT_PROP, 0);
            String[] boots = getResources().getStringArray(R.array.boot_animation_names);
            if (boot >= 0 && boot < boots.length - 1) look.put("boot", boot);
            return look;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void delete(JSONObject look) {
        List<JSONObject> looks = mine();
        looks.removeIf(l -> l.toString().equals(look.toString()));
        store(looks);
    }

    private void confirmDelete(JSONObject look) {
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(getString(R.string.looks_delete_confirm, look.optString("name")))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.looks_delete, (d, w) -> delete(look))
                .show();
    }

    private int indexOf(JSONObject look) {
        List<JSONObject> looks = mine();
        for (int i = 0; i < looks.size(); i++) {
            if (looks.get(i).toString().equals(look.toString())) return i;
        }
        return -1;
    }

    private static JSONObject copy(JSONObject look) {
        try {
            return new JSONObject(look.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Opens the editor on [look]; [index] is its place in your looks, -1 for a new one. */
    private void edit(JSONObject look, int index) {
        if (index < 0 && look.optString("name").isEmpty()) {
            try {
                look.put("name", getString(R.string.looks_mine));
            } catch (Exception ignored) {
                // Unnamed; the editor names it.
            }
        }
        mEditor.launch(new Intent(this, LookEditorActivity.class)
                .putExtra(LookEditorActivity.EXTRA_LOOK, look.toString())
                .putExtra(LookEditorActivity.EXTRA_INDEX, index));
    }

    private void onEdited(Intent data) {
        JSONObject look = parse(data.getStringExtra(LookEditorActivity.EXTRA_LOOK));
        int index = data.getIntExtra(LookEditorActivity.EXTRA_INDEX, -1);
        List<JSONObject> looks = mine();
        boolean known = index >= 0 && index < looks.size();
        if (data.getBooleanExtra(LookEditorActivity.EXTRA_DELETE, false)) {
            if (known) looks.remove(index);
            store(looks);
            return;
        }
        if (look == null) return;
        if (known) looks.set(index, look);
        else looks.add(look);
        store(looks);
        if (data.getBooleanExtra(LookEditorActivity.EXTRA_APPLY, false)) {
            boolean[] all = new boolean[PART_LABELS.length];
            java.util.Arrays.fill(all, true);
            apply(look, all);
        } else {
            Toast.makeText(this, getString(R.string.looks_saved, look.optString("name")),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static String encode(JSONObject look) {
        JSONObject copy;
        try {
            copy = new JSONObject(look.toString());
        } catch (Exception e) {
            copy = look;
        }
        copy.remove("order");
        return PREFIX + Base64.encodeToString(copy.toString().getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    /** As a QR code or a small .rrlook file; scanning it opens the import screen. */
    private void share(JSONObject look) {
        String text = encode(look);
        LinearLayout box = new LinearLayout(mThemed);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        try {
            int size = dp(220);
            Bitmap qr = QrCodeGenerator.encodeQrCode(text, size);
            ImageView image = new ImageView(mThemed);
            image.setImageBitmap(qr);
            GradientDrawable card = new GradientDrawable();
            card.setColor(Color.WHITE);
            card.setCornerRadius(dp(16));
            image.setBackground(card);
            image.setPadding(dp(8), dp(8), dp(8), dp(8));
            box.addView(image, new LinearLayout.LayoutParams(size + dp(16), size + dp(16)));
        } catch (Exception e) {
            // Too long for a QR code; the file still works.
        }
        TextView hint = new TextView(mThemed);
        hint.setText(R.string.looks_share_hint);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(12), 0, 0);
        box.addView(hint);
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(getString(R.string.looks_share_title, look.optString("name")))
                .setView(box)
                .setPositiveButton(R.string.looks_share_file, (d, w) -> shareFile(look, text))
                .setNeutralButton(R.string.looks_save_file, (d, w) -> {
                    mSaving = look;
                    mSaver.launch(fileName(look));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String fileName(JSONObject look) {
        String name = look.optString("name", "look").replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return (name.isEmpty() ? "look" : name) + ".rrlook";
    }

    private void saveFile(Uri uri, JSONObject look) {
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            out.write((encode(look) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Toast.makeText(this, R.string.looks_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(JSONObject look, String text) {
        try {
            File dir = new File(getCacheDir(), "looks");
            dir.mkdirs();
            File file = new File(dir, fileName(look));
            try (OutputStream out = new FileOutputStream(file)) {
                out.write((text + "\n").getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .putExtra(Intent.EXTRA_SUBJECT, look.optString("name"))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri(null, uri));
            startActivity(Intent.createChooser(send, look.optString("name")));
        } catch (Exception e) {
            Toast.makeText(this, R.string.looks_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseImport() {
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(R.string.looks_import)
                .setItems(new CharSequence[] {getString(R.string.looks_import_scan),
                        getString(R.string.looks_import_file),
                        getString(R.string.looks_import_clipboard)}, (d, which) -> {
                            if (which == 0) {
                                mScanner.launch(new Intent(this, LookScanActivity.class));
                            } else if (which == 1) {
                                mOpener.launch(new String[] {"*/*"});
                            } else {
                                ClipboardManager clipboard =
                                        getSystemService(ClipboardManager.class);
                                CharSequence text = clipboard != null && clipboard.hasPrimaryClip()
                                        ? clipboard.getPrimaryClip().getItemAt(0)
                                                .coerceToText(this)
                                        : null;
                                importFrom(text != null ? text.toString() : null,
                                        R.string.looks_from_clipboard);
                            }
                        })
                .show();
    }

    private String read(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return readAll(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && text.length() < 100_000) {
                text.append(line);
            }
            return text.toString();
        }
    }

    /** A look from rrlook: text or plain JSON, or null. */
    static JSONObject parse(String text) {
        if (text == null) return null;
        try {
            String json = text.trim();
            if (json.startsWith(PREFIX)) {
                String data = json.substring(PREFIX.length());
                byte[] bytes;
                try {
                    bytes = Base64.decode(data, Base64.URL_SAFE);
                } catch (IllegalArgumentException e) {
                    bytes = Base64.decode(data, Base64.DEFAULT);
                }
                json = new String(bytes, StandardCharsets.UTF_8);
            }
            JSONObject look = new JSONObject(json);
            return look.has("name") ? look : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Shows exactly what will change before anything is applied. */
    private void importFrom(String text, int source) {
        JSONObject look = parse(text);
        if (look == null) {
            Toast.makeText(this, R.string.looks_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        JSONObject now = current("");
        List<String> changes = new ArrayList<>();
        boolean[] parts = new boolean[PART_LABELS.length];
        for (int part = 0; part < PART_LABELS.length; part++) {
            if (!has(look, part)) continue;
            parts[part] = true;
            String before = describe(now, part);
            String after = describe(look, part);
            if (!differs(look, now, part)) continue;
            changes.add(getString(PART_LABELS[part]) + " · " + before + " → " + after);
        }
        StringBuilder message = new StringBuilder(getString(R.string.looks_import_from,
                look.optString("name"), getString(source)));
        message.append("\n\n");
        if (changes.isEmpty()) {
            message.append(getString(R.string.looks_import_same));
        } else {
            message.append(getResources().getQuantityString(R.plurals.looks_import_changes,
                    changes.size(), changes.size()));
            for (String change : changes) message.append("\n• ").append(change);
        }
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(R.string.looks_import_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.looks_apply, (d, w) -> {
                    List<JSONObject> looks = mine();
                    String key = look.toString();
                    if (looks.stream().noneMatch(l -> l.toString().equals(key))) {
                        looks.add(look);
                        store(looks);
                    }
                    apply(look, parts);
                })
                .show();
    }

    private boolean differs(JSONObject look, JSONObject now, int part) {
        if (part == P_FONT) {
            return !Looks.fontPackage(this, look.optString("font")).equals(now.optString("font"));
        }
        for (String key : PART_KEYS[part]) {
            if (!look.has(key)) continue;
            if (!look.optString(key).equalsIgnoreCase(now.optString(key))) return true;
        }
        return false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
