/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.om.OverlayInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.widget.NestedScrollView;

import com.android.internal.util.rr.Utils;
import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Remix Looks: accent, QS style, clock face, font, status bar clock look and edge lighting saved
 * together. RR ships four; yours are kept in rr_looks and can be shared as text or a file.
 */
public class LooksActivity extends CollapsingToolbarBaseActivity {
    private static final String KEY_LOOKS = "rr_looks";
    private static final String PREFIX = "rrlook:";
    private static final String QS_STYLE = "rr_qs_style";
    private static final String CLOCK_LOOK = "status_bar_clock_rr_style";
    private static final String CLOCK_FACE = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE;

    private Context mThemed;
    private LinearLayout mList;
    private ActivityResultLauncher<String[]> mOpener;

    private static JSONObject look(String name, String accent, int qs, String face, String font,
            int clockLook, String edge) {
        try {
            JSONObject o = new JSONObject().put("name", name).put("accent", accent)
                    .put("qs_style", qs).put("clock_face", face).put("clock_look", clockLook)
                    .put("edge", edge);
            if (font != null) o.put("font", font);
            return o;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<JSONObject> builtIn() {
        List<JSONObject> looks = new ArrayList<>();
        looks.add(look("Crimson classic", "#FF003F", 1, "System", null, 0, "accent"));
        looks.add(look("Neon night", "#FF3B5C", 3, "Big 3", "exotwo", 1, "#FF3B5C"));
        looks.add(look("Glass", "#7AA7FF", 3, "Simple", null, 0, "accent"));
        looks.add(look("Mono", "#9E9E9E", 7, "Text", null, 4, "#FFFFFF"));
        return looks;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.looks_title);
        mThemed = new ContextThemeWrapper(this, R.style.Theme_Material3_DynamicColors_DayNight);
        NestedScrollView scroll = new NestedScrollView(mThemed);
        mList = new LinearLayout(mThemed);
        mList.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        mList.setPadding(pad, 0, pad, dp(24));
        scroll.addView(mList);
        setContentView(scroll);
        mOpener = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) importFrom(read(uri));
        });
        build();
    }

    private void build() {
        mList.removeAllViews();
        section(R.string.looks_rr);
        grid(builtIn(), false);
        section(R.string.looks_yours);
        List<JSONObject> mine = mine();
        grid(mine, true);
        LinearLayout actions = new LinearLayout(mThemed);
        actions.addView(button(R.string.looks_save, v -> saveCurrent()));
        actions.addView(button(R.string.looks_import, v -> chooseImport()));
        mList.addView(actions);
    }

    private void section(int title) {
        TextView text = new TextView(mThemed);
        text.setText(title);
        text.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        text.setPadding(dp(4), dp(24), dp(4), dp(8));
        mList.addView(text);
    }

    private void grid(List<JSONObject> looks, boolean mine) {
        GridLayout grid = new GridLayout(mThemed);
        grid.setColumnCount(2);
        for (JSONObject look : looks) {
            MaterialCardView card = new MaterialCardView(mThemed);
            card.setRadius(dp(18));
            LinearLayout box = new LinearLayout(mThemed);
            box.setOrientation(LinearLayout.VERTICAL);
            FrameLayout preview = new FrameLayout(mThemed);
            int accent = Color.parseColor(look.optString("accent", "#FF003F"));
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
            if (look.optInt("clock_look") == 1) time.setShadowLayer(dp(6), 0, 0, accent);
            if (look.optInt("clock_look") == 4) time.setTypeface(Typeface.MONOSPACE);
            preview.addView(time, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            box.addView(preview, new LinearLayout.LayoutParams(-1, dp(84)));
            TextView name = new TextView(mThemed);
            name.setText(look.optString("name"));
            name.setPadding(dp(12), dp(10), dp(12), dp(12));
            name.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            box.addView(name);
            card.addView(box);
            card.setOnClickListener(v -> open(look, mine));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(card, lp);
        }
        if (looks.isEmpty()) {
            TextView none = new TextView(mThemed);
            none.setText(R.string.looks_none);
            none.setPadding(dp(4), 0, dp(4), dp(8));
            mList.addView(none);
            return;
        }
        mList.addView(grid);
    }

    private View button(int label, View.OnClickListener onClick) {
        com.google.android.material.button.MaterialButton button =
                new com.google.android.material.button.MaterialButton(mThemed);
        button.setText(label);
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(4), dp(12), dp(4), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private void open(JSONObject look, boolean mine) {
        String[] parts = {getString(R.string.looks_part_accent), getString(R.string.looks_part_qs),
                getString(R.string.looks_part_clock_face), getString(R.string.looks_part_font),
                getString(R.string.looks_part_clock_look), getString(R.string.looks_part_edge)};
        boolean[] chosen = {true, true, true, look.has("font"), true, true};
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(mThemed)
                .setTitle(look.optString("name"))
                .setMultiChoiceItems(parts, chosen, (d, which, checked) -> chosen[which] = checked)
                .setPositiveButton(R.string.looks_apply, (d, w) -> apply(look, chosen))
                .setNeutralButton(R.string.looks_share, (d, w) -> share(look));
        if (mine) {
            dialog.setNegativeButton(R.string.looks_delete, (d, w) -> delete(look));
        }
        dialog.show();
    }

    private void apply(JSONObject look, boolean[] parts) {
        try {
            if (parts[0] && look.has("accent")) {
                new AccentController(this).apply(look.getString("accent"));
            }
            if (parts[1] && look.has("qs_style")) {
                putSystem(QS_STYLE, look.getInt("qs_style"));
            }
            boolean restart = false;
            if (parts[2] && look.has("clock_face")) {
                int face = faceIndex(look.getString("clock_face"));
                int old = Settings.Secure.getIntForUser(getContentResolver(), CLOCK_FACE, 0,
                        UserHandle.USER_CURRENT);
                if (face >= 0 && face != old) {
                    Settings.Secure.putIntForUser(getContentResolver(), CLOCK_FACE, face,
                            UserHandle.USER_CURRENT);
                    restart = (face == 0) != (old == 0);
                }
            }
            if (parts[3] && look.has("font")) {
                applyFont(look.getString("font"));
            }
            if (parts[4] && look.has("clock_look")) {
                putSystem(CLOCK_LOOK, look.getInt("clock_look"));
            }
            if (parts[5] && look.has("edge")) {
                String edge = look.getString("edge");
                if ("accent".equals(edge)) {
                    Settings.System.putStringForUser(getContentResolver(),
                            "edge_light_color_mode", "accent", UserHandle.USER_CURRENT);
                } else {
                    Settings.System.putStringForUser(getContentResolver(),
                            "edge_light_color_mode", "custom", UserHandle.USER_CURRENT);
                    putSystem("edge_light_custom_color", Color.parseColor(edge));
                }
            }
            Toast.makeText(this, getString(R.string.looks_applied, look.optString("name")),
                    Toast.LENGTH_SHORT).show();
            if (restart) Utils.restartSystemUI();
        } catch (Exception e) {
            Toast.makeText(this, R.string.looks_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void applyFont(String font) throws Exception {
        FontController fonts = new FontController(this);
        for (OverlayInfo info : fonts.fonts()) {
            if (info.packageName.equals(font) || info.packageName.toLowerCase().contains(font)) {
                fonts.apply(info.packageName);
                return;
            }
        }
    }

    private int faceIndex(String name) {
        String[] names = getResources().getStringArray(R.array.clock_face_names);
        if ("System".equals(name)) return 0;
        for (int i = 0; i < names.length; i++) {
            if (names[i].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private void putSystem(String key, int value) {
        Settings.System.putIntForUser(getContentResolver(), key, value, UserHandle.USER_CURRENT);
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
                if (accent != null) look.put("accent", "#" + accent);
            }
            look.put("qs_style", Settings.System.getIntForUser(getContentResolver(), QS_STYLE, 0,
                    UserHandle.USER_CURRENT));
            int face = Settings.Secure.getIntForUser(getContentResolver(), CLOCK_FACE, 0,
                    UserHandle.USER_CURRENT);
            String[] names = getResources().getStringArray(R.array.clock_face_names);
            look.put("clock_face", face == 0 || face >= names.length ? "System" : names[face]);
            String font = new FontController(this).enabledPackage();
            if (font != null) look.put("font", font);
            look.put("clock_look", Settings.System.getIntForUser(getContentResolver(), CLOCK_LOOK,
                    0, UserHandle.USER_CURRENT));
            String mode = Settings.System.getStringForUser(getContentResolver(),
                    "edge_light_color_mode", UserHandle.USER_CURRENT);
            if ("custom".equals(mode)) {
                int color = Settings.System.getIntForUser(getContentResolver(),
                        "edge_light_custom_color", Color.WHITE, UserHandle.USER_CURRENT);
                look.put("edge", String.format("#%06X", color & 0xFFFFFF));
            } else {
                look.put("edge", "accent");
            }
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

    private void share(JSONObject look) {
        String text = PREFIX + Base64.encodeToString(look.toString().getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP);
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .putExtra(Intent.EXTRA_SUBJECT, look.optString("name"));
        startActivity(Intent.createChooser(send, look.optString("name")));
    }

    private void chooseImport() {
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(R.string.looks_import)
                .setItems(new CharSequence[] {getString(R.string.looks_import_clipboard),
                        getString(R.string.looks_import_file)}, (d, which) -> {
                            if (which == 0) {
                                ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                                CharSequence text = clipboard != null && clipboard.hasPrimaryClip()
                                        ? clipboard.getPrimaryClip().getItemAt(0).coerceToText(this)
                                        : null;
                                importFrom(text != null ? text.toString() : null);
                            } else {
                                mOpener.launch(new String[] {"*/*"});
                            }
                        })
                .show();
    }

    private String read(Uri uri) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && text.length() < 100_000) text.append(line);
            return text.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void importFrom(String text) {
        try {
            String json = text.trim();
            if (json.startsWith(PREFIX)) {
                json = new String(Base64.decode(json.substring(PREFIX.length()), Base64.DEFAULT),
                        StandardCharsets.UTF_8);
            }
            JSONObject look = new JSONObject(json);
            if (!look.has("name")) throw new IllegalArgumentException();
            List<JSONObject> looks = mine();
            looks.add(look);
            store(looks);
            open(look, true);
        } catch (Exception e) {
            Toast.makeText(this, R.string.looks_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
