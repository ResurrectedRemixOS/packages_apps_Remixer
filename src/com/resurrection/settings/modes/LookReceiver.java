/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.modes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;

import com.resurrection.settings.Looks;

import org.json.JSONObject;

/** SystemUI asks for a Look here when a Mode with one turns on or off. */
public class LookReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.resurrection.settings.APPLY_LOOK";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_WALLPAPER = "wallpaper";
    /** The Look a Mode put on last, so SystemUI only asks when it changes. */
    public static final String KEY_APPLIED = "rr_mode_look_applied";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        String name = intent.getStringExtra(EXTRA_NAME);
        JSONObject look = Looks.find(context, name);
        if (look == null) return;
        PendingResult result = goAsync();
        new Thread(() -> {
            Looks.apply(context, look, Looks.parts(intent.getBooleanExtra(EXTRA_WALLPAPER, true)));
            Settings.System.putStringForUser(context.getContentResolver(), KEY_APPLIED, name,
                    UserHandle.USER_CURRENT);
            result.finish();
        }, "RemixModes").start();
    }
}
