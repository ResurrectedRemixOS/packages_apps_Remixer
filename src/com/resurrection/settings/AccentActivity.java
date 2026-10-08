package com.resurrection.settings;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;

/**
 * Resurrected Remix accent seeds. Wallpaper restores Material You from the wallpaper; Custom
 * opens a picker for any color.
 */
public class AccentActivity extends CollapsingToolbarBaseActivity {
    private static final String WALLPAPER = "wallpaper";
    private static final String CUSTOM = "custom";
    /** Custom sits right after Wallpaper. */
    private static final int CUSTOM_POSITION = 1;
    private static final String PREFS = "remixer_accents";
    private static final String KEY_RECENT = "custom_recent";
    private static final int MAX_RECENT = 6;
    private static final int GRID_SPAN = 4;
    private static final int[] WALLPAPER_SWEEP = {0xFFE02437, 0xFFFFC233, 0xFF3DDC84,
            0xFF3D8BFF, 0xFF8C5CFF, 0xFFE02437};

    private AccentController mAccents;
    private String[] mNames;
    private String[] mColors;
    private int mSelected;
    private Adapter mAdapter;
    private TextView mPreviewName;
    private TextView mShadeValue;
    private MaterialButton mFilled;
    private MaterialButton mTonal;
    private MaterialSwitch mSwitch;
    private Slider mShade;
    /** Shade baked into this activity's theme colors. */
    private int mThemeShade;
    private boolean mTracking;
    private int mPrimary;
    private int mOnPrimary;
    private int mSecondaryContainer;
    private int mOnSecondaryContainer;
    private ColorStateList mFilledBackground;
    private ColorStateList mFilledText;
    private ColorStateList mTonalBackground;
    private ColorStateList mTonalText;
    private ColorStateList mTrackTint;
    private ColorStateList mThumbTint;
    private ColorStateList mSliderActive;
    private ColorStateList mSliderInactive;
    private ColorStateList mSliderThumb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_accents);
        mAccents = new AccentController(this);
        mNames = withCustom(getResources().getStringArray(R.array.accent_names),
                getString(R.string.accent_custom));
        mColors = withCustom(getResources().getStringArray(R.array.accent_colors), CUSTOM);
        mSelected = findSelected();

        mPreviewName = findViewById(R.id.accent_preview_name);
        RecyclerView grid = findViewById(R.id.accents_grid);
        grid.setLayoutManager(new GridLayoutManager(this, GRID_SPAN));
        mAdapter = new Adapter();
        grid.setAdapter(mAdapter);
        updatePreview();
        setupShade();
    }

    private void setupShade() {
        mShadeValue = findViewById(R.id.accent_shade_value);
        mFilled = findViewById(R.id.accent_preview_filled);
        mTonal = findViewById(R.id.accent_preview_tonal);
        mSwitch = findViewById(R.id.accent_preview_switch);
        mShade = findViewById(R.id.accent_shade);

        mPrimary = MaterialColors.getColor(mFilled, androidx.appcompat.R.attr.colorPrimary);
        mOnPrimary = MaterialColors.getColor(mFilled,
                com.google.android.material.R.attr.colorOnPrimary);
        mSecondaryContainer = MaterialColors.getColor(mTonal,
                com.google.android.material.R.attr.colorSecondaryContainer);
        mOnSecondaryContainer = MaterialColors.getColor(mTonal,
                com.google.android.material.R.attr.colorOnSecondaryContainer);
        mFilledBackground = mFilled.getBackgroundTintList();
        mFilledText = mFilled.getTextColors();
        mTonalBackground = mTonal.getBackgroundTintList();
        mTonalText = mTonal.getTextColors();
        mTrackTint = mSwitch.getTrackTintList();
        mThumbTint = mSwitch.getThumbTintList();
        mSliderActive = mShade.getTrackActiveTintList();
        mSliderInactive = mShade.getTrackInactiveTintList();
        mSliderThumb = mShade.getThumbTintList();

        mThemeShade = mAccents.shade();
        mShade.setValue(mThemeShade);
        mShade.setLabelFormatter(value -> shadeLabel(Math.round(value)));
        updateShadeLabel(mThemeShade);
        mShade.addOnChangeListener((slider, value, fromUser) -> {
            int shade = Math.round(value);
            updateShadeLabel(shade);
            previewShade(shade);
            if (fromUser && !mTracking) {
                applyShade(shade);
            }
        });
        mShade.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                mTracking = true;
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                mTracking = false;
                applyShade(Math.round(slider.getValue()));
            }
        });
    }

    private String shadeLabel(int shade) {
        if (shade > 0) {
            return getString(R.string.accent_shade_lighter_value, shade);
        }
        if (shade < 0) {
            return getString(R.string.accent_shade_darker_value, -shade);
        }
        return getString(R.string.accent_shade_default);
    }

    private void updateShadeLabel(int shade) {
        mShadeValue.setText(shadeLabel(shade));
    }

    private void applyShade(int shade) {
        if (shade == mAccents.shade()) {
            return;
        }
        try {
            mAccents.setShade(shade);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.accent_failed, Toast.LENGTH_SHORT).show();
            mShade.setValue(mAccents.shade());
        }
    }

    /** Recolors the preview until SystemUI regenerates the palette and the activity restarts. */
    private void previewShade(int shade) {
        if (shade == mThemeShade) {
            mFilled.setBackgroundTintList(mFilledBackground);
            mFilled.setTextColor(mFilledText);
            mTonal.setBackgroundTintList(mTonalBackground);
            mTonal.setTextColor(mTonalText);
            mSwitch.setTrackTintList(mTrackTint);
            mSwitch.setThumbTintList(mThumbTint);
            mShade.setTrackActiveTintList(mSliderActive);
            mShade.setTrackInactiveTintList(mSliderInactive);
            mShade.setThumbTintList(mSliderThumb);
            return;
        }
        double gamma = Math.pow(2, -(shade - mThemeShade) * AccentController.SHADE_STEP);
        int primary = shadeColor(mPrimary, gamma);
        int onPrimary = readable(mOnPrimary, mPrimary, primary);
        int container = shadeColor(mSecondaryContainer, gamma);
        int onContainer = readable(mOnSecondaryContainer, mSecondaryContainer, container);
        mFilled.setBackgroundTintList(ColorStateList.valueOf(primary));
        mFilled.setTextColor(onPrimary);
        mTonal.setBackgroundTintList(ColorStateList.valueOf(container));
        mTonal.setTextColor(onContainer);
        mSwitch.setTrackTintList(ColorStateList.valueOf(primary));
        mSwitch.setThumbTintList(ColorStateList.valueOf(onPrimary));
        mShade.setTrackActiveTintList(ColorStateList.valueOf(primary));
        mShade.setTrackInactiveTintList(ColorStateList.valueOf(container));
        mShade.setThumbTintList(ColorStateList.valueOf(primary));
    }

    /** Same tone curve as ThemeOverlayController; HCT tone is L*. */
    private static int shadeColor(int color, double gamma) {
        double[] lab = new double[3];
        ColorUtils.colorToLAB(color, lab);
        lab[0] = 100.0 * Math.pow(Math.max(0, Math.min(100, lab[0])) / 100.0, gamma);
        return ColorUtils.LABToColor(lab[0], lab[1], lab[2]);
    }

    /** Keeps the foreground if it still reads on the new background, else black or white. */
    private static int readable(int foreground, int oldBackground, int background) {
        double target = Math.min(4.5, ColorUtils.calculateContrast(foreground, oldBackground));
        if (ColorUtils.calculateContrast(foreground, background) >= target) {
            return foreground;
        }
        return ColorUtils.calculateContrast(Color.WHITE, background)
                >= ColorUtils.calculateContrast(Color.BLACK, background)
                ? Color.WHITE : Color.BLACK;
    }

    private static String[] withCustom(String[] items, String custom) {
        String[] out = new String[items.length + 1];
        out[0] = items[0];
        out[CUSTOM_POSITION] = custom;
        System.arraycopy(items, 1, out, CUSTOM_POSITION + 1, items.length - 1);
        return out;
    }

    private int findSelected() {
        String current = mAccents.current();
        for (int i = 0; i < mColors.length; i++) {
            String color = mColors[i];
            if (CUSTOM.equals(color)) {
                continue;
            }
            if (WALLPAPER.equals(color)) {
                if (current == null) {
                    return i;
                }
            } else if (current != null
                    && current.equals(String.format("%06X", Color.parseColor(color) & 0xFFFFFF))) {
                return i;
            }
        }
        return current != null ? CUSTOM_POSITION : -1;
    }

    /** The active custom color, else the last one picked, else null. */
    private String customColor() {
        if (mSelected == CUSTOM_POSITION) {
            return "#" + mAccents.current();
        }
        List<String> recent = recent();
        return recent.isEmpty() ? null : recent.get(0);
    }

    private List<String> recent() {
        List<String> out = new ArrayList<>();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_RECENT, "");
        for (String hex : raw.split(",")) {
            if (hex.matches("#[0-9A-F]{6}")) {
                out.add(hex);
            }
        }
        return out;
    }

    private void addRecent(String hex) {
        List<String> recent = recent();
        recent.remove(hex);
        recent.add(0, hex);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_RECENT, String.join(",", recent)).apply();
    }

    private void updatePreview() {
        mPreviewName.setText(mSelected >= 0 && mSelected != CUSTOM_POSITION
                ? getString(R.string.accent_current, mNames[mSelected])
                : getString(R.string.accent_current_custom));
    }

    private void apply(int position) {
        if (position == CUSTOM_POSITION) {
            showPicker();
            return;
        }
        if (position == mSelected) {
            return;
        }
        String color = mColors[position];
        try {
            mAccents.apply(WALLPAPER.equals(color) ? null : color);
        } catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(this, R.string.accent_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        int old = mSelected;
        mSelected = position;
        if (old >= 0) {
            mAdapter.notifyItemChanged(old);
        }
        mAdapter.notifyItemChanged(position);
        updatePreview();
        Toast.makeText(this, R.string.accent_applied, Toast.LENGTH_SHORT).show();
    }

    private void applyCustom(String hex) {
        try {
            mAccents.apply(hex);
        } catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(this, R.string.accent_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        addRecent(hex);
        int old = mSelected;
        mSelected = CUSTOM_POSITION;
        if (old >= 0 && old != CUSTOM_POSITION) {
            mAdapter.notifyItemChanged(old);
        }
        mAdapter.notifyItemChanged(CUSTOM_POSITION);
        updatePreview();
        Toast.makeText(this, R.string.accent_applied, Toast.LENGTH_SHORT).show();
    }

    private void showPicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(this,
                com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog);
        View view = LayoutInflater.from(dialog.getContext())
                .inflate(R.layout.remixer_accent_picker, null);
        dialog.setContentView(view);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        int start;
        String custom = customColor();
        if (custom != null) {
            start = Color.parseColor(custom);
        } else if (mSelected > CUSTOM_POSITION) {
            start = Color.parseColor(mColors[mSelected]);
        } else {
            start = mPrimary;
        }
        new Picker(dialog, view).set(start);
        dialog.show();
    }

    private static int onColor(int color) {
        return ColorUtils.calculateContrast(Color.WHITE, color)
                >= ColorUtils.calculateContrast(Color.BLACK, color) ? Color.WHITE : Color.BLACK;
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    /** The custom color sheet. Color lives as HSV so the pad and hue bar stay in step. */
    private final class Picker {
        private final float[] mHsv = new float[3];
        private final View mSwatch;
        private final EditText mHex;
        private final ColorPadView mPad;
        private final HueBarView mHue;
        private final MaterialButton mFilled;
        private final MaterialButton mTonal;
        private final MaterialSwitch mSwitch;
        private final MaterialButton mApply;
        private final LinearLayout mRecent;
        private final int mContainer;
        private final int mOnSurface;
        private boolean mWritingHex;

        Picker(BottomSheetDialog dialog, View view) {
            mSwatch = view.findViewById(R.id.picker_swatch);
            mHex = view.findViewById(R.id.picker_hex);
            mPad = view.findViewById(R.id.picker_pad);
            mHue = view.findViewById(R.id.picker_hue);
            mFilled = view.findViewById(R.id.picker_filled);
            mTonal = view.findViewById(R.id.picker_tonal);
            mSwitch = view.findViewById(R.id.picker_switch);
            mApply = view.findViewById(R.id.picker_apply);
            mRecent = view.findViewById(R.id.picker_recent);
            mContainer = MaterialColors.getColor(view,
                    com.google.android.material.R.attr.colorSurfaceContainer);
            mOnSurface = MaterialColors.getColor(view,
                    com.google.android.material.R.attr.colorOnSurface);
            view.findViewById(R.id.picker_hex_box).setBackground(round(MaterialColors.getColor(
                    view, com.google.android.material.R.attr.colorSurfaceContainerHighest),
                    dp(16)));
            view.findViewById(R.id.picker_preview).setBackground(round(mContainer, dp(20)));

            mPad.setOnChangeListener((saturation, value) -> {
                mHsv[1] = saturation;
                mHsv[2] = value;
                update(true);
            });
            mHue.setOnChangeListener(hue -> {
                mHsv[0] = hue;
                update(true);
            });
            mHex.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (mWritingHex) {
                        return;
                    }
                    String text = s.toString();
                    if (!text.startsWith("#")) {
                        text = "#" + text;
                    }
                    if (text.matches("#[0-9a-fA-F]{6}")) {
                        Color.colorToHSV(Color.parseColor(text), mHsv);
                        update(false);
                    }
                }
            });
            view.findViewById(R.id.picker_cancel).setOnClickListener(v -> dialog.dismiss());
            mApply.setOnClickListener(v -> {
                dialog.dismiss();
                applyCustom(hex());
            });

            List<String> recent = recent();
            if (recent.isEmpty()) {
                view.findViewById(R.id.picker_recent_title).setVisibility(View.GONE);
                mRecent.setVisibility(View.GONE);
            }
            for (String hex : recent) {
                View dot = new View(view.getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
                lp.setMarginEnd(dp(10));
                dot.setLayoutParams(lp);
                dot.setTag(hex);
                dot.setContentDescription(getString(R.string.accent_custom_use, hex));
                dot.setOnClickListener(v -> set(Color.parseColor(hex)));
                mRecent.addView(dot);
            }
        }

        void set(int color) {
            Color.colorToHSV(color, mHsv);
            update(true);
        }

        private String hex() {
            return String.format("#%06X", Color.HSVToColor(mHsv) & 0xFFFFFF);
        }

        private void update(boolean writeHex) {
            int color = Color.HSVToColor(mHsv);
            int on = onColor(color);
            String hex = hex();
            mPad.setColor(mHsv[0], mHsv[1], mHsv[2]);
            mHue.setHue(mHsv[0]);
            GradientDrawable swatch = new GradientDrawable();
            swatch.setShape(GradientDrawable.OVAL);
            swatch.setColor(color);
            swatch.setStroke(dp(1), ColorUtils.setAlphaComponent(mOnSurface, 0x33));
            mSwatch.setBackground(swatch);
            if (writeHex) {
                mWritingHex = true;
                mHex.setText(hex);
                mHex.setSelection(mHex.length());
                mWritingHex = false;
            }
            mFilled.setBackgroundTintList(ColorStateList.valueOf(color));
            mFilled.setTextColor(on);
            mTonal.setBackgroundTintList(ColorStateList.valueOf(
                    ColorUtils.blendARGB(color, mContainer, 0.72f)));
            mTonal.setTextColor(ColorUtils.blendARGB(color, mOnSurface, 0.45f));
            mSwitch.setTrackTintList(ColorStateList.valueOf(color));
            mSwitch.setThumbTintList(ColorStateList.valueOf(on));
            mApply.setBackgroundTintList(ColorStateList.valueOf(color));
            mApply.setTextColor(on);
            for (int i = 0; i < mRecent.getChildCount(); i++) {
                View dot = mRecent.getChildAt(i);
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                shape.setColor(Color.parseColor((String) dot.getTag()));
                if (hex.equals(dot.getTag())) {
                    shape.setStroke(dp(3), mOnSurface);
                }
                dot.setBackground(shape);
            }
        }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final View item;
        final View ring;
        final ImageView swatch;
        final ImageView badge;
        final TextView name;

        Holder(View view) {
            super(view);
            item = view.findViewById(R.id.accent_item);
            ring = view.findViewById(R.id.accent_ring);
            swatch = view.findViewById(R.id.accent_swatch);
            badge = view.findViewById(R.id.accent_badge);
            name = view.findViewById(R.id.accent_name);
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return mColors.length;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_accent_item, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            String color = mColors[position];
            boolean selected = position == mSelected;
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            int value;
            int icon = selected ? R.drawable.remixer_ic_pulse_check : 0;
            boolean badge = false;
            if (WALLPAPER.equals(color)) {
                dot.setGradientType(GradientDrawable.SWEEP_GRADIENT);
                dot.setColors(WALLPAPER_SWEEP);
                value = Color.GRAY;
            } else if (CUSTOM.equals(color)) {
                String custom = customColor();
                if (custom == null) {
                    // Nothing picked yet: an outlined circle with a plus.
                    dot.setColor(Color.TRANSPARENT);
                    dot.setStroke(dp(2), MaterialColors.getColor(holder.item,
                            com.google.android.material.R.attr.colorOutline));
                    value = MaterialColors.getColor(holder.item,
                            com.google.android.material.R.attr.colorSurface);
                    icon = R.drawable.remixer_ic_add;
                } else {
                    value = Color.parseColor(custom);
                    dot.setColor(value);
                    badge = true;
                }
            } else {
                value = Color.parseColor(color);
                dot.setColor(value);
                if (Color.luminance(value) > 0.85f) {
                    dot.setStroke(dp(1), 0x33000000);
                }
            }
            holder.swatch.setBackground(dot);
            holder.swatch.setImageResource(icon);
            holder.swatch.setImageTintList(ColorStateList.valueOf(
                    Color.luminance(value) > 0.6f ? Color.BLACK : Color.WHITE));
            holder.ring.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            holder.badge.setVisibility(badge ? View.VISIBLE : View.GONE);
            if (badge) {
                GradientDrawable chip = new GradientDrawable();
                chip.setShape(GradientDrawable.OVAL);
                chip.setColor(MaterialColors.getColor(holder.item,
                        com.google.android.material.R.attr.colorSurfaceContainerHighest));
                chip.setStroke(dp(2), MaterialColors.getColor(holder.item,
                        android.R.attr.colorBackground));
                holder.badge.setBackground(chip);
                holder.badge.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(
                        holder.item, com.google.android.material.R.attr.colorOnSurface)));
            }
            holder.name.setText(mNames[position]);
            holder.item.setSelected(selected);
            holder.item.setContentDescription(mNames[position]);
            holder.item.setOnClickListener(v -> apply(holder.getBindingAdapterPosition()));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
