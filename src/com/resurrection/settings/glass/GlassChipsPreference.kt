/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.RadioButton
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.internal.R as InternalR
import com.android.settings.R
import kotlin.math.roundToInt

/**
 * A choice kept as an int in Settings.System, picked from chips in its row: nothing opens over
 * the page, so the stage keeps showing what the choice does. A change listener hears a pick
 * before it's written, as a list's does.
 */
open class GlassChipsPreference(context: Context, attrs: AttributeSet) :
    Preference(context, attrs) {

    private val entries: Array<CharSequence> =
        context.resources.getTextArray(attrs.getAttributeResourceValue(ANDROID, "entries", 0))
    private val values: IntArray =
        context.resources
            .getStringArray(attrs.getAttributeResourceValue(ANDROID, "entryValues", 0))
            .map { it.toInt() }
            .toIntArray()
    private val default = attrs.getAttributeIntValue(ANDROID, "defaultValue", 0)

    init {
        layoutResource = R.layout.remixer_now_bar_chips
        isSelectable = false
        isPersistent = false
    }

    /** The value whose chip shows as picked. */
    protected open fun value(): Int =
        Settings.System.getIntForUser(context.contentResolver, key, default, UserHandle.USER_CURRENT)

    private fun pick(value: Int) {
        if (value == value() || !callChangeListener(value)) return
        Settings.System.putIntForUser(context.contentResolver, key, value, UserHandle.USER_CURRENT)
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val chips = holder.findViewById(R.id.now_bar_chips) as ViewGroup
        chips.removeAllViews()
        val picked = value()
        for (i in entries.indices) {
            chips.addView(chip(entries[i], values[i], values[i] == picked))
        }
    }

    private fun chip(text: CharSequence, value: Int, selected: Boolean): View {
        val density = context.resources.displayMetrics.density
        val shape =
            GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(
                    if (selected) context.getColor(InternalR.color.materialColorSecondaryContainer)
                    else Color.TRANSPARENT
                )
                setStroke(
                    density.roundToInt(),
                    context.getColor(
                        if (selected) InternalR.color.materialColorPrimary
                        else InternalR.color.materialColorOutlineVariant
                    ),
                )
            }
        val ripple = context.getColor(InternalR.color.materialColorOnSurface) and 0x1FFFFFFF
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            minHeight = (44 * density).roundToInt()
            setPadding((14 * density).roundToInt(), 0, (14 * density).roundToInt(), 0)
            textSize = 14f
            setTextColor(
                context.getColor(
                    if (selected) InternalR.color.materialColorOnSecondaryContainer
                    else InternalR.color.materialColorOnSurface
                )
            )
            background = RippleDrawable(ColorStateList.valueOf(ripple), shape, null)
            isEnabled = this@GlassChipsPreference.isEnabled
            alpha = if (isEnabled) 1f else 0.5f
            isSelected = selected
            setOnClickListener { pick(value) }
            accessibilityDelegate =
                object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfo,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = RadioButton::class.java.name
                        info.isCheckable = true
                        info.isChecked = host.isSelected
                    }
                }
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
