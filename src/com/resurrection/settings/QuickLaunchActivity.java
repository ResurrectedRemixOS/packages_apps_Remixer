/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fingerprint quick launch: the switch, up to five shortcuts and how they behave. SystemUI's
 * QuickLaunchController reads the same settings.
 */
public class QuickLaunchActivity extends CollapsingToolbarBaseActivity {
    private static final String KEY_ENABLED = "fp_quick_launch";
    private static final String KEY_SHORTCUTS = "fp_quick_launch_shortcuts";
    private static final String KEY_HOLD = "fp_quick_launch_hold";
    private static final String KEY_HAPTICS = "fp_quick_launch_haptics";
    private static final String DEFAULT_SHORTCUTS = "camera,flashlight,qr,timer";
    private static final String APP = "app:";
    private static final int MAX = 5;
    private static final String[] BUILT_IN = {"camera", "video", "flashlight", "qr", "timer"};
    private static final int[] BUILT_IN_LABELS = {R.string.quick_launch_camera,
            R.string.quick_launch_video, R.string.quick_launch_flashlight, R.string.quick_launch_qr,
            R.string.quick_launch_timer};
    private static final int[] BUILT_IN_ICONS = {R.drawable.remixer_ql_camera,
            R.drawable.remixer_ql_video, R.drawable.remixer_ql_flashlight,
            R.drawable.remixer_ql_qr, R.drawable.remixer_ql_timer};
    private static final int[] HOLD_MS = {300, 500, 800};

    private final List<String> mTokens = new ArrayList<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Adapter mAdapter;
    private RecyclerView mSlots;
    private TextView mHoldValue;
    private boolean mMoved;

    /** Quick launch needs an in-display sensor. */
    public static boolean isSupported(Context context) {
        FingerprintManager fingerprint = context.getSystemService(FingerprintManager.class);
        if (fingerprint == null) {
            return false;
        }
        for (FingerprintSensorPropertiesInternal props : fingerprint.getSensorPropertiesInternal()) {
            if (props.isAnyUdfpsType()) {
                return true;
            }
        }
        return false;
    }

    /** For the Remixer entry. */
    public static String summary(Context context) {
        if (Settings.System.getIntForUser(context.getContentResolver(), KEY_ENABLED, 0,
                UserHandle.USER_CURRENT) != 1) {
            return context.getString(R.string.quick_launch_off);
        }
        int count = tokens(context).size();
        return context.getResources().getQuantityString(R.plurals.quick_launch_on, count, count);
    }

