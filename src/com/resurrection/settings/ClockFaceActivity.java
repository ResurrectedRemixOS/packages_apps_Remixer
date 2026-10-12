/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings;

import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.LruCache;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.util.rr.Utils;
import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Lock screen clock faces: a stage showing the picked face on the lock screen wallpaper, a strip
 * of every face, and the look settings. SystemUI's ClockStyle reads the same Settings.Secure
 * names and its ClockFacePreviewProvider draws the previews.
 */
public class ClockFaceActivity extends CollapsingToolbarBaseActivity {
    private static final String AUTHORITY = "com.android.systemui.clockfaces";
    private static final String METHOD_RENDER = "render";
    private static final String EXTRA_STYLE = "style";
    private static final String EXTRA_WIDTH = "width";
    private static final String EXTRA_HEIGHT_FRACTION = "height_fraction";
    private static final String EXTRA_PNG = "png";
    private static final String STATE_SELECTED = "selected";

    // Part of the lock screen shown, from the top: the clock and what sits under it.
    private static final float REGION = 0.62f;

    private static final String KEY_STYLE = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE;
    private static final String KEY_COLOR_MODE = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_COLOR_MODE;
    private static final String KEY_CUSTOM_COLOR =
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_CUSTOM_COLOR;
    private static final String KEY_OPACITY = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_OPACITY;
    private static final String KEY_TOP = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_MARGIN_TOP;
    private static final String KEY_START = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_MARGIN_START;
    private static final String KEY_SIZE = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_SIZE;
    private static final String KEY_AOD_ANIM = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_AOD_ANIM;
    private static final String KEY_ALBUM_ART =
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_ALBUM_ART_COLOR;
    private static final String KEY_WOBBLE =
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_WOBBLE_ON_CHARGE;
    private static final String KEY_GRADIENT =
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_ENABLED;
    private static final String KEY_GRADIENT_START =
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_COLOR_START;
    private static final String KEY_GRADIENT_END =
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_COLOR_END;

    // ClockStyle's color modes.
    private static final String MODE_DEFAULT = "default";
    private static final String MODE_ACCENT = "accent";
    private static final String MODE_CUSTOM = "custom";

    private static final int COLOR_DEFAULT = 0;
    private static final int COLOR_ACCENT = 1;
    private static final int COLOR_CUSTOM = 2;
    private static final int COLOR_ALBUM = 3;
    private static final int COLOR_GRADIENT = 4;
    private static final int[] COLOR_CHIPS = {R.id.clock_color_default, R.id.clock_color_accent,
            R.id.clock_color_custom, R.id.clock_color_album, R.id.clock_color_gradient};

    private static final int[] SWATCHES = {0xFFE02437, 0xFFFF7A1A, 0xFF3DDC84, 0xFF1EC8C8,
            0xFF3D8BFF, 0xFF8C5CFF, 0xFFFF4FA3, 0xFFFFFFFF};
    // Start and end of each gradient preset; the first is ClockStyle's default.
    private static final int[][] GRADIENTS = {{0xFF00E5FF, 0xFFFF2DAA},
            {0xFFE02437, 0xFFFF9A3D}, {0xFFFF5F6D, 0xFFFFC371}, {0xFF3D8BFF, 0xFF8C5CFF},
            {0xFF3DDC84, 0xFF1EC8C8}, {0xFFFFFFFF, 0xFF9AA0A6}};

