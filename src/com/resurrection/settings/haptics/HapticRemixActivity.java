/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.haptics;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Haptic Remix: tap a rhythm on the pad, keep it as a pattern, and give it to calls, a contact,
 * an app's notifications, the alarm, plugging in or a fingerprint match. Patterns and
 * assignments are the JSON the framework's RemixHaptics reads.
 */
public class HapticRemixActivity extends CollapsingToolbarBaseActivity {
    static final String KEY_PATTERNS = "rr_haptic_patterns";
    static final String KEY_ASSIGN = "rr_haptic_assign";
    private static final String T_CALL = "call";
    private static final String T_ALARM = "alarm";
    private static final String T_PLUG = "plug";
    private static final String T_FINGERPRINT = "fingerprint";
    private static final String P_APP = "app:";
    private static final String P_NUMBER = "number:";
    private static final int REQ_CONTACT = 41;
    /** A tap shorter than this is a tick. */
    private static final long MIN_ON_MS = 30;
    private static final long MAX_GAP_MS = 2_000;
    private static final long MAX_TOTAL_MS = 30_000;

    private Context mThemed;
    private LinearLayout mList;
    private Vibrator mVibrator;
    private PadView mPad;
    private StripView mStrip;
    private TextView mLength;
    private Slider mStrength;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<Long> mTimings = new ArrayList<>();
    private final ArrayList<Integer> mAmplitudes = new ArrayList<>();
    private long mDownAt;
    private long mLastUp;
    private JSONArray mPatterns = new JSONArray();
    private JSONObject mAssign = new JSONObject();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.haptic_remix_title);
        mThemed = new ContextThemeWrapper(this,
                com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight);
        mVibrator = getSystemService(Vibrator.class);
        NestedScrollView scroll = new NestedScrollView(mThemed);
        mList = new LinearLayout(mThemed);
        mList.setOrientation(LinearLayout.VERTICAL);
        mList.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(mList);
        setContentView(scroll);
        load();
        build();
    }

    // Storage

    private void load() {
        try {
            String json = Settings.System.getStringForUser(getContentResolver(), KEY_PATTERNS,
                    UserHandle.USER_CURRENT);
            mPatterns = new JSONArray(json != null ? json : "[]");
        } catch (Exception e) {
            mPatterns = new JSONArray();
        }
        try {
            String json = Settings.System.getStringForUser(getContentResolver(), KEY_ASSIGN,
                    UserHandle.USER_CURRENT);
            mAssign = new JSONObject(json != null ? json : "{}");
        } catch (Exception e) {
            mAssign = new JSONObject();
        }
    }

    private void store() {
        Settings.System.putStringForUser(getContentResolver(), KEY_PATTERNS,
                mPatterns.toString(), UserHandle.USER_CURRENT);
        Settings.System.putStringForUser(getContentResolver(), KEY_ASSIGN, mAssign.toString(),
                UserHandle.USER_CURRENT);
    }

    private List<String> names() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < mPatterns.length(); i++) {
            names.add(mPatterns.optJSONObject(i).optString("name"));
        }
        return names;
    }

    // Page

    private void build() {
        mList.removeAllViews();
        section(R.string.haptic_remix_record);
        mPad = new PadView(mThemed);
        mList.addView(mPad, new LinearLayout.LayoutParams(dp(220), dp(220)) {{
            gravity = android.view.Gravity.CENTER_HORIZONTAL;
            topMargin = dp(8);
            bottomMargin = dp(12);
        }});
        mStrip = new StripView(mThemed);
        mList.addView(mStrip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)));
        mLength = new TextView(mThemed);
        mLength.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
        mLength.setPadding(dp(4), dp(6), dp(4), 0);
        mList.addView(mLength);
        updateLength();

        TextView strength = label(R.string.haptic_remix_strength);
        mList.addView(strength);
        mStrength = new Slider(mThemed);
        mStrength.setValueFrom(20f);
        mStrength.setValueTo(100f);
        mStrength.setStepSize(5f);
        mStrength.setValue(85f);
        mList.addView(mStrength);

        LinearLayout buttons = row();
        buttons.addView(button(R.string.haptic_remix_clear, v -> clear()), weight());
        buttons.addView(button(R.string.haptic_remix_play, v -> play(current(false))), weight());
        buttons.addView(button(R.string.haptic_remix_save, v -> save()), weight());
        mList.addView(buttons);

        section(R.string.haptic_remix_presets);
        ChipGroup presets = new ChipGroup(mThemed);
        presets.setSingleLine(false);
        preset(presets, R.string.haptic_remix_preset_flatline,
                new long[] {0, 70, 110, 50, 500}, new int[] {0, 255, 0, 160, 0});
        preset(presets, R.string.haptic_remix_preset_neon,
                new long[] {0, 40, 60, 40, 60, 40, 400}, new int[] {0, 220, 0, 220, 0, 220, 0});
        preset(presets, R.string.haptic_remix_preset_glass,
                new long[] {0, 70, 30, 70, 30, 70, 30, 110, 400},
                new int[] {0, 70, 0, 120, 0, 180, 0, 250, 0});
        preset(presets, R.string.haptic_remix_preset_sos,
                new long[] {0, 100, 100, 100, 100, 100, 250, 300, 100, 300, 100, 300, 250, 100,
                        100, 100, 100, 100, 700},
                new int[] {0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0, 230, 0,
                        230, 0});
        Chip beat = new Chip(mThemed);
        beat.setText(R.string.haptic_remix_preset_ringtone);
        beat.setOnClickListener(v -> beatOfTheRingtone());
        presets.addView(beat);
        mList.addView(presets);

        section(R.string.haptic_remix_patterns);
        if (mPatterns.length() == 0) {
            mList.addView(hint(R.string.haptic_remix_patterns_none));
        }
        for (int i = 0; i < mPatterns.length(); i++) {
            JSONObject pattern = mPatterns.optJSONObject(i);
            final int index = i;
            View row = item(pattern.optString("name"),
                    getString(R.string.haptic_remix_seconds, seconds(pattern)),
                    v -> loadPattern(pattern));
            row.setOnLongClickListener(v -> {
                deletePattern(index);
                return true;
            });
            mList.addView(row);
        }

        section(R.string.haptic_remix_assign);
        mList.addView(target(R.string.haptic_remix_calls, T_CALL));
        mList.addView(target(R.string.haptic_remix_alarm, T_ALARM));
        mList.addView(target(R.string.haptic_remix_plug, T_PLUG));
        mList.addView(target(R.string.haptic_remix_fingerprint, T_FINGERPRINT));
        JSONArray contacts = mAssign.optJSONArray("contacts");
        for (int i = 0; contacts != null && i < contacts.length(); i++) {
            JSONObject contact = contacts.optJSONObject(i);
            final int index = i;
            String summary = getString(R.string.haptic_remix_contact_summary,
                    contact.optString("pattern"));
            int colorIndex = colorIndex(contact.optString("color"));
            if (colorIndex > 0) summary += " · " + getString(COLOR_NAMES[colorIndex]);
            View row = item(contact.optString("name"), summary,
                    v -> pickFor(P_NUMBER + contact.optString("number"), contact.optString("name"),
                            name -> {
                                try {
                                    if (name == null) {
                                        mAssign.getJSONArray("contacts").remove(index);
                                    } else {
                                        contact.put("pattern", name);
                                    }
                                } catch (Exception e) {
                                    // Nothing to keep.
                                }
                            }));
            row.setOnLongClickListener(v -> {
                pickColor(contact);
                return true;
            });
            mList.addView(row);
        }
        mList.addView(item(getString(R.string.haptic_remix_add_contact), null, v -> {
            Intent pick = new Intent(Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            try {
                startActivityForResult(pick, REQ_CONTACT);
            } catch (Exception e) {
                Toast.makeText(this, R.string.haptic_remix_no_contacts, Toast.LENGTH_SHORT).show();
            }
        }));
        for (Iterator<String> keys = mAssign.keys(); keys.hasNext();) {
            String key = keys.next();
            if (!key.startsWith(P_APP)) continue;
            String pkg = key.substring(P_APP.length());
            mList.addView(item(appLabel(pkg),
                    getString(R.string.haptic_remix_contact_summary, mAssign.optString(key)),
                    v -> pickFor(key, appLabel(pkg), null)));
        }
        mList.addView(item(getString(R.string.haptic_remix_add_app), null, v -> pickApp()));
    }

    /** Edge light colors a contact's messages can take; the first is the accent. */
    private static final int[] COLOR_NAMES = {R.string.haptic_remix_color_accent,
            R.string.haptic_remix_color_crimson, R.string.haptic_remix_color_violet,
            R.string.haptic_remix_color_blue, R.string.haptic_remix_color_green,
            R.string.haptic_remix_color_amber, R.string.haptic_remix_color_white};
    private static final String[] COLOR_VALUES = {"", "#FF2D55", "#8B5CF6", "#3B82F6", "#22C55E",
            "#F59E0B", "#FFFFFF"};

    private static int colorIndex(String value) {
        for (int i = 1; i < COLOR_VALUES.length; i++) {
            if (COLOR_VALUES[i].equalsIgnoreCase(value)) return i;
        }
        return 0;
    }

    /** Long-press on a contact: the edge light color their messages get. */
    private void pickColor(JSONObject contact) {
        CharSequence[] names = new CharSequence[COLOR_NAMES.length];
        for (int i = 0; i < names.length; i++) names[i] = getString(COLOR_NAMES[i]);
        new MaterialAlertDialogBuilder(mThemed, R.style.ThemeOverlay_Remixer_AlertDialog)
                .setTitle(getString(R.string.haptic_remix_edge_color, contact.optString("name")))
                .setSingleChoiceItems(names, colorIndex(contact.optString("color")), (d, which) -> {
                    try {
                        if (which == 0) contact.remove("color");
                        else contact.put("color", COLOR_VALUES[which]);
                    } catch (Exception e) {
                        // A color is always a valid value.
                    }
                    store();
                    d.dismiss();
                    build();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private View target(int title, String key) {
        String name = mAssign.optString(key);
        return item(getString(title), name.isEmpty() ? getString(R.string.haptic_remix_default)
                : name, v -> pickFor(key, getString(title), null));
    }

    private interface Change {
        void apply(String name);
    }

    /** Default or one of the patterns, for [key]. [after] hears the chosen name, null for Default. */
    private void pickFor(String key, CharSequence title, Change after) {
        List<String> names = names();
        if (names.isEmpty()) {
            Toast.makeText(this, R.string.haptic_remix_patterns_none, Toast.LENGTH_SHORT).show();
            return;
        }
        List<CharSequence> choices = new ArrayList<>(names);
        choices.add(0, getString(R.string.haptic_remix_default));
        int current = Math.max(0, choices.indexOf(mAssign.optString(key)));
        new MaterialAlertDialogBuilder(mThemed, R.style.ThemeOverlay_Remixer_AlertDialog)
                .setTitle(title)
                .setSingleChoiceItems(choices.toArray(new CharSequence[0]), current, (d, which) -> {
                    String name = which == 0 ? null : names.get(which - 1);
                    try {
                        if (name == null) mAssign.remove(key); else mAssign.put(key, name);
                    } catch (Exception e) {
                        // A name is always a valid value.
                    }
                    if (after != null) after.apply(name);
                    store();
                    d.dismiss();
                    build();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CONTACT || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        String number = null;
        String name = null;
        try (Cursor cursor = getContentResolver().query(data.getData(), new String[] {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                number = cursor.getString(0);
                name = cursor.getString(1);
            }
        } catch (Exception e) {
            // No access: say so below.
        }
        if (TextUtils.isEmpty(number)) {
            Toast.makeText(this, R.string.haptic_remix_no_contacts, Toast.LENGTH_SHORT).show();
            return;
        }
        String normalized = PhoneNumberUtils.normalizeNumber(number);
        String contactName = TextUtils.isEmpty(name) ? number : name;
        pickFor(P_NUMBER + normalized, contactName, chosen -> {
            try {
                JSONArray contacts = mAssign.optJSONArray("contacts");
                if (contacts == null) mAssign.put("contacts", contacts = new JSONArray());
                for (int i = contacts.length() - 1; i >= 0; i--) {
                    if (normalized.equals(contacts.optJSONObject(i).optString("number"))) {
                        contacts.remove(i);
                    }
                }
                if (chosen != null) {
                    contacts.put(new JSONObject().put("name", contactName)
                            .put("number", normalized).put("pattern", chosen));
                }
            } catch (Exception e) {
                // Nothing to keep.
            }
        });
    }

    private void pickApp() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<String> packages = new ArrayList<>();
            List<CharSequence> labels = new ArrayList<>();
            for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
                String pkg = info.activityInfo.packageName;
                if (packages.contains(pkg)) continue;
                packages.add(pkg);
                labels.add(info.loadLabel(pm));
            }
            Integer[] order = new Integer[packages.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Collator collator = Collator.getInstance();
            java.util.Arrays.sort(order, (a, b) ->
                    collator.compare(labels.get(a).toString(), labels.get(b).toString()));
            CharSequence[] sortedLabels = new CharSequence[order.length];
            String[] sortedPackages = new String[order.length];
            for (int i = 0; i < order.length; i++) {
                sortedLabels[i] = labels.get(order[i]);
                sortedPackages[i] = packages.get(order[i]);
            }
            mHandler.post(() -> {
                if (isFinishing()) return;
                new MaterialAlertDialogBuilder(mThemed)
                        .setTitle(R.string.haptic_remix_add_app)
                        .setItems(sortedLabels, (d, which) ->
                                pickFor(P_APP + sortedPackages[which], sortedLabels[which], null))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }, "HapticRemixApps").start();
    }

    private CharSequence appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    // Recording

    private int amplitude() {
        return Math.round(255f * mStrength.getValue() / 100f);
    }

    private void onPadDown() {
        long now = System.currentTimeMillis();
        long gap = mTimings.isEmpty() ? 0 : Math.min(MAX_GAP_MS, now - mLastUp);
        mTimings.add(gap);
        mAmplitudes.add(0);
        mDownAt = now;
        mVibrator.vibrate(VibrationEffect.createOneShot(MIN_ON_MS, amplitude()));
    }

    private void onPadUp() {
        long now = System.currentTimeMillis();
        long on = Math.max(MIN_ON_MS, now - mDownAt);
        mTimings.add(on);
        mAmplitudes.add(amplitude());
        mLastUp = now;
        if (total() > MAX_TOTAL_MS) {
            mTimings.remove(mTimings.size() - 1);
            mTimings.remove(mTimings.size() - 1);
            mAmplitudes.remove(mAmplitudes.size() - 1);
            mAmplitudes.remove(mAmplitudes.size() - 1);
            Toast.makeText(this, R.string.haptic_remix_too_long, Toast.LENGTH_SHORT).show();
        }
        mStrip.invalidate();
        updateLength();
    }

    private long total() {
        long total = 0;
        for (long t : mTimings) total += t;
        return total;
    }

    private void updateLength() {
        mLength.setText(mTimings.isEmpty() ? getString(R.string.haptic_remix_empty)
                : getString(R.string.haptic_remix_seconds, total() / 1000f));
    }

    private void clear() {
        mTimings.clear();
        mAmplitudes.clear();
        mStrip.invalidate();
        updateLength();
    }

    private VibrationEffect current(boolean repeat) {
        if (mTimings.isEmpty()) return null;
        long[] timings = new long[mTimings.size()];
        int[] amplitudes = new int[mAmplitudes.size()];
        for (int i = 0; i < timings.length; i++) {
            timings[i] = mTimings.get(i);
            amplitudes[i] = mAmplitudes.get(i);
        }
        return VibrationEffect.createWaveform(timings, amplitudes, repeat ? 0 : -1);
    }

    private void play(VibrationEffect effect) {
        if (effect == null) return;
        mVibrator.cancel();
        mVibrator.vibrate(effect);
    }

    private void setPattern(long[] timings, int[] amplitudes) {
        mTimings.clear();
        mAmplitudes.clear();
        for (int i = 0; i < timings.length; i++) {
            mTimings.add(timings[i]);
            mAmplitudes.add(amplitudes[i]);
        }
        mStrip.invalidate();
        updateLength();
        play(current(false));
    }

    private void preset(ChipGroup group, int title, long[] timings, int[] amplitudes) {
        Chip chip = new Chip(mThemed);
        chip.setText(title);
        chip.setOnClickListener(v -> setPattern(timings, amplitudes));
        group.addView(chip);
    }

    private void loadPattern(JSONObject pattern) {
        try {
            JSONArray t = pattern.getJSONArray("timings");
            JSONArray a = pattern.getJSONArray("amplitudes");
            long[] timings = new long[t.length()];
            int[] amplitudes = new int[t.length()];
            for (int i = 0; i < t.length(); i++) {
                timings[i] = t.getLong(i);
                amplitudes[i] = a.getInt(i);
            }
            setPattern(timings, amplitudes);
        } catch (Exception e) {
            Toast.makeText(this, R.string.haptic_remix_broken, Toast.LENGTH_SHORT).show();
        }
    }

    private float seconds(JSONObject pattern) {
        long total = 0;
        JSONArray t = pattern.optJSONArray("timings");
        for (int i = 0; t != null && i < t.length(); i++) total += t.optLong(i);
        return total / 1000f;
    }

    private void save() {
        if (mTimings.isEmpty()) {
            Toast.makeText(this, R.string.haptic_remix_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        EditText name = new EditText(mThemed);
        name.setHint(R.string.haptic_remix_name);
        FrameLayout box = new FrameLayout(mThemed);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(name);
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(R.string.haptic_remix_save)
                .setView(box)
                .setPositiveButton(R.string.haptic_remix_save, (d, w) -> {
                    String text = name.getText().toString().trim();
                    if (text.isEmpty()) return;
                    try {
                        for (int i = mPatterns.length() - 1; i >= 0; i--) {
                            if (text.equals(mPatterns.optJSONObject(i).optString("name"))) {
                                mPatterns.remove(i);
                            }
                        }
                        JSONArray t = new JSONArray();
                        JSONArray a = new JSONArray();
                        for (int i = 0; i < mTimings.size(); i++) {
                            t.put(mTimings.get(i));
                            a.put(mAmplitudes.get(i));
                        }
                        mPatterns.put(new JSONObject().put("name", text).put("timings", t)
                                .put("amplitudes", a));
                        store();
                        build();
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.haptic_remix_broken, Toast.LENGTH_SHORT)
                                .show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deletePattern(int index) {
        JSONObject pattern = mPatterns.optJSONObject(index);
        String name = pattern.optString("name");
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(getString(R.string.haptic_remix_delete, name))
                .setPositiveButton(R.string.haptic_remix_delete_button, (d, w) -> {
                    mPatterns.remove(index);
                    // Targets that used it go back to Default.
                    List<String> gone = new ArrayList<>();
                    for (Iterator<String> keys = mAssign.keys(); keys.hasNext();) {
                        String key = keys.next();
                        if (name.equals(mAssign.optString(key))) gone.add(key);
                    }
                    for (String key : gone) mAssign.remove(key);
                    JSONArray contacts = mAssign.optJSONArray("contacts");
                    for (int i = contacts == null ? -1 : contacts.length() - 1; i >= 0; i--) {
                        if (name.equals(contacts.optJSONObject(i).optString("pattern"))) {
                            contacts.remove(i);
                        }
                    }
                    store();
                    build();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Beat of the ringtone

    private void beatOfTheRingtone() {
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
        if (uri == null) {
            Toast.makeText(this, R.string.haptic_remix_no_ringtone, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.haptic_remix_listening, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            long[][] beat;
            try {
                beat = beat(HapticRemixActivity.this, uri);
            } catch (Exception e) {
                beat = null;
            }
            final long[][] result = beat;
            mHandler.post(() -> {
                if (isFinishing()) return;
                if (result == null) {
                    Toast.makeText(this, R.string.haptic_remix_no_ringtone, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                int[] amplitudes = new int[result[1].length];
                for (int i = 0; i < amplitudes.length; i++) amplitudes[i] = (int) result[1][i];
                setPattern(result[0], amplitudes);
            });
        }, "HapticRemixBeat").start();
    }

    /**
     * Decodes up to 20 seconds of the ringtone and turns its loudness into on and off times:
     * timings in the first row, amplitudes in the second.
     */
    private static long[][] beat(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, uri, null);
        MediaFormat format = null;
        String mime = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String m = f.getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("audio/")) {
                extractor.selectTrack(i);
                format = f;
                mime = m;
                break;
            }
        }
        if (format == null) throw new IllegalStateException("no audio");
        int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int frame = Math.max(1, rate * channels * 40 / 1000);
        List<Float> levels = new ArrayList<>();
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        double acc = 0;
        int n = 0;
        while (levels.size() < 500) {
            if (!inputDone) {
                int in = codec.dequeueInputBuffer(10_000);
                if (in >= 0) {
                    ByteBuffer buffer = codec.getInputBuffer(in);
                    int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);
                    if (size < 0) {
                        codec.queueInputBuffer(in, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }
            int out = codec.dequeueOutputBuffer(info, 10_000);
            if (out >= 0) {
                ByteBuffer buffer = codec.getOutputBuffer(out);
                if (buffer != null) {
                    buffer.order(ByteOrder.nativeOrder());
                    for (int i = info.offset; i + 1 < info.offset + info.size; i += 2) {
                        short sample = buffer.getShort(i);
                        acc += (double) sample * sample;
                        if (++n >= frame) {
                            levels.add((float) Math.sqrt(acc / n));
                            acc = 0;
                            n = 0;
                        }
                    }
                }
                codec.releaseOutputBuffer(out, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            } else if (out == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                break;
            }
        }
        codec.stop();
        codec.release();
        extractor.release();
        float max = 0;
        for (float level : levels) max = Math.max(max, level);
        if (max <= 0) throw new IllegalStateException("silent");
        // Frames above a third of the peak buzz, at a strength that follows the loudness.
        List<Long> timings = new ArrayList<>();
        List<Long> amplitudes = new ArrayList<>();
        boolean on = false;
        long run = 0;
        double sum = 0;
        int count = 0;
        timings.add(0L);
        amplitudes.add(0L);
        for (float level : levels) {
            boolean loud = level > 0.33f * max;
            if (loud == on) {
                run += 40;
                sum += level;
                count++;
                continue;
            }
            if (run > 0) {
                timings.add(run);
                amplitudes.add(on ? Math.round(60 + 195 * (sum / count) / max) : 0L);
            }
            on = loud;
            run = 40;
            sum = level;
            count = 1;
        }
        if (run > 0) {
            timings.add(run);
            amplitudes.add(on ? Math.round(60 + 195 * (sum / count) / max) : 0L);
        }
        long[][] result = new long[2][timings.size()];
        for (int i = 0; i < timings.size(); i++) {
            result[0][i] = timings.get(i);
            result[1][i] = amplitudes.get(i);
        }
        return result;
    }

    // Widgets

    private void section(int title) {
        TextView text = new TextView(mThemed);
        text.setText(title);
        text.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        text.setPadding(dp(4), dp(24), dp(4), dp(8));
        mList.addView(text);
    }

    private TextView label(int title) {
        TextView text = new TextView(mThemed);
        text.setText(title);
        text.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
        text.setPadding(dp(4), dp(12), dp(4), 0);
        return text;
    }

    private TextView hint(int text) {
        TextView view = new TextView(mThemed);
        view.setText(text);
        view.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        view.setPadding(dp(4), dp(4), dp(4), dp(8));
        return view;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(mThemed);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        return row;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(dp(6));
        return params;
    }

    private MaterialButton button(int title, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(mThemed, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(title);
        button.setOnClickListener(listener);
        return button;
    }

    private View item(CharSequence title, CharSequence summary, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(mThemed);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(14), dp(12), dp(14));
        row.setClickable(true);
        row.setFocusable(true);
        android.util.TypedValue out = new android.util.TypedValue();
        mThemed.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        row.setBackgroundResource(out.resourceId);
        TextView text = new TextView(mThemed);
        text.setText(title);
        text.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        row.addView(text);
        if (summary != null) {
            TextView sub = new TextView(mThemed);
            sub.setText(summary);
            sub.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            sub.setTextColor(MaterialColors.getColor(mThemed,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
            row.addView(sub);
        }
        row.setOnClickListener(listener);
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** The round pad: a neon ring that swells while a finger is on it. */
    private class PadView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int mAccent = MaterialColors.getColor(mThemed,
                androidx.appcompat.R.attr.colorPrimary, Color.RED);
        private final String mLabel = getString(R.string.haptic_remix_pad);
        private boolean mPressed;

        PadView(Context context) {
            super(context);
            setContentDescription(mLabel);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mPressed = true;
                    onPadDown();
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mPressed) {
                        mPressed = false;
                        onPadUp();
                        invalidate();
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - dp(6);
            float ring = mPressed ? r : r - dp(4);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor((mAccent & 0x00FFFFFF) | (mPressed ? 0x40000000 : 0x18000000));
            canvas.drawCircle(cx, cy, ring, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(dp(2));
            mPaint.setColor(mAccent);
            mPaint.setShadowLayer(dp(mPressed ? 14 : 8), 0, 0, mAccent);
            canvas.drawCircle(cx, cy, ring, mPaint);
            mPaint.clearShadowLayer();
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(MaterialColors.getColor(mThemed,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
            mPaint.setTextAlign(Paint.Align.CENTER);
            mPaint.setTextSize(dp(13));
            canvas.drawText(mLabel, cx, cy + dp(5), mPaint);
        }
    }

    /** The pattern as bars: height is strength, width is time, gaps are the pauses. */
    private class StripView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int mAccent = MaterialColors.getColor(mThemed,
                androidx.appcompat.R.attr.colorPrimary, Color.RED);

        StripView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            mPaint.setColor((mAccent & 0x00FFFFFF) | 0x14000000);
            canvas.drawRoundRect(0, 0, w, h, dp(10), dp(10), mPaint);
            long total = Math.max(total(), 1000);
            float x = dp(6);
            float usable = w - dp(12);
            for (int i = 0; i < mTimings.size(); i++) {
                float width = usable * mTimings.get(i) / total;
                int amplitude = mAmplitudes.get(i);
                if (amplitude > 0) {
                    float height = (h - dp(8)) * amplitude / 255f;
                    mPaint.setColor(mAccent);
                    canvas.drawRoundRect(x, h / 2f - height / 2f, Math.max(x + dp(3), x + width),
                            h / 2f + height / 2f, dp(2), dp(2), mPaint);
                }
                x += width;
            }
        }
    }
}