    private static List<String> tokens(Context context) {
        String value = Settings.System.getStringForUser(context.getContentResolver(),
                KEY_SHORTCUTS, UserHandle.USER_CURRENT);
        List<String> tokens = new ArrayList<>();
        for (String token : (value != null ? value : DEFAULT_SHORTCUTS).split(",")) {
            token = token.trim();
            if (!token.isEmpty() && !tokens.contains(token) && tokens.size() < MAX) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_quick_launch);
        setTitle(R.string.quick_launch_title);
        mTokens.addAll(tokens(this));

        MaterialSwitch main = findViewById(R.id.ql_main_switch);
        main.setChecked(getInt(KEY_ENABLED, 0) == 1);
        findViewById(R.id.ql_main_row).setOnClickListener(v -> main.toggle());
        main.setOnCheckedChangeListener((button, checked) -> putInt(KEY_ENABLED, checked ? 1 : 0));

        mSlots = findViewById(R.id.ql_slots);
        mSlots.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        mAdapter = new Adapter();
        mSlots.setAdapter(mAdapter);
        new ItemTouchHelper(new DragCallback()).attachToRecyclerView(mSlots);

        mHoldValue = findViewById(R.id.ql_hold_value);
        updateHold();
        findViewById(R.id.ql_hold_row).setOnClickListener(v -> pickHold());

        View haptics = findViewById(R.id.ql_haptics_row);
        ((TextView) haptics.findViewById(R.id.clock_row_title)).setText(R.string.quick_launch_haptics);
        haptics.findViewById(R.id.clock_row_summary).setVisibility(View.GONE);
        MaterialSwitch hapticsSwitch = haptics.findViewById(R.id.clock_row_switch);
        hapticsSwitch.setChecked(getInt(KEY_HAPTICS, 1) == 1);
        haptics.setOnClickListener(v -> hapticsSwitch.toggle());
        hapticsSwitch.setOnCheckedChangeListener(
                (button, checked) -> putInt(KEY_HAPTICS, checked ? 1 : 0));
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    /** Material dialogs need the layout's Material3 theme, not the activity's. */
    private Context themed() {
        return mSlots.getContext();
    }

    private int getInt(String key, int def) {
        return Settings.System.getIntForUser(getContentResolver(), key, def,
                UserHandle.USER_CURRENT);
    }

    private void putInt(String key, int value) {
        Settings.System.putIntForUser(getContentResolver(), key, value, UserHandle.USER_CURRENT);
    }

    private void save() {
        Settings.System.putStringForUser(getContentResolver(), KEY_SHORTCUTS,
                TextUtils.join(",", mTokens), UserHandle.USER_CURRENT);
        mAdapter.notifyDataSetChanged();
    }

    private void updateHold() {
        int hold = getInt(KEY_HOLD, 500);
        String[] entries = getResources().getStringArray(R.array.quick_launch_hold_entries);
        mHoldValue.setText(entries[holdIndex(hold)]);
    }

    private static int holdIndex(int hold) {
        int best = 0;
        for (int i = 1; i < HOLD_MS.length; i++) {
            if (Math.abs(HOLD_MS[i] - hold) < Math.abs(HOLD_MS[best] - hold)) {
                best = i;
            }
        }
        return best;
    }

    private void pickHold() {
        new MaterialAlertDialogBuilder(themed())
                .setTitle(R.string.quick_launch_hold)
                .setSingleChoiceItems(R.array.quick_launch_hold_entries,
                        holdIndex(getInt(KEY_HOLD, 500)), (dialog, which) -> {
                            putInt(KEY_HOLD, HOLD_MS[which]);
                            updateHold();
                            dialog.dismiss();
                        })
                .show();
    }

    /** Replaces the shortcut at {@code position}, or adds one when it's -1. */
    private void choose(int position) {
        CharSequence[] items = new CharSequence[BUILT_IN.length + 1];
        for (int i = 0; i < BUILT_IN.length; i++) {
            items[i] = getString(BUILT_IN_LABELS[i]);
        }
        items[BUILT_IN.length] = getString(R.string.quick_launch_app);
        new MaterialAlertDialogBuilder(themed())
                .setTitle(position < 0 ? R.string.quick_launch_add : R.string.quick_launch_shortcuts)
                .setItems(items, (dialog, which) -> {
                    if (which < BUILT_IN.length) {
                        set(position, BUILT_IN[which]);
                    } else {
                        pickApp(position);
                    }
                })
                .show();
    }

    private void set(int position, String token) {
        // A shortcut already in the list moves instead of showing twice.
        int existing = mTokens.indexOf(token);
        if (existing >= 0 && existing != position) {
            mTokens.remove(existing);
            if (position > existing) {
                position--;
            }
        }
        if (position < 0 || position >= mTokens.size()) {
            if (mTokens.size() < MAX) {
                mTokens.add(token);
            }
        } else {
            mTokens.set(position, token);
        }
        save();
    }

    private void remove(int position) {
        if (position < 0 || position >= mTokens.size()) {
            return;
        }
        String token = mTokens.remove(position);
        save();
        Snackbar.make(mSlots, getString(R.string.quick_launch_removed, label(token)),
                Snackbar.LENGTH_LONG)
                .setAction(R.string.quick_launch_undo, v -> {
                    mTokens.add(Math.min(position, mTokens.size()), token);
                    save();
                })
                .show();
    }

    private static class App {
        final CharSequence label;
        final Drawable icon;
        final ComponentName component;

        App(CharSequence label, Drawable icon, ComponentName component) {
            this.label = label;
            this.icon = icon;
            this.component = component;
        }
    }

    private void pickApp(int position) {
        mExecutor.execute(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<App> apps = new ArrayList<>();
            for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
                ActivityInfo activity = info.activityInfo;
                apps.add(new App(info.loadLabel(pm), info.loadIcon(pm),
                        new ComponentName(activity.packageName, activity.name)));
            }
            Collator collator = Collator.getInstance();
            apps.sort((a, b) -> collator.compare(a.label.toString(), b.label.toString()));
            mHandler.post(() -> showApps(position, apps));
        });
    }

    private void showApps(int position, List<App> apps) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        ArrayAdapter<App> adapter = new ArrayAdapter<App>(themed(),
                R.layout.remixer_quick_launch_app_row, apps) {
            @NonNull
            @Override
            public View getView(int index, View convertView, @NonNull ViewGroup parent) {
                View row = convertView != null ? convertView : LayoutInflater.from(getContext())
                        .inflate(R.layout.remixer_quick_launch_app_row, parent, false);
                App app = getItem(index);
                ((ImageView) row.findViewById(R.id.ql_app_icon)).setImageDrawable(app.icon);
                ((TextView) row.findViewById(R.id.ql_app_label)).setText(app.label);
                return row;
            }
        };
        new MaterialAlertDialogBuilder(themed())
                .setTitle(R.string.quick_launch_app)
                .setAdapter(adapter, (dialog, which) ->
                        set(position, APP + apps.get(which).component.flattenToString()))
                .show();
    }

    private int builtInIndex(String token) {
        for (int i = 0; i < BUILT_IN.length; i++) {
            if (BUILT_IN[i].equals(token)) {
                return i;
            }
        }
        return -1;
    }

    private CharSequence label(String token) {
        int index = builtInIndex(token);
        if (index >= 0) {
            return getString(BUILT_IN_LABELS[index]);
        }
        ActivityInfo info = appInfo(token);
        return info != null ? info.loadLabel(getPackageManager()) : token;
    }

    private ActivityInfo appInfo(String token) {
        if (!token.startsWith(APP)) {
            return null;
        }
        ComponentName component = ComponentName.unflattenFromString(token.substring(APP.length()));
        if (component == null) {
            return null;
        }
        try {
            return getPackageManager().getActivityInfo(component, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return mTokens.size() + (mTokens.size() < MAX ? 1 : 0);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_quick_launch_slot, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(position);
        }
    }

    private class Holder extends RecyclerView.ViewHolder {
        private final View mCircle;
        private final ImageView mIcon;
        private final TextView mLabel;

        Holder(View view) {
            super(view);
            mCircle = view.findViewById(R.id.ql_slot_circle);
            mIcon = view.findViewById(R.id.ql_slot_icon);
            mLabel = view.findViewById(R.id.ql_slot_label);
        }

        void bind(int position) {
            ColorStateList tint = ColorStateList.valueOf(MaterialColors.getColor(itemView,
                    com.google.android.material.R.attr.colorOnSurface));
            if (position >= mTokens.size()) {
                mCircle.setBackgroundResource(R.drawable.remixer_ql_slot_add);
                mIcon.setImageResource(R.drawable.remixer_ql_add);
                mIcon.setImageTintList(tint);
                mLabel.setText(R.string.quick_launch_add);
                itemView.setOnClickListener(v -> choose(-1));
            } else {
                String token = mTokens.get(position);
                mCircle.setBackgroundResource(R.drawable.remixer_ql_slot);
                int index = builtInIndex(token);
                if (index >= 0) {
                    mIcon.setImageResource(BUILT_IN_ICONS[index]);
                    mIcon.setImageTintList(tint);
                } else {
                    ActivityInfo info = appInfo(token);
                    mIcon.setImageDrawable(info != null
                            ? info.loadIcon(getPackageManager()) : null);
                    mIcon.setImageTintList(null);
                }
                mLabel.setText(label(token));
                itemView.setOnClickListener(v -> choose(getBindingAdapterPosition()));
            }
            itemView.setContentDescription(mLabel.getText());
        }
    }

    /** Long-press and drag to reorder; long-press without moving removes. */
    private class DragCallback extends ItemTouchHelper.Callback {
        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder holder) {
            return holder.getBindingAdapterPosition() < mTokens.size()
                    ? makeMovementFlags(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) : 0;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
            int a = from.getBindingAdapterPosition();
            int b = to.getBindingAdapterPosition();
            if (a < 0 || b < 0 || a >= mTokens.size() || b >= mTokens.size()) {
                return false;
            }
            Collections.swap(mTokens, a, b);
            mAdapter.notifyItemMoved(a, b);
            mMoved = true;
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder holder, int actionState) {
            super.onSelectedChanged(holder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && holder != null) {
                mMoved = false;
                holder.itemView.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start();
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder holder) {
            super.clearView(recyclerView, holder);
            holder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            int position = holder.getBindingAdapterPosition();
            // Posted: the adapter can't change while RecyclerView is still laying out the drop.
            mSlots.post(() -> {
                if (mMoved) {
                    save();
                } else {
                    remove(position);
                }
            });
        }
    }
}
