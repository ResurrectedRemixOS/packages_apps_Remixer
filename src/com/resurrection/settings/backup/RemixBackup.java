/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.backup;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;

import com.android.settings.R;

import lineageos.providers.LineageSettings;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Remix backup: every RR customization in one file, and back. Only keys on RR's own list are
 * saved or restored: the ones Remixer's pages write, RR-prefixed keys its custom pages use, the
 * LineageParts settings, the theme and the boot animation. No accounts, passwords, networks,
 * apps or messages are involved.
 */
public final class RemixBackup {
    static final String FORMAT = "rr-backup";
    static final String KEY_LAST = "rr_backup_last";
    static final String KEY_WEEKLY = "rr_backup_weekly";
    static final String KEY_FOLDER = "rr_backup_folder";
    static final String KEY_OFFER = "rr_backup_offer_update";
    static final String KEY_PHOTO = "rr_backup_header_photo";

    static final String PART_REMIXER = "remixer";
    static final String PART_LINEAGE = "lineage";
    static final String PART_THEME = "theme";
    static final String PART_HEADER = "header";
    static final String PART_BOOT = "boot";
    static final String PART_LOOKS = "looks";
    static final String[] PARTS = {PART_REMIXER, PART_LINEAGE, PART_THEME, PART_HEADER,
            PART_BOOT, PART_LOOKS};

    /** Restore summary groups, in the order Remixer shows its sections. */
    static final int[] GROUPS = {R.string.backup_group_status_bar, R.string.backup_group_lock_screen,
            R.string.backup_group_theme, R.string.backup_group_qs, R.string.backup_group_notifications,
            R.string.backup_group_gestures, R.string.backup_group_other};

    private static final String BOOT_PROP = "persist.sys.bootanimation_style";
    private static final String THEME = Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES;
    private static final String LOOKS = "rr_looks";
    private static final String HEADER_FILE = Settings.System.STATUS_BAR_FILE_HEADER_IMAGE;
    private static final String HEADER_PROVIDER = Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER;
    private static final String HEADER_RELATIVE_PATH = "Pictures/QSHeader";
    private static final String HEADER_DISPLAY_NAME = "qs_header_image";
    private static final String[] HEADER_PREFIXES = {"status_bar_custom_header",
            "status_bar_daylight_header", "status_bar_file_header"};
    private static final String[] SYSTEM_PREFIXES = {"rr_", "status_bar_", "statusbar_", "qs_",
            "edge_light", "now_bar", "fp_quick_launch", "charge_report_",
            "pulse_", "udfps_", "lockscreen_", "lock_screen_", "clock_", "screen_on_time",
            "pocket_", "network_traffic", "omni_", "header_", "pie_"};
    private static final String[] SECURE_KEYS = {THEME, "rr_accent_shade", "rr_accent_saturation"};
    /** The clock face page's settings. */
    private static final String SECURE_PREFIX = "lock_screen_custom_clock_";
    private static final Set<String> NEVER = Set.of("charge_report_history", "rr_ember_unlocked");
    private static final String NEVER_PREFIX = "rr_backup_";

    private static final String WEEKLY_PREFIX = "rr-remix-weekly-";
    private static final int WEEKLY_KEEP = 4;
    private static final String NAMESPACE = "rr_backup";
    private static final int JOB_ID = 1;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    private RemixBackup() {}

    /** Keys the Remixer pages write, with the summary group of the page they're on. */
    private static final class XmlKeys {
        final Map<String, Integer> system = new HashMap<>();
        final Map<String, Integer> secure = new HashMap<>();
    }

