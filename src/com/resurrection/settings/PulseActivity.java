package com.resurrection.settings;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.function.IntConsumer;

/**
 * Pulse music bars. SystemUI's com.android.systemui.rr.pulse.PulseRenderer reads the same
 * Settings.System names.
 */
public class PulseActivity extends CollapsingToolbarBaseActivity {
    private static final String KEY_ENABLED = "pulse_navbar_enabled";
    private static final String KEY_STYLE = "pulse_style";
    private static final String KEY_COLOR_MODE = "pulse_color_mode";
    private static final String KEY_COLOR = "pulse_color";
    private static final String KEY_BARS = "pulse_bar_count";
    private static final String KEY_HEIGHT = "pulse_height";
    private static final String KEY_OPACITY = "pulse_opacity";
    private static final int BARS_DEFAULT = 28;
    private static final int HEIGHT_DEFAULT = 48;
    private static final int OPACITY_DEFAULT = 90;
    private static final String KEY_LOCKSCREEN = "pulse_lockscreen";

    private static final int DEFAULT_COLOR = 0xFFE02437;
    private static final int[] SWATCHES = {DEFAULT_COLOR, 0xFFFF7A1A, 0xFF3DDC84, 0xFF1EC8C8,
            0xFF3D8BFF, 0xFF8C5CFF, 0xFFFF4FA3, 0xFFFFFFFF};
    private static final int[] STYLE_BUTTONS = {R.id.pulse_style_lines, R.id.pulse_style_fading,
            R.id.pulse_style_blocks, R.id.pulse_style_mirror};
    private static final int[] COLOR_BUTTONS = {R.id.pulse_color_custom,
            R.id.pulse_color_accent, R.id.pulse_color_gradient};

    private boolean mEnabled;
    private int mStyle;
    private int mColorMode;
    private int mColor;
    private int mBars;
    private int mHeightDp;
    private int mOpacity;
    private boolean mLockscreen;
    private boolean mGuard;
    private boolean mTracking;

