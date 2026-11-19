package com.resurrection.settings.charging;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.Utils;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.systemui.bliss.charging.BlissChargingAnimationView;
import com.android.systemui.rr.charging.DefibrillatorView;
import com.android.systemui.rr.charging.HyperBubblesView;
import com.android.systemui.rr.charging.NeonRevivalView;
import com.android.systemui.rr.charging.OneUiCapsuleView;
import com.android.systemui.rr.charging.OriginOrbView;
import com.android.systemui.rr.charging.PulseRingView;
import com.android.systemui.rr.charging.RrChargingView;
import com.android.systemui.rr.charging.SuperChargeView;
import com.android.systemui.surfaceeffects.core.ripple.RippleShader;
import com.android.systemui.surfaceeffects.view.ripple.RippleView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChargingAnimationActivity extends CollapsingToolbarBaseActivity {
    static final String SETTING = "rr_charging_animation";
    private static final String PREVIEW = "rr_charging_animation_preview";
    private static final String LEGACY_RING = "rr_charging_ring";
    private static final String RING_LIBRARY = "/system_ext/lib64/libnativeChargingRing.so";
    private static final String OVERLAY_PREFIX = "com.android.systemui.charging_animation.";
    private static final String NONE = "none";
    private static final String RR = "rr";
    private static final String MAKERS = "makers";
    private static final String ROMS = "roms";
    private static final long REPLAY_MS = 700;

    private static final Style[] STYLES = {
        new Style("rr_neon", RR, R.string.charging_style_rr_neon, R.string.charging_source_rr),
        new Style("rr_defib", RR, R.string.charging_style_rr_defib, R.string.charging_source_rr),
        new Style("rr_pulse", RR, R.string.charging_style_rr_pulse, R.string.charging_source_rr),
        new Style("pixel", MAKERS, R.string.charging_style_pixel, R.string.charging_source_android),
        new Style("oneplus", MAKERS, R.string.charging_style_oneplus, R.string.charging_source_oneplus),
        new Style("oneui", MAKERS, R.string.charging_style_oneui, R.string.charging_source_samsung),
        new Style("hyperos", MAKERS, R.string.charging_style_hyperos, R.string.charging_source_xiaomi),
        new Style("originos", MAKERS, R.string.charging_style_originos, R.string.charging_source_vivo),
        new Style("supercharge", MAKERS, R.string.charging_style_supercharge,
                R.string.charging_source_huawei),
        new Style("moto", MAKERS, R.string.charging_style_moto, R.string.charging_source_moto),
        new Style("nothing", MAKERS, R.string.charging_style_nothing, R.string.charging_source_nothing),
        new Style("bliss_flash", ROMS, R.string.charging_style_bliss_flash,
                R.string.charging_source_bliss),
        new Style("bliss_glow", ROMS, R.string.charging_style_bliss_glow, R.string.charging_source_bliss),
        new Style("bliss_battery", ROMS, R.string.charging_style_bliss_battery,
                R.string.charging_source_bliss),
    };

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mLoader = Executors.newSingleThreadExecutor();
    private final List<Style> mStyles = new ArrayList<>();
    private final List<Speed> mSpeeds = new ArrayList<>();
    private final List<TextView> mSpeedChips = new ArrayList<>();
    private ChargingUi mUi;
    private Context mThemed;
    private ChargingStageView mStage;
    private TextView mName;
    private TextView mSource;
    private MaterialButtonToggleGroup mTabs;
    private Adapter mAdapter;
    private MaterialButton mApply;
    private MaterialButton mTry;
    private TextView mEffectsState;
    private MaterialSwitch mSwitch;
    private String mStyle;
    private String mApplied;
    private String mGroup;
    private int mSpeed;
    private int mGeneration;

    public static String selected(Context context) {
        String style = Settings.System.getStringForUser(context.getContentResolver(), SETTING,
                UserHandle.USER_CURRENT);
        if (style != null && !style.isEmpty()) {
            return style;
        }
        if (Settings.System.getIntForUser(context.getContentResolver(), LEGACY_RING, 1,
                UserHandle.USER_CURRENT) == 0) {
            return "pixel";
        }
        return isAvailable(context, "oneplus") ? "oneplus" : "rr_neon";
    }

    public static String currentName(Context context) {
        Style style = find(selected(context));
        return context.getString(style != null ? style.name : R.string.charging_off);
    }

    private static Style find(String key) {
        for (Style style : STYLES) {
            if (style.key.equals(key)) {
                return style;
            }
        }
        return null;
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_charging_animation);
        setTitle(R.string.charging_animation_title);
        mUi = new ChargingUi(this);
        mThemed = new ContextThemeWrapper(this,
                com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight);
        for (Style style : STYLES) {
            if (isAvailable(this, style.key)) {
                mStyles.add(style);
            }
        }
        mApplied = selected(this);
        Style current = find(mApplied);
        mStyle = current != null ? current.key : NONE.equals(mApplied) ? NONE : mStyles.get(0).key;
        mGroup = current != null ? current.group : RR;

        View stage = findViewById(R.id.charging_stage);
        stage.setBackground(mUi.stage());
        stage.setClipToOutline(true);
        mStage = findViewById(R.id.charging_phone);
        mName = findViewById(R.id.charging_name);
        mSource = findViewById(R.id.charging_source);
        stepButton(R.id.charging_prev, -1);
        stepButton(R.id.charging_next, 1);

        LinearLayout chips = findViewById(R.id.charging_chips);
        speeds();
        for (Speed speed : mSpeeds) {
            TextView chip = mUi.chip(mThemed, speed.chip, true);
            chip.setOnClickListener(v -> {
                mSpeed = speed.speed;
                update();
                play();
            });
            chips.addView(chip);
            mSpeedChips.add(chip);
        }
        mSpeed = mSpeeds.get(mSpeeds.size() - 1).speed;

        mTry = findViewById(R.id.charging_try);
        mTry.setStrokeColor(ColorStateList.valueOf(mUi.accent));
        mTry.setTextColor(mUi.accent);
        mTry.setOnClickListener(v -> tryOnLockScreen());

        mTabs = findViewById(R.id.charging_tabs);
        mTabs.check(tabFor(mGroup));
        mTabs.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                mGroup = id == R.id.charging_tab_rr ? RR : id == R.id.charging_tab_makers ? MAKERS : ROMS;
                mAdapter.notifyDataSetChanged();
            }
        });
        RecyclerView grid = findViewById(R.id.charging_grid);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        mAdapter = new Adapter();
        grid.setAdapter(mAdapter);

        GradientDrawable dot = new GradientDrawable();
        dot.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        dot.setGradientCenter(0.5f, 0.8f);
        dot.setGradientRadius(26 * mUi.density);
        dot.setColors(new int[] {mUi.accent, 0xFF3A2A2E});
        dot.setShape(GradientDrawable.OVAL);
        findViewById(R.id.charging_effects_dot).setBackground(dot);
        mEffectsState = findViewById(R.id.charging_effects_state);
        findViewById(R.id.charging_effects).setOnClickListener(
                v -> startActivity(new Intent(this, ChargingEffectsActivity.class)));

        mApply = findViewById(R.id.charging_apply);
        mApply.setOnClickListener(v -> apply());
        update();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mSwitch = new MaterialSwitch(mThemed);
        mSwitch.setContentDescription(getString(R.string.charging_animation_switch));
        mSwitch.setChecked(!NONE.equals(mStyle));
        mSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked == !NONE.equals(mStyle)) {
                return;
            }
            mStyle = checked ? find(mApplied) != null ? mApplied : mStyles.get(0).key : NONE;
            update();
            play();
        });
        MenuItem item = menu.add(R.string.charging_animation_switch);
        item.setActionView(mSwitch);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mEffectsState.setText(ChargingEffectsActivity.summary(this));
        play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGeneration++;
        mHandler.removeCallbacksAndMessages(null);
        mStage.setEffect(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLoader.shutdownNow();
    }

    private int tabFor(String group) {
        return RR.equals(group) ? R.id.charging_tab_rr
                : MAKERS.equals(group) ? R.id.charging_tab_makers : R.id.charging_tab_roms;
    }

    private void stepButton(int id, int dir) {
        ImageButton button = findViewById(id);
        button.setBackground(mUi.shape(0x1FFFFFFF, 0, 18, 0));
        button.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        button.setOnClickListener(v -> step(dir));
    }

    private void step(int dir) {
        Style current = find(mStyle);
        int i = current != null ? mStyles.indexOf(current) : -1;
        choose(mStyles.get(Math.floorMod(i + dir, mStyles.size())));
    }

    private void choose(Style style) {
        mStyle = style.key;
        if (!style.group.equals(mGroup)) {
            mGroup = style.group;
            mTabs.check(tabFor(mGroup));
        }
        if (mSwitch != null) {
            mSwitch.setChecked(true);
        }
        update();
        play();
    }

    private void speeds() {
        int fast = getResources().getInteger(com.android.internal.R.integer.config_chargingFastWatts);
        int max = getResources().getInteger(com.android.internal.R.integer.config_chargingMaxWatts);
        String brand = getString(com.android.internal.R.string.config_chargingMaxLabel);
        mSpeeds.add(new Speed(RrChargingView.SPEED_NORMAL, getString(R.string.charging_speed_normal),
                getString(R.string.charging_label_normal)));
        String fastLabel = getString(R.string.charging_label_fast);
        mSpeeds.add(new Speed(RrChargingView.SPEED_FAST,
                fast > 0 ? watts(R.string.charging_speed_fast, fast) : getString(R.string.charging_speed_fast),
                fast > 0 ? getString(R.string.charging_watts, fastLabel, fast) : fastLabel));
        if (max > 0) {
            String chip = brand.isEmpty() ? getString(R.string.charging_speed_super) : brand;
            String label = brand.isEmpty() ? getString(R.string.charging_label_super) : brand;
            mSpeeds.add(new Speed(RrChargingView.SPEED_SUPER, getString(R.string.charging_watts, chip, max),
                    getString(R.string.charging_watts, label, max)));
        }
    }

    private String watts(int name, int w) {
        return getString(R.string.charging_watts, getString(name), w);
    }

    private Speed speed() {
        for (Speed speed : mSpeeds) {
            if (speed.speed == mSpeed) {
                return speed;
            }
        }
        return mSpeeds.get(0);
    }

    private void update() {
        Style style = find(mStyle);
        boolean on = style != null;
        mName.setText(on ? style.name : R.string.charging_off);
        mSource.setText(on ? style.source : R.string.charging_off_source);
        for (int i = 0; i < mSpeedChips.size(); i++) {
            mUi.setStageChip(mSpeedChips.get(i), mSpeeds.get(i).speed == mSpeed);
        }
        mTry.setEnabled(on);
        mAdapter.notifyDataSetChanged();
        boolean pending = !Objects.equals(mStyle, mApplied);
        mApply.setEnabled(pending);
        mApply.setText(pending ? R.string.charging_apply : R.string.charging_applied);
    }

    private void apply() {
        Settings.System.putStringForUser(getContentResolver(), SETTING, mStyle, UserHandle.USER_CURRENT);
        mApplied = mStyle;
        update();
    }

    private void tryOnLockScreen() {
        if (find(mStyle) == null) {
            return;
        }
        Settings.System.putStringForUser(getContentResolver(), PREVIEW,
                mStyle + ":" + SystemClock.uptimeMillis(), UserHandle.USER_CURRENT);
    }

    private void play() {
        int generation = ++mGeneration;
        mHandler.removeCallbacksAndMessages(null);
        mStage.setStatus(null);
        if (find(mStyle) == null) {
            mStage.setEffect(null);
            mStage.setStatus(getString(R.string.charging_off_source));
            return;
        }
        Runnable again = () -> {
            if (generation == mGeneration) {
                mHandler.postDelayed(this::play, REPLAY_MS);
            }
        };
        int level = ChargingUi.batteryLevel(this);
        RrChargingView drawn = drawn(mStyle);
        if (drawn != null) {
            drawn.setLevel(level);
            drawn.setSpeed(mSpeed);
            drawn.setLabel(speed().label);
            drawn.setPortX(0.5f);
            drawn.setPortY(1f);
            mStage.setEffect(drawn);
            mHandler.post(() -> drawn.play(again));
            return;
        }
        switch (mStyle) {
            case "bliss_flash":
            case "bliss_glow":
            case "bliss_battery": {
                BlissChargingAnimationView bliss = new BlissChargingAnimationView(this);
                int style = "bliss_flash".equals(mStyle) ? BlissChargingAnimationView.STYLE_FLASH
                        : "bliss_glow".equals(mStyle) ? BlissChargingAnimationView.STYLE_GLOW_PULSE
                        : BlissChargingAnimationView.STYLE_BATTERY_FILL;
                bliss.configure(style, level, again);
                mStage.setEffect(bliss);
                mHandler.post(bliss::startAnimation);
                break;
            }
            case "pixel": {
                RippleView ripple = new RippleView(this, null);
                ripple.setupShader(RippleShader.RippleShape.CIRCLE);
                ripple.setColor(Utils.getColorAttrDefaultColor(this, android.R.attr.colorAccent),
                        RippleShader.RIPPLE_DEFAULT_ALPHA);
                float max = Math.max(mStage.screenWidth(), mStage.screenHeight()) * 2f;
                ripple.setMaxSize(max, max);
                ripple.setCenter(mStage.screenWidth() / 2f, mStage.screenHeight());
                mStage.setEffect(ripple);
                mHandler.post(() -> ripple.startRipple(again));
                break;
            }
            case "oneplus":
                mStage.setEffect(new ChargingSketchView(this, ChargingSketchView.ONEPLUS, level, mLoader));
                break;
            case "moto":
                mStage.setEffect(new ChargingSketchView(this, ChargingSketchView.MOTO, level, mLoader));
                break;
            case "nothing":
                mStage.setEffect(new ChargingSketchView(this, ChargingSketchView.NOTHING, level, mLoader));
                break;
            default:
                mStage.setEffect(null);
        }
    }

    private RrChargingView drawn(String key) {
        switch (key) {
            case "rr_neon":
                return new NeonRevivalView(this);
            case "rr_defib":
                return new DefibrillatorView(this);
            case "rr_pulse":
                return new PulseRingView(this);
            case "oneui":
                return new OneUiCapsuleView(this);
            case "hyperos":
                return new HyperBubblesView(this);
            case "originos":
                return new OriginOrbView(this);
            case "supercharge":
                return new SuperChargeView(this);
            default:
                return null;
        }
    }

    private List<Style> grid() {
        List<Style> list = new ArrayList<>();
        for (Style style : mStyles) {
            if (style.group.equals(mGroup)) {
                list.add(style);
            }
        }
        return list;
    }

    private static final class Style {
        final String key;
        final String group;
        final int name;
        final int source;

        Style(String key, String group, int name, int source) {
            this.key = key;
            this.group = group;
            this.name = name;
            this.source = source;
        }
    }

    private static final class Speed {
        final int speed;
        final String chip;
        final String label;

        Speed(int speed, String chip, String label) {
            this.speed = speed;
            this.chip = chip;
            this.label = label;
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_charging_tile, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Style style = grid().get(position);
            boolean selected = style.key.equals(mStyle);
            MaterialCardView card = (MaterialCardView) holder.itemView;
            holder.icon.setImageDrawable(new ChargingGlyph(style.key));
            holder.name.setText(style.name);
            card.setStrokeColor(selected ? mUi.accent : Color.TRANSPARENT);
            card.setCardBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(card,
                    selected ? com.google.android.material.R.attr.colorSecondaryContainer
                            : com.google.android.material.R.attr.colorSurfaceContainerHigh)));
            card.setSelected(selected);
            card.setContentDescription(getString(style.name));
            card.setOnClickListener(v -> choose(style));
        }

        @Override
        public int getItemCount() {
            return grid().size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;

        Holder(View view) {
            super(view);
            icon = view.findViewById(R.id.charging_tile_icon);
            name = view.findViewById(R.id.charging_tile_name);
        }
    }
}
