/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.gestures;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.gesture.Gesture;
import android.gesture.GestureOverlayView;
import android.gesture.GesturePoint;
import android.gesture.GestureStroke;
import android.graphics.Color;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.android.settings.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


/** Draw a new gesture, pick what it opens, and it's saved for SystemUI. */
public class GestureRecordActivity extends Activity {
    private static final int SAMPLES = 3;
    private final List<Gesture> mSamples = new ArrayList<>();
    private TextView mHint;
    private static final String[] ACTIONS = {"camera", "music", "flashlight", "screenshot", "dnd"};
    private static final int[] LABELS = {R.string.gesture_action_camera,
            R.string.gesture_action_music, R.string.gesture_action_flashlight,
            R.string.gesture_action_screenshot, R.string.gesture_action_dnd};

    static CharSequence label(Context context, String action) {
        for (int i = 0; i < ACTIONS.length; i++) {
            if (ACTIONS[i].equals(action)) return context.getString(LABELS[i]);
        }
        if (action != null && action.startsWith("app:")) {
            ComponentName component = ComponentName.unflattenFromString(action.substring(4));
            try {
                if (component != null) {
                    return context.getPackageManager().getActivityInfo(component, 0)
                            .loadLabel(context.getPackageManager());
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Uninstalled.
            }
        }
        return action;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0405);
        TextView hint = new TextView(this);
        mHint = hint;
        hint.setText(R.string.gesture_anywhere_record_hint);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(17);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        ((FrameLayout.LayoutParams) hint.getLayoutParams()).topMargin =
                Math.round(120 * getResources().getDisplayMetrics().density);
        GestureOverlayView canvas = new GestureOverlayView(this);
        int accent = getColor(android.R.color.system_accent1_400);
        canvas.setGestureColor(accent);
        canvas.setUncertainGestureColor(accent);
        canvas.setGestureStrokeWidth(10 * getResources().getDisplayMetrics().density);
        canvas.setGestureStrokeType(GestureOverlayView.GESTURE_STROKE_TYPE_MULTIPLE);
        canvas.setFadeOffset(700);
        canvas.addOnGesturePerformedListener((overlay, gesture) -> {
            mSamples.add(gesture);
            if (mSamples.size() < SAMPLES) {
                mHint.setText(getString(R.string.gesture_anywhere_record_again,
                        mSamples.size() + 1, SAMPLES));
            } else {
                chooseAction(gesture);
            }
        });
        root.addView(canvas, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private void chooseAction(Gesture gesture) {
        pickAction(this, action -> save(gesture, action));
    }

    /** A tool, or "App..." and then any app; the action string goes to [onPicked]. */
    static void pickAction(Context context, java.util.function.Consumer<String> onPicked) {
        CharSequence[] items = new CharSequence[ACTIONS.length + 1];
        for (int i = 0; i < ACTIONS.length; i++) items[i] = context.getString(LABELS[i]);
        items[ACTIONS.length] = context.getString(R.string.gesture_action_app);
        new AlertDialog.Builder(context)
                .setTitle(R.string.gesture_anywhere_record_action)
                .setItems(items, (d, which) -> {
                    if (which < ACTIONS.length) onPicked.accept(ACTIONS[which]);
                    else chooseApp(context, onPicked);
                })
                .show();
    }

    private static void chooseApp(Context context, java.util.function.Consumer<String> onPicked) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        apps.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        CharSequence[] labels = new CharSequence[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).loadLabel(pm);
        new AlertDialog.Builder(context)
                .setTitle(R.string.gesture_action_app)
                .setItems(labels, (d, which) -> {
                    ActivityInfo info = apps.get(which).activityInfo;
                    onPicked.accept("app:" + new ComponentName(info.packageName, info.name)
                            .flattenToString());
                })
                .show();
    }

    private static JSONArray strokes(Gesture gesture) throws org.json.JSONException {
        JSONArray strokes = new JSONArray();
        for (GestureStroke stroke : gesture.getStrokes()) {
            JSONArray flat = new JSONArray();
            float[] points = stroke.points;
            long t = 0;
            for (int i = 0; i + 1 < points.length; i += 2) {
                flat.put(points[i]).put(points[i + 1]).put(t);
                t += 16;
            }
            strokes.put(flat);
        }
        return strokes;
    }

    private void save(Gesture gesture, String action) {
        try {
            JSONArray samples = new JSONArray();
            for (Gesture sample : mSamples) samples.put(strokes(sample));
            JSONArray strokes = strokes(gesture);
            String json = Settings.System.getStringForUser(getContentResolver(),
                    GestureAnywhereSettings.KEY_CUSTOM, UserHandle.USER_CURRENT);
            JSONArray all = new JSONArray(json != null ? json : "[]");
            all.put(new JSONObject().put("action", action).put("strokes", strokes)
                    .put("samples", samples));
            Settings.System.putStringForUser(getContentResolver(),
                    GestureAnywhereSettings.KEY_CUSTOM, all.toString(), UserHandle.USER_CURRENT);
        } catch (Exception e) {
            // Nothing saved.
        }
        finish();
    }
}
