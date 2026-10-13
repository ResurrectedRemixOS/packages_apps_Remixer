/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.system.ErrnoException;
import android.system.Os;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Boot animation picker. BootAnimation.cpp plays the file at index
 * persist.sys.bootanimation_style of the same list; the last entry is a file the user picks.
 */
public class BootAnimationActivity extends CollapsingToolbarBaseActivity {
    private static final String PROP_STYLE = "persist.sys.bootanimation_style";
    private static final int PREVIEW_STEP = 3;
    private static final int PREVIEW_MAX_FRAMES = 90;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final List<Integer> mItems = new ArrayList<>();
    private final Bitmap[] mThumbs = new Bitmap[32];

    private String[] mNames;
    private String[] mFiles;
    private int mCustom;
    private int mSelected;
    private int mPreviewGeneration;

    private ImageView mPreview;
    private ProgressBar mProgress;
    private TextView mName;
    private Adapter mAdapter;
    private ActivityResultLauncher<String[]> mPicker;

    /** Name of the chosen animation, for the Remixer entry summary. */
    public static String currentName(Context context) {
        String[] names = context.getResources().getStringArray(R.array.boot_animation_names);
        int style = SystemProperties.getInt(PROP_STYLE, 0);
        return style >= 0 && style < names.length ? names[style] : names[0];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_boot_animation);
        setTitle(R.string.remixer_boot_animation);
        mNames = getResources().getStringArray(R.array.boot_animation_names);
        mFiles = getResources().getStringArray(R.array.boot_animation_files);
        mCustom = mFiles.length - 1;
        int style = SystemProperties.getInt(PROP_STYLE, 0);
        mSelected = style >= 0 && style < mFiles.length ? style : 0;
        for (int i = 0; i < mFiles.length; i++) {
            // Only what this build ships; the custom entry is always offered.
            if (i == mCustom || new File(mFiles[i]).canRead()) {
                mItems.add(i);
            }
        }

        mPreview = findViewById(R.id.boot_preview);
        mProgress = findViewById(R.id.boot_progress);
        mName = findViewById(R.id.boot_name);
        RecyclerView grid = findViewById(R.id.boot_grid);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        mAdapter = new Adapter();
        grid.setAdapter(mAdapter);

        mPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                mExecutor.execute(() -> importCustom(uri));
            }
        });

        updateSelection();
        for (int index : mItems) {
            mExecutor.execute(() -> loadThumb(index));
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    private void onItemClicked(int index) {
        if (index == mCustom && !new File(mFiles[mCustom]).canRead()) {
            mPicker.launch(new String[] {"application/zip", "application/octet-stream"});
            return;
        }
        select(index);
    }

    private void select(int index) {
        try {
            SystemProperties.set(PROP_STYLE, String.valueOf(index));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.boot_animation_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        mSelected = index;
        mAdapter.notifyDataSetChanged();
        updateSelection();
    }

    private void updateSelection() {
        mName.setText(mNames[mSelected]);
        final int generation = ++mPreviewGeneration;
        final int index = mSelected;
        mPreview.setImageDrawable(null);
        mProgress.setVisibility(View.VISIBLE);
        mExecutor.execute(() -> {
            AnimationDrawable anim = loadPreview(mFiles[index], dp(260));
            mHandler.post(() -> {
                if (generation != mPreviewGeneration) {
                    return;
                }
                mProgress.setVisibility(View.GONE);
                if (anim != null) {
                    mPreview.setImageDrawable(anim);
                    anim.start();
                }
            });
        });
    }

    // Custom file

    private void importCustom(Uri uri) {
        File target = new File(mFiles[mCustom]);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        boolean ok = false;
        try (InputStream in = getContentResolver().openInputStream(uri);
                OutputStream out = new FileOutputStream(temp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            ok = isBootAnimation(temp) && temp.renameTo(target);
            if (ok) {
                // bootanim reads it as another user.
                Os.chmod(target.getPath(), 0644);
            }
        } catch (IOException | ErrnoException | SecurityException e) {
            ok = false;
        } finally {
            temp.delete();
        }
        final boolean imported = ok;
        mHandler.post(() -> {
            if (!imported) {
                Toast.makeText(this, R.string.boot_animation_custom_invalid,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            mThumbs[mCustom] = null;
            mExecutor.execute(() -> loadThumb(mCustom));
            select(mCustom);
        });
    }

    private static boolean isBootAnimation(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            Desc desc = Desc.read(zip);
            return desc != null && !desc.parts.isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    // Frames

    private static final class Desc {
        int width;
        int height;
        int fps;
        final List<String> parts = new ArrayList<>();

        static Desc read(ZipFile zip) throws IOException {
            ZipEntry entry = zip.getEntry("desc.txt");
            if (entry == null) {
                return null;
            }
            Desc desc = new Desc();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zip.getInputStream(entry)))) {
                String line = reader.readLine();
                if (line == null) {
                    return null;
                }
                String[] head = line.trim().split("\\s+");
                if (head.length < 3) {
                    return null;
                }
                desc.width = Integer.parseInt(head[0]);
                desc.height = Integer.parseInt(head[1]);
                desc.fps = Math.max(1, Integer.parseInt(head[2]));
                while ((line = reader.readLine()) != null) {
                    String[] f = line.trim().split("\\s+");
                    // "c|p count pause folder [color...]"
                    if (f.length >= 4 && (f[0].equals("c") || f[0].equals("p"))) {
                        desc.parts.add(f[3]);
                    }
                }
            } catch (NumberFormatException e) {
                return null;
            }
            return desc;
        }
    }

    private static List<ZipEntry> frames(ZipFile zip, String part) {
        List<ZipEntry> out = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName();
            String lower = name.toLowerCase();
            if (name.startsWith(part + "/") && (lower.endsWith(".png") || lower.endsWith(".jpg"))) {
                out.add(e);
            }
        }
        Collections.sort(out, (a, b) -> a.getName().compareTo(b.getName()));
        return out;
    }

    private static Bitmap decode(ZipFile zip, ZipEntry entry, int size, int sample) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream in = zip.getInputStream(entry)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, opts);
            if (bitmap == null) {
                return null;
            }
            float scale = Math.min(1f, (float) size / Math.max(bitmap.getWidth(),
                    bitmap.getHeight()));
            if (scale < 1f) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                        Math.max(1, Math.round(bitmap.getWidth() * scale)),
                        Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                }
                return scaled;
            }
            return bitmap;
        } catch (IOException e) {
            return null;
        }
    }

    private static int sampleFor(Desc desc, int size) {
        int sample = 1;
        while (Math.max(desc.width, desc.height) / (sample * 2) >= size) {
            sample *= 2;
        }
        return sample;
    }

    /** One frame from the looping part, where the logo is fully drawn. */
    private void loadThumb(int index) {
        Bitmap thumb = null;
        File file = new File(mFiles[index]);
        if (file.canRead()) {
            try (ZipFile zip = new ZipFile(file)) {
                Desc desc = Desc.read(zip);
                if (desc != null && !desc.parts.isEmpty()) {
                    String part = desc.parts.get(Math.min(1, desc.parts.size() - 1));
                    List<ZipEntry> list = frames(zip, part);
                    if (list.isEmpty()) {
                        list = frames(zip, desc.parts.get(0));
                    }
                    if (!list.isEmpty()) {
                        int size = dp(96);
                        thumb = decode(zip, list.get(list.size() / 2), size, sampleFor(desc, size));
                    }
                }
            } catch (IOException e) {
                // Left blank.
            }
        }
        final Bitmap result = thumb;
        mHandler.post(() -> {
            mThumbs[index] = result;
            int position = mItems.indexOf(index);
            if (position >= 0) {
                mAdapter.notifyItemChanged(position);
            }
        });
    }

    /** The intro and one pass of the loop, every few frames, small enough to keep in memory. */
    private AnimationDrawable loadPreview(String path, int size) {
        File file = new File(path);
        if (!file.canRead()) {
            return null;
        }
        try (ZipFile zip = new ZipFile(file)) {
            Desc desc = Desc.read(zip);
            if (desc == null || desc.parts.isEmpty()) {
                return null;
            }
            List<ZipEntry> list = new ArrayList<>();
            for (int i = 0; i < Math.min(2, desc.parts.size()); i++) {
                list.addAll(frames(zip, desc.parts.get(i)));
            }
            int step = Math.max(PREVIEW_STEP, (list.size() + PREVIEW_MAX_FRAMES - 1)
                    / PREVIEW_MAX_FRAMES);
            int duration = Math.max(16, 1000 * step / desc.fps);
            int sample = sampleFor(desc, size);
            AnimationDrawable anim = new AnimationDrawable();
            for (int i = 0; i < list.size(); i += step) {
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }
                Bitmap frame = decode(zip, list.get(i), size, sample);
                if (frame != null) {
                    anim.addFrame(new BitmapDrawable(getResources(), frame), duration);
                }
            }
            anim.setOneShot(false);
            return anim.getNumberOfFrames() > 0 ? anim : null;
        } catch (IOException e) {
            return null;
        }
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_boot_animation_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(mItems.get(position));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }

    private class Holder extends RecyclerView.ViewHolder {
        private final MaterialCardView mCard;
        private final ImageView mThumb;
        private final TextView mLabel;
        private int mIndex;

        Holder(View view) {
            super(view);
            mCard = (MaterialCardView) view;
            mThumb = view.findViewById(R.id.boot_thumb);
            mLabel = view.findViewById(R.id.boot_thumb_name);
            view.setOnClickListener(v -> onItemClicked(mIndex));
            view.setOnLongClickListener(v -> {
                if (mIndex != mCustom) {
                    return false;
                }
                mPicker.launch(new String[] {"application/zip", "application/octet-stream"});
                return true;
            });
        }

        void bind(int index) {
            mIndex = index;
            mThumb.setImageBitmap(mThumbs[index]);
            boolean customMissing = index == mCustom && !new File(mFiles[mCustom]).canRead();
            mLabel.setText(customMissing ? getString(R.string.boot_animation_custom_pick)
                    : mNames[index]);
            boolean selected = index == mSelected;
            mCard.setStrokeColor(selected ? MaterialColors.getColor(mCard,
                    androidx.appcompat.R.attr.colorPrimary) : Color.TRANSPARENT);
            mCard.setSelected(selected);
            mCard.setContentDescription(mNames[index]);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