    private PulsePreviewView mPreview;
    private MaterialCardView mPreviewCard;
    private MaterialCardView mSwitchCard;
    private MaterialSwitch mSwitch;
    private TextView mSwitchTitle;
    private TextView mSwitchSummary;
    private View mContent;
    private MaterialButtonToggleGroup mStyleGroup;
    private MaterialButtonToggleGroup mColorGroup;
    private LinearLayout mSwatches;
    private TextView mBarsLabel;
    private TextView mHeightLabel;
    private TextView mOpacityLabel;
    private MaterialSwitch mLockscreenSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_pulse);
        readSettings();

        mPreview = findViewById(R.id.pulse_preview);
        mPreviewCard = findViewById(R.id.pulse_preview_card);
        mSwitchCard = findViewById(R.id.pulse_switch_card);
        mSwitch = findViewById(R.id.pulse_switch);
        mSwitchTitle = findViewById(R.id.pulse_switch_title);
        mSwitchSummary = findViewById(R.id.pulse_switch_summary);
        mContent = findViewById(R.id.pulse_content);
        mStyleGroup = findViewById(R.id.pulse_style);
        mColorGroup = findViewById(R.id.pulse_color_mode);
        mSwatches = findViewById(R.id.pulse_swatches);
        mBarsLabel = findViewById(R.id.pulse_bars_label);
        mHeightLabel = findViewById(R.id.pulse_height_label);
        mOpacityLabel = findViewById(R.id.pulse_opacity_label);
        mLockscreenSwitch = findViewById(R.id.pulse_lockscreen);

        mSwitch.setChecked(mEnabled);
        mSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (mGuard || checked == mEnabled) {
                return;
            }
            if (!putInt(KEY_ENABLED, checked ? 1 : 0)) {
                setGuarded(mSwitch, mEnabled);
                return;
            }
            mEnabled = checked;
            applyState();
        });
        mSwitchCard.setOnClickListener(v -> mSwitch.toggle());
        mSwitchCard.setAccessibilityDelegate(switchDelegate(mSwitch));

        mStyleGroup.check(STYLE_BUTTONS[mStyle]);
        mStyleGroup.addOnButtonCheckedListener((group, id, checked) -> {
            int style = indexOf(STYLE_BUTTONS, id);
            if (!checked || mGuard || style == mStyle) {
                return;
            }
            if (!putInt(KEY_STYLE, style)) {
                checkGuarded(mStyleGroup, STYLE_BUTTONS[mStyle]);
                return;
            }
            mStyle = style;
            applyState();
        });

        mColorGroup.check(COLOR_BUTTONS[mColorMode]);
        mColorGroup.addOnButtonCheckedListener((group, id, checked) -> {
            int mode = indexOf(COLOR_BUTTONS, id);
            if (!checked || mGuard || mode == mColorMode) {
                return;
            }
            if (!putInt(KEY_COLOR_MODE, mode)) {
                checkGuarded(mColorGroup, COLOR_BUTTONS[mColorMode]);
                return;
            }
            mColorMode = mode;
            applyState();
        });
        buildSwatches();

        bindSlider(findViewById(R.id.pulse_bars), KEY_BARS, mBars, value -> {
            mBars = value;
            updateLook();
        });
        bindSlider(findViewById(R.id.pulse_height), KEY_HEIGHT, mHeightDp, value -> {
            mHeightDp = value;
            updateLook();
        });
        bindSlider(findViewById(R.id.pulse_opacity), KEY_OPACITY, mOpacity, value -> {
            mOpacity = value;
            updateLook();
        });
        SliderReset.attach(mBarsLabel, findViewById(R.id.pulse_bars), BARS_DEFAULT,
                () -> putInt(KEY_BARS, BARS_DEFAULT));
        SliderReset.attach(mHeightLabel, findViewById(R.id.pulse_height), HEIGHT_DEFAULT,
                () -> putInt(KEY_HEIGHT, HEIGHT_DEFAULT));
        SliderReset.attach(mOpacityLabel, findViewById(R.id.pulse_opacity), OPACITY_DEFAULT,
                () -> putInt(KEY_OPACITY, OPACITY_DEFAULT));

        mLockscreenSwitch.setChecked(mLockscreen);
        mLockscreenSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (mGuard || checked == mLockscreen) {
                return;
            }
            if (!putInt(KEY_LOCKSCREEN, checked ? 1 : 0)) {
                setGuarded(mLockscreenSwitch, mLockscreen);
                return;
            }
            mLockscreen = checked;
        });
        View lockscreenRow = findViewById(R.id.pulse_lockscreen_row);
        lockscreenRow.setOnClickListener(v -> mLockscreenSwitch.toggle());
        lockscreenRow.setAccessibilityDelegate(switchDelegate(mLockscreenSwitch));

        applyState();
    }

    private void readSettings() {
        mEnabled = getInt(KEY_ENABLED, 0) == 1;
        mStyle = clamp(getInt(KEY_STYLE, 0), 0, STYLE_BUTTONS.length - 1);
        mColorMode = clamp(getInt(KEY_COLOR_MODE, 0), 0, COLOR_BUTTONS.length - 1);
        mColor = getInt(KEY_COLOR, DEFAULT_COLOR) | 0xFF000000;
        mBars = clamp(getInt(KEY_BARS, BARS_DEFAULT), 12, 48);
        mHeightDp = clamp(getInt(KEY_HEIGHT, HEIGHT_DEFAULT), 24, 120);
        mOpacity = clamp(getInt(KEY_OPACITY, OPACITY_DEFAULT), 10, 100);
        mLockscreen = getInt(KEY_LOCKSCREEN, 1) == 1;
    }

    private void applyState() {
        int cardColor = color(mSwitchCard, mEnabled
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerHigh);
        int textColor = color(mSwitchCard, mEnabled
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurface);
        mSwitchCard.setCardBackgroundColor(cardColor);
        mSwitchTitle.setTextColor(textColor);
        mSwitchSummary.setTextColor(textColor);
        setTreeEnabled(mContent, mEnabled);
        mPreviewCard.setAlpha(mEnabled ? 1f : 0.5f);
        mSwatches.setVisibility(mColorMode == 0 ? View.VISIBLE : View.GONE);
        updateSwatches();
        mPreview.setStyle(mStyle);
        mPreview.setColors(mColorMode, mColor);
        updateLook();
    }

    private void updateLook() {
        mBarsLabel.setText(getString(R.string.pulse_bars_value, mBars));
        mHeightLabel.setText(getString(R.string.pulse_height_value, mHeightDp));
        mOpacityLabel.setText(getString(R.string.pulse_opacity_value, mOpacity));
        mPreview.setBars(mBars);
        mPreview.setOpacity(mOpacity);
        ViewGroup.LayoutParams lp = mPreview.getLayoutParams();
        int height = dp(mHeightDp);
        if (lp.height != height) {
            lp.height = height;
            mPreview.setLayoutParams(lp);
        }
        // Keep the stage tall enough for the title above the tallest bars.
        ViewGroup.LayoutParams card = mPreviewCard.getLayoutParams();
        int cardHeight = Math.max(dp(168), height + dp(72));
        if (card.height != cardHeight) {
            card.height = cardHeight;
            mPreviewCard.setLayoutParams(card);
        }
    }

    private void buildSwatches() {
        String[] names = getResources().getStringArray(R.array.pulse_swatch_names);
        int size = dp(40);
        for (int i = 0; i < SWATCHES.length; i++) {
            int swatch = SWATCHES[i];
            FrameLayout cell = new FrameLayout(mSwatches.getContext());
            ImageView dot = new ImageView(mSwatches.getContext());
            dot.setBackgroundResource(R.drawable.remixer_pulse_swatch);
            dot.setBackgroundTintList(ColorStateList.valueOf(swatch));
            dot.setImageResource(R.drawable.remixer_ic_pulse_check);
            dot.setImageTintList(ColorStateList.valueOf(
                    Color.luminance(swatch) > 0.6f ? Color.BLACK : Color.WHITE));
            dot.setScaleType(ImageView.ScaleType.CENTER);
            dot.setContentDescription(names[i]);
            dot.setOnClickListener(v -> onSwatchClicked(swatch));
            cell.addView(dot, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            mSwatches.addView(cell, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
    }

    private void updateSwatches() {
        for (int i = 0; i < mSwatches.getChildCount(); i++) {
            ImageView dot = (ImageView) ((ViewGroup) mSwatches.getChildAt(i)).getChildAt(0);
            boolean selected = SWATCHES[i] == mColor;
            dot.setImageAlpha(selected ? 255 : 0);
            dot.setSelected(selected);
            dot.setScaleX(selected ? 1.15f : 1f);
            dot.setScaleY(selected ? 1.15f : 1f);
            dot.setElevation(selected ? dp(3) : 0);
        }
    }

    private void onSwatchClicked(int swatch) {
        if (swatch == mColor || !putInt(KEY_COLOR, swatch)) {
            return;
        }
        mColor = swatch;
        applyState();
    }

    private void bindSlider(Slider slider, String key, int initial, IntConsumer onValue) {
        slider.setValue(clamp(initial, (int) slider.getValueFrom(), (int) slider.getValueTo()));
        slider.setLabelFormatter(value -> String.valueOf(Math.round(value)));
        slider.addOnChangeListener((s, value, fromUser) -> {
            onValue.accept(Math.round(value));
            if (fromUser && !mTracking) {
                putInt(key, Math.round(value));
            }
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider s) {
                mTracking = true;
            }

            @Override
            public void onStopTrackingTouch(Slider s) {
                mTracking = false;
                putInt(key, Math.round(s.getValue()));
            }
        });
    }

    private View.AccessibilityDelegate switchDelegate(MaterialSwitch target) {
        return new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Switch.class.getName());
                info.setCheckable(true);
                info.setChecked(target.isChecked());
            }
        };
    }

    private void setGuarded(MaterialSwitch target, boolean checked) {
        mGuard = true;
        target.setChecked(checked);
        mGuard = false;
    }

    private void checkGuarded(MaterialButtonToggleGroup group, int id) {
        mGuard = true;
        group.check(id);
        mGuard = false;
    }

    private int getInt(String key, int def) {
        return Settings.System.getInt(getContentResolver(), key, def);
    }

    private boolean putInt(String key, int value) {
        try {
            if (Settings.System.putInt(getContentResolver(), key, value)) {
                return true;
            }
        } catch (SecurityException ignored) {
            // Reported below.
        }
        Toast.makeText(this, R.string.pulse_failed, Toast.LENGTH_SHORT).show();
        return false;
    }

    private static void setTreeEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                setTreeEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    private static int indexOf(int[] ids, int id) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == id) {
                return i;
            }
        }
        return 0;
    }

    private static int color(View view, int attr) {
        return MaterialColors.getColor(view, attr);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