    private static XmlKeys xmlKeys(Context context) {
        XmlKeys keys = new XmlKeys();
        for (Field field : R.xml.class.getFields()) {
            String file = field.getName();
            if (!file.startsWith("remixer_")) continue;
            int group = groupForPage(file);
            try (XmlResourceParser parser = context.getResources().getXml(field.getInt(null))) {
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event != XmlPullParser.START_TAG) continue;
                    String name = parser.getName();
                    String key = parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "key");
                    if (key == null || name.contains("Lineage") || name.contains("Global")) continue;
                    if (name.contains("SecureSetting")) keys.secure.put(key, group);
                    else if (name.contains("SystemSetting")) keys.system.put(key, group);
                }
            } catch (Exception e) {
                // Skip what can't be read.
            }
        }
        return keys;
    }

    private static int groupForPage(String file) {
        if (file.contains("statusbar")) return R.string.backup_group_status_bar;
        if (file.contains("lockscreen") || file.contains("now_bar") || file.contains("weather")) {
            return R.string.backup_group_lock_screen;
        }
        if (file.contains("theme")) return R.string.backup_group_theme;
        if (file.contains("_qs")) return R.string.backup_group_qs;
        if (file.contains("notifications") || file.contains("edge_light")) {
            return R.string.backup_group_notifications;
        }
        if (file.contains("gesture") || file.contains("pie") || file.contains("navigation")
                || file.contains("navbar")) {
            return R.string.backup_group_gestures;
        }
        return R.string.backup_group_other;
    }

    private static int groupForKey(String key) {
        if (isHeader(key) || key.startsWith("qs_") || key.startsWith("omni_")
                || key.startsWith("header_")) {
            return R.string.backup_group_qs;
        }
        if (key.startsWith("status_bar_") || key.startsWith("statusbar_")
                || key.startsWith("network_traffic") || key.startsWith("clock_")
                || key.startsWith("screen_on_time")) {
            return R.string.backup_group_status_bar;
        }
        if (key.startsWith("lockscreen_") || key.startsWith("lock_screen_")
                || key.startsWith("now_bar")
                || key.startsWith("udfps_") || key.startsWith("pocket_")
                || key.startsWith("fp_quick_launch")) {
            return R.string.backup_group_lock_screen;
        }
        if (key.startsWith("edge_light") || key.startsWith("notification_")
                || key.startsWith("pulse_")) {
            return R.string.backup_group_notifications;
        }
        if (key.startsWith("pie_") || key.startsWith("gesture_") || key.startsWith("navigation_")
                || key.startsWith("navbar_") || key.contains("_gesture")) {
            return R.string.backup_group_gestures;
        }
        if (key.equals(THEME) || key.startsWith("rr_accent") || key.equals(LOOKS)
                || key.startsWith("berry_")) {
            return R.string.backup_group_theme;
        }
        return R.string.backup_group_other;
    }

    private static boolean isHeader(String key) {
        for (String prefix : HEADER_PREFIXES) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String systemPart(String key) {
        if (isHeader(key)) return PART_HEADER;
        if (LOOKS.equals(key)) return PART_LOOKS;
        return PART_REMIXER;
    }

    private static String securePart(String key) {
        return key.equals(THEME) || key.startsWith("rr_accent") ? PART_THEME : PART_REMIXER;
    }

    private static boolean allowedSystem(String key, XmlKeys xml) {
        if (NEVER.contains(key) || key.startsWith(NEVER_PREFIX)) return false;
        if (xml.system.containsKey(key)) return true;
        for (String prefix : SYSTEM_PREFIXES) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean allowedSecure(String key, XmlKeys xml) {
        if (xml.secure.containsKey(key) || key.startsWith(SECURE_PREFIX)) return true;
        for (String allowed : SECURE_KEYS) {
            if (allowed.equals(key)) return true;
        }
        return false;
    }

    static boolean included(Context context, String part) {
        return Settings.System.getIntForUser(context.getContentResolver(), "rr_backup_inc_" + part,
                1, UserHandle.USER_CURRENT) == 1;
    }

    /** The whole backup as JSON, with only the parts that are switched on. */
    static JSONObject build(Context context) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        XmlKeys xml = xmlKeys(context);
        JSONObject system = new JSONObject();
        try (Cursor cursor = resolver.query(Settings.System.CONTENT_URI,
                new String[] {Settings.NameValueTable.NAME}, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                String key = cursor.getString(0);
                if (key == null || !allowedSystem(key, xml)) continue;
                if (!included(context, systemPart(key))) continue;
                if (HEADER_FILE.equals(key)) continue; // Only as the photo, below.
                String value = Settings.System.getStringForUser(resolver, key,
                        UserHandle.USER_CURRENT);
                if (value != null) system.put(key, value);
            }
        }
        JSONObject secure = new JSONObject();
        List<String> secureKeys = new ArrayList<>(xml.secure.keySet());
        Collections.addAll(secureKeys, SECURE_KEYS);
        try (Cursor cursor = resolver.query(Settings.Secure.CONTENT_URI,
                new String[] {Settings.NameValueTable.NAME}, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                String key = cursor.getString(0);
                if (key != null && key.startsWith(SECURE_PREFIX) && !secureKeys.contains(key)) {
                    secureKeys.add(key);
                }
            }
        }
        for (String key : secureKeys) {
            if (!included(context, securePart(key))) continue;
            String value = Settings.Secure.getStringForUser(resolver, key, UserHandle.USER_CURRENT);
            if (value != null) secure.put(key, value);
        }
        JSONObject root = new JSONObject().put("format", FORMAT).put("version", 2)
                .put("created", System.currentTimeMillis())
                .put("system", system).put("secure", secure);
        if (included(context, PART_LINEAGE)) {
            JSONObject lineageSystem = new JSONObject();
            for (String key : LineageSettings.System.VALIDATORS.keySet()) {
                String value = LineageSettings.System.getStringForUser(resolver, key,
                        UserHandle.USER_CURRENT);
                if (value != null) lineageSystem.put(key, value);
            }
            JSONObject lineageSecure = new JSONObject();
            for (String key : LineageSettings.Secure.VALIDATORS.keySet()) {
                String value = LineageSettings.Secure.getStringForUser(resolver, key,
                        UserHandle.USER_CURRENT);
                if (value != null) lineageSecure.put(key, value);
            }
            root.put("lineage_system", lineageSystem).put("lineage_secure", lineageSecure);
        }
        if (included(context, PART_BOOT)) {
            root.put("props", new JSONObject().put(BOOT_PROP, SystemProperties.get(BOOT_PROP, "0")));
        }
        if (included(context, PART_HEADER) && Settings.System.getIntForUser(resolver, KEY_PHOTO, 0,
                UserHandle.USER_CURRENT) == 1) {
            String photo = headerPhoto(resolver);
            if (photo != null) root.put("header_photo", photo);
        }
        return root;
    }

    static int count(JSONObject root) {
        int count = 0;
        for (String table : new String[] {"system", "secure", "lineage_system", "lineage_secure",
                "props"}) {
            JSONObject json = root.optJSONObject(table);
            if (json != null) count += json.length();
        }
        return count;
    }

    static void write(Context context, Uri uri, JSONObject root) throws Exception {
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        Settings.System.putLongForUser(context.getContentResolver(), KEY_LAST,
                System.currentTimeMillis(), UserHandle.USER_CURRENT);
    }

    static JSONObject read(Context context, Uri uri) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && text.length() < 8_000_000) {
                text.append(line).append('\n');
            }
            JSONObject root = new JSONObject(text.toString());
            if (!FORMAT.equals(root.optString("format"))) throw new IllegalArgumentException();
            return root;
        }
    }

    static String fileName() {
        return "rr-remix-" + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date())
                + ".rrbackup";
    }

    /** What a restore would do, before it does it. */
    static final class Preview {
        /** Group label to the number of settings it sets, in GROUPS order. */
        final Map<Integer, Integer> groups = new LinkedHashMap<>();
        int total;
        int skipped;
        boolean photo;
    }

    private interface Visitor {
        void visit(String table, String key, String value, int group);
    }

    /** Walks every setting in the file this build allows; returns how many it skipped. */
    private static int walk(Context context, JSONObject root, Visitor visitor) {
        XmlKeys xml = xmlKeys(context);
        int skipped = 0;
        skipped += walkTable(root.optJSONObject("lineage_system"), "lineage_system",
                key -> LineageSettings.System.VALIDATORS.containsKey(key), key -> groupForKey(key),
                visitor);
        skipped += walkTable(root.optJSONObject("lineage_secure"), "lineage_secure",
                key -> LineageSettings.Secure.VALIDATORS.containsKey(key), key -> groupForKey(key),
                visitor);
        skipped += walkTable(root.optJSONObject("system"), "system",
                key -> allowedSystem(key, xml), key -> {
                    Integer group = xml.system.get(key);
                    return group != null ? group : groupForKey(key);
                }, visitor);
        skipped += walkTable(root.optJSONObject("secure"), "secure",
                key -> allowedSecure(key, xml), key -> {
                    Integer group = xml.secure.get(key);
                    return group != null ? group : groupForKey(key);
                }, visitor);
        skipped += walkTable(root.optJSONObject("props"), "props", BOOT_PROP::equals,
                key -> R.string.backup_group_theme, visitor);
        return skipped;
    }

    private interface KeyTest {
        boolean ok(String key);
    }

    private interface KeyGroup {
        int group(String key);
    }

    private static int walkTable(JSONObject json, String table, KeyTest test, KeyGroup group,
            Visitor visitor) {
        if (json == null) return 0;
        int skipped = 0;
        for (Iterator<String> it = json.keys(); it.hasNext(); ) {
            String key = it.next();
            if (test.ok(key)) {
                visitor.visit(table, key, json.optString(key), group.group(key));
            } else {
                skipped++;
            }
        }
        return skipped;
    }

    static Preview preview(Context context, JSONObject root) {
        Preview preview = new Preview();
        for (int group : GROUPS) preview.groups.put(group, 0);
        preview.skipped = walk(context, root, (table, key, value, group) -> {
            preview.groups.put(group, preview.groups.get(group) + 1);
            preview.total++;
        });
        preview.photo = root.has("header_photo");
        return preview;
    }

    /** Writes the file's settings; returns how many were set. */
    static int restore(Context context, JSONObject root, boolean photo) {
        ContentResolver resolver = context.getContentResolver();
        boolean photoRestored = photo && restorePhoto(resolver, root.optString("header_photo"));
        int[] written = {0};
        walk(context, root, (table, key, value, group) -> {
            boolean ok;
            switch (table) {
                case "lineage_system":
                    ok = LineageSettings.System.putStringForUser(resolver, key, value,
                            UserHandle.USER_CURRENT);
                    break;
                case "lineage_secure":
                    ok = LineageSettings.Secure.putStringForUser(resolver, key, value,
                            UserHandle.USER_CURRENT);
                    break;
                case "system":
                    // A file header without its photo would point at a picture that's gone.
                    if (HEADER_FILE.equals(key)) return;
                    if (!photoRestored && HEADER_PROVIDER.equals(key) && "file".equals(value)) {
                        return;
                    }
                    ok = Settings.System.putStringForUser(resolver, key, value,
                            UserHandle.USER_CURRENT);
                    break;
                case "secure":
                    ok = Settings.Secure.putStringForUser(resolver, key, value,
                            UserHandle.USER_CURRENT);
                    break;
                default:
                    SystemProperties.set(key, value);
                    ok = true;
                    break;
            }
            if (ok) written[0]++;
        });
        return written[0] + (photoRestored ? 1 : 0);
    }

    /** The header photo, scaled down and as JPEG, in base64. */
    private static String headerPhoto(ContentResolver resolver) {
        String value = Settings.System.getStringForUser(resolver, HEADER_FILE,
                UserHandle.USER_CURRENT);
        if (value == null || value.isEmpty()) return null;
        Uri uri = Uri.parse(value);
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > 1600) options.inSampleSize *= 2;
            Bitmap bitmap;
            try (InputStream in = resolver.openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /** Puts the photo back where the header page keeps it, and points the header at it. */
    private static boolean restorePhoto(ContentResolver resolver, String base64) {
        if (base64 == null || base64.isEmpty()) return false;
        Uri out = null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, HEADER_DISPLAY_NAME);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, HEADER_RELATIVE_PATH);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            out = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (out == null) return false;
            try (OutputStream output = resolver.openOutputStream(out, "wt")) {
                output.write(bytes);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(out, done, null, null);
            try {
                resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.MediaColumns.RELATIVE_PATH + "=? AND "
                                + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? AND "
                                + MediaStore.MediaColumns._ID + "!=?",
                        new String[] {HEADER_RELATIVE_PATH + "/", HEADER_DISPLAY_NAME + "%",
                                String.valueOf(ContentUris.parseId(out))});
            } catch (Exception ignored) {
                // A leftover older copy is harmless.
            }
            return Settings.System.putStringForUser(resolver, HEADER_FILE, out.toString(),
                    UserHandle.USER_CURRENT);
        } catch (Exception e) {
            if (out != null) {
                try {
                    resolver.delete(out, null, null);
                } catch (Exception ignored) {
                    // Already unusable.
                }
            }
            return false;
        }
    }

    /** The weekly job: a dated backup in the picked folder, keeping the last few. */
    static boolean writeWeekly(Context context) {
        ContentResolver resolver = context.getContentResolver();
        String folder = Settings.System.getStringForUser(resolver, KEY_FOLDER,
                UserHandle.USER_CURRENT);
        if (folder == null) return false;
        try {
            Uri tree = Uri.parse(folder);
            String treeId = DocumentsContract.getTreeDocumentId(tree);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId);
            String name = WEEKLY_PREFIX + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                    .format(new Date()) + ".rrbackup";
            Uri doc = DocumentsContract.createDocument(resolver, parent,
                    "application/octet-stream", name);
            if (doc == null) return false;
            write(context, doc, build(context));
            List<String[]> weekly = new ArrayList<>();
            try (Cursor cursor = resolver.query(
                    DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
                    new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                while (cursor != null && cursor.moveToNext()) {
                    String child = cursor.getString(1);
                    if (child != null && child.startsWith(WEEKLY_PREFIX)) {
                        weekly.add(new String[] {child, cursor.getString(0)});
                    }
                }
            }
            weekly.sort((a, b) -> b[0].compareTo(a[0]));
            for (int i = WEEKLY_KEEP; i < weekly.size(); i++) {
                DocumentsContract.deleteDocument(resolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, weekly.get(i)[1]));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void schedule(Context context, boolean on) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        scheduler = scheduler.forNamespace(NAMESPACE);
        if (!on) {
            scheduler.cancel(JOB_ID);
            return;
        }
        scheduler.schedule(new JobInfo.Builder(JOB_ID,
                new ComponentName(context, BackupJobService.class))
                .setPeriodic(7 * DAY_MS, DAY_MS)
                .setRequiresCharging(true)
                .setPersisted(true)
                .build());
    }
}
