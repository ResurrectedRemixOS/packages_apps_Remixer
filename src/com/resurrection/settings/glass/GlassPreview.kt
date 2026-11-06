/*
 * SPDX-FileCopyrightText: 2026 Project Catalyxx
 * SPDX-License-Identifier: Apache-2.0
 */

package com.resurrection.settings.glass

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.RemoteException
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManagerGlobal
import android.widget.PopupWindow
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.android.internal.util.catalyxx.GlassSettings
import com.android.settings.R
import com.google.android.material.appbar.AppBarLayout

/** What a glass page's preview shows, and which real panel its button opens. */
enum class GlassPreviewKind {
    ALL_GLASS,
    TOUCH,
    MOTION,
    LOCK_SCREEN,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    VOLUME,
    POWER,
}

/**
 * The live preview at the top of a glass page, so a change shows without leaving the page. Glass
 * only blurs what's behind its window, so the page keeps a strip of wallpaper and the glass
 * ([GlassSampleView]) goes in a small window of its own over it: SurfaceFlinger draws it as it
 * draws the real panels, and it reads the real settings. A button changes what's behind it, and on
 * some pages another opens the real panel over the page.
 */
class GlassPreview(private val fragment: Fragment, private val kind: GlassPreviewKind) {

    private lateinit var strip: GlassPreviewStrip
    private var backdrop: GlassPreviewBackdrop? = null
    private var window: PopupWindow? = null
    private var sample: GlassSampleView? = null
    private val location = IntArray(2)
    private val newLocation = IntArray(2)
    private var hasSampleNotification = false

