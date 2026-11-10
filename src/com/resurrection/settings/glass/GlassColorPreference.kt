/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.android.internal.util.catalyxx.GlassSettings

/**
 * Glass color. Until one is picked here the material picks it: Frosted keeps Android's own tint,
 * as Android's blur looks, and Liquid is frosted white. So the row shows what glass uses now and
 * writes only what's picked.
 */
class GlassColorPreference(context: Context, attrs: AttributeSet?) :
    ListPreference(context, attrs) {
    init {
        isPersistent = false
    }

    override fun onAttached() {
        super.onAttached()
        refresh()
    }

    /** Shows the color glass uses now, which the material can change. */
    fun refresh() {
        value = GlassSettings.color(context.contentResolver).toString()
    }

    override fun callChangeListener(newValue: Any?): Boolean {
        val color = (newValue as? String)?.toIntOrNull() ?: return false
        if (!super.callChangeListener(newValue)) return false
        Settings.System.putIntForUser(context.contentResolver, key, color, UserHandle.USER_CURRENT)
        return true
    }
}
