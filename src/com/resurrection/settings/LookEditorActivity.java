/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings;

import android.content.Context;
import android.content.Intent;
import android.content.om.OverlayInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.widget.NestedScrollView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a look part by part: each row has a switch for whether the look sets it and a picker for
 * its value. Hands the look back to {@link LooksActivity}, which saves and applies it.
 */
public class LookEditorActivity extends CollapsingToolbarBaseActivity {
    static final String EXTRA_LOOK = "look";
    /** Position in your looks, or -1 for a new one. */
    static final String EXTRA_INDEX = "index";
    static final String EXTRA_APPLY = "apply";
    static final String EXTRA_DELETE = "delete";

    private static final int ROW_ACCENT = 0;
    private static final int ROW_SATURATION = 1;
    private static final int ROW_QS = 2;
    private static final int ROW_FACE = 3;
    private static final int ROW_CLOCK_COLOR = 4;
    private static final int ROW_FONT = 5;
    private static final int ROW_CLOCK_LOOK = 6;
    private static final int ROW_CHIP = 7;
    private static final int ROW_EDGE = 8;
    private static final int ROW_EDGE_STYLE = 9;
    private static final int ROW_WALLPAPER = 10;
    private static final int ROW_BOOT = 11;
    /** The look key each row sets. */
    private static final String[] KEYS = {"accent", "saturation", "qs_style", "clock_face",
            "clock_color", "font", "clock_look", "clock_chip", "edge", "edge_style", "wallpaper",
            "boot"};
    private static final int[] TITLES = {R.string.looks_part_accent,
            R.string.looks_part_saturation, R.string.looks_part_qs, R.string.looks_part_clock_face,
            R.string.looks_part_clock_color, R.string.looks_part_font,
            R.string.looks_part_clock_look, R.string.looks_part_clock_chip,
            R.string.looks_part_edge, R.string.looks_part_edge_style,
            R.string.looks_part_wallpaper, R.string.looks_part_boot};
    /** Shown for a row the look doesn't set yet, and used once it's switched on. */
    private static final Object[] DEFAULTS = {"#FF003F", 100, 1, "System", "default", "android",
            0, 0, "accent", "default", "rr", 0};

