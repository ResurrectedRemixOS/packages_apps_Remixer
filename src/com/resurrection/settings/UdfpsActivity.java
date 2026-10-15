package com.resurrection.settings;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.LruCache;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Under-display fingerprint icon or touch animation, drawn by SystemUI from the UdfpsIcons and
 * UdfpsAnimations resource packages. Index 0 is the stock icon and no animation.
 */
public class UdfpsActivity extends CollapsingToolbarBaseActivity {
    public static final String KIND_ICON = "icon";
    public static final String KIND_ANIMATION = "animation";
    private static final String EXTRA_KIND = "kind";
    private static final String ICONS_PACKAGE = "org.evolution.udfps.icons";
    private static final String ANIMATIONS_PACKAGE = "org.evolution.udfps.animations";
    private static final int ICON_SPAN = 3;
    private static final int ANIMATION_SPAN = 2;
    // Preview frame sizes in dp; the swatch sits 12dp inside the selection ring.
    private static final int ICON_FRAME = 92;
    private static final int ANIMATION_FRAME = 132;

    // Previews are big mdpi images: decode them off the main thread at the swatch's size.
    private final ExecutorService mLoader = Executors.newFixedThreadPool(2);
    private final LruCache<Integer, Object> mPreviews = new LruCache<>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(Integer key, Object value) {
            return value instanceof Bitmap ? ((Bitmap) value).getAllocationByteCount() : 1024;
        }
    };

    public static Intent intent(Context context, String kind) {
        return new Intent(context, UdfpsActivity.class).putExtra(EXTRA_KIND, kind);
    }

    public static boolean hasIcons(Context context) {
        return resources(context, ICONS_PACKAGE) != null;
    }

    public static boolean hasAnimations(Context context) {
        return resources(context, ANIMATIONS_PACKAGE) != null;
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
        setContentView(R.layout.remixer_udfps);

        boolean animation = KIND_ANIMATION.equals(getIntent().getStringExtra(EXTRA_KIND));
        setTitle(animation ? R.string.remixer_udfps_animation : R.string.remixer_udfps_icon);

        RecyclerView grid = findViewById(R.id.udfps_grid);
        if (animation) {
            Resources res = resources(this, ANIMATIONS_PACKAGE);
            if (res == null) {
                finish();
                return;
            }
            grid.setLayoutManager(new GridLayoutManager(this, ANIMATION_SPAN));
            grid.setAdapter(new Picker(res, ANIMATIONS_PACKAGE,
                    array(res, ANIMATIONS_PACKAGE, "udfps_animation_previews"),
                    array(res, ANIMATIONS_PACKAGE, "udfps_animation_titles"),
                    Settings.System.UDFPS_ANIM_STYLE, ANIMATION_FRAME));
        } else {
            Resources res = resources(this, ICONS_PACKAGE);
            if (res == null) {
                finish();
                return;
            }
            grid.setLayoutManager(new GridLayoutManager(this, ICON_SPAN));
            grid.setAdapter(new Picker(res, ICONS_PACKAGE,
                    array(res, ICONS_PACKAGE, "udfps_icons"), null, Settings.System.UDFPS_ICON,
                    ICON_FRAME));
        }
    }

    @Override
    protected void onDestroy() {
        mLoader.shutdownNow();
        super.onDestroy();
    }

    private static String[] array(Resources res, String pkg, String name) {
        int id = res.getIdentifier(name, "array", pkg);
        return id == 0 ? new String[0] : res.getStringArray(id);
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
        private final int mFrame;
        private final int[] mIds;
        private int mSelected;

        Picker(Resources res, String pkg, String[] drawables, String[] titles, String key,
                int frame) {
            mRes = res;
            mPackage = pkg;
            mDrawables = drawables;
            mTitles = titles;
            mKey = key;
            mFrame = frame;
            mIds = new int[drawables.length];
            for (int i = 0; i < drawables.length; i++) {
                mIds[i] = res.getIdentifier(drawables[i], "drawable", pkg);
            }
            mSelected = Settings.System.getIntForUser(getContentResolver(), key, 0,
                    UserHandle.USER_CURRENT);
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return mDrawables.length;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_accent_item, parent, false);
            Holder holder = new Holder(view);
            size((View) holder.ring.getParent(), mFrame);
            size(holder.swatch, mFrame - 12);
            return holder;
        }

        private void size(View view, int sizeDp) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.width = dp(sizeDp);
            lp.height = dp(sizeDp);
            view.setLayoutParams(lp);
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
            int pad = dp(mFrame / 12);
            holder.swatch.setPadding(pad, pad, pad, pad);
            holder.swatch.setImageTintList(null);
            showPreview(holder, position);
            holder.ring.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            String label = mTitles != null && position < mTitles.length ? mTitles[position]
                    : position == 0 ? getString(R.string.font_default) : null;
            holder.name.setText(label);
            holder.name.setVisibility(label != null ? View.VISIBLE : View.GONE);
            holder.item.setSelected(selected);
            holder.item.setContentDescription(label != null ? label : mDrawables[position]);
            holder.item.setOnClickListener(v -> select(holder.getBindingAdapterPosition()));
        }

        private void showPreview(Holder holder, int position) {
            holder.swatch.setTag(position);
            Object cached = mPreviews.get(position);
            if (cached != null) {
                set(holder.swatch, cached);
                return;
            }
            holder.swatch.setImageDrawable(null);
            int id = mIds[position];
            if (id == 0) return;
            int size = dp(mFrame - 12);
            mLoader.execute(() -> {
                Object preview = load(id, size);
                if (preview == null) return;
                runOnUiThread(() -> {
                    mPreviews.put(position, preview);
                    if (Integer.valueOf(position).equals(holder.swatch.getTag())) {
                        set(holder.swatch, preview);
                    }
                });
            });
        }

        /** A bitmap scaled down to about [size] px, or a drawable's state for vectors. */
        private Object load(int id, int size) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(mRes, id, options);
            if (options.outWidth <= 0) {
                Drawable drawable = mRes.getDrawable(id, null);
                return drawable != null ? drawable.getConstantState() : null;
            }
            int sample = 1;
            while (options.outWidth / (sample * 2) >= size) sample *= 2;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            return BitmapFactory.decodeResource(mRes, id, options);
        }

        private void set(ImageView view, Object preview) {
            if (preview instanceof Bitmap) {
                view.setImageBitmap((Bitmap) preview);
            } else {
                view.setImageDrawable(((Drawable.ConstantState) preview).newDrawable(mRes));
            }
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
