package com.resurrection.settings;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

/**
 * Resurrected Remix accent seeds. Wallpaper restores Material You from the wallpaper.
 */
public class AccentActivity extends CollapsingToolbarBaseActivity {
    private static final String WALLPAPER = "wallpaper";
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
        mNames = getResources().getStringArray(R.array.accent_names);
        mColors = getResources().getStringArray(R.array.accent_colors);
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

    private int findSelected() {
        String current = mAccents.current();
        for (int i = 0; i < mColors.length; i++) {
            String color = mColors[i];
            if (WALLPAPER.equals(color)) {
                if (current == null) {
                    return i;
                }
            } else if (current != null
                    && current.equals(String.format("%06X", Color.parseColor(color) & 0xFFFFFF))) {
                return i;
            }
        }
        return -1;
    }

    private void updatePreview() {
        mPreviewName.setText(mSelected >= 0
                ? getString(R.string.accent_current, mNames[mSelected])
                : getString(R.string.accent_current_custom));
    }

    private void apply(int position) {
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

    private final class Holder extends RecyclerView.ViewHolder {
        final View item;
        final View ring;
        final ImageView swatch;
        final TextView name;

        Holder(View view) {
            super(view);
            item = view.findViewById(R.id.accent_item);
            ring = view.findViewById(R.id.accent_ring);
            swatch = view.findViewById(R.id.accent_swatch);
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
            if (WALLPAPER.equals(color)) {
                dot.setGradientType(GradientDrawable.SWEEP_GRADIENT);
                dot.setColors(WALLPAPER_SWEEP);
                value = Color.GRAY;
            } else {
                value = Color.parseColor(color);
                dot.setColor(value);
                if (Color.luminance(value) > 0.85f) {
                    dot.setStroke(dp(1), 0x33000000);
                }
            }
            holder.swatch.setBackground(dot);
            holder.swatch.setImageResource(selected ? R.drawable.remixer_ic_pulse_check : 0);
            holder.swatch.setImageTintList(ColorStateList.valueOf(
                    Color.luminance(value) > 0.6f ? Color.BLACK : Color.WHITE));
            holder.ring.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
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
