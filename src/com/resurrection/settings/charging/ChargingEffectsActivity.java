package com.resurrection.settings.charging;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.systemui.charging.ChargingAnimationView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChargingEffectsActivity extends CollapsingToolbarBaseActivity {
    private static final String KEY_ENABLED = "charging_animation_enabled";
    private static final String KEY_STYLE = "charging_animation_style";
    private static final String KEY_COLOR = "charging_color_mode";
    private static final String KEY_OPACITY = "charging_ripple_opacity";
    private static final String KEY_GLOW = "charging_glow_intensity";
    private static final String KEY_IMAGE = "charging_image_flow_style";
    private static final String KEY_IMAGE_OPACITY = "charging_image_flow_opacity";
    private static final String KEY_IMAGE_FLOW = "charging_image_flow_strength";
    private static final String KEY_LOCK = "charging_show_on_lockscreen";
    private static final String KEY_AOD = "charging_show_on_aod";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final int IMAGES = 9;
    private static final int[] EFFECTS = {
        R.string.charging_effect_bubbles, R.string.charging_effect_neon, R.string.charging_effect_beams,
        R.string.charging_effect_plasma, R.string.charging_effect_sparks, R.string.charging_effect_nebula,
        R.string.charging_effect_matrix, R.string.charging_effect_geoflow,
        R.string.charging_effect_image_flow, R.string.charging_effect_image_lines,
    };
    private static final String[] KEYS = {KEY_ENABLED, KEY_STYLE, KEY_COLOR, KEY_OPACITY, KEY_GLOW,
        KEY_IMAGE, KEY_IMAGE_OPACITY, KEY_IMAGE_FLOW, KEY_LOCK, KEY_AOD};
    private static final int[] DEFAULTS = {0, 0, 1, 100, 80, 0, 80, 5, 1, 1};

    private final int[] mValues = new int[KEYS.length];
    private final int[] mApplied = new int[KEYS.length];
    private final List<TextView> mEffectChips = new ArrayList<>();
    private final List<TextView> mPlaceChips = new ArrayList<>();
    private final List<TextView> mColorChips = new ArrayList<>();
    private final List<TextView> mImageChips = new ArrayList<>();
    private ChargingUi mUi;
    private Context mThemed;
    private ChargingStageView mStage;
    private TextView mName;
    private TextView mCredit;
    private View mColorTitle;
    private View mColors;
    private View mImages;
    private LinearLayout mSliders;
    private MaterialButton mApply;
    private Drawable[] mFlowImages;
    private int mSurface;

    public static String summary(Context context) {
        ContentResolver cr = context.getContentResolver();
        if (Settings.System.getIntForUser(cr, KEY_ENABLED, 0, UserHandle.USER_CURRENT) == 0) {
            return context.getString(R.string.charging_effects_off);
        }
        int style = Math.max(0, Math.min(EFFECTS.length - 1,
                Settings.System.getIntForUser(cr, KEY_STYLE, 0, UserHandle.USER_CURRENT)));
        return context.getString(R.string.charging_effects_on, context.getString(EFFECTS[style]));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_charging_effects);
        findViewById(R.id.charging_effects_scroll).setNestedScrollingEnabled(false);
        setTitle(R.string.charging_effects_title);
        mUi = new ChargingUi(this);
        mThemed = new ContextThemeWrapper(this,
                com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight);
        ContentResolver cr = getContentResolver();
        for (int i = 0; i < KEYS.length; i++) {
            mValues[i] = Settings.System.getIntForUser(cr, KEYS[i], DEFAULTS[i], UserHandle.USER_CURRENT);
        }
        System.arraycopy(mValues, 0, mApplied, 0, mValues.length);

        View stage = findViewById(R.id.effects_stage);
        stage.setBackground(mUi.stage());
        stage.setClipToOutline(true);
        mStage = findViewById(R.id.effects_phone);
        mName = findViewById(R.id.effects_name);
        mCredit = findViewById(R.id.effects_credit);
        mColorTitle = findViewById(R.id.effects_color_title);
        mColors = findViewById(R.id.effects_colors);
        mImages = findViewById(R.id.effects_images_scroll);
        mSliders = findViewById(R.id.effects_sliders);
        mSurface = MaterialColors.getColor(mStage, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        stepButton(R.id.effects_prev, -1);
        stepButton(R.id.effects_next, 1);

        LinearLayout places = findViewById(R.id.effects_places);
        addPlace(places, R.string.charging_effects_lockscreen, KEY_LOCK);
        addPlace(places, R.string.charging_effects_aod, KEY_AOD);

        LinearLayout chips = findViewById(R.id.effects_chips);
        for (int i = 0; i < EFFECTS.length; i++) {
            int style = i;
            TextView chip = mUi.chip(mThemed, getString(EFFECTS[i]), false);
            chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurface));
            chip.setOnClickListener(v -> set(KEY_STYLE, style));
            chips.addView(chip);
            mEffectChips.add(chip);
        }

        LinearLayout colors = findViewById(R.id.effects_colors);
        int[] names = {R.string.charging_effects_green, R.string.charging_effects_accent,
            R.string.charging_effects_rainbow};
        for (int i = 0; i < names.length; i++) {
            int mode = i;
            TextView chip = mUi.chip(mThemed, getString(names[i]), false);
            chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurface));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.round(52 * mUi.density), 1f);
            lp.setMarginEnd(i < names.length - 1 ? Math.round(8 * mUi.density) : 0);
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setCompoundDrawablePadding(Math.round(10 * mUi.density));
            chip.setCompoundDrawablesRelative(swatch(i), null, null, null);
            chip.setOnClickListener(v -> set(KEY_COLOR, mode));
            colors.addView(chip);
            mColorChips.add(chip);
        }

        LinearLayout images = findViewById(R.id.effects_images);
        for (int i = 0; i < IMAGES; i++) {
            int image = i;
            TextView chip = mUi.chip(mThemed, getString(R.string.charging_effects_image_n, i + 1), false);
            chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurface));
            chip.setOnClickListener(v -> set(KEY_IMAGE, image));
            images.addView(chip);
            mImageChips.add(chip);
        }
        mFlowImages = flowImages();

        mApply = findViewById(R.id.effects_apply);
        mApply.setOnClickListener(v -> apply());
        update();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MaterialSwitch toggle = new MaterialSwitch(mThemed);
        toggle.setContentDescription(getString(R.string.charging_effects_switch));
        toggle.setChecked(value(KEY_ENABLED) != 0);
        toggle.setOnCheckedChangeListener((button, checked) -> set(KEY_ENABLED, checked ? 1 : 0));
        MenuItem item = menu.add(R.string.charging_effects_switch);
        item.setActionView(toggle);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mStage.setEffect(null);
    }

    private GradientDrawable swatch(int mode) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        int size = Math.round(24 * mUi.density);
        dot.setSize(size, size);
        if (mode == 2) {
            dot.setGradientType(GradientDrawable.SWEEP_GRADIENT);
            dot.setColors(new int[] {0xFFFF5F6D, 0xFFFFC371, 0xFF7CFF6B, 0xFF4FACFE, 0xFFB06AB3,
                0xFFFF5F6D});
        } else {
            dot.setColor(mode == 0 ? 0xFF4CAF50 : mUi.accent);
        }
        dot.setBounds(0, 0, size, size);
        return dot;
    }

    private Drawable[] flowImages() {
        Drawable[] out = new Drawable[IMAGES];
        try {
            Resources res = getPackageManager().getResourcesForApplication(SYSTEMUI);
            for (int i = 0; i < IMAGES; i++) {
                int id = res.getIdentifier("charging_flow_" + (i + 1), "drawable", SYSTEMUI);
                out[i] = id != 0 ? res.getDrawable(id, null) : null;
            }
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
            return out;
        }
        return out;
    }

    private void addPlace(LinearLayout parent, int label, String key) {
        TextView chip = mUi.chip(mThemed, getString(label), true);
        chip.setTag(key);
        chip.setOnClickListener(v -> set(key, value(key) != 0 ? 0 : 1));
        parent.addView(chip);
        mPlaceChips.add(chip);
    }

    private void stepButton(int id, int dir) {
        ImageButton button = findViewById(id);
        button.setBackground(mUi.shape(0x1FFFFFFF, 0, 18, 0));
        button.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        button.setOnClickListener(v -> set(KEY_STYLE, Math.floorMod(value(KEY_STYLE) + dir, EFFECTS.length)));
    }

    private int index(String key) {
        return Arrays.asList(KEYS).indexOf(key);
    }

    private int value(String key) {
        return mValues[index(key)];
    }

    private void set(String key, int value) {
        if (value(key) == value) {
            return;
        }
        mValues[index(key)] = value;
        update();
        play();
    }

    private void update() {
        boolean on = value(KEY_ENABLED) != 0;
        int style = Math.max(0, Math.min(EFFECTS.length - 1, value(KEY_STYLE)));
        boolean images = style >= 8;
        mName.setText(on ? getString(EFFECTS[style]) : getString(R.string.charging_effects_off));
        mCredit.setText(getString(R.string.charging_effects_credit, style + 1, EFFECTS.length));
        for (int i = 0; i < mEffectChips.size(); i++) {
            mUi.setChip(mEffectChips.get(i), i == style, mSurface);
        }
        for (TextView chip : mPlaceChips) {
            mUi.setStageChip(chip, value((String) chip.getTag()) != 0);
        }
        for (int i = 0; i < mColorChips.size(); i++) {
            mUi.setChip(mColorChips.get(i), i == value(KEY_COLOR), mSurface);
        }
        for (int i = 0; i < mImageChips.size(); i++) {
            mUi.setChip(mImageChips.get(i), i == value(KEY_IMAGE), mSurface);
        }
        mColorTitle.setVisibility(images ? View.GONE : View.VISIBLE);
        mColors.setVisibility(images ? View.GONE : View.VISIBLE);
        mImages.setVisibility(images ? View.VISIBLE : View.GONE);
        mSliders.removeAllViews();
        if (images) {
            slider(R.string.charging_effects_image_opacity, KEY_IMAGE_OPACITY, 10, 100, 10);
            slider(R.string.charging_effects_image_strength, KEY_IMAGE_FLOW, 1, 10, 1);
        } else if (style == ChargingAnimationView.STYLE_BUBBLE_STREAM) {
            slider(R.string.charging_effects_opacity, KEY_OPACITY, 10, 100, 10);
        } else if (style == ChargingAnimationView.STYLE_NEON || style == ChargingAnimationView.STYLE_PLASMA
                || style == ChargingAnimationView.STYLE_NEBULA) {
            slider(R.string.charging_effects_glow, KEY_GLOW, 0, 100, 10);
        }
        findViewById(R.id.effects_options).setAlpha(on ? 1f : 0.45f);
        boolean pending = !Arrays.equals(mValues, mApplied);
        mApply.setEnabled(pending);
        mApply.setText(pending ? R.string.charging_apply : R.string.charging_applied);
    }

    private void slider(int label, String key, int min, int max, int step) {
        LinearLayout row = new LinearLayout(mThemed);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Math.round(10 * mUi.density), 0, 0);
        TextView name = new TextView(mThemed);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurface));
        row.addView(name, new LinearLayout.LayoutParams(Math.round(108 * mUi.density),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        Slider slider = new Slider(mThemed);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(step);
        slider.setValue(Math.max(min, Math.min(max, value(key) - (value(key) - min) % step)));
        slider.setTrackActiveTintList(ColorStateList.valueOf(mUi.accent));
        slider.setThumbTintList(ColorStateList.valueOf(mUi.accent));
        slider.addOnChangeListener((s, v, fromUser) -> {
            if (fromUser) {
                mValues[index(key)] = Math.round(v);
                mApply.setEnabled(!Arrays.equals(mValues, mApplied));
                mApply.setText(mApply.isEnabled() ? R.string.charging_apply : R.string.charging_applied);
                play();
            }
        });
        row.addView(slider, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        mSliders.addView(row);
    }

    private void apply() {
        ContentResolver cr = getContentResolver();
        for (int i = 0; i < KEYS.length; i++) {
            if (mValues[i] != mApplied[i]) {
                Settings.System.putIntForUser(cr, KEYS[i], mValues[i], UserHandle.USER_CURRENT);
            }
        }
        System.arraycopy(mValues, 0, mApplied, 0, mValues.length);
        update();
    }

    private void play() {
        mStage.setStatus(getString(R.string.charging_effects_status, ChargingUi.batteryLevel(this)));
        if (value(KEY_ENABLED) == 0) {
            mStage.setEffect(null);
            return;
        }
        ChargingAnimationView view = new ChargingAnimationView(this);
        view.setDefaultChargingColor(0xFF4CAF50);
        view.setAccentColor(mUi.accent);
        view.setColorMode(value(KEY_COLOR));
        view.setRippleOpacity(value(KEY_OPACITY) / 100f);
        view.setGlowIntensity(value(KEY_GLOW) / 100f);
        view.setImageFlowOpacity(value(KEY_IMAGE_OPACITY) / 100f);
        view.setImageFlowStrength(value(KEY_IMAGE_FLOW));
        view.setImageFlowDrawableIndex(value(KEY_IMAGE));
        view.setImageFlowDrawables(mFlowImages);
        view.setBatteryLevel(ChargingUi.batteryLevel(this));
        view.setAnimationStyle(value(KEY_STYLE));
        mStage.setEffect(view);
        view.post(view::show);
    }
}
