package com.resurrection.settings;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.LruCache;
import android.util.Size;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * Quick settings header image. The setting names match Evolution's header screen;
 * SystemUI's StatusBarHeaderMachine draws the result.
 */
public class HeaderActivity extends CollapsingToolbarBaseActivity {
    private static final String PROVIDER_DAYLIGHT = "daylight";
    private static final String PROVIDER_STATIC = "static";
    private static final String PROVIDER_FILE = "file";
    private static final String OMNISTYLE = "org.omnirom.omnistyle";
    // DaylightHeaderProvider falls back to this pack when none is stored.
    private static final String PACK_DEFAULT = OMNISTYLE + "/" + OMNISTYLE + ".remixer";
    private static final String HEADER_RELATIVE_PATH = "Pictures/QSHeader";
    private static final String HEADER_DISPLAY_NAME = "qs_header_image";
    private static final int HEIGHT_MIN = 100;
    private static final int HEIGHT_MAX = 200;
    private static final int HEIGHT_DEFAULT = 142;
    private static final int SHADOW_MAX = 200;
    private static final int GRID_SPAN = 2;
    private static final int THUMB_WIDTH = 540;
    private static final float FADE_FRACTION = 0.55f;

    private final ExecutorService mExecutor = Executors.newFixedThreadPool(2);
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> mThumbs = new LruCache<>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getAllocationByteCount();
        }
    };
    private final Map<String, List<ImageView>> mPending = new HashMap<>();
    private final List<Pack> mPacks = new ArrayList<>();
    private final PackAdapter mPackAdapter = new PackAdapter();
    private final ImageAdapter mImageAdapter = new ImageAdapter();
    private final ActivityResultLauncher<PickVisualMediaRequest> mPickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                    this::onPhotoPicked);

    private boolean mEnabled;
    private String mProvider = PROVIDER_DAYLIGHT;
    private String mPack;
    private String mStaticImage;
    private String mFileImage;
    private int mHeightDp;
    private int mShadow;
    private boolean mGuard;
    private boolean mTracking;
    private boolean mLoaded;
    private int mPreviewToken;
    private String mPreviewKey;
    private Pack mBrowsing;

    private MaterialCardView mPreviewCard;
    private ImageView mPreviewImage;
    private View mPreviewFade;
    private TextView mPreviewEmpty;
    private MaterialCardView mSwitchCard;
    private MaterialSwitch mSwitch;
    private TextView mSwitchTitle;
    private TextView mSwitchSummary;
    private View mContent;
    private MaterialButtonToggleGroup mSource;
    private View mGallery;
    private TextView mPacksSummary;
    private RecyclerView mPacksView;
    private TextView mImagesTitle;
    private View mPhotoSection;
    private TextView mHeightLabel;
    private TextView mShadowLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_header_picker);
        readSettings();

        mPreviewCard = findViewById(R.id.header_preview_card);
        mPreviewImage = findViewById(R.id.header_preview_image);
        mPreviewFade = findViewById(R.id.header_preview_fade);
        mPreviewEmpty = findViewById(R.id.header_preview_empty);
        mSwitchCard = findViewById(R.id.header_switch_card);
        mSwitch = findViewById(R.id.header_switch);
        mSwitchTitle = findViewById(R.id.header_switch_title);
        mSwitchSummary = findViewById(R.id.header_switch_summary);
        mContent = findViewById(R.id.header_content);
        mSource = findViewById(R.id.header_source);
        mGallery = findViewById(R.id.header_gallery);
        mPacksSummary = findViewById(R.id.header_collections_summary);
        mPacksView = findViewById(R.id.header_collections);
        mImagesTitle = findViewById(R.id.header_images_title);
        mPhotoSection = findViewById(R.id.header_photo_section);
        mHeightLabel = findViewById(R.id.header_height_label);
        mShadowLabel = findViewById(R.id.header_shadow_label);

        mSwitch.setChecked(mEnabled);
        mSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (mGuard || checked == mEnabled) {
                return;
            }
            if (!putInt(Settings.System.STATUS_BAR_CUSTOM_HEADER, checked ? 1 : 0)) {
                mGuard = true;
                mSwitch.setChecked(mEnabled);
                mGuard = false;
                return;
            }
            mEnabled = checked;
            applyState();
        });
        mSwitchCard.setOnClickListener(v -> mSwitch.toggle());
        mSwitchCard.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Switch.class.getName());
                info.setCheckable(true);
                info.setChecked(mSwitch.isChecked());
            }
        });

        mSource.check(sourceButton(mProvider));
        mSource.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked || mGuard) {
                return;
            }
            String provider = providerFor(id);
            if (provider.equals(mProvider)) {
                return;
            }
            if (!putString(Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, provider)) {
                checkSource(mProvider);
                return;
            }
            mProvider = provider;
            Pack active = activePack();
            if (PROVIDER_DAYLIGHT.equals(provider) && active != null) {
                mBrowsing = active;
            }
            applyState();
            if (PROVIDER_FILE.equals(provider) && mFileImage == null) {
                pickPhoto();
            }
        });

        mPacksView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mPacksView.setAdapter(mPackAdapter);
        RecyclerView images = findViewById(R.id.header_images);
        images.setLayoutManager(new GridLayoutManager(this, GRID_SPAN));
        images.setAdapter(mImageAdapter);
        findViewById(R.id.header_photo).setOnClickListener(v -> pickPhoto());

        Slider height = findViewById(R.id.header_height);
        height.setValue(mHeightDp);
        height.setLabelFormatter(value -> String.valueOf(Math.round(value)));
        bindSlider(height, Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, value -> {
            mHeightDp = value;
            updateLook();
        });
        Slider shadow = findViewById(R.id.header_shadow);
        shadow.setValue(mShadow);
        shadow.setLabelFormatter(value -> percent(Math.round(value)) + "%");
        bindSlider(shadow, Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, value -> {
            mShadow = value;
            updateLook();
        });
        SliderReset.attach(mHeightLabel, height, HEIGHT_DEFAULT,
                () -> putInt(Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, HEIGHT_DEFAULT));
        SliderReset.attach(mShadowLabel, shadow, 0,
                () -> putInt(Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, 0));

        applyState();
        mExecutor.execute(() -> {
            List<Pack> packs = loadPacks();
            mHandler.post(() -> onPacksLoaded(packs));
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mPreviewImage.getDrawable() instanceof Animatable anim) {
            anim.start();
        }
    }

    @Override
    protected void onStop() {
        if (mPreviewImage.getDrawable() instanceof Animatable anim) {
            anim.stop();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void readSettings() {
        ContentResolver resolver = getContentResolver();
        mEnabled = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0) == 1;
        String provider = Settings.System.getString(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER);
        if (PROVIDER_STATIC.equals(provider) || PROVIDER_FILE.equals(provider)) {
            mProvider = provider;
        }
        mPack = Settings.System.getString(resolver,
                Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK);
        if (mPack == null) {
            mPack = PACK_DEFAULT;
        }
        mStaticImage = Settings.System.getString(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_IMAGE);
        mFileImage = Settings.System.getString(resolver,
                Settings.System.STATUS_BAR_FILE_HEADER_IMAGE);
        mHeightDp = clamp(Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, HEIGHT_DEFAULT),
                HEIGHT_MIN, HEIGHT_MAX);
        mShadow = clamp(Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, 0), 0, SHADOW_MAX);
    }

    private void applyState() {
        boolean daylight = PROVIDER_DAYLIGHT.equals(mProvider);
        boolean file = PROVIDER_FILE.equals(mProvider);
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
        mGallery.setAlpha(mEnabled ? 1f : 0.5f);
        mGallery.setVisibility(file ? View.GONE : View.VISIBLE);
        mPhotoSection.setVisibility(file ? View.VISIBLE : View.GONE);
        if (mLoaded && mPacks.isEmpty()) {
            mPacksSummary.setText(R.string.header_collections_empty);
        } else {
            mPacksSummary.setText(daylight ? R.string.header_collections_summary_daylight
                    : R.string.header_collections_summary_static);
        }
        mImagesTitle.setText(daylight ? R.string.header_images_daylight : R.string.header_images);
        mImagesTitle.setVisibility(mBrowsing == null ? View.GONE : View.VISIBLE);
        mPackAdapter.notifyDataSetChanged();
        mImageAdapter.notifyDataSetChanged();
        updateLook();
        updatePreview();
    }

    private void updateLook() {
        mHeightLabel.setText(getString(R.string.header_height_value, mHeightDp));
        mShadowLabel.setText(getString(R.string.header_shadow_value, percent(mShadow)));
        // Scale the preview so it keeps the on-device header's shape.
        int screen = getResources().getDisplayMetrics().widthPixels;
        int height = Math.round(dp(mHeightDp) * (float) (screen - dp(32)) / screen);
        ViewGroup.LayoutParams card = mPreviewCard.getLayoutParams();
        if (card.height != height) {
            card.height = height;
            mPreviewCard.setLayoutParams(card);
        }
        ViewGroup.LayoutParams fade = mPreviewFade.getLayoutParams();
        int fadeHeight = Math.round(height * FADE_FRACTION);
        if (fade.height != fadeHeight) {
            fade.height = fadeHeight;
            mPreviewFade.setLayoutParams(fade);
        }
        mPreviewImage.setImageAlpha(255 - mShadow);
        mPreviewCard.setAlpha(mEnabled ? 1f : 0.5f);
    }

    private void updatePreview() {
        String provider = mProvider;
        Pack pack = activePack();
        String image = pack != null ? pack.now() : null;
        String staticImage = mStaticImage;
        String file = mFileImage;
        String key;
        if (PROVIDER_FILE.equals(provider)) {
            key = provider + ":" + file;
        } else if (PROVIDER_STATIC.equals(provider)) {
            key = provider + ":" + staticImage;
        } else {
            key = provider + ":" + (pack != null ? pack.value + "/" + image : null);
        }
        if (key.equals(mPreviewKey)) {
            return;
        }
        mPreviewKey = key;
        int token = ++mPreviewToken;
        int width = getResources().getDisplayMetrics().widthPixels;
        mExecutor.execute(() -> {
            Drawable drawable;
            if (PROVIDER_FILE.equals(provider)) {
                drawable = loadFile(file, width);
            } else if (PROVIDER_STATIC.equals(provider)) {
                drawable = loadStatic(staticImage, width);
            } else {
                drawable = pack != null ? toDrawable(decode(pack.res, pack.id(image), width)) : null;
            }
            mHandler.post(() -> {
                if (token == mPreviewToken && !isDestroyed()) {
                    showPreview(drawable);
                }
            });
        });
    }

    private void showPreview(Drawable drawable) {
        if (mPreviewImage.getDrawable() instanceof Animatable old) {
            old.stop();
        }
        mPreviewImage.setImageDrawable(drawable);
        if (drawable instanceof Animatable anim) {
            anim.start();
        }
        boolean waiting = !mLoaded && PROVIDER_DAYLIGHT.equals(mProvider);
        mPreviewEmpty.setVisibility(drawable == null && !waiting ? View.VISIBLE : View.GONE);
        if (drawable != null) {
            mPreviewImage.setAlpha(0f);
            mPreviewImage.animate().alpha(1f).setDuration(200).start();
        }
    }

    private Drawable loadStatic(String value, int width) {
        int slash = value != null ? value.indexOf('/') : -1;
        if (slash <= 0) {
            return null;
        }
        String pkg = value.substring(0, slash);
        try {
            Resources res = getPackageManager().getResourcesForApplication(pkg);
            return toDrawable(decode(res,
                    res.getIdentifier(value.substring(slash + 1), "drawable", pkg), width));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private Drawable loadFile(String value, int width) {
        if (value == null) {
            return null;
        }
        try {
            ImageDecoder.Source source =
                    ImageDecoder.createSource(getContentResolver(), Uri.parse(value));
            return ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                Size size = info.getSize();
                if (size.getWidth() > width) {
                    decoder.setTargetSampleSize(size.getWidth() / width);
                }
            });
        } catch (Exception e) {
            return null;
        }
    }

    private Drawable toDrawable(Bitmap bitmap) {
        return bitmap != null ? new BitmapDrawable(getResources(), bitmap) : null;
    }

    private static Bitmap decode(Resources res, int id, int width) {
        if (res == null || id == 0) {
            return null;
        }
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            options.inScaled = false;
            BitmapFactory.decodeResource(res, id, options);
            if (options.outWidth > 0) {
                int sample = 1;
                while (options.outWidth / (sample * 2) >= width) {
                    sample *= 2;
                }
                options.inJustDecodeBounds = false;
                options.inSampleSize = sample;
                return BitmapFactory.decodeResource(res, id, options);
            }
            Drawable drawable = res.getDrawable(id, null);
            Bitmap bitmap = Bitmap.createBitmap(width, width * 3 / 8, Bitmap.Config.ARGB_8888);
            drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            drawable.draw(new Canvas(bitmap));
            return bitmap;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void loadThumb(ImageView view, Pack pack, String image) {
        String key = pack.pkg + "/" + image;
        view.setTag(key);
        Bitmap cached = mThumbs.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            view.setAlpha(1f);
            return;
        }
        view.setImageDrawable(null);
        List<ImageView> waiting = mPending.get(key);
        if (waiting != null) {
            waiting.add(view);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(view);
        mPending.put(key, waiting);
        mExecutor.execute(() -> {
            Bitmap bitmap = decode(pack.res, pack.id(image), THUMB_WIDTH);
            mHandler.post(() -> {
                List<ImageView> views = mPending.remove(key);
                if (bitmap == null || views == null) {
                    return;
                }
                mThumbs.put(key, bitmap);
                for (ImageView target : views) {
                    if (key.equals(target.getTag())) {
                        target.setImageBitmap(bitmap);
                        target.setAlpha(0f);
                        target.animate().alpha(1f).setDuration(150).start();
                    }
                }
            });
        });
    }

    private List<Pack> loadPacks() {
        PackageManager pm = getPackageManager();
        Map<String, Pack> found = new LinkedHashMap<>();
        Intent daylight = new Intent("org.omnirom.DaylightHeaderPack");
        for (ResolveInfo info : pm.queryIntentActivities(daylight, 0)) {
            ActivityInfo activity = info.activityInfo;
            found.putIfAbsent(activity.packageName,
                    new Pack(activity.packageName, null, activity.loadLabel(pm)));
        }
        Intent packs1 = new Intent("org.omnirom.DaylightHeaderPack1");
        for (ResolveInfo info : pm.queryIntentActivities(packs1, 0)) {
            addActivity(found, pm, info.activityInfo);
        }
        if (found.isEmpty()) {
            addOmniStyle(found, pm);
        }
        List<Pack> packs = new ArrayList<>();
        for (Pack pack : found.values()) {
            try {
                pack.res = pm.getResourcesForApplication(pack.pkg);
                pack.parse();
            } catch (Exception e) {
                continue;
            }
            if (!pack.images.isEmpty()) {
                packs.add(pack);
            }
        }
        packs.sort(Comparator.comparingInt(Pack::rank)
                .thenComparing(pack -> pack.label, String.CASE_INSENSITIVE_ORDER));
        return packs;
    }

    private static void addActivity(Map<String, Pack> found, PackageManager pm,
            ActivityInfo activity) {
        if (activity.name.endsWith(".theme")) {
            return;
        }
        Pack pack = new Pack(activity.packageName, activity.name, activity.loadLabel(pm));
        found.putIfAbsent(pack.value, pack);
    }

    /** OmniStyle's packs can be missed by the intent query; read its activity list directly. */
    private static void addOmniStyle(Map<String, Pack> found, PackageManager pm) {
        PackageInfo info;
        try {
            info = pm.getPackageInfo(OMNISTYLE, PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        if (info.activities == null) {
            return;
        }
        for (ActivityInfo activity : info.activities) {
            String name = activity.name;
            if (name.endsWith("BrowseHeaderActivity")
                    || name.endsWith("PickHeaderActivity")
                    || name.endsWith("BrowseThemeHeaderActivity")
                    || name.endsWith("BrowseWallsActivity")
                    || name.endsWith("BrowseWallsFilterActivity")
                    || name.endsWith("WallpaperActivity")) {
                continue;
            }
            addActivity(found, pm, activity);
        }
    }

    private void onPacksLoaded(List<Pack> packs) {
        if (isDestroyed()) {
            return;
        }
        mLoaded = true;
        mPacks.addAll(packs);
        mBrowsing = initialBrowsing();
        mPreviewKey = null;
        applyState();
        int index = mPacks.indexOf(mBrowsing);
        if (index > 0) {
            mPacksView.scrollToPosition(index);
        }
    }

    private Pack initialBrowsing() {
        int slash = mStaticImage != null ? mStaticImage.indexOf('/') : -1;
        if (PROVIDER_STATIC.equals(mProvider) && slash > 0) {
            String pkg = mStaticImage.substring(0, slash);
            String image = mStaticImage.substring(slash + 1);
            for (Pack pack : mPacks) {
                if (pack.pkg.equals(pkg) && pack.images.contains(image)) {
                    return pack;
                }
            }
        }
        Pack active = activePack();
        if (active != null) {
            return active;
        }
        return mPacks.isEmpty() ? null : mPacks.get(0);
    }

    private Pack activePack() {
        Pack fallback = null;
        for (Pack pack : mPacks) {
            if (pack.value.equals(mPack)) {
                return pack;
            }
            if (pack.value.equals(PACK_DEFAULT)) {
                fallback = pack;
            }
        }
        return fallback;
    }

    private void onPackClicked(Pack pack) {
        if (PROVIDER_DAYLIGHT.equals(mProvider) && !pack.value.equals(mPack)) {
            if (!putString(Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK, pack.value)) {
                return;
            }
            mPack = pack.value;
        }
        mBrowsing = pack;
        applyState();
    }

    private void onImageClicked(Pack pack, String image) {
        String value = pack.pkg + "/" + image;
        if (!value.equals(mStaticImage)
                && !putString(Settings.System.STATUS_BAR_CUSTOM_HEADER_IMAGE, value)) {
            return;
        }
        mStaticImage = value;
        if (!PROVIDER_STATIC.equals(mProvider)) {
            if (!putString(Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, PROVIDER_STATIC)) {
                return;
            }
            mProvider = PROVIDER_STATIC;
            checkSource(mProvider);
        }
        applyState();
    }

    private void pickPhoto() {
        try {
            mPickPhoto.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        } catch (Exception e) {
            toastFailed();
        }
    }

    private void onPhotoPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        mExecutor.execute(() -> {
            ContentResolver resolver = getContentResolver();
            Uri out = copyHeader(resolver, uri);
            if (out != null) {
                deleteOthers(resolver, out);
            }
            mHandler.post(() -> {
                if (isDestroyed()) {
                    return;
                }
                if (out == null) {
                    toastFailed();
                    return;
                }
                String value = out.toString();
                if (putString(Settings.System.STATUS_BAR_FILE_HEADER_IMAGE, value)) {
                    mFileImage = value;
                    updatePreview();
                }
            });
        });
    }

    private void deleteOthers(ContentResolver resolver, Uri keep) {
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND "
                + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? AND "
                + MediaStore.MediaColumns._ID + "!=?";
        String[] args = new String[] {HEADER_RELATIVE_PATH + "/", HEADER_DISPLAY_NAME + "%",
                String.valueOf(ContentUris.parseId(keep))};
        try {
            resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, args);
        } catch (Exception ignored) {
            // A leftover older copy is harmless.
        }
    }

    private Uri copyHeader(ContentResolver resolver, Uri inUri) {
        String mime = resolver.getType(inUri);
        if (mime == null) {
            mime = "image/*";
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, HEADER_DISPLAY_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, HEADER_RELATIVE_PATH);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri out = null;
        try {
            out = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (out == null) {
                return null;
            }
            try (InputStream in = resolver.openInputStream(inUri);
                    OutputStream output = resolver.openOutputStream(out, "wt")) {
                if (in == null || output == null) {
                    resolver.delete(out, null, null);
                    return null;
                }
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) > 0) {
                    output.write(buffer, 0, count);
                }
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(out, done, null, null);
            return out;
        } catch (Exception e) {
            if (out != null) {
                try {
                    resolver.delete(out, null, null);
                } catch (Exception ignored) {
                    // The failed copy is already unusable.
                }
            }
            return null;
        }
    }

    private void bindSlider(Slider slider, String key, IntConsumer onValue) {
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

    private void checkSource(String provider) {
        mGuard = true;
        mSource.check(sourceButton(provider));
        mGuard = false;
    }

    private static int sourceButton(String provider) {
        if (PROVIDER_STATIC.equals(provider)) {
            return R.id.header_source_image;
        }
        if (PROVIDER_FILE.equals(provider)) {
            return R.id.header_source_photo;
        }
        return R.id.header_source_collection;
    }

    private static String providerFor(int id) {
        if (id == R.id.header_source_image) {
            return PROVIDER_STATIC;
        }
        if (id == R.id.header_source_photo) {
            return PROVIDER_FILE;
        }
        return PROVIDER_DAYLIGHT;
    }

    private boolean putString(String key, String value) {
        try {
            if (Settings.System.putString(getContentResolver(), key, value)) {
                return true;
            }
        } catch (SecurityException ignored) {
            // Reported below.
        }
        toastFailed();
        return false;
    }

    private boolean putInt(String key, int value) {
        try {
            if (Settings.System.putInt(getContentResolver(), key, value)) {
                return true;
            }
        } catch (SecurityException ignored) {
            // Reported below.
        }
        toastFailed();
        return false;
    }

    private void toastFailed() {
        Toast.makeText(this, R.string.header_failed, Toast.LENGTH_SHORT).show();
    }

    private static void setTreeEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                setTreeEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    private static int color(View view, int attr) {
        return MaterialColors.getColor(view, attr);
    }

    private static int percent(int shadow) {
        return Math.round(shadow * 100f / 255);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** A header collection: the activity name picks its assets/<name>.xml. */
    private static final class Pack {
        final String pkg;
        final String activity;
        final String value;
        final String label;
        final List<String> images = new ArrayList<>();
        final List<Integer> hours = new ArrayList<>();
        final List<String> hourImages = new ArrayList<>();
        Resources res;

        Pack(String pkg, String activity, CharSequence label) {
            this.pkg = pkg;
            this.activity = activity;
            this.value = activity == null ? pkg : pkg + "/" + activity;
            this.label = label.toString();
        }

        int rank() {
            return label.startsWith("RR ") ? 0 : 1;
        }

        int id(String image) {
            return res.getIdentifier(image, "drawable", pkg);
        }

        void parse() throws Exception {
            String asset = activity == null ? "daylight_header.xml"
                    : activity.substring(activity.lastIndexOf('.') + 1) + ".xml";
            try (InputStream in = res.getAssets().open(asset)) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(in, null);
                for (int type = parser.getEventType(); type != XmlPullParser.END_DOCUMENT;
                        type = parser.next()) {
                    if (type != XmlPullParser.START_TAG) {
                        continue;
                    }
                    String image = parser.getAttributeValue(null, "image");
                    if (image == null || id(image) == 0) {
                        continue;
                    }
                    if (!images.contains(image)) {
                        images.add(image);
                    }
                    String hour = parser.getAttributeValue(null, "hour");
                    boolean dated = parser.getAttributeValue(null, "day") != null
                            || parser.getAttributeValue(null, "month") != null;
                    if (hour != null && !dated) {
                        try {
                            hours.add(Integer.parseInt(hour));
                            hourImages.add(image);
                        } catch (NumberFormatException ignored) {
                            // Not part of the daily schedule.
                        }
                    }
                }
            }
        }

        /** The image the daylight provider shows at this hour. */
        String now() {
            if (hours.isEmpty()) {
                return images.get(0);
            }
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            String best = null;
            String last = null;
            int bestHour = -1;
            int lastHour = -1;
            for (int i = 0; i < hours.size(); i++) {
                int h = hours.get(i);
                if (h <= hour && h >= bestHour) {
                    bestHour = h;
                    best = hourImages.get(i);
                }
                if (h >= lastHour) {
                    lastHour = h;
                    last = hourImages.get(i);
                }
            }
            return best != null ? best : last;
        }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView thumb;
        final ImageView check;
        final TextView title;
        final TextView summary;

        Holder(View view) {
            super(view);
            card = view.findViewById(R.id.header_card);
            thumb = view.findViewById(R.id.header_thumb);
            check = view.findViewById(R.id.header_check);
            title = view.findViewById(R.id.header_title);
            summary = view.findViewById(R.id.header_summary);
        }

        void bind(Pack pack, String image, boolean outlined, boolean checked) {
            card.setStrokeColor(color(card, androidx.appcompat.R.attr.colorPrimary));
            card.setStrokeWidth(outlined ? dp(3) : 0);
            check.setVisibility(checked ? View.VISIBLE : View.GONE);
            card.setEnabled(mEnabled);
            card.setSelected(checked);
            loadThumb(thumb, pack, image);
        }
    }

    private final class PackAdapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return mPacks.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_header_collection_item, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            Pack pack = mPacks.get(position);
            int count = pack.images.size();
            holder.title.setText(pack.label);
            holder.summary.setText(getResources().getQuantityString(
                    R.plurals.header_image_count, count, count));
            boolean active = PROVIDER_DAYLIGHT.equals(mProvider) && pack == activePack();
            holder.bind(pack, pack.now(), pack == mBrowsing, active);
            holder.card.setContentDescription(pack.label);
            holder.card.setOnClickListener(v -> onPackClicked(pack));
        }
    }

    private final class ImageAdapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return mBrowsing == null ? 0 : mBrowsing.images.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_header_image_item, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            Pack pack = mBrowsing;
            String image = pack.images.get(position);
            boolean selected;
            if (PROVIDER_STATIC.equals(mProvider)) {
                selected = (pack.pkg + "/" + image).equals(mStaticImage);
            } else {
                selected = pack == activePack() && image.equals(pack.now());
            }
            holder.bind(pack, image, selected, selected);
            holder.card.setContentDescription(image.replace('_', ' '));
            holder.card.setOnClickListener(v -> onImageClicked(pack, image));
        }
    }
}
