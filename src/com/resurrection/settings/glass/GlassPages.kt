/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.app.AlertDialog
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.annotation.XmlRes
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.internal.util.catalyxx.GlassSettings
import com.android.internal.util.rr.Utils
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.resurrection.settings.ClockFaceActivity

/**
 * A Liquid Glass page: its rows under the live preview of what they set. The rows wait while glass
 * can't show what they set: all of them while the material is Solid or Android's blur is off,
 * those that only glass with a curved rim has while it's Frosted, and the panels' morph switches
 * while Morph, on the Motion page, is off.
 */
abstract class GlassPageFragment(
    @XmlRes private val xml: Int,
    private val kind: GlassPreviewKind,
) : SettingsPreferenceFragment() {
    private var preview: GlassPreview? = null
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshRows()
            }
        }

    /** Top rows that never wait, such as links to other pages. */
    protected open val alwaysOn: Set<String> = emptySet()

    override fun getMetricsCategory() = MetricsEvent.VIEW_UNKNOWN

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(xml)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val newPreview = GlassPreview(this, kind)
        preview = newPreview
        setPinnedHeaderView(newPreview.createView(layoutInflater, view as ViewGroup))
        // Scrolling moves the rows alone: pulled down, the toolbar would take the preview with it
        listView.isNestedScrollingEnabled = false
        // Settings themes turn clipChildren off, which lets rows draw over the preview
        view.findViewById<ViewGroup>(android.R.id.list_container)?.clipChildren = true
    }

    override fun onDestroyView() {
        preview = null
        super.onDestroyView()
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

    protected open fun refreshRows() {
        val context = context ?: return
        val resolver = context.contentResolver
        val isOn = GlassPreview.isBlurOn(context) && GlassSettings.isEnabled(resolver)
        val hasRim = isOn && GlassSettings.style(resolver) != GlassSettings.STYLE_FLAT
        val morphs = isOn && GlassSettings.getInt(resolver, GlassSettings.MORPH, 1) == 1
        val screen = preferenceScreen
        for (i in 0 until screen.preferenceCount) {
            val row = screen.getPreference(i)
            row.isEnabled = isOn || row.key in alwaysOn
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
    }

    private companion object {
        // What other pages set that rows here wait on
        val WATCHED =
            arrayOf(
                GlassSettings.MATERIAL,
                GlassSettings.STYLE,
                GlassSettings.COLOR,
                GlassSettings.MORPH,
            )
        // What only glass with a curved rim has
        val RIM_KEYS =
            arrayOf(
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
    }
}

/** Glass: touch glow, squish, tap buzz and the glass selector. */
class GlassTouchFragment : GlassPageFragment(R.xml.remixer_glass_touch, GlassPreviewKind.TOUCH)

/** Glass: morph and melt. */
class GlassMotionFragment :
    GlassPageFragment(R.xml.remixer_glass_motion, GlassPreviewKind.MOTION)

/** Glass on notifications. */
class GlassNotificationsFragment :
    GlassPageFragment(R.xml.remixer_glass_notifications, GlassPreviewKind.NOTIFICATIONS)

/** Glass on Quick Settings and the shade. */
class GlassQuickSettingsFragment :
    GlassPageFragment(R.xml.remixer_glass_quick_settings, GlassPreviewKind.QUICK_SETTINGS)

/** Glass on the volume panel. */
class GlassVolumeFragment :
    GlassPageFragment(R.xml.remixer_glass_volume, GlassPreviewKind.VOLUME)

/** Glass on the power menu, menus and dialogs. */
class GlassPowerFragment : GlassPageFragment(R.xml.remixer_glass_power, GlassPreviewKind.POWER)

/**
 * Glass on the lock screen, and the Glass clock: a clock face of its own, which is glass whether
 * or not glass is on elsewhere.
 */
class GlassLockscreenFragment :
    GlassPageFragment(R.xml.remixer_glass_lockscreen, GlassPreviewKind.LOCK_SCREEN) {
    override val alwaysOn = setOf(KEY_CLOCK_CATEGORY, KEY_CLOCK_NOTE)

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        findPreference<SwitchPreferenceCompat>(KEY_CLOCK_USE)?.setOnPreferenceChangeListener {
            _,
            value ->
            useGlassClock(value as Boolean)
            // The switch follows the face once it's written
            false
        }
        findPreference<Preference>(KEY_CLOCK_FACES)?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ClockFaceActivity::class.java))
            true
        }
    }

    override fun refreshRows() {
        super.refreshRows()
        findPreference<SwitchPreferenceCompat>(KEY_CLOCK_USE)?.isChecked =
            face() == GLASS_CLOCK_FACE
    }

    private fun face() =
        Settings.Secure.getIntForUser(
            requireContext().contentResolver,
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE,
            0,
            UserHandle.USER_CURRENT,
        )

    /** Puts the Glass clock on the lock screen, or back the face it replaced. */
    private fun useGlassClock(on: Boolean) {
        val resolver = requireContext().contentResolver
        val current = face()
        val next =
            if (on) {
                GLASS_CLOCK_FACE
            } else {
                Settings.System.getIntForUser(resolver, KEY_PREVIOUS_FACE, 0, UserHandle.USER_CURRENT)
                    .takeIf { it != GLASS_CLOCK_FACE } ?: 0
            }
        if (next == current) return
        if (on) {
            Settings.System.putIntForUser(resolver, KEY_PREVIOUS_FACE, current, UserHandle.USER_CURRENT)
        }
        if ((current == 0) != (next == 0)) {
            // The lock screen sets custom faces up when SystemUI starts, as the face picker says
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clock_faces_restart_title)
                .setMessage(R.string.clock_faces_restart_message)
                .setPositiveButton(R.string.clock_faces_restart) { _, _ ->
                    if (writeFace(next)) Utils.restartSystemUI()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            writeFace(next)
        }
    }

    private fun writeFace(face: Int): Boolean {
        val written =
            Settings.Secure.putIntForUser(
                requireContext().contentResolver,
                Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE,
                face,
                UserHandle.USER_CURRENT,
            )
        refreshRows()
        return written
    }

    private companion object {
        const val KEY_CLOCK_CATEGORY = "glass_clock_category"
        const val KEY_CLOCK_NOTE = "glass_clock_note"
        const val KEY_CLOCK_USE = "glass_clock_use"
        const val KEY_CLOCK_FACES = "glass_clock_faces"
        const val KEY_PREVIOUS_FACE = "rr_glass_clock_previous_face"
        // The Glass clock's place in SystemUI's ClockStyle and Remixer's clock_face_names
        const val GLASS_CLOCK_FACE = 96
    }
}
