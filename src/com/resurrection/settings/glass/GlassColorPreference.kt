/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.content.Context
import android.util.AttributeSet
import com.android.internal.util.catalyxx.GlassSettings

/**
 * Glass color. Until one is picked here the material picks it: Frosted keeps Android's own tint,
 * as Android's blur looks, and Liquid is frosted white. So the row shows what glass uses now and
 * writes only what's picked.
 */
class GlassColorPreference(context: Context, attrs: AttributeSet) :
    GlassChipsPreference(context, attrs) {

    override fun value(): Int = GlassSettings.color(context.contentResolver)

    /** Shows the color glass uses now, which the material can change. */
    fun refresh() = notifyChanged()
}
