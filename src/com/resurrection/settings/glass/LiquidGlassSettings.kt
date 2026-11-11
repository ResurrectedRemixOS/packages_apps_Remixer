/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.content.Context
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import com.android.internal.R as InternalR
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.internal.util.catalyxx.GlassSettings
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.widget.LayoutPreference
import kotlin.math.roundToInt

/**
 * Liquid Glass, on one screen. A stage of real glass stays pinned at the top ([GlassPreview]) with
 * the tabs under it: Look, Edges, Touch, Motion and Places. Each tab shows its rows and puts its
 * own scene on the stage, and a row that only shows over light content or over lines puts that
 * behind the glass when it's touched, so every row shows what it does. Catalyxx's glass engine
 * draws the glass: SurfaceFlinger what's behind windows, SystemUI the rims on what it draws itself.
 *
 * Rows wait while glass can't show what they set: all of them while the material is Solid or
 * Android's blur is off, those that only glass with a curved rim has while it's Frosted, and the
 * panels' morph switches while Morph is off.
 */
class LiquidGlassSettings : SettingsPreferenceFragment() {

    private var preview: GlassPreview? = null
    private var tab = TAB_LOOK
    private val tabViews = ArrayList<TextView>()
    private val materialCards = ArrayList<View>()
    private val styleCards = ArrayList<View>()

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshRows()
            }
        }

    override fun getMetricsCategory() = MetricsEvent.VIEW_UNKNOWN

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        tab = (icicle?.getInt(STATE_TAB) ?: TAB_LOOK).coerceIn(TAB_LOOK, TAB_PLACES)
        addPreferencesFromResource(R.xml.remixer_liquid_glass)

        setUpCards(KEY_MATERIAL, MATERIAL_CARDS, MATERIAL_NAMES, MATERIAL_SUMMARIES, materialCards) {
            write(GlassSettings.MATERIAL, it)
        }
        setUpCards(KEY_STYLE, STYLE_CARDS, STYLE_NAMES, STYLE_SUMMARIES, styleCards) {
            write(GlassSettings.EDGE_STYLE, it)
        }
        for (i in styleCards.indices) {
            // Large enough to show the edge, which is all that differs
            styleCards[i]
                .findViewById<GlassPreviewView>(R.id.liquid_glass_swatch)
                .setSwatch(GlassSettings.MATERIAL_LIQUID, i == GlassSettings.EDGE_RR, true)
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
        findPreference<Preference>(KEY_BANNER)?.setOnPreferenceClickListener {
            write(GlassSettings.MATERIAL, GlassSettings.MATERIAL_LIQUID)
            true
        }
        // A row that only shows over some backdrop brings it onto the stage
        for ((key, backdrop) in STAGE_BACKDROPS) {
            findPreference<Preference>(key)?.setOnPreferenceChangeListener { _, _ ->
                preview?.backdrop = backdrop
                true
            }
        }
    }

    /** Sets up the cards of the row at [key], a card for each value from 0 up. */
    private fun setUpCards(
        key: String,
        ids: IntArray,
        names: IntArray,
        summaries: IntArray,
        cards: MutableList<View>,
        pick: (Int) -> Unit,
    ) {
        val row = findPreference<LayoutPreference>(key) ?: return
        val context = requireContext()
        val density = resources.displayMetrics.density
        val fill =
            ColorStateList(
                SELECTED_OR_NOT,
                intArrayOf(
                    context.getColor(InternalR.color.materialColorSecondaryContainer),
                    context.getColor(InternalR.color.materialColorSurfaceContainerHigh),
                ),
            )
        val stroke =
            ColorStateList(
                SELECTED_OR_NOT,
                intArrayOf(context.getColor(InternalR.color.materialColorPrimary), Color.TRANSPARENT),
            )
        for (i in ids.indices) {
            val card = row.findViewById<View>(ids[i])
            card.findViewById<GlassPreviewView>(R.id.liquid_glass_swatch).setSwatch(i)
            card.findViewById<TextView>(R.id.liquid_glass_label).setText(names[i])
            card.findViewById<TextView>(R.id.liquid_glass_desc).setText(summaries[i])
            card.background = pickable(context, 22 * density, fill, (2 * density).roundToInt(), stroke)
            card.setOnClickListener { pick(i) }
            card.accessibilityDelegate = GlassPreview.RADIO
            cards.add(card)
        }
    }

    /**
     * A background that shows whether its view is selected, with a ripple. It's made once and
     * left on its view: Android ends a pressed ripple that's swapped out but leaves its sparkle
     * running, which kept the page drawing for seven seconds after every pick.
     */
    private fun pickable(
        context: Context,
        radius: Float,
        fill: ColorStateList,
        strokeWidth: Int,
        stroke: ColorStateList,
    ): RippleDrawable {
        val shape =
            GradientDrawable().apply {
                cornerRadius = radius
                setColor(fill)
                setStroke(strokeWidth, stroke)
            }
        val ripple = context.getColor(InternalR.color.materialColorOnSurface) and 0x1FFFFFFF
        return RippleDrawable(ColorStateList.valueOf(ripple), shape, null)
    }

    private fun write(key: String, value: Int) {
        Settings.System.putIntForUser(
            requireContext().contentResolver,
            key,
            value,
            UserHandle.USER_CURRENT,
        )
        refreshRows()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val stage = GlassPreview(this)
        preview = stage
        setPinnedHeaderView(stage.createView(layoutInflater, view as ViewGroup))
        // Scrolling moves the rows alone: pulled down, the toolbar would take the stage with it
        listView.isNestedScrollingEnabled = false
        // Settings themes turn clipChildren off, which lets rows draw over the stage
        view.findViewById<ViewGroup>(android.R.id.list_container)?.clipChildren = true
        addTabs(stage.tabs)
        showTab(tab)
    }

    override fun onDestroyView() {
        preview?.onDestroyView()
        preview = null
        tabViews.clear()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, tab)
    }

    override fun onResume() {
        super.onResume()
        val resolver = requireContext().contentResolver
        GlassSettings.observe(resolver, observer, *WATCHED)
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.DISABLE_WINDOW_BLURS),
            false,
            observer,
        )
        refreshRows()
        preview?.onResume()
    }

    override fun onPause() {
        preview?.onPause()
        requireContext().contentResolver.unregisterContentObserver(observer)
        super.onPause()
    }

    private fun addTabs(row: ViewGroup) {
        val context = row.context
        val density = resources.displayMetrics.density
        val fill =
            ColorStateList(
                SELECTED_OR_NOT,
                intArrayOf(
                    context.getColor(InternalR.color.materialColorSecondaryContainer),
                    Color.TRANSPARENT,
                ),
            )
        val stroke =
            ColorStateList(
                SELECTED_OR_NOT,
                intArrayOf(
                    context.getColor(InternalR.color.materialColorPrimary),
                    context.getColor(InternalR.color.materialColorOutlineVariant),
                ),
            )
        val text =
            ColorStateList(
                SELECTED_OR_NOT,
                intArrayOf(
                    context.getColor(InternalR.color.materialColorOnSecondaryContainer),
                    context.getColor(InternalR.color.materialColorOnSurface),
                ),
            )
        for (i in TAB_NAMES.indices) {
            val chip =
                TextView(context).apply {
                    setText(TAB_NAMES[i])
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    textSize = 14f
                    setTextColor(text)
                    background = pickable(context, 22 * density, fill, density.roundToInt(), stroke)
                    setOnClickListener { showTab(i) }
                    accessibilityDelegate = GlassPreview.RADIO
                }
            val params = LinearLayout.LayoutParams(0, (44 * density).roundToInt(), 1f)
            if (i > 0) {
                params.marginStart = (6 * density).roundToInt()
            }
            row.addView(chip, params)
            tabViews.add(chip)
        }
    }

    /** Shows the rows of the tab at [index], and its scene on the stage. */
    private fun showTab(index: Int) {
        if (context == null) return
        val entering = index != tab
        tab = index
        val screen = preferenceScreen
        for (i in 0 until screen.preferenceCount) {
            val row = screen.getPreference(i)
            val key = row.key ?: continue
            if (key.startsWith(TAB_PREFIX)) {
                row.isVisible = key.startsWith(TAB_KEYS[index])
            }
        }
        preview?.let {
            it.scene = SCENES[index]
            if (entering) {
                // The edge shows best over lines; the other tabs start on the wallpaper
                it.backdrop =
                    if (index == TAB_EDGES) GlassPreviewBackdrop.BACKGROUND_LINES
                    else GlassPreviewBackdrop.BACKGROUND_WALLPAPER
            }
        }
        for (i in tabViews.indices) {
            tabViews[i].isSelected = i == index
        }
        refreshRows()
        if (entering) {
            listView?.scrollToPosition(0)
        }
    }

    private fun refreshRows() {
        val context = context ?: return
        val resolver = context.contentResolver
        val material =
            GlassSettings.getInt(resolver, GlassSettings.MATERIAL, GlassSettings.DEFAULT_MATERIAL)
        val edge =
            GlassSettings.getInt(
                resolver,
                GlassSettings.EDGE_STYLE,
                GlassSettings.DEFAULT_EDGE_STYLE,
            )
        val lens = edge != GlassSettings.EDGE_CATALYXX
        val blurOn = GlassPreview.isBlurOn(context)
        val isOn = blurOn && GlassSettings.isEnabled(material)
        val hasRim = isOn && material == GlassSettings.MATERIAL_LIQUID
        val morphs = isOn && GlassSettings.getInt(resolver, GlassSettings.MORPH, 1) == 1

        val screen = preferenceScreen
        for (i in 0 until screen.preferenceCount) {
            val row = screen.getPreference(i)
            row.isEnabled = isOn || row.key in ALWAYS_ON
        }
        for (key in RIM_KEYS) {
            findPreference<Preference>(key)?.isEnabled = hasRim
        }
        for (key in MORPH_KEYS) {
            findPreference<Preference>(key)?.isEnabled = morphs
        }
        // Android's own color comes with its own tint
        findPreference<Preference>(GlassSettings.TINT)?.isEnabled =
            isOn && GlassSettings.color(resolver) != GlassSettings.COLOR_ANDROID
        findPreference<GlassColorPreference>(GlassSettings.COLOR)?.refresh()

        findPreference<Preference>(KEY_NEEDS_BLUR)?.isVisible = !blurOn
        findPreference<Preference>(KEY_BANNER)?.let {
            val solid = material == GlassSettings.MATERIAL_SOLID
            // Edges are all about the rim, which Frosted hasn't got
            val flat = material == GlassSettings.MATERIAL_FROSTED && tab == TAB_EDGES
            it.isVisible = blurOn && (solid || flat)
            it.setTitle(
                if (solid) R.string.glass_banner_solid_title else R.string.glass_banner_frosted_title
            )
            it.setSummary(
                if (solid) R.string.glass_banner_solid_summary
                else R.string.glass_banner_frosted_summary
            )
        }
        // With RR's lens edge the bend shows on every panel; without it, only on clear glass
        findPreference<Preference>(KEY_BEND)
            ?.setTitle(if (lens) R.string.glass_section_lens else R.string.glass_section_clear)
        findPreference<Preference>(KEY_EDGES_NOTE)
            ?.setTitle(if (lens) R.string.glass_edges_note_rr else R.string.glass_edges_note_catalyxx)

        paintCards(materialCards, material, true)
        paintCards(styleCards, edge, hasRim)
        materialCards
            .getOrNull(GlassSettings.MATERIAL_LIQUID)
            ?.findViewById<GlassPreviewView>(R.id.liquid_glass_swatch)
            ?.setSwatch(GlassSettings.MATERIAL_LIQUID, lens, false)
    }

    /** Marks the card at [picked] as the one picked; cards wait while they're not [enabled]. */
    private fun paintCards(cards: List<View>, picked: Int, enabled: Boolean) {
        for (i in cards.indices) {
            cards[i].apply {
                isSelected = i == picked
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.5f
            }
        }
    }

    private companion object {
        const val STATE_TAB = "glass_tab"

        // For colors that tell the picked tab or card from the others
        val SELECTED_OR_NOT = arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf())

        const val TAB_LOOK = 0
        const val TAB_EDGES = 1
        const val TAB_TOUCH = 2
        const val TAB_MOTION = 3
        const val TAB_PLACES = 4
        val TAB_NAMES =
            intArrayOf(
                R.string.liquid_glass_look_title,
                R.string.liquid_glass_edges_title,
                R.string.glass_touch_title,
                R.string.glass_motion_title,
                R.string.glass_tab_places,
            )
        // What the keys of a tab's rows start with, at the top of the page
        const val TAB_PREFIX = "tab_"
        val TAB_KEYS = arrayOf("tab_look_", "tab_edges_", "tab_touch_", "tab_motion_", "tab_places_")
        val SCENES =
            arrayOf(
                GlassScene.ALL,
                GlassScene.EDGE,
                GlassScene.TOUCH,
                GlassScene.MOTION,
                GlassScene.ALL,
            )

        const val KEY_NEEDS_BLUR = "glass_needs_blur"
        const val KEY_BANNER = "glass_banner"
        const val KEY_MATERIAL = "tab_look_material"
        const val KEY_STYLE = "tab_look_style"
        const val KEY_BEND = "tab_edges_bend"
        const val KEY_EDGES_NOTE = "tab_edges_note"

        // Top rows that never wait: what turns glass on, notes, and the Glass clock, which is
        // glass whether or not glass is on elsewhere
        val ALWAYS_ON =
            setOf(
                KEY_NEEDS_BLUR,
                KEY_BANNER,
                KEY_MATERIAL,
                KEY_EDGES_NOTE,
                "tab_touch_note",
                "tab_motion_note",
                "tab_places_clock",
                "tab_places_note",
            )

        // In GlassSettings' MATERIAL_ order
        val MATERIAL_CARDS =
            intArrayOf(R.id.liquid_glass_solid, R.id.liquid_glass_frosted, R.id.liquid_glass_liquid)
        val MATERIAL_NAMES =
            intArrayOf(
                R.string.liquid_glass_solid,
                R.string.liquid_glass_frosted,
                R.string.liquid_glass_liquid,
            )
        val MATERIAL_SUMMARIES =
            intArrayOf(
                R.string.liquid_glass_solid_summary,
                R.string.liquid_glass_frosted_summary,
                R.string.liquid_glass_liquid_summary,
            )

        // In GlassSettings' EDGE_ order
        val STYLE_CARDS = intArrayOf(R.id.glass_style_rr, R.id.glass_style_catalyxx)
        val STYLE_NAMES = intArrayOf(R.string.glass_style_rr, R.string.glass_style_catalyxx)
        val STYLE_SUMMARIES =
            intArrayOf(R.string.glass_style_rr_summary, R.string.glass_style_catalyxx_summary)

        // What the page's rows wait on
        val WATCHED =
            arrayOf(
                GlassSettings.MATERIAL,
                GlassSettings.EDGE_STYLE,
                GlassSettings.COLOR,
                GlassSettings.MORPH,
            )

        // What only glass with a curved rim has
        val RIM_KEYS =
            arrayOf(
                KEY_STYLE,
                GlassSettings.STYLE,
                GlassSettings.BEND,
                GlassSettings.RIM_WIDTH,
                GlassSettings.RIM_LIGHT,
                GlassSettings.RAINBOW,
                GlassSettings.SHADOW,
                GlassSettings.TILT,
                GlassSettings.TILT_AMOUNT,
                GlassSettings.ADAPTIVE,
                GlassSettings.SMOKE,
                GlassSettings.SMOKE_COLOR,
                GlassSettings.SWITCH_POINT,
                GlassSettings.TOUCH_GLOW,
                GlassSettings.TOUCH_GLOW_COLOR,
                GlassSettings.TOUCH_GLOW_SIZE,
                GlassSettings.VOLUME_FROSTED,
                GlassSettings.POWER_MENU_BLUR_BEHIND,
            )
        val MORPH_KEYS =
            arrayOf(
                GlassSettings.MORPH_VOLUME,
                GlassSettings.MORPH_POWER_MENU,
                GlassSettings.MORPH_TILES,
                GlassSettings.MORPH_NOTIFICATIONS,
                GlassSettings.MORPH_HEADS_UP,
            )

        // What a row needs behind the glass to show: smoke, the switch point and the shadow only
        // show over something light, and the blur and the bend best over lines
        val STAGE_BACKDROPS =
            mapOf(
                GlassSettings.ADAPTIVE to GlassPreviewBackdrop.BACKGROUND_LIGHT,
                GlassSettings.SMOKE to GlassPreviewBackdrop.BACKGROUND_LIGHT,
                GlassSettings.SMOKE_COLOR to GlassPreviewBackdrop.BACKGROUND_LIGHT,
                GlassSettings.SWITCH_POINT to GlassPreviewBackdrop.BACKGROUND_LIGHT,
                GlassSettings.SHADOW to GlassPreviewBackdrop.BACKGROUND_LIGHT,
                GlassSettings.BLUR_STRENGTH to GlassPreviewBackdrop.BACKGROUND_LINES,
                GlassSettings.STYLE to GlassPreviewBackdrop.BACKGROUND_LINES,
                GlassSettings.BEND to GlassPreviewBackdrop.BACKGROUND_LINES,
                GlassSettings.RAINBOW to GlassPreviewBackdrop.BACKGROUND_LINES,
            )
    }
}