    private val settingsObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                sample?.refresh()
            }
        }

    // The glass's window doesn't move with the page by itself
    private val followStrip =
        ViewTreeObserver.OnPreDrawListener {
            followStrip()
            true
        }

    // The real shade has closed, with the sample notification in it
    private val windowFocusListener =
        ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) cancelSampleNotification()
        }

    fun createView(inflater: LayoutInflater, parent: ViewGroup): View {
        val view = inflater.inflate(R.layout.remixer_glass_preview, parent, false)
        strip = view.findViewById(R.id.glass_preview_strip)
        val background = view.findViewById<TextView>(R.id.glass_preview_background)
        val real = view.findViewById<TextView>(R.id.glass_preview_real)
        if (kind == GlassPreviewKind.LOCK_SCREEN) {
            // The real clock, which draws the lock screen's wallpaper behind itself
            inflater.inflate(R.layout.remixer_glass_clock_preview, strip, true)
            view.findViewById<View>(R.id.glass_preview_buttons).visibility = View.GONE
        } else {
            val behind = GlassPreviewBackdrop(inflater.context)
            behind.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            strip.addView(behind, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            backdrop = behind
            showBackgroundName(background, behind.choice)
            background.setOnClickListener {
                behind.choice = (behind.choice + 1) % BACKGROUND_NAMES.size
                showBackgroundName(background, behind.choice)
            }
            if (hasRealOne()) {
                real.setOnClickListener { showRealOne() }
            } else {
                real.visibility = View.GONE
            }
        }
        return view
    }

    private fun showBackgroundName(button: TextView, background: Int) {
        val name = button.context.getString(BACKGROUND_NAMES[background])
        button.text = name
        button.contentDescription =
            button.context.getString(R.string.glass_preview_background_description, name)
    }

    fun onResume() {
        val activity = fragment.requireActivity()
        // The strip stays where it is, so the toolbar has to as well
        activity.findViewById<AppBarLayout>(R.id.app_bar)?.setExpanded(false, false)
        val resolver = activity.contentResolver
        GlassSettings.observe(resolver, settingsObserver, *GlassSettings.KEYS)
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.DISABLE_WINDOW_BLURS),
            false,
            settingsObserver,
        )
        strip.viewTreeObserver.addOnWindowFocusChangeListener(windowFocusListener)
        // After the first layout, also when the page opens with the activity
        strip.post { showSample() }
    }

    fun onPause() {
        fragment.requireContext().contentResolver.unregisterContentObserver(settingsObserver)
        strip.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocusListener)
        hideSample()
        cancelSampleNotification()
    }

    private fun showSample() {
        if (!fragment.isResumed || window != null || !strip.isAttachedToWindow || strip.width == 0) {
            return
        }
        val view = GlassSampleView(strip.context, kind, strip.width, strip.height)
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        strip.getLocationInWindow(location)
        window =
            PopupWindow(view, strip.width, strip.height, false).apply {
                // The Touch page's glass answers a finger; elsewhere touches go to the page
                isTouchable = kind == GlassPreviewKind.TOUCH
                isClippingEnabled = false
                showAtLocation(strip, Gravity.TOP or Gravity.START, location[0], location[1])
            }
        sample = view
        strip.viewTreeObserver.addOnPreDrawListener(followStrip)
    }

    private fun hideSample() {
        strip.viewTreeObserver.removeOnPreDrawListener(followStrip)
        window?.dismiss()
        window = null
        sample = null
    }

    private fun followStrip() {
        val popup = window ?: return
        if (popup.width != strip.width || popup.height != strip.height) {
            // The Glass clock took another layout: the buttons belong below it
            hideSample()
            strip.post { showSample() }
            return
        }
        strip.getLocationInWindow(newLocation)
        if (!newLocation.contentEquals(location)) {
            newLocation.copyInto(location)
            popup.update(location[0], location[1], -1, -1)
        }
    }

    private fun hasRealOne() =
        when (kind) {
            GlassPreviewKind.TOUCH,
            GlassPreviewKind.MOTION,
            GlassPreviewKind.VOLUME,
            GlassPreviewKind.NOTIFICATIONS,
            GlassPreviewKind.QUICK_SETTINGS,
            GlassPreviewKind.POWER -> true
            else -> false
        }

    /** Opens the real panel over the page. It closes as it always does, back to the page. */
    private fun showRealOne() {
        val context = fragment.requireContext()
        when (kind) {
            GlassPreviewKind.NOTIFICATIONS -> {
                // Android holds pop-ups back for a while after an unlock, so into the shade it goes
                postSampleNotification()
                context.getSystemService(StatusBarManager::class.java)?.expandNotificationsPanel()
            }
            GlassPreviewKind.QUICK_SETTINGS ->
                context.getSystemService(StatusBarManager::class.java)?.expandSettingsPanel()
            GlassPreviewKind.POWER ->
                try {
                    WindowManagerGlobal.getWindowManagerService()?.showGlobalActions()
                } catch (e: RemoteException) {
                    // It's gone as we're asking: nothing to show
                }
            else ->
                context
                    .getSystemService(AudioManager::class.java)
                    ?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_SAME,
                        AudioManager.FLAG_SHOW_UI,
                    )
        }
    }

    private fun postSampleNotification() {
        val context = fragment.requireContext()
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            try {
                context.packageManager.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Process.myUserHandle(),
                )
            } catch (e: SecurityException) {
                // The shade still opens, with what's in it already
                return
            }
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                    SAMPLE_CHANNEL,
                    context.getString(R.string.glass_preview_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                }
        )
        manager.notify(
            SAMPLE_ID,
            Notification.Builder(context, SAMPLE_CHANNEL)
                .setSmallIcon(R.drawable.ic_rr_tune)
                .setContentTitle(context.getString(R.string.liquid_glass_title))
                .setContentText(context.getString(R.string.glass_preview_sample_text))
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(SAMPLE_TIMEOUT_MS)
                .build(),
        )
        hasSampleNotification = true
    }

    private fun cancelSampleNotification() {
        if (hasSampleNotification) {
            hasSampleNotification = false
            fragment.context?.getSystemService(NotificationManager::class.java)?.cancel(SAMPLE_ID)
        }
    }

    companion object {
        /** Whether Android's blur is on: glass needs it. */
        fun isBlurOn(context: Context) =
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DISABLE_WINDOW_BLURS,
                0,
            ) == 0

        private val BACKGROUND_NAMES =
            intArrayOf(
                R.string.glass_preview_background_wallpaper,
                R.string.glass_preview_background_light,
                R.string.glass_preview_background_colors,
            )
        private const val SAMPLE_CHANNEL = "glass_preview"
        private const val SAMPLE_ID = 1
        private const val SAMPLE_TIMEOUT_MS = 60_000L
    }
}
