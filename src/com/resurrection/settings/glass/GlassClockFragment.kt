/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.app.AlertDialog
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.Preference
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.internal.util.catalyxx.GlassClockView
import com.android.internal.util.rr.Utils
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.google.android.material.appbar.AppBarLayout
import com.resurrection.settings.ClockFaceActivity

/**
 * The Glass clock: a lock screen clock face of its own, which is glass whether or not glass is on
 * elsewhere. The real clock stays pinned at the top, over the lock screen's wallpaper or over a
 * dark or light backdrop, which show what the wallpaper may hide. Under it the page says which
 * face the lock screen has now, and a button makes it this one.
 */
class GlassClockFragment : SettingsPreferenceFragment() {

    private var clock: GlassClockView? = null
    private var nowView: TextView? = null
    private var useButton: TextView? = null
    private val backdropButtons = ArrayList<TextView>()
    private var backdrop = BACKDROP_WALLPAPER

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                showFace()
            }
        }

    override fun getMetricsCategory() = MetricsEvent.VIEW_UNKNOWN

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        backdrop = icicle?.getInt(STATE_BACKDROP) ?: BACKDROP_WALLPAPER
        addPreferencesFromResource(R.xml.remixer_glass_clock)
        findPreference<Preference>(KEY_CLOCK_FACES)?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ClockFaceActivity::class.java))
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val header =
            layoutInflater.inflate(R.layout.remixer_glass_clock_stage, view as ViewGroup, false)
        val strip = header.findViewById<GlassPreviewStrip>(R.id.glass_stage)
        // The clock keeps clear of the backdrop buttons under it
        strip.roomBelowClock =
            resources.getDimensionPixelSize(R.dimen.glass_stage_bar_height) +
                2 * resources.getDimensionPixelSize(R.dimen.glass_stage_bar_margin)
        clock = strip.getChildAt(0) as? GlassClockView
        nowView = header.findViewById(R.id.glass_clock_now)
        useButton =
            header.findViewById<TextView>(R.id.glass_clock_use).apply {
                setOnClickListener { useGlassClock() }
            }
        backdropButtons.clear()
        for (id in BACKDROP_BUTTONS) {
            val button = header.findViewById<TextView>(id)
            val choice = backdropButtons.size
            button.setOnClickListener { showBackdrop(choice) }
            button.accessibilityDelegate = GlassPreview.RADIO
            backdropButtons.add(button)
        }
        showBackdrop(backdrop)
        setPinnedHeaderView(header)
        // Scrolling moves the rows alone: pulled down, the toolbar would take the clock with it
        listView.isNestedScrollingEnabled = false
        // Settings themes turn clipChildren off, which lets rows draw over the clock
        view.findViewById<ViewGroup>(android.R.id.list_container)?.clipChildren = true
    }

    override fun onDestroyView() {
        clock = null
        nowView = null
        useButton = null
        backdropButtons.clear()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_BACKDROP, backdrop)
    }

    override fun onResume() {
        super.onResume()
        // The clock stays where it is, so the toolbar has to as well
        requireActivity().findViewById<AppBarLayout>(R.id.app_bar)?.setExpanded(false, false)
        requireContext()
            .contentResolver
            .registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE),
                false,
                observer,
                UserHandle.USER_CURRENT,
            )
        showFace()
    }

    override fun onPause() {
        requireContext().contentResolver.unregisterContentObserver(observer)
        super.onPause()
    }

    /** Puts [choice], one of the `BACKDROP_` values, behind the clock. */
    private fun showBackdrop(choice: Int) {
        backdrop = choice
        for (i in backdropButtons.indices) {
            backdropButtons[i].isSelected = i == choice
        }
        when (choice) {
            BACKDROP_DARK -> clock?.setBackdropDim(Color.BLACK, PLAIN_DIM)
            BACKDROP_LIGHT -> clock?.setBackdropDim(Color.WHITE, PLAIN_DIM)
            // As the lock screen dims its wallpaper
            else -> clock?.setBackdropDim(Color.BLACK, LOCK_SCREEN_DIM)
        }
    }

    private fun face() =
        Settings.Secure.getIntForUser(
            requireContext().contentResolver,
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE,
            0,
            UserHandle.USER_CURRENT,
        )

    /** Says which face the lock screen has now, and whether it's this one. */
    private fun showFace() {
        val context = context ?: return
        val applied = face() == GLASS_CLOCK_FACE
        nowView?.text =
            getString(R.string.glass_clock_now, ClockFaceActivity.currentName(context))
        useButton?.apply {
            setText(if (applied) R.string.clock_faces_applied else R.string.glass_clock_use)
            isEnabled = !applied
            alpha = if (applied) 0.6f else 1f
        }
    }

    /** Puts the Glass clock on the lock screen. */
    private fun useGlassClock() {
        if (face() == 0) {
            // The lock screen sets custom faces up when SystemUI starts, as the face picker says
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clock_faces_restart_title)
                .setMessage(R.string.clock_faces_restart_message)
                .setPositiveButton(R.string.clock_faces_restart) { _, _ ->
                    if (writeFace()) Utils.restartSystemUI()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            writeFace()
        }
    }

    private fun writeFace(): Boolean {
        val written =
            Settings.Secure.putIntForUser(
                requireContext().contentResolver,
                Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE,
                GLASS_CLOCK_FACE,
                UserHandle.USER_CURRENT,
            )
        showFace()
        return written
    }

    private companion object {
        const val STATE_BACKDROP = "glass_clock_backdrop"
        const val KEY_CLOCK_FACES = "glass_clock_faces"
        // The Glass clock's place in SystemUI's ClockStyle and Remixer's clock_face_names
        const val GLASS_CLOCK_FACE = 96

        const val BACKDROP_WALLPAPER = 0
        const val BACKDROP_DARK = 1
        const val BACKDROP_LIGHT = 2
        // In the order of the BACKDROP_ values
        val BACKDROP_BUTTONS =
            intArrayOf(
                R.id.glass_backdrop_wallpaper,
                R.id.glass_backdrop_dark,
                R.id.glass_backdrop_light,
            )
        // GlassClockView's own dim for the lock screen, and one that hides the wallpaper
        const val LOCK_SCREEN_DIM = 0.2f
        const val PLAIN_DIM = 0.94f
    }
}
