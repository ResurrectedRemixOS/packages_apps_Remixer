/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.rr.Utils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Remix backup: every RR customization in one file, and back. Only keys on RR's own list are
 * saved or restored: the ones Remixer's pages write, plus RR-prefixed keys its custom pages use.
 * No accounts, passwords, networks, apps or messages are involved.
 */
public class BackupSettings extends SettingsPreferenceFragment {
    private static final String FORMAT = "rr-backup";
    private static final String KEY_LAST = "rr_backup_last";
    private static final String BOOT_PROP = "persist.sys.bootanimation_style";
    private static final String[] SYSTEM_PREFIXES = {"rr_", "status_bar_", "statusbar_", "qs_",
            "edge_light", "charging_glow", "now_bar", "fp_quick_launch", "charge_report_min",
            "pulse_", "udfps_", "lockscreen_", "lock_screen_", "clock_", "screen_on_time",
            "pocket_", "network_traffic", "omni_", "header_"};
    private static final String[] SECURE_KEYS = {
            Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
            "lock_screen_custom_clock_face", "lock_screen_custom_clock_style"};
    private static final Set<String> NEVER = Set.of("charge_report_history", KEY_LAST,
            "rr_ember_unlocked");

    private ActivityResultLauncher<String> mSaver;
    private ActivityResultLauncher<String[]> mOpener;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.remixer_backup);
        mSaver = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                    if (uri != null) save(uri);
                });
        mOpener = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) preview(uri);
        });
        findPreference("backup_now").setOnPreferenceClickListener(p -> {
            mSaver.launch("rr-remix-" + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                    .format(new Date()) + ".rrbackup");
            return true;
        });
        findPreference("backup_restore").setOnPreferenceClickListener(p -> {
            mOpener.launch(new String[] {"*/*"});
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        long last = Settings.System.getLongForUser(getContentResolver(), KEY_LAST, 0,
                UserHandle.USER_CURRENT);
        Preference now = findPreference("backup_now");
        now.setSummary(last == 0 ? getString(R.string.backup_never)
                : getString(R.string.backup_last, DateUtils.getRelativeTimeSpanString(last)));
    }

    /** Keys the Remixer pages write, by the settings table their preference uses. */
    private void collectXmlKeys(Set<String> system, Set<String> secure) {
        for (Field field : R.xml.class.getFields()) {
            if (!field.getName().startsWith("remixer_")) continue;
            try (XmlResourceParser parser = getResources().getXml(field.getInt(null))) {
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event != XmlPullParser.START_TAG) continue;
                    String name = parser.getName();
                    String key = parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "key");
                    if (key == null || name.contains("Lineage") || name.contains("Global")) continue;
                    if (name.contains("SecureSetting")) secure.add(key);
                    else if (name.contains("SystemSetting")) system.add(key);
                }
            } catch (Exception e) {
                // Skip what can't be read.
            }
        }
    }

    private boolean allowedSystem(String key, Set<String> xmlKeys) {
        if (NEVER.contains(key)) return false;
        if (xmlKeys.contains(key)) return true;
        for (String prefix : SYSTEM_PREFIXES) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    private void save(Uri uri) {
        Context context = requireContext();
        ContentResolver resolver = context.getContentResolver();
        Set<String> system = new HashSet<>();
        Set<String> secure = new HashSet<>();
        collectXmlKeys(system, secure);
        try {
            JSONObject systemJson = new JSONObject();
            try (Cursor cursor = resolver.query(Settings.System.CONTENT_URI,
                    new String[] {Settings.NameValueTable.NAME}, null, null, null)) {
                while (cursor != null && cursor.moveToNext()) {
                    String key = cursor.getString(0);
                    if (key == null || !allowedSystem(key, system)) continue;
                    String value = Settings.System.getStringForUser(resolver, key,
                            UserHandle.USER_CURRENT);
                    if (value != null) systemJson.put(key, value);
                }
            }
            JSONObject secureJson = new JSONObject();
            for (String key : secure) put(secureJson, key, resolver);
            for (String key : SECURE_KEYS) put(secureJson, key, resolver);
            JSONObject props = new JSONObject().put(BOOT_PROP, SystemProperties.get(BOOT_PROP, "0"));
            JSONObject root = new JSONObject().put("format", FORMAT).put("version", 1)
                    .put("created", System.currentTimeMillis())
                    .put("system", systemJson).put("secure", secureJson).put("props", props);
            try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
                out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Settings.System.putLongForUser(resolver, KEY_LAST, System.currentTimeMillis(),
                    UserHandle.USER_CURRENT);
            int count = systemJson.length() + secureJson.length();
            Toast.makeText(context, getString(R.string.backup_saved, count), Toast.LENGTH_SHORT)
                    .show();
            onResume();
        } catch (Exception e) {
            Toast.makeText(context, R.string.backup_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static void put(JSONObject json, String key, ContentResolver resolver) {
        String value = Settings.Secure.getStringForUser(resolver, key, UserHandle.USER_CURRENT);
        if (value == null) return;
        try {
            json.put(key, value);
        } catch (Exception ignored) {
            // Not a valid JSON name.
        }
    }

    private void preview(Uri uri) {
        Context context = requireContext();
        JSONObject root;
        try (InputStream in = context.getContentResolver().openInputStream(uri);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && text.length() < 2_000_000) {
                text.append(line).append('\n');
            }
            root = new JSONObject(text.toString());
            if (!FORMAT.equals(root.optString("format"))) throw new IllegalArgumentException();
        } catch (Exception e) {
            Toast.makeText(context, R.string.backup_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> system = new HashSet<>();
        Set<String> secure = new HashSet<>();
        collectXmlKeys(system, secure);
        for (String key : SECURE_KEYS) secure.add(key);
        JSONObject systemJson = root.optJSONObject("system");
        JSONObject secureJson = root.optJSONObject("secure");
        JSONObject props = root.optJSONObject("props");
        int systemCount = count(systemJson, key -> allowedSystem(key, system));
        int secureCount = count(secureJson, secure::contains);
        int total = systemCount + secureCount;
        new AlertDialog.Builder(context)
                .setTitle(R.string.backup_restore_title)
                .setMessage(getString(R.string.backup_restore_message, total))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.backup_restore, (d, w) -> {
                    ContentResolver resolver = context.getContentResolver();
                    apply(systemJson, key -> allowedSystem(key, system), (k, v) ->
                            Settings.System.putStringForUser(resolver, k, v,
                                    UserHandle.USER_CURRENT));
                    apply(secureJson, secure::contains, (k, v) ->
                            Settings.Secure.putStringForUser(resolver, k, v,
                                    UserHandle.USER_CURRENT));
                    if (props != null && props.has(BOOT_PROP)) {
                        SystemProperties.set(BOOT_PROP, props.optString(BOOT_PROP, "0"));
                    }
                    Toast.makeText(context, getString(R.string.backup_restored, total),
                            Toast.LENGTH_LONG).show();
                    Utils.restartSystemUI();
                })
                .show();
    }

    private interface KeyTest {
        boolean ok(String key);
    }

    private interface Writer {
        void write(String key, String value);
    }

    private static int count(JSONObject json, KeyTest test) {
        int count = 0;
        if (json == null) return 0;
        for (Iterator<String> it = json.keys(); it.hasNext(); ) {
            if (test.ok(it.next())) count++;
        }
        return count;
    }

    private static void apply(JSONObject json, KeyTest test, Writer writer) {
        if (json == null) return;
        for (Iterator<String> it = json.keys(); it.hasNext(); ) {
            String key = it.next();
            if (test.ok(key)) writer.write(key, json.optString(key));
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