    private Context mThemed;
    private JSONObject mLook;
    private int mIndex;
    private EditText mName;
    private FrameLayout mPreview;
    private TextView mPreviewTime;
    private final TextView[] mValues = new TextView[KEYS.length];
    private final MaterialSwitch[] mSwitches = new MaterialSwitch[KEYS.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mThemed = new ContextThemeWrapper(this,
                com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight);
        mIndex = getIntent().getIntExtra(EXTRA_INDEX, -1);
        String json = savedInstanceState != null ? savedInstanceState.getString(EXTRA_LOOK)
                : getIntent().getStringExtra(EXTRA_LOOK);
        try {
            mLook = new JSONObject(json != null ? json : "{}");
        } catch (Exception e) {
            mLook = new JSONObject();
        }
        mLook.remove("order");
        setTitle(mIndex >= 0 ? R.string.looks_edit_title : R.string.looks_new);

        NestedScrollView scroll = new NestedScrollView(mThemed);
        LinearLayout list = new LinearLayout(mThemed);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(list);
        setContentView(scroll);

        list.addView(previewCard());

        mName = new EditText(mThemed);
        mName.setHint(R.string.looks_name);
        mName.setSingleLine(true);
        mName.setText(mLook.optString("name", ""));
        mName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                put("name", s.toString().trim());
            }
        });
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.topMargin = dp(12);
        list.addView(mName, nameLp);

        TextView section = new TextView(mThemed);
        section.setText(R.string.looks_in_look);
        section.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        section.setPadding(dp(4), dp(20), dp(4), dp(8));
        list.addView(section);

        for (int row = 0; row < KEYS.length; row++) list.addView(row(row));

        LinearLayout buttons = new LinearLayout(mThemed);
        buttons.setPadding(0, dp(16), 0, 0);
        MaterialButton save = new MaterialButton(mThemed, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        save.setText(R.string.looks_save_look);
        save.setOnClickListener(v -> finishWith(false, false));
        MaterialButton apply = new MaterialButton(mThemed);
        apply.setText(R.string.looks_save_apply);
        apply.setOnClickListener(v -> finishWith(true, false));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1f);
        half.setMargins(dp(4), 0, dp(4), 0);
        buttons.addView(save, half);
        buttons.addView(apply, new LinearLayout.LayoutParams(half));
        list.addView(buttons);

        if (mIndex >= 0) {
            MaterialButton delete = new MaterialButton(mThemed, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            delete.setText(R.string.looks_delete);
            delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(mThemed)
                    .setTitle(getString(R.string.looks_delete_confirm, name()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.looks_delete, (d, w) -> finishWith(false, true))
                    .show());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(8);
            list.addView(delete, lp);
        }
        updatePreview();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_LOOK, mLook.toString());
    }

    private String name() {
        String name = mLook.optString("name", "").trim();
        return name.isEmpty() ? getString(R.string.looks_mine) : name;
    }

    private void finishWith(boolean apply, boolean delete) {
        put("name", name());
        Intent data = new Intent()
                .putExtra(EXTRA_LOOK, mLook.toString())
                .putExtra(EXTRA_INDEX, mIndex)
                .putExtra(EXTRA_APPLY, apply)
                .putExtra(EXTRA_DELETE, delete);
        setResult(RESULT_OK, data);
        finish();
    }

    private void put(String key, Object value) {
        try {
            if (value == null) mLook.remove(key);
            else mLook.put(key, value);
        } catch (Exception e) {
            // Not a valid value; left as it was.
        }
    }

    private View previewCard() {
        MaterialCardView card = new MaterialCardView(mThemed);
        card.setRadius(dp(24));
        mPreview = new FrameLayout(mThemed);
        mPreviewTime = new TextView(mThemed);
        mPreviewTime.setText("10:42");
        mPreviewTime.setTextColor(Color.WHITE);
        mPreviewTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        mPreview.addView(mPreviewTime, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        card.addView(mPreview, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(150));
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private void updatePreview() {
        int accent = color(mLook.optString("accent", "#FF003F"), 0xFFFF003F);
        GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientCenter(0.5f, 1.2f);
        bg.setGradientRadius(dp(260));
        bg.setColors(new int[] {accent, 0xFF0A0506});
        mPreview.setBackground(bg);
        int clockLook = mLook.optInt("clock_look");
        mPreviewTime.setShadowLayer(clockLook == 1 ? dp(10) : 0, 0, 0, accent);
        mPreviewTime.setTypeface(clockLook == 4 ? Typeface.MONOSPACE : Typeface.DEFAULT);
    }

    private View row(int row) {
        LinearLayout box = new LinearLayout(mThemed);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(4), dp(10), dp(4), dp(10));
        TypedValue ripple = new TypedValue();
        mThemed.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        box.setBackgroundResource(ripple.resourceId);

        LinearLayout text = new LinearLayout(mThemed);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(mThemed);
        title.setText(TITLES[row]);
        title.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        TextView value = new TextView(mThemed);
        value.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        TypedValue secondary = new TypedValue();
        mThemed.getTheme().resolveAttribute(android.R.attr.textColorSecondary, secondary, true);
        if (secondary.resourceId != 0) {
            value.setTextColor(mThemed.getColorStateList(secondary.resourceId));
        }
        text.addView(title);
        text.addView(value);
        box.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialSwitch include = new MaterialSwitch(mThemed);
        include.setChecked(mLook.has(KEYS[row]));
        include.setOnCheckedChangeListener((b, checked) -> {
            if (checked == mLook.has(KEYS[row])) return;
            put(KEYS[row], checked ? current(row) : null);
            refresh(row);
        });
        box.addView(include);
        mValues[row] = value;
        mSwitches[row] = include;
        box.setOnClickListener(v -> pick(row));
        refresh(row);
        return box;
    }

    /** The row's value in the look, or what it would start at. */
    private Object current(int row) {
        Object value = mLook.opt(KEYS[row]);
        return value != null ? value : DEFAULTS[row];
    }

    private void set(int row, Object value) {
        put(KEYS[row], value);
        refresh(row);
    }

    private void refresh(int row) {
        boolean included = mLook.has(KEYS[row]);
        mSwitches[row].setChecked(included);
        mValues[row].setText(included ? label(row, current(row))
                : getString(R.string.looks_not_included));
        updatePreview();
    }

    private String label(int row, Object value) {
        switch (row) {
            case ROW_ACCENT:
                return accentName(String.valueOf(value));
            case ROW_SATURATION:
                return toInt(value) + "%";
            case ROW_QS: {
                int index = toInt(value);
                return QsStyleActivity.isStyle(index)
                        ? getString(QsStyleActivity.NAMES[index]) : String.valueOf(index);
            }
            case ROW_FACE:
                return String.valueOf(value);
            case ROW_CLOCK_COLOR:
            case ROW_EDGE:
                return colorName(String.valueOf(value));
            case ROW_FONT:
                return new FontController(this).label(String.valueOf(value));
            case ROW_CLOCK_LOOK:
                return entry(R.array.status_bar_clock_rr_style_entries, toInt(value));
            case ROW_CHIP:
                return entry(R.array.sb_clock_chip_entries, toInt(value));
            case ROW_EDGE_STYLE:
                return getString("rounded".equals(value) ? R.string.edge_light_style_frame
                        : R.string.edge_light_style_default);
            case ROW_WALLPAPER:
                return LooksActivity.wallpaperName(this, String.valueOf(value));
            case ROW_BOOT:
                return entry(R.array.boot_animation_names, toInt(value));
            default:
                return String.valueOf(value);
        }
    }

    private void pick(int row) {
        switch (row) {
            case ROW_ACCENT:
                pickAccent();
                break;
            case ROW_SATURATION:
                pickSaturation();
                break;
            case ROW_QS: {
                int[] styles = QsStyleActivity.styles();
                String[] names = new String[styles.length];
                int checked = -1;
                for (int i = 0; i < styles.length; i++) {
                    names[i] = getString(QsStyleActivity.NAMES[styles[i]]);
                    if (styles[i] == toInt(current(row))) checked = i;
                }
                choose(row, names, checked, i -> set(row, styles[i]));
                break;
            }
            case ROW_FACE: {
                String[] names = getResources().getStringArray(R.array.clock_face_names);
                String[] values = names.clone();
                values[0] = "System";
                int checked = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i].equalsIgnoreCase(String.valueOf(current(row)))) checked = i;
                }
                choose(row, names, checked, i -> set(row, values[i]));
                break;
            }
            case ROW_CLOCK_COLOR:
                pickColor(row, new String[] {"default", "accent"});
                break;
            case ROW_EDGE:
                pickColor(row, new String[] {"accent"});
                break;
            case ROW_FONT: {
                List<String> packages = new ArrayList<>();
                packages.add("android");
                for (OverlayInfo info : new FontController(this).fonts()) {
                    packages.add(info.packageName);
                }
                String[] names = new String[packages.size()];
                FontController fonts = new FontController(this);
                for (int i = 0; i < names.length; i++) names[i] = fonts.label(packages.get(i));
                choose(row, names, Math.max(0, packages.indexOf(String.valueOf(current(row)))),
                        i -> set(row, packages.get(i)));
                break;
            }
            case ROW_CLOCK_LOOK:
                choose(row, getResources().getStringArray(R.array.status_bar_clock_rr_style_entries),
                        toInt(current(row)), i -> set(row, i));
                break;
            case ROW_CHIP:
                choose(row, getResources().getStringArray(R.array.sb_clock_chip_entries),
                        toInt(current(row)), i -> set(row, i));
                break;
            case ROW_EDGE_STYLE:
                choose(row, new String[] {getString(R.string.edge_light_style_default),
                                getString(R.string.edge_light_style_frame)},
                        "rounded".equals(current(row)) ? 1 : 0,
                        i -> set(row, i == 1 ? "rounded" : "default"));
                break;
            case ROW_WALLPAPER:
                pickWallpaper();
                break;
            case ROW_BOOT: {
                String[] all = getResources().getStringArray(R.array.boot_animation_names);
                String[] names = new String[all.length - 1]; // not the file you pick
                System.arraycopy(all, 0, names, 0, names.length);
                choose(row, names, toInt(current(row)), i -> set(row, i));
                break;
            }
        }
    }

    private interface OnChosen {
        void chosen(int index);
    }

    private void choose(int row, String[] names, int checked, OnChosen onChosen) {
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(TITLES[row])
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    onChosen.chosen(which);
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickAccent() {
        String[] names = getResources().getStringArray(R.array.accent_names);
        String[] colors = getResources().getStringArray(R.array.accent_colors);
        List<String> presetNames = new ArrayList<>();
        List<String> presetColors = new ArrayList<>();
        for (int i = 0; i < Math.min(names.length, colors.length); i++) {
            if (!colors[i].startsWith("#")) continue; // "wallpaper" isn't a color
            presetNames.add(names[i]);
            presetColors.add(colors[i]);
        }
        if (Settings.System.getIntForUser(getContentResolver(), "rr_ember_unlocked", 0,
                UserHandle.USER_CURRENT) == 1) {
            presetNames.add(getString(R.string.accent_ember));
            presetColors.add("#FF5A1F");
        }
        GridLayout grid = new GridLayout(mThemed);
        grid.setColumnCount(6);
        grid.setPadding(dp(20), dp(8), dp(20), 0);
        androidx.appcompat.app.AlertDialog[] dialog = new androidx.appcompat.app.AlertDialog[1];
        for (int i = 0; i < presetColors.size(); i++) {
            String color = presetColors.get(i);
            View swatch = new View(mThemed);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color(color, Color.GRAY));
            swatch.setBackground(dot);
            swatch.setContentDescription(presetNames.get(i));
            swatch.setOnClickListener(v -> {
                set(ROW_ACCENT, color.toUpperCase(Locale.ROOT));
                dialog[0].dismiss();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(36);
            lp.height = dp(36);
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            grid.addView(swatch, lp);
        }
        NestedScrollView scroll = new NestedScrollView(mThemed);
        scroll.addView(grid);
        dialog[0] = new MaterialAlertDialogBuilder(mThemed)
                .setTitle(R.string.looks_part_accent)
                .setView(scroll)
                .setNeutralButton(R.string.looks_custom_color, (d, w) -> pickHex(ROW_ACCENT))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickSaturation() {
        Slider slider = new Slider(mThemed);
        slider.setValueFrom(0);
        slider.setValueTo(200);
        slider.setStepSize(5);
        slider.setValue(Math.max(0, Math.min(200, Math.round(toInt(current(ROW_SATURATION)) / 5f)
                * 5)));
        FrameLayout box = new FrameLayout(mThemed);
        box.setPadding(dp(20), dp(12), dp(20), 0);
        box.addView(slider);
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(R.string.looks_part_saturation)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (d, w) -> set(ROW_SATURATION, Math.round(slider.getValue())))
                .show();
    }

    /** The named choices in [fixed], or a custom color. */
    private void pickColor(int row, String[] fixed) {
        String[] names = new String[fixed.length + 1];
        for (int i = 0; i < fixed.length; i++) names[i] = colorName(fixed[i]);
        names[fixed.length] = getString(R.string.looks_custom_color);
        int checked = fixed.length;
        for (int i = 0; i < fixed.length; i++) {
            if (fixed[i].equals(current(row))) checked = i;
        }
        choose(row, names, checked, i -> {
            if (i < fixed.length) set(row, fixed[i]);
            else pickHex(row);
        });
    }

    private void pickHex(int row) {
        Object now = current(row);
        String start = now instanceof String && ((String) now).startsWith("#")
                ? (String) now : "#FF003F";
        LinearLayout box = new LinearLayout(mThemed);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(24), dp(12), dp(24), 0);
        View swatch = new View(mThemed);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color(start, Color.GRAY));
        swatch.setBackground(dot);
        box.addView(swatch, new LinearLayout.LayoutParams(dp(32), dp(32)));
        EditText hex = new EditText(mThemed);
        hex.setSingleLine(true);
        hex.setHint("#RRGGBB");
        hex.setText(start);
        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                int parsed = color(normalize(s.toString()), 0);
                if (parsed != 0) dot.setColor(parsed);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = dp(16);
        box.addView(hex, lp);
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(TITLES[row])
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String value = normalize(hex.getText().toString());
                    if (color(value, 0) != 0) set(row, value);
                })
                .show();
    }

    private static String normalize(String text) {
        String value = text.trim().toUpperCase(Locale.ROOT);
        if (!value.startsWith("#")) value = "#" + value;
        return value.length() == 7 ? value : "";
    }

    private void pickWallpaper() {
        String[] names = getResources().getStringArray(com.rr.wallpapers.R.array.partner_wallpapers);
        List<String> values = new ArrayList<>();
        for (String name : names) {
            if (getResources().getIdentifier(name, "drawable", getPackageName()) != 0) {
                values.add(name);
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(mThemed, 0, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout item = convertView instanceof LinearLayout
                        ? (LinearLayout) convertView : new LinearLayout(mThemed);
                item.removeAllViews();
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(24), dp(8), dp(24), dp(8));
                ImageView thumb = new ImageView(mThemed);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                String value = values.get(position);
                int small = getResources().getIdentifier(value + "_small", "drawable",
                        getPackageName());
                thumb.setImageResource(small != 0 ? small : getResources().getIdentifier(
                        value, "drawable", getPackageName()));
                GradientDrawable clip = new GradientDrawable();
                clip.setCornerRadius(dp(8));
                thumb.setBackground(clip);
                thumb.setClipToOutline(true);
                item.addView(thumb, new LinearLayout.LayoutParams(dp(40), dp(64)));
                TextView label = new TextView(mThemed);
                label.setText(LooksActivity.wallpaperName(LookEditorActivity.this, value));
                label.setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                label.setPadding(dp(16), 0, 0, 0);
                item.addView(label);
                return item;
            }
        };
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(R.string.looks_part_wallpaper)
                .setAdapter(adapter, (d, which) -> set(ROW_WALLPAPER, values.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String accentName(String color) {
        String[] names = getResources().getStringArray(R.array.accent_names);
        String[] colors = getResources().getStringArray(R.array.accent_colors);
        for (int i = 0; i < Math.min(names.length, colors.length); i++) {
            if (colors[i].equalsIgnoreCase(color)) return names[i] + " · " + color.toUpperCase();
        }
        return color.toUpperCase();
    }

    private String colorName(String value) {
        if ("accent".equals(value)) return getString(R.string.looks_color_accent);
        if ("default".equals(value)) return getString(R.string.looks_color_default);
        return value.toUpperCase();
    }

    private String entry(int array, int index) {
        String[] entries = getResources().getStringArray(array);
        return index >= 0 && index < entries.length ? entries[index] : String.valueOf(index);
    }

    private static int toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int color(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
