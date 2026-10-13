/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.chargereport;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

import org.json.JSONArray;

/** A charge report row with its curve under the numbers. */
public class ChargeReportPreference extends Preference {
    private JSONArray mCurve;

    public ChargeReportPreference(Context context) {
        super(context);
        setLayoutResource(R.layout.remixer_charge_report_row);
        setSelectable(false);
    }

    public void setCurve(JSONArray curve) {
        mCurve = curve;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        ChargeCurveView curve = (ChargeCurveView) holder.findViewById(R.id.charge_curve);
        curve.setCurve(mCurve);
        curve.setVisibility(mCurve != null && mCurve.length() > 1 ? android.view.View.VISIBLE
                : android.view.View.GONE);
    }
}
