package com.resurrection.settings;

import android.app.Activity;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.rr.wallpapers.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sets a wallpaper from the Resurrection Remix wallpaper pack.
 */
public class WallpaperActivity extends Activity {

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final List<Integer> mWallpapers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.android.settings.R.layout.wallpaper);
        loadWallpapers();
        GridView grid = findViewById(com.android.settings.R.id.grid);
        grid.setAdapter(new WallpaperAdapter());
        grid.setOnItemClickListener((parent, view, position, id) -> setWallpaper(mWallpapers.get(position)));
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    private void loadWallpapers() {
        String[] names = getResources().getStringArray(R.array.partner_wallpapers);
        for (String name : names) {
            int id = wallpaperId(name);
            if (id != 0) {
                mWallpapers.add(id);
            }
        }
    }

    private static int wallpaperId(String name) {
        try {
            return R.drawable.class.getField(name).getInt(null);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    private void setWallpaper(int resId) {
        mExecutor.execute(() -> {
            try (InputStream in = getResources().openRawResource(resId)) {
                WallpaperManager.getInstance(this).setStream(in, null, true,
                        WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
                runOnUiThread(() -> Toast.makeText(this, com.android.settings.R.string.wallpaper_set,
                        Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, com.android.settings.R.string.wallpaper_failed,
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    private final class WallpaperAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mWallpapers.size();
        }

        @Override
        public Integer getItem(int position) {
            return mWallpapers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mWallpapers.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView image = convertView instanceof ImageView
                    ? (ImageView) convertView
                    : new ImageView(WallpaperActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setAdjustViewBounds(false);
            int size = parent.getWidth() / 3;
            if (size <= 0) {
                size = 360;
            }
            image.setLayoutParams(new GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT, size));
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8;
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), mWallpapers.get(position), options);
            image.setImageBitmap(bitmap);
            return image;
        }
    }
}
