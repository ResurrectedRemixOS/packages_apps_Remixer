/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.backup;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.TwoStatePreference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.rr.Utils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import org.json.JSONObject;

import java.util.Map;

/**
 * Backup & restore: save every RR customization to one file and put it back after a clean flash.
 * Weekly backups go to a folder picked once; the Updater offers a backup before it installs.
 */
public class BackupSettings extends SettingsPreferenceFragment {
    /** From the Updater: save a backup right away, then go back to it. */
    public static final String EXTRA_BACKUP_NOW = "backup_now";

    private ActivityResultLauncher<String> mSaver;
    private ActivityResultLauncher<String[]> mOpener;
    private ActivityResultLauncher<Uri> mFolder;
    private TwoStatePreference mWeekly;
    private boolean mFromUpdater;
    private boolean mPendingSave;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_backup);
        Intent intent = getActivity() != null ? getActivity().getIntent() : null;
        mFromUpdater = intent != null && intent.getBooleanExtra(EXTRA_BACKUP_NOW, false);
        mPendingSave = mFromUpdater && icicle == null;
        mSaver = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                    if (uri != null) {
                        save(uri);
                    } else if (mFromUpdater) {
                        backToUpdater(false);
                    }
                });
        mOpener = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) preview(uri);
        });
        mFolder = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        setFolder(uri);
                    } else {
                        updateWeekly();
                    }
                });
        findPreference("backup_now").setOnPreferenceClickListener(p -> {
            mSaver.launch(RemixBackup.fileName());
            return true;
        });
        findPreference("backup_restore").setOnPreferenceClickListener(p -> {
            mOpener.launch(new String[] {"*/*"});
            return true;
        });
        mWeekly = findPreference("backup_weekly");
        mWeekly.setOnPreferenceChangeListener((p, value) -> {
            if ((Boolean) value) {
                mFolder.launch(null);
                return false;
            }
            Settings.System.putIntForUser(getContentResolver(), RemixBackup.KEY_WEEKLY, 0,
                    UserHandle.USER_CURRENT);
            RemixBackup.schedule(requireContext(), false);
            getView().post(this::updateWeekly);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        long last = Settings.System.getLongForUser(getContentResolver(), RemixBackup.KEY_LAST, 0,
                UserHandle.USER_CURRENT);
        findPreference("backup_now").setSummary(last == 0 ? getString(R.string.backup_never)
                : getString(R.string.backup_last, DateUtils.getRelativeTimeSpanString(last)));
        updateWeekly();
        if (mPendingSave) {
            mPendingSave = false;
            mSaver.launch(RemixBackup.fileName());
        }
    }

    private void updateWeekly() {
        ContentResolver resolver = getContentResolver();
        boolean on = Settings.System.getIntForUser(resolver, RemixBackup.KEY_WEEKLY, 0,
                UserHandle.USER_CURRENT) == 1;
        String folder = Settings.System.getStringForUser(resolver, RemixBackup.KEY_FOLDER,
                UserHandle.USER_CURRENT);
        mWeekly.setChecked(on && folder != null);
        mWeekly.setSummary(on && folder != null
                ? getString(R.string.backup_weekly_on, folderName(Uri.parse(folder)))
                : getString(R.string.backup_weekly_summary));
    }

    private static String folderName(Uri tree) {
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            int colon = id.indexOf(':');
            String path = colon >= 0 ? id.substring(colon + 1) : id;
            return path.isEmpty() ? id : path;
        } catch (Exception e) {
            return tree.toString();
        }
    }

    private void setFolder(Uri uri) {
        Context context = requireContext();
        ContentResolver resolver = context.getContentResolver();
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(context, R.string.backup_failed, Toast.LENGTH_SHORT).show();
            updateWeekly();
            return;
        }
        Settings.System.putStringForUser(resolver, RemixBackup.KEY_FOLDER, uri.toString(),
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, RemixBackup.KEY_WEEKLY, 1, UserHandle.USER_CURRENT);
        RemixBackup.schedule(context, true);
        updateWeekly();
        Context app = context.getApplicationContext();
        new Thread(() -> RemixBackup.writeWeekly(app), "RemixBackup").start();
    }

    private void save(Uri uri) {
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            int count = -1;
            try {
                JSONObject root = RemixBackup.build(app);
                RemixBackup.write(app, uri, root);
                count = RemixBackup.count(root);
            } catch (Exception e) {
                // Reported below.
            }
            int saved = count;
            Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                Toast.makeText(app, saved >= 0 ? app.getString(R.string.backup_saved, saved)
                        : app.getString(R.string.backup_failed), Toast.LENGTH_SHORT).show();
                if (mFromUpdater) {
                    backToUpdater(saved >= 0);
                } else if (isResumed()) {
                    onResume();
                }
            });
        }, "RemixBackup").start();
    }

    private void backToUpdater(boolean saved) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.setResult(saved ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
        activity.finish();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) {
                return cursor.getString(0).replaceAll("\\.rrbackup$", "");
            }
        } catch (Exception e) {
            // Fall through.
        }
        return getString(R.string.backup_title);
    }

    /** Reads the file off the main thread, then shows what a restore would do. */
    private void preview(Uri uri) {
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            JSONObject root;
            RemixBackup.Preview preview;
            try {
                root = RemixBackup.read(app, uri);
                preview = RemixBackup.preview(app, root);
            } catch (Exception e) {
                root = null;
                preview = null;
            }
            JSONObject read = root;
            RemixBackup.Preview found = preview;
            Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (read == null) {
                    Toast.makeText(app, R.string.backup_invalid, Toast.LENGTH_SHORT).show();
                } else {
                    preview(uri, read, found);
                }
            });
        }, "RemixBackup").start();
    }

    private void preview(Uri uri, JSONObject root, RemixBackup.Preview preview) {
        Context context = getPrefContext();
        int pad = dp(24);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(4), pad, 0);
        TextView sets = text(context, getString(R.string.backup_sets, preview.total), 14, true);
        sets.setPadding(0, 0, 0, dp(8));
        box.addView(sets);
        for (Map.Entry<Integer, Integer> group : preview.groups.entrySet()) {
            if (group.getValue() == 0) continue;
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            row.addView(text(context, getString(group.getKey()), 16, false),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(text(context, String.valueOf(group.getValue()), 16, true));
            box.addView(row);
        }
        Switch photo = new Switch(context);
        if (preview.photo) {
            photo.setText(R.string.backup_part_photo);
            photo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            photo.setChecked(true);
            photo.setPadding(0, dp(10), 0, dp(4));
            box.addView(photo);
        }
        new AlertDialog.Builder(context)
                .setTitle(displayName(uri))
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.backup_restore, (d, w) ->
                        restore(root, preview.photo && photo.isChecked(), preview.skipped))
                .show();
    }

    /** Writes the settings off the main thread: the header photo goes through MediaStore. */
    private void restore(JSONObject root, boolean photo, int skipped) {
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            int restored = RemixBackup.restore(app, root, photo);
            Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (isAdded()) restored(getPrefContext(), restored, skipped);
            });
        }, "RemixBackup").start();
    }

    private void restored(Context context, int restored, int skipped) {
        String message = getString(R.string.backup_restored_message);
        if (skipped > 0) message += "\n\n" + getString(R.string.backup_skipped, skipped);
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.backup_restored, restored))
                .setMessage(message)
                .setPositiveButton(R.string.backup_done, null)
                .setOnDismissListener(d -> Utils.restartSystemUI())
                .show();
    }

    private TextView text(Context context, String value, int sp, boolean secondary) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (secondary) {
            TypedValue tv = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, tv, true);
            view.setTextColor(tv.resourceId != 0 ? context.getColorStateList(tv.resourceId)
                    : android.content.res.ColorStateList.valueOf(tv.data));
        }
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
