package com.resurrection.settings;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.color.MaterialColors;

/**
 * Under-display fingerprint icon and touch animation, drawn by SystemUI from the UdfpsIcons and
 * UdfpsAnimations resource packages. Index 0 is the stock icon and no animation.
 */
public class FingerprintActivity extends CollapsingToolbarBaseActivity {
    private static final String ICONS_PACKAGE = "org.evolution.udfps.icons";
    private static final String ANIMATIONS_PACKAGE = "org.evolution.udfps.animations";
    private static final int ICON_SPAN = 4;
    private static final int ANIMATION_SPAN = 3;

    /** Whether the device ships the UDFPS resource packages. */
    public static boolean isAvailable(Context context) {
        return resources(context, ICONS_PACKAGE) != null
                || resources(context, ANIMATIONS_PACKAGE) != null;
    }

    private static Resources resources(Context context, String pkg) {
        try {
            return context.getPackageManager().getResourcesForApplication(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_fingerprint);

        Resources icons = resources(this, ICONS_PACKAGE);
        if (icons != null) {
            String[] names = array(icons, ICONS_PACKAGE, "udfps_icons");
            bind(R.id.fingerprint_icons, ICON_SPAN,
                    new Picker(icons, ICONS_PACKAGE, names, null, Settings.System.UDFPS_ICON));
        } else {
            hide(R.id.fingerprint_icons, R.id.fingerprint_icons_title);
        }

        Resources animations = resources(this, ANIMATIONS_PACKAGE);
        if (animations != null) {
            String[] previews = array(animations, ANIMATIONS_PACKAGE, "udfps_animation_previews");
            String[] titles = array(animations, ANIMATIONS_PACKAGE, "udfps_animation_titles");
            bind(R.id.fingerprint_animations, ANIMATION_SPAN,
                    new Picker(animations, ANIMATIONS_PACKAGE, previews, titles,
                            Settings.System.UDFPS_ANIM_STYLE));
        } else {
            hide(R.id.fingerprint_animations, R.id.fingerprint_animations_title);
        }
    }

    private static String[] array(Resources res, String pkg, String name) {
        int id = res.getIdentifier(name, "array", pkg);
        return id == 0 ? new String[0] : res.getStringArray(id);
    }

    private void bind(int gridId, int span, Picker picker) {
        RecyclerView grid = findViewById(gridId);
        grid.setLayoutManager(new GridLayoutManager(this, span));
        grid.setAdapter(picker);
    }

    private void hide(int gridId, int titleId) {
        findViewById(gridId).setVisibility(View.GONE);
        findViewById(titleId).setVisibility(View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Holder extends RecyclerView.ViewHolder {
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

    /** One grid of styles backed by a resource package; the selection is a Settings.System index. */
    private final class Picker extends RecyclerView.Adapter<Holder> {
        private final Resources mRes;
        private final String mPackage;
        private final String[] mDrawables;
        private final String[] mTitles;
        private final String mKey;
        private int mSelected;

        Picker(Resources res, String pkg, String[] drawables, String[] titles, String key) {
            mRes = res;
            mPackage = pkg;
            mDrawables = drawables;
            mTitles = titles;
            mKey = key;
            mSelected = Settings.System.getIntForUser(getContentResolver(), key, 0,
                    UserHandle.USER_CURRENT);
        }

        @Override
        public int getItemCount() {
            return mDrawables.length;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_accent_item, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            boolean selected = position == mSelected;
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(MaterialColors.getColor(holder.item,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest));
            holder.swatch.setBackground(dot);
            holder.swatch.setScaleType(ImageView.ScaleType.FIT_CENTER);
            holder.swatch.setPadding(dp(6), dp(6), dp(6), dp(6));
            holder.swatch.setImageTintList(null);
            holder.swatch.setImageDrawable(drawable(position));
            holder.ring.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            String label = mTitles != null && position < mTitles.length ? mTitles[position]
                    : position == 0 ? getString(R.string.font_default) : null;
            holder.name.setText(label);
            holder.name.setVisibility(label != null ? View.VISIBLE : View.GONE);
            holder.item.setSelected(selected);
            holder.item.setContentDescription(label != null ? label : mDrawables[position]);
            holder.item.setOnClickListener(v -> select(holder.getBindingAdapterPosition()));
        }

        private Drawable drawable(int position) {
            int id = mRes.getIdentifier(mDrawables[position], "drawable", mPackage);
            return id == 0 ? null : mRes.getDrawable(id, null);
        }

        private void select(int position) {
            if (position == RecyclerView.NO_POSITION || position == mSelected) {
                return;
            }
            Settings.System.putIntForUser(getContentResolver(), mKey, position,
                    UserHandle.USER_CURRENT);
            int old = mSelected;
            mSelected = position;
            notifyItemChanged(old);
            notifyItemChanged(position);
        }
    }
}
