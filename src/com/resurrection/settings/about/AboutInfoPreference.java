/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.about;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.android.settings.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A read-only About phone row whose value comes from the build. Hidden when empty. */
public class AboutInfoPreference extends Preference {

    private static final String KEY_SECURITY_PATCH = "rr_security_patch";
    private static final String KEY_BUILD_DATE = "rr_build_date";
    private static final String KEY_MAINTAINER = "rr_maintainer";

    public AboutInfoPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.rr_about_value_row);
        setSelectable(false);
        setCopyingEnabled(true);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        CharSequence value = value();
        setSummary(value);
        setVisible(!TextUtils.isEmpty(value));
    }

    private CharSequence value() {
        switch (String.valueOf(getKey())) {
            case KEY_SECURITY_PATCH:
                try {
                    Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .parse(Build.VERSION.SECURITY_PATCH);
                    return date == null ? Build.VERSION.SECURITY_PATCH : format(date.getTime());
                } catch (ParseException e) {
                    return Build.VERSION.SECURITY_PATCH;
                }
            case KEY_BUILD_DATE:
                long utc = SystemProperties.getLong("ro.build.date.utc", 0);
                return utc > 0 ? format(utc * 1000) : null;
            case KEY_MAINTAINER:
                return SystemProperties.get("ro.rr.maintainer");
            default:
                return null;
        }
    }

    private static CharSequence format(long millis) {
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy");
        return DateFormat.format(pattern, millis);
    }
}
