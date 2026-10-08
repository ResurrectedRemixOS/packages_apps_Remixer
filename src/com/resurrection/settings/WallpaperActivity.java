package com.resurrection.settings;

import android.app.WallpaperManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Resurrection Remix wallpaper pack (RRWallpapers-res), merged into Settings' resources.
 */
public class WallpaperActivity extends CollapsingToolbarBaseActivity {
    private static final int THUMB_WIDTH = 360;
    private static final int PREVIEW_WIDTH = 720;
    private static final int GRID_SPAN = 3;

    private final ExecutorService mExecutor = Executors.newFixedThreadPool(2);
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final LruCache<Integer, Bitmap> mThumbs = new LruCache<>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(Integer key, Bitmap value) {
            return value.getAllocationByteCount();
        }
    };
    private final List<Wallpaper> mWallpapers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_wallpapers);
        loadWallpapers();
        TextView summary = findViewById(R.id.wallpapers_summary);
        summary.setText(getResources().getQuantityString(R.plurals.wallpapers_summary,
                mWallpapers.size(), mWallpapers.size()));
        RecyclerView grid = findViewById(R.id.wallpapers_grid);
        grid.setLayoutManager(new GridLayoutManager(this, GRID_SPAN));
        grid.setAdapter(new Adapter());
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** R8 strips the library's R fields, so resolve the pack by name. */
    private void loadWallpapers() {
        Resources res = getResources();
        String pkg = getPackageName();
        for (String name : res.getStringArray(com.rr.wallpapers.R.array.partner_wallpapers)) {
            int full = res.getIdentifier(name, "drawable", pkg);
            if (full == 0) {
                continue;
            }
            int small = res.getIdentifier(name + "_small", "drawable", pkg);
            mWallpapers.add(new Wallpaper(name, full, small != 0 ? small : full));
        }
    }

    private void showPreview(Wallpaper wallpaper, View anchor) {
        View content = LayoutInflater.from(anchor.getContext())
                .inflate(R.layout.remixer_wallpaper_preview, null);
        ImageView preview = content.findViewById(R.id.wallpaper_preview);
        Bitmap cached = mThumbs.get(wallpaper.thumb);
        if (cached != null) {
            preview.setImageBitmap(cached);
        }
        mExecutor.execute(() -> {
            Bitmap bitmap = decode(wallpaper.full, PREVIEW_WIDTH);
            mHandler.post(() -> {
                if (bitmap != null) {
                    preview.setImageBitmap(bitmap);
                }
            });
        });
        new MaterialAlertDialogBuilder(anchor.getContext())
                .setTitle(R.string.wallpaper_apply_title)
                .setView(content)
                .setPositiveButton(R.string.wallpaper_apply_both, (d, w) -> apply(wallpaper,
                        WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK))
                .setNegativeButton(R.string.wallpaper_apply_lock, (d, w) -> apply(wallpaper,
                        WallpaperManager.FLAG_LOCK))
                .setNeutralButton(R.string.wallpaper_apply_home, (d, w) -> apply(wallpaper,
                        WallpaperManager.FLAG_SYSTEM))
                .show();
    }

    private void apply(Wallpaper wallpaper, int which) {
        mExecutor.execute(() -> {
            boolean ok;
            try (InputStream in = getResources().openRawResource(wallpaper.full)) {
                WallpaperManager.getInstance(this).setStream(in, null, true, which);
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            boolean result = ok;
            mHandler.post(() -> Toast.makeText(this,
                    result ? R.string.wallpaper_set : R.string.wallpaper_failed,
                    Toast.LENGTH_SHORT).show());
        });
    }

    private Bitmap decode(int id, int width) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            options.inScaled = false;
            BitmapFactory.decodeResource(getResources(), id, options);
            int sample = 1;
            while (options.outWidth / (sample * 2) >= width) {
                sample *= 2;
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            return BitmapFactory.decodeResource(getResources(), id, options);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void loadThumb(ImageView view, Wallpaper wallpaper) {
        view.setTag(wallpaper.thumb);
        Bitmap cached = mThumbs.get(wallpaper.thumb);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        mExecutor.execute(() -> {
            Bitmap bitmap = decode(wallpaper.thumb, THUMB_WIDTH);
            mHandler.post(() -> {
                if (bitmap == null) {
                    return;
                }
                mThumbs.put(wallpaper.thumb, bitmap);
                if (Integer.valueOf(wallpaper.thumb).equals(view.getTag())) {
                    view.setImageBitmap(bitmap);
                    view.setAlpha(0f);
                    view.animate().alpha(1f).setDuration(150).start();
                }
            });
        });
    }

    private static final class Wallpaper {
        final String name;
        final int full;
        final int thumb;

        Wallpaper(String name, int full, int thumb) {
            this.name = name;
            this.full = full;
            this.thumb = thumb;
        }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView thumb;

        Holder(View view) {
            super(view);
            card = view.findViewById(R.id.wallpaper_card);
            thumb = view.findViewById(R.id.wallpaper_thumb);
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return mWallpapers.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_wallpaper_item, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            Wallpaper wallpaper = mWallpapers.get(position);
            holder.card.setContentDescription(wallpaper.name.replace('_', ' '));
            holder.card.setOnClickListener(v -> showPreview(wallpaper, v));
            loadThumb(holder.thumb, wallpaper);
        }
    }
}
