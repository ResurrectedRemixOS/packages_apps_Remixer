package com.resurrection.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quick settings header image. The setting names match Evolution's header screen.
 */
public class HeaderActivity extends Activity {
    private static final String PROVIDER_DAYLIGHT = "daylight";
    private static final String PROVIDER_STATIC = "static";
    private static final String PROVIDER_FILE = "file";
    private static final int REQUEST_PICK_IMAGE = 10001;
    private static final String HEADER_RELATIVE_PATH = "Pictures/QSHeader";
    private static final String HEADER_DISPLAY_NAME = "qs_header_image";
    private static final int HEIGHT_MIN = 100;
    private static final int HEIGHT_MAX = 200;
    private static final int HEIGHT_DEFAULT = 142;
    private static final int SHADOW_MAX = 200;

    private boolean mGuard;
    private String mProvider = PROVIDER_DAYLIGHT;
    private final List<String> mPackLabels = new ArrayList<>();
    private final List<String> mPackValues = new ArrayList<>();

    private RadioGroup mProviders;
    private Button mPackButton;
    private Button mBrowse;
    private TextView mBrowseSummary;
    private Button mFile;
    private SeekBar mHeight;
    private SeekBar mShadow;
    private TextView mHeightValue;
    private TextView mShadowValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getContentResolver();
        String stored = Settings.System.getString(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER);
        if (PROVIDER_STATIC.equals(stored) || PROVIDER_FILE.equals(stored)
                || PROVIDER_DAYLIGHT.equals(stored)) {
            mProvider = stored;
        }
        loadPacks();

        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(16), pad, pad);

        Switch toggle = new Switch(this);
        toggle.setText(R.string.header_switch);
        boolean enabled = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0) == 1;
        toggle.setChecked(enabled);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (writeInt(button, Settings.System.STATUS_BAR_CUSTOM_HEADER, checked ? 1 : 0,
                    checked)) {
                applyEnabled(checked);
            }
        });
        root.addView(toggle, match());

        TextView summary = new TextView(this);
        summary.setText(R.string.header_switch_summary);
        summary.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        summary.setPadding(0, dp(8), 0, dp(20));
        root.addView(summary, match());

        root.addView(label(R.string.header_provider), match());
        mProviders = new RadioGroup(this);
        mProviders.setOrientation(LinearLayout.VERTICAL);
        addProvider(mProviders, 1, R.string.header_provider_daylight);
        addProvider(mProviders, 2, R.string.header_provider_static);
        addProvider(mProviders, 3, R.string.header_provider_file);
        mProviders.check(providerId(mProvider));
        mProviders.setOnCheckedChangeListener((group, id) -> {
            if (mGuard) {
                return;
            }
            String value = providerValue(id);
            try {
                if (!Settings.System.putString(resolver,
                        Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, value)) {
                    revertProvider();
                    return;
                }
            } catch (SecurityException e) {
                revertProvider();
                return;
            }
            mProvider = value;
            applyEnabled(toggle.isChecked());
        });
        root.addView(mProviders, match());

        root.addView(label(R.string.header_pack), match());
        mPackButton = new Button(this);
        mPackButton.setOnClickListener(v -> showPacks());
        root.addView(mPackButton, match());

        mBrowse = new Button(this);
        mBrowse.setOnClickListener(v -> openPicker());
        root.addView(mBrowse, match());
        mBrowseSummary = small(R.string.header_browse_summary);
        root.addView(mBrowseSummary, match());

        mFile = new Button(this);
        mFile.setText(R.string.header_file);
        mFile.setOnClickListener(v -> pickFile());
        root.addView(mFile, match());
        TextView fileSummary = small(R.string.header_file_summary);
        root.addView(fileSummary, match());

        root.addView(label(R.string.header_offset), match());
        TextView offsetSummary = small(R.string.header_offset_summary);
        root.addView(offsetSummary, match());
        mHeightValue = small(0);
        root.addView(mHeightValue, match());
        mHeight = new SeekBar(this);
        mHeight.setMin(HEIGHT_MIN);
        mHeight.setMax(HEIGHT_MAX);
        int height = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, HEIGHT_DEFAULT);
        if (height < HEIGHT_MIN || height > HEIGHT_MAX) {
            height = HEIGHT_DEFAULT;
        }
        mHeight.setProgress(height);
        mHeightValue.setText(String.valueOf(height));
        mHeight.setOnSeekBarChangeListener(new IntSettingListener(
                Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, mHeightValue));
        root.addView(mHeight, match());

        root.addView(label(R.string.header_shadow), match());
        TextView shadowSummary = small(R.string.header_shadow_summary);
        root.addView(shadowSummary, match());
        mShadowValue = small(0);
        root.addView(mShadowValue, match());
        mShadow = new SeekBar(this);
        mShadow.setMax(SHADOW_MAX);
        int shadow = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, 0);
        if (shadow < 0 || shadow > SHADOW_MAX) {
            shadow = 0;
        }
        mShadow.setProgress(shadow);
        mShadowValue.setText(String.valueOf(shadow));
        mShadow.setOnSeekBarChangeListener(new IntSettingListener(
                Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, mShadowValue));
        root.addView(mShadow, match());

        updatePackLabel();
        applyEnabled(enabled);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void applyEnabled(boolean enabled) {
        setGroupEnabled(mProviders, enabled);
        boolean daylight = PROVIDER_DAYLIGHT.equals(mProvider);
        boolean file = PROVIDER_FILE.equals(mProvider);
        mPackButton.setEnabled(enabled && daylight && !mPackValues.isEmpty());
        boolean browse = enabled && !file && omniStyleInstalled();
        mBrowse.setEnabled(browse);
        mBrowse.setText(daylight ? R.string.header_browse : R.string.header_pick);
        mBrowseSummary.setText(daylight
                ? R.string.header_browse_summary : R.string.header_pick_summary);
        mFile.setEnabled(enabled && file);
        mHeight.setEnabled(enabled);
        mShadow.setEnabled(enabled);
    }

    private void showPacks() {
        if (mPackLabels.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.header_pack)
                .setItems(mPackLabels.toArray(new String[0]), (dialog, which) -> {
                    String value = mPackValues.get(which);
                    try {
                        if (!Settings.System.putString(getContentResolver(),
                                Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK, value)) {
                            toastFailed();
                            return;
                        }
                    } catch (SecurityException e) {
                        toastFailed();
                        return;
                    }
                    updatePackLabel();
                })
                .show();
    }

    private void updatePackLabel() {
        String current = Settings.System.getString(getContentResolver(),
                Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK);
        int index = mPackValues.indexOf(current);
        mPackButton.setText(index >= 0 ? mPackLabels.get(index) : getString(R.string.header_pack_none));
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("org.omnirom.omnistyle", "org.omnirom.omnistyle.PickHeaderActivity");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.missing_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void pickFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                    "image/jpeg", "image/png", "image/gif", "image/webp"});
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.header_needs_gallery, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        ContentResolver resolver = getContentResolver();
        deleteExisting(resolver);
        Uri out = copyHeader(resolver, data.getData());
        if (out == null) {
            toastFailed();
            return;
        }
        try {
            if (!Settings.System.putString(resolver,
                    Settings.System.STATUS_BAR_FILE_HEADER_IMAGE, out.toString())) {
                toastFailed();
            }
        } catch (SecurityException e) {
            toastFailed();
        }
    }

    private void loadPacks() {
        Map<String, String> packs = new HashMap<>();
        PackageManager pm = getPackageManager();
        Intent daylight = new Intent("org.omnirom.DaylightHeaderPack");
        for (ResolveInfo info : pm.queryIntentActivities(daylight, 0)) {
            String label = info.activityInfo.loadLabel(pm).toString();
            packs.put(label, info.activityInfo.packageName);
        }
        Intent packs1 = new Intent("org.omnirom.DaylightHeaderPack1");
        for (ResolveInfo info : pm.queryIntentActivities(packs1, 0)) {
            if (info.activityInfo.name.endsWith(".theme")) {
                continue;
            }
            String label = info.activityInfo.loadLabel(pm).toString();
            packs.put(label, info.activityInfo.packageName + "/" + info.activityInfo.name);
        }
        if (packs.isEmpty()) {
            loadOmniStylePacks(pm, packs);
        }
        List<String> labels = new ArrayList<>(packs.keySet());
        Collections.sort(labels);
        for (String label : labels) {
            mPackLabels.add(label);
            mPackValues.add(packs.get(label));
        }
    }

    /**
     * OmniStyle declares its collections as non-exported activities, so another
     * app's intent query can miss them. Read that package's activity list directly.
     */
    private void loadOmniStylePacks(PackageManager pm, Map<String, String> packs) {
        PackageInfo info;
        try {
            info = pm.getPackageInfo("org.omnirom.omnistyle", PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        if (info.activities == null) {
            return;
        }
        for (ActivityInfo activity : info.activities) {
            String name = activity.name;
            if (name.endsWith(".theme")
                    || name.endsWith("BrowseHeaderActivity")
                    || name.endsWith("PickHeaderActivity")
                    || name.endsWith("BrowseThemeHeaderActivity")
                    || name.endsWith("BrowseWallsActivity")
                    || name.endsWith("BrowseWallsFilterActivity")
                    || name.endsWith("WallpaperActivity")) {
                continue;
            }
            String label = activity.loadLabel(pm).toString();
            packs.put(label, activity.packageName + "/" + name);
        }
    }

    private boolean omniStyleInstalled() {
        Intent browse = new Intent();
        browse.setClassName("org.omnirom.omnistyle", "org.omnirom.omnistyle.PickHeaderActivity");
        return getPackageManager().resolveActivity(browse, 0) != null;
    }

    private void deleteExisting(ContentResolver resolver) {
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND "
                + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?";
        String[] args = new String[] {HEADER_RELATIVE_PATH + "/", HEADER_DISPLAY_NAME + "%"};
        try {
            resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, args);
        } catch (Exception ignored) {
            // A missing older copy should not block the new image.
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

    private boolean writeInt(CompoundButton button, String key, int value, boolean checked) {
        if (mGuard || button.getTag() != null) {
            return false;
        }
        try {
            if (Settings.System.putInt(getContentResolver(), key, value)) {
                return true;
            }
        } catch (SecurityException e) {
            // Put the switch back below.
        }
        button.setTag(Boolean.TRUE);
        button.setChecked(!checked);
        button.setTag(null);
        toastFailed();
        return false;
    }

    private void revertProvider() {
        mGuard = true;
        mProviders.check(providerId(mProvider));
        mGuard = false;
        toastFailed();
    }

    private void toastFailed() {
        Toast.makeText(this, R.string.header_failed, Toast.LENGTH_SHORT).show();
    }

    private void addProvider(RadioGroup group, int id, int label) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(label);
        group.addView(button);
    }

    private static int providerId(String provider) {
        if (PROVIDER_STATIC.equals(provider)) {
            return 2;
        }
        if (PROVIDER_FILE.equals(provider)) {
            return 3;
        }
        return 1;
    }

    private static String providerValue(int id) {
        if (id == 2) {
            return PROVIDER_STATIC;
        }
        if (id == 3) {
            return PROVIDER_FILE;
        }
        return PROVIDER_DAYLIGHT;
    }

    private void setGroupEnabled(RadioGroup group, boolean enabled) {
        group.setEnabled(enabled);
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setEnabled(enabled);
        }
    }

    private TextView label(int text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        view.setPadding(0, dp(16), 0, dp(8));
        return view;
    }

    private TextView small(int text) {
        TextView view = new TextView(this);
        if (text != 0) {
            view.setText(text);
        }
        view.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class IntSettingListener implements SeekBar.OnSeekBarChangeListener {
        private final String mKey;
        private final TextView mValue;

        IntSettingListener(String key, TextView value) {
            mKey = key;
            mValue = value;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mValue.setText(String.valueOf(progress));
            if (!fromUser) {
                return;
            }
            try {
                if (!Settings.System.putInt(getContentResolver(), mKey, progress)) {
                    toastFailed();
                }
            } catch (SecurityException e) {
                toastFailed();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