    // Faces that ignore the color settings or the horizontal offset, as in ClockStyle.
    private static final Set<Integer> NO_COLOR = Set.of(1, 2, 25, 26);
    private static final Set<Integer> NO_HORIZONTAL = Set.of(45, 73, 74);

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final LruCache<Integer, Bitmap> mThumbs = new LruCache<>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(Integer key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final Set<Integer> mWantedThumbs = new HashSet<>();
    private final Set<Integer> mLoadingThumbs = new HashSet<>();
    private final Runnable mLookChanged = this::onLookChanged;

    private String[] mNames;
    private int mApplied;
    private int mSelected;
    private int mStageGeneration;
    private int mThumbGeneration;
    private boolean mGuard;
    private boolean mTracking;
    private volatile boolean mPreviewFailed;
    private int mScreenWidth;
    private int mRegionHeight;
    private Bitmap mWallpaper;

    private MaterialCardView mStageCard;
    private FrameLayout mStage;
    private ImageView mStageWallpaper;
    private ImageView mStageFace;
    private TextView mStageMessage;
    private ProgressBar mStageProgress;
    private TextView mName;
    private TextView mPosition;
    private MaterialButton mApply;
    private FaceAdapter mAdapter;
    private RecyclerView mStrip;
    private View mLook;
    private ChipGroup mColorGroup;
    private TextView mColorHint;
    private LinearLayout mSwatches;
    private TextView mSizeLabel;
    private TextView mTopLabel;
    private TextView mStartLabel;
    private TextView mOpacityLabel;
    private Slider mStartSlider;

    /** Name of the applied face, for the Remixer entry summary. */
    public static String currentName(Context context) {
        String[] names = context.getResources().getStringArray(R.array.clock_face_names);
        int style = Settings.Secure.getIntForUser(context.getContentResolver(), KEY_STYLE, 0,
                UserHandle.USER_CURRENT);
        return style >= 0 && style < names.length ? names[style] : names[0];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_clock_faces);
        setTitle(R.string.remixer_clock_faces);
        mNames = getResources().getStringArray(R.array.clock_face_names);
        mApplied = clampStyle(getInt(KEY_STYLE, 0));
        mSelected = savedInstanceState != null
                ? clampStyle(savedInstanceState.getInt(STATE_SELECTED, mApplied)) : mApplied;

        Rect bounds = getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        mScreenWidth = bounds.width();
        mRegionHeight = Math.round(bounds.height() * REGION);

        mStageCard = findViewById(R.id.clock_stage_card);
        mStage = findViewById(R.id.clock_stage);
        mStageWallpaper = findViewById(R.id.clock_stage_wallpaper);
        mStageFace = findViewById(R.id.clock_stage_face);
        mStageMessage = findViewById(R.id.clock_stage_message);
        mStageProgress = findViewById(R.id.clock_stage_progress);
        mName = findViewById(R.id.clock_face_name);
        mPosition = findViewById(R.id.clock_face_position);
        mApply = findViewById(R.id.clock_face_apply);
        mStrip = findViewById(R.id.clock_face_strip);
        mLook = findViewById(R.id.clock_look);
        mColorGroup = findViewById(R.id.clock_color_mode);
        mColorHint = findViewById(R.id.clock_color_hint);
        mSwatches = findViewById(R.id.clock_swatches);
        mSizeLabel = findViewById(R.id.clock_size_label);
        mTopLabel = findViewById(R.id.clock_top_label);
        mStartLabel = findViewById(R.id.clock_start_label);
        mOpacityLabel = findViewById(R.id.clock_opacity_label);
        mStartSlider = findViewById(R.id.clock_start);

        // The stage keeps the lock screen's proportions.
        mStageCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int height = Math.round((r - l) * (float) mRegionHeight / mScreenWidth);
            ViewGroup.LayoutParams lp = mStage.getLayoutParams();
            if (r > l && lp.height != height) {
                lp.height = height;
                mStage.post(() -> mStage.setLayoutParams(lp));
            }
        });

        mApply.setOnClickListener(v -> apply());

        mAdapter = new FaceAdapter();
        mStrip.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,
                false));
        mStrip.setAdapter(mAdapter);
        mStrip.scrollToPosition(Math.max(0, mSelected - 1));

        mColorGroup.check(COLOR_CHIPS[readColorChoice()]);
        mColorGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (mGuard || ids.isEmpty()) {
                return;
            }
            int choice = indexOf(COLOR_CHIPS, ids.get(0));
            if (choice != readColorChoice()) {
                writeColorChoice(choice);
            }
            updateColorViews();
            lookChanged();
        });

        bindSlider(findViewById(R.id.clock_size), KEY_SIZE, getInt(KEY_SIZE, 100), value ->
                mSizeLabel.setText(getString(R.string.clock_faces_size_value, value)));
        bindSlider(findViewById(R.id.clock_top), KEY_TOP, getInt(KEY_TOP, 15), value ->
                mTopLabel.setText(getString(R.string.clock_faces_top_value, value)));
        bindSlider(mStartSlider, KEY_START, getInt(KEY_START, 0), this::updateStartLabel);
        bindSlider(findViewById(R.id.clock_opacity), KEY_OPACITY, getInt(KEY_OPACITY, 100),
                value -> mOpacityLabel.setText(getString(R.string.clock_faces_opacity_value,
                        value)));

        bindSwitchRow(findViewById(R.id.clock_wobble_row), R.string.clock_faces_wobble,
                R.string.clock_faces_wobble_summary, KEY_WOBBLE);
        bindSwitchRow(findViewById(R.id.clock_aod_row), R.string.clock_faces_aod_anim,
                R.string.clock_faces_aod_anim_summary, KEY_AOD_ANIM);

        updateColorViews();
        updateSelection();
        mExecutor.execute(this::loadWallpaper);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED, mSelected);
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    private void select(int style) {
        if (style == mSelected) {
            return;
        }
        int old = mSelected;
        mSelected = style;
        mAdapter.notifyItemChanged(old);
        mAdapter.notifyItemChanged(style);
        updateSelection();
    }

    private void updateSelection() {
        mName.setText(mNames[mSelected]);
        mPosition.setText(mSelected == 0 ? getString(R.string.clock_faces_system_desc)
                : getString(R.string.clock_faces_position, mSelected, mNames.length - 1));
        boolean applied = mSelected == mApplied;
        mApply.setEnabled(!applied);
        mApply.setText(applied ? R.string.clock_faces_applied : R.string.clock_faces_apply);
        setTreeEnabled(mLook, mSelected != 0);
        mLook.setAlpha(mSelected != 0 ? 1f : 0.5f);
        updateColorViews();
        updateStartLabel(Math.round(mStartSlider.getValue()));
        renderStage();
    }

    private void apply() {
        if (mSelected == mApplied) {
            return;
        }
        if ((mApplied == 0) != (mSelected == 0)) {
            // The lock screen sets custom faces up when SystemUI starts.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.clock_faces_restart_title)
                    .setMessage(R.string.clock_faces_restart_message)
                    .setPositiveButton(R.string.clock_faces_restart, (d, w) -> {
                        if (writeStyle()) {
                            Utils.restartSystemUI();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            writeStyle();
        }
    }

    private boolean writeStyle() {
        if (!putInt(KEY_STYLE, mSelected)) {
            return false;
        }
        int old = mApplied;
        mApplied = mSelected;
        mAdapter.notifyItemChanged(old);
        mAdapter.notifyItemChanged(mApplied);
        updateSelection();
        return true;
    }

    // Color

    private int readColorChoice() {
        if (getInt(KEY_GRADIENT, 0) != 0) {
            return COLOR_GRADIENT;
        }
        if (getInt(KEY_ALBUM_ART, 0) != 0) {
            return COLOR_ALBUM;
        }
        String mode = readString(KEY_COLOR_MODE);
        if (MODE_ACCENT.equals(mode)) {
            return COLOR_ACCENT;
        }
        return MODE_CUSTOM.equals(mode) ? COLOR_CUSTOM : COLOR_DEFAULT;
    }

    private void writeColorChoice(int choice) {
        putInt(KEY_GRADIENT, choice == COLOR_GRADIENT ? 1 : 0);
        putInt(KEY_ALBUM_ART, choice == COLOR_ALBUM ? 1 : 0);
        switch (choice) {
            case COLOR_DEFAULT -> putString(KEY_COLOR_MODE, MODE_DEFAULT);
            case COLOR_ACCENT -> putString(KEY_COLOR_MODE, MODE_ACCENT);
            case COLOR_CUSTOM -> putString(KEY_COLOR_MODE, MODE_CUSTOM);
            default -> { }
        }
    }

    private void updateColorViews() {
        int choice = readColorChoice();
        if (NO_COLOR.contains(mSelected)) {
            mColorHint.setText(R.string.clock_faces_own_colors);
            mColorHint.setVisibility(View.VISIBLE);
        } else if (choice == COLOR_ALBUM) {
            mColorHint.setText(R.string.clock_faces_album_hint);
            mColorHint.setVisibility(View.VISIBLE);
        } else {
            mColorHint.setVisibility(View.GONE);
        }
        mSwatches.removeAllViews();
        if (choice == COLOR_CUSTOM) {
            String[] names = getResources().getStringArray(R.array.pulse_swatch_names);
            int current = getInt(KEY_CUSTOM_COLOR, Color.WHITE) | 0xFF000000;
            for (int i = 0; i < SWATCHES.length; i++) {
                final int color = SWATCHES[i];
                addSwatch(new int[] {color, color}, names[i], color == current, () -> {
                    putInt(KEY_CUSTOM_COLOR, color);
                    updateColorViews();
                    lookChanged();
                });
            }
        } else if (choice == COLOR_GRADIENT) {
            String[] names = getResources().getStringArray(R.array.clock_faces_gradient_names);
            int start = getInt(KEY_GRADIENT_START, GRADIENTS[0][0]) | 0xFF000000;
            int end = getInt(KEY_GRADIENT_END, GRADIENTS[0][1]) | 0xFF000000;
            for (int i = 0; i < GRADIENTS.length; i++) {
                final int[] pair = GRADIENTS[i];
                addSwatch(pair, names[i], pair[0] == start && pair[1] == end, () -> {
                    putInt(KEY_GRADIENT_START, pair[0]);
                    putInt(KEY_GRADIENT_END, pair[1]);
                    updateColorViews();
                    lookChanged();
                });
            }
        }
        mSwatches.setVisibility(mSwatches.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void addSwatch(int[] colors, String name, boolean selected, Runnable onClick) {
        int size = dp(40);
        FrameLayout cell = new FrameLayout(mSwatches.getContext());
        ImageView dot = new ImageView(mSwatches.getContext());
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        fill.setShape(GradientDrawable.OVAL);
        dot.setBackground(fill);
        dot.setImageResource(R.drawable.remixer_ic_pulse_check);
        dot.setImageTintList(ColorStateList.valueOf(
                Color.luminance(colors[0]) > 0.6f ? Color.BLACK : Color.WHITE));
        dot.setImageAlpha(selected ? 255 : 0);
        dot.setScaleType(ImageView.ScaleType.CENTER);
        dot.setScaleX(selected ? 1.15f : 1f);
        dot.setScaleY(selected ? 1.15f : 1f);
        dot.setSelected(selected);
        dot.setContentDescription(name);
        dot.setOnClickListener(v -> onClick.run());
        cell.addView(dot, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        mSwatches.addView(cell, new LinearLayout.LayoutParams(0, dp(48), 1f));
    }

    // Size, position and motion

    private void updateStartLabel(int value) {
        if (NO_HORIZONTAL.contains(mSelected)) {
            mStartLabel.setText(R.string.clock_faces_horizontal_fixed);
        } else if (value == 0) {
            mStartLabel.setText(R.string.clock_faces_horizontal_center);
        } else {
            mStartLabel.setText(getString(value > 0 ? R.string.clock_faces_horizontal_right
                    : R.string.clock_faces_horizontal_left, Math.abs(value)));
        }
    }

    private void bindSlider(Slider slider, String key, int initial, IntConsumer onValue) {
        float step = slider.getStepSize();
        float value = Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), initial));
        if (step > 0) {
            value = slider.getValueFrom()
                    + Math.round((value - slider.getValueFrom()) / step) * step;
        }
        slider.setValue(value);
        onValue.accept(Math.round(value));
        slider.setLabelFormatter(v -> String.valueOf(Math.round(v)));
        slider.addOnChangeListener((s, v, fromUser) -> {
            onValue.accept(Math.round(v));
            if (fromUser && !mTracking) {
                putInt(key, Math.round(v));
                lookChanged();
            }
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider s) {
                mTracking = true;
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider s) {
                mTracking = false;
                putInt(key, Math.round(s.getValue()));
                lookChanged();
            }
        });
    }

    private void bindSwitchRow(View row, int title, int summary, String key) {
        ((TextView) row.findViewById(R.id.clock_row_title)).setText(title);
        ((TextView) row.findViewById(R.id.clock_row_summary)).setText(summary);
        MaterialSwitch toggle = row.findViewById(R.id.clock_row_switch);
        toggle.setChecked(getInt(key, 1) != 0);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!putInt(key, checked ? 1 : 0)) {
                button.setChecked(!checked);
            }
        });
        row.setOnClickListener(v -> toggle.toggle());
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Switch.class.getName());
                info.setCheckable(true);
                info.setChecked(toggle.isChecked());
            }
        });
    }

    // Previews

    private void lookChanged() {
        mHandler.removeCallbacks(mLookChanged);
        mHandler.postDelayed(mLookChanged, 150);
    }

    private void onLookChanged() {
        mThumbGeneration++;
        mThumbs.evictAll();
        mLoadingThumbs.clear();
        mAdapter.notifyItemRangeChanged(0, mNames.length);
        renderStage();
    }

    private void loadWallpaper() {
        Bitmap region = null;
        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            Drawable drawable = wm.getDrawable(WallpaperManager.FLAG_LOCK);
            if (drawable == null) {
                drawable = wm.getDrawable(WallpaperManager.FLAG_SYSTEM);
            }
            if (drawable != null) {
                region = lockScreenRegion(drawable);
            }
        } catch (RuntimeException e) {
            // Falls back to the plain stage.
        }
        final Bitmap result = region;
        mHandler.post(() -> {
            mWallpaper = result;
            mStageWallpaper.setImageBitmap(result);
            mAdapter.notifyItemRangeChanged(0, mNames.length);
        });
    }

    /** The wallpaper as the lock screen crops it, cut to the stage's part of the screen. */
    private Bitmap lockScreenRegion(Drawable drawable) {
        int width = Math.min(mScreenWidth, 1080);
        float scale = (float) width / mScreenWidth;
        int screenHeight = Math.round(mRegionHeight / REGION * scale);
        int height = Math.round(mRegionHeight * scale);
        int dw = Math.max(1, drawable.getIntrinsicWidth());
        int dh = Math.max(1, drawable.getIntrinsicHeight());
        float fill = Math.max((float) width / dw, (float) screenHeight / dh);
        int left = Math.round((width - dw * fill) / 2f);
        int top = Math.round((screenHeight - dh * fill) / 2f);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        drawable.setBounds(left, top, left + Math.round(dw * fill), top + Math.round(dh * fill));
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    private void renderStage() {
        final int generation = ++mStageGeneration;
        final int style = mSelected;
        mStageFace.setImageDrawable(null);
        if (style == 0) {
            mStageProgress.setVisibility(View.GONE);
            mStageMessage.setText(R.string.clock_faces_system);
            mStageMessage.setVisibility(View.VISIBLE);
            return;
        }
        mStageMessage.setVisibility(View.GONE);
        mStageProgress.setVisibility(View.VISIBLE);
        final int width = Math.max(mStage.getWidth(), dp(320));
        mExecutor.execute(() -> {
            Bitmap face = render(style, width);
            mHandler.post(() -> {
                if (generation != mStageGeneration) {
                    return;
                }
                mStageProgress.setVisibility(View.GONE);
                if (face != null) {
                    mStageFace.setImageBitmap(face);
                    mStageFace.setContentDescription(mNames[style]);
                } else {
                    mStageMessage.setText(R.string.clock_faces_preview_unavailable);
                    mStageMessage.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void loadThumb(int style, int width) {
        if (mPreviewFailed || mLoadingThumbs.contains(style)) {
            return;
        }
        mLoadingThumbs.add(style);
        final int generation = mThumbGeneration;
        mExecutor.execute(() -> {
            if (!stillWanted(style)) {
                mHandler.post(() -> mLoadingThumbs.remove(style));
                return;
            }
            Bitmap face = render(style, width);
            mHandler.post(() -> {
                if (generation != mThumbGeneration) {
                    return;
                }
                mLoadingThumbs.remove(style);
                if (face != null) {
                    mThumbs.put(style, face);
                    mAdapter.notifyItemChanged(style);
                }
            });
        });
    }

    private boolean stillWanted(int style) {
        synchronized (mWantedThumbs) {
            return mWantedThumbs.contains(style);
        }
    }

    /** Asks SystemUI to draw a face; runs on the executor. */
    private Bitmap render(int style, int width) {
        Bundle extras = new Bundle();
        extras.putInt(EXTRA_STYLE, style);
        extras.putInt(EXTRA_WIDTH, width);
        extras.putFloat(EXTRA_HEIGHT_FRACTION, REGION);
        try {
            Bundle result = getContentResolver().call(AUTHORITY, METHOD_RENDER, null, extras);
            byte[] png = result != null ? result.getByteArray(EXTRA_PNG) : null;
            return png != null ? BitmapFactory.decodeByteArray(png, 0, png.length) : null;
        } catch (RuntimeException e) {
            // SystemUI without the provider.
            mPreviewFailed = true;
            return null;
        }
    }

    private class FaceAdapter extends RecyclerView.Adapter<FaceHolder> {
        @NonNull
        @Override
        public FaceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_clock_face_item, parent, false);
            return new FaceHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FaceHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public void onViewRecycled(@NonNull FaceHolder holder) {
            synchronized (mWantedThumbs) {
                mWantedThumbs.remove(holder.mStyle);
            }
        }

        @Override
        public int getItemCount() {
            return mNames.length;
        }
    }

    private class FaceHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView mCard;
        private final ImageView mWallpaperView;
        private final ImageView mFace;
        private final TextView mLabel;
        private final ImageView mAppliedBadge;
        private int mStyle;

        FaceHolder(View view) {
            super(view);
            mCard = (MaterialCardView) view;
            mWallpaperView = view.findViewById(R.id.clock_thumb_wallpaper);
            mFace = view.findViewById(R.id.clock_thumb_face);
            mLabel = view.findViewById(R.id.clock_thumb_label);
            mAppliedBadge = view.findViewById(R.id.clock_thumb_applied);
            int primary = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary);
            mAppliedBadge.setBackgroundTintList(ColorStateList.valueOf(primary));
            mAppliedBadge.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(view,
                    com.google.android.material.R.attr.colorOnPrimary)));
            view.setOnClickListener(v -> select(mStyle));
        }

        void bind(int style) {
            mStyle = style;
            synchronized (mWantedThumbs) {
                mWantedThumbs.add(style);
            }
            mWallpaperView.setImageBitmap(mWallpaper);
            mCard.setContentDescription(mNames[style]);
            boolean selected = style == mSelected;
            mCard.setStrokeColor(selected ? MaterialColors.getColor(mCard,
                    androidx.appcompat.R.attr.colorPrimary) : Color.TRANSPARENT);
            mCard.setSelected(selected);
            mAppliedBadge.setVisibility(style == mApplied ? View.VISIBLE : View.GONE);
            if (style == 0) {
                mFace.setImageDrawable(null);
                mLabel.setText(R.string.clock_faces_system);
                mLabel.setVisibility(View.VISIBLE);
                return;
            }
            Bitmap thumb = mThumbs.get(style);
            mFace.setImageBitmap(thumb);
            mLabel.setVisibility(View.GONE);
            if (thumb == null) {
                loadThumb(style, Math.max(itemView.getLayoutParams().width, dp(76)));
            }
        }
    }

    // Settings

    private int getInt(String key, int def) {
        return Settings.Secure.getIntForUser(getContentResolver(), key, def,
                UserHandle.USER_CURRENT);
    }

    private String readString(String key) {
        return Settings.Secure.getStringForUser(getContentResolver(), key,
                UserHandle.USER_CURRENT);
    }

    private boolean putInt(String key, int value) {
        return put(() -> Settings.Secure.putIntForUser(getContentResolver(), key, value,
                UserHandle.USER_CURRENT));
    }

    private boolean putString(String key, String value) {
        return put(() -> Settings.Secure.putStringForUser(getContentResolver(), key, value,
                UserHandle.USER_CURRENT));
    }

    private boolean put(BooleanSupplier write) {
        try {
            if (write.getAsBoolean()) {
                return true;
            }
        } catch (SecurityException ignored) {
            // Reported below.
        }
        Toast.makeText(this, R.string.clock_faces_failed, Toast.LENGTH_SHORT).show();
        return false;
    }

    private int clampStyle(int style) {
        return style >= 0 && style < mNames.length ? style : 0;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
