/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.RadioButton
import android.widget.TextView
import androidx.preference.Preference
import com.android.internal.util.catalyxx.GlassSettings
import com.android.settings.R
import com.android.settingslib.widget.LayoutPreference

/**
 * Liquid Glass: the material, how all glass looks, a page each for touch and motion and for every
 * place glass shows, and Battery Saver. Catalyxx's glass engine draws it: SurfaceFlinger the glass
 * behind windows, SystemUI the rims on what it draws itself.
 */
class LiquidGlassSettings :
    GlassPageFragment(R.xml.remixer_liquid_glass, GlassPreviewKind.ALL_GLASS) {
    override val alwaysOn =
        setOf(KEY_NEEDS_BLUR, KEY_HEADER, KEY_MORE, KEY_SURFACES, KEY_FOOTER)

    private val cards = arrayOfNulls<View>(CARDS.size)
    private var material = GlassSettings.DEFAULT_MATERIAL

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        val header = findPreference<LayoutPreference>(KEY_HEADER) ?: return
        for (i in CARDS.indices) {
            val card = header.findViewById<View>(CARDS[i])
            card.findViewById<GlassPreviewView>(R.id.liquid_glass_swatch).setSwatch(i)
            card.findViewById<TextView>(R.id.liquid_glass_label).setText(NAMES[i])
            card.findViewById<TextView>(R.id.liquid_glass_desc).setText(SUMMARIES[i])
            card.setOnClickListener { select(i) }
            card.accessibilityDelegate =
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
            cards[i] = card
        }
        findPreference<Preference>(KEY_NEEDS_BLUR)?.setOnPreferenceClickListener {
            Settings.Global.putInt(
                requireContext().contentResolver,
                Settings.Global.DISABLE_WINDOW_BLURS,
                0,
            )
            refreshRows()
            true
        }
    }

    private fun select(choice: Int) {
        if (choice == material) return
        Settings.System.putIntForUser(
            requireContext().contentResolver,
            GlassSettings.MATERIAL,
            choice,
            UserHandle.USER_CURRENT,
        )
        refreshRows()
    }

    override fun refreshRows() {
        val context = context ?: return
        material =
            GlassSettings.getInt(
                context.contentResolver,
                GlassSettings.MATERIAL,
                GlassSettings.DEFAULT_MATERIAL,
            )
        super.refreshRows()
        findPreference<Preference>(KEY_NEEDS_BLUR)?.isVisible = !GlassPreview.isBlurOn(context)
        val density = resources.displayMetrics.density
        val primary = context.getColor(com.android.internal.R.color.materialColorPrimary)
        val selectedFill =
            context.getColor(com.android.internal.R.color.materialColorSecondaryContainer)
        val fill = context.getColor(com.android.internal.R.color.materialColorSurfaceContainerHigh)
        val ripple =
            context.getColor(com.android.internal.R.color.materialColorOnSurface) and 0x1FFFFFFF
        for (i in cards.indices) {
            val card = cards[i] ?: continue
            val selected = i == material
            val shape =
                GradientDrawable().apply {
                    cornerRadius = 22 * density
                    setColor(if (selected) selectedFill else fill)
                    setStroke(Math.round(2 * density), if (selected) primary else Color.TRANSPARENT)
                }
            card.background = RippleDrawable(ColorStateList.valueOf(ripple), shape, null)
            card.isSelected = selected
        }
    }

    private companion object {
        const val KEY_NEEDS_BLUR = "glass_needs_blur"
        const val KEY_HEADER = "liquid_glass_header"
        const val KEY_MORE = "liquid_glass_more"
        const val KEY_SURFACES = "liquid_glass_surfaces"
        const val KEY_FOOTER = "liquid_glass_footer"

        // In GlassSettings' MATERIAL_ order
        val CARDS =
            intArrayOf(R.id.liquid_glass_solid, R.id.liquid_glass_frosted, R.id.liquid_glass_liquid)
        val NAMES =
            intArrayOf(
                R.string.liquid_glass_solid,
                R.string.liquid_glass_frosted,
                R.string.liquid_glass_liquid,
            )
        val SUMMARIES =
            intArrayOf(
                R.string.liquid_glass_solid_summary,
                R.string.liquid_glass_frosted_summary,
                R.string.liquid_glass_liquid_summary,
            )
    }
}
