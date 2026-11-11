/*
 * SPDX-FileCopyrightText: 2026 Project Catalyxx
 * SPDX-FileCopyrightText: The Resurrected Remix Project
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
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.PopupWindow
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.android.internal.util.catalyxx.GlassSettings
import com.android.settings.R
import com.google.android.material.appbar.AppBarLayout

/** What the stage shows. */
enum class GlassScene {
    /** Every place glass has, a piece each. */
    ALL,
    /** The edge of glass with and without text on it, and a corner of one under a loupe. */
    EDGE,
    /** Glass that answers a finger. */
    TOUCH,
    /** Glass that melts together, and a panel that keeps opening and closing. */
    MOTION,
}

/**
 * The stage pinned at the top of the Liquid Glass page, so a change shows without leaving the
 * page. Glass only blurs what's behind its window, so the page keeps a strip of wallpaper and the
 * glass ([GlassSampleView]) goes in a small window of its own over it: SurfaceFlinger draws it as
 * it draws the real panels, and it reads the real settings. Buttons along the strip's bottom
 * change what's behind the glass and open a real panel over the page.
 */
class GlassPreview(private val fragment: Fragment) {

    private lateinit var strip: GlassPreviewStrip
    private lateinit var behind: GlassPreviewBackdrop
    private lateinit var realButton: TextView
    private val backdropButtons = ArrayList<TextView>()
    private var barRoom = 0
    private var window: PopupWindow? = null
    private var sample: GlassSampleView? = null
    private val location = IntArray(2)
    private val newLocation = IntArray(2)
    private var hasSampleNotification = false
    private var resumed = false

    /** The row under the stage, for the page's tabs. */
    lateinit var tabs: ViewGroup
        private set

    /** What the glass on the stage shows. */
    var scene = GlassScene.ALL
        set(value) {
            if (field == value) return
            field = value
            sample?.scene = value
            window?.let {
                // Only glass that answers a finger takes touches; elsewhere they go to the page
                it.isTouchable = value == GlassScene.TOUCH
                it.update()
            }
            showRealName()
        }

    /** What's behind the glass: one of [GlassPreviewBackdrop]'s `BACKGROUND_` values. */
    var backdrop = GlassPreviewBackdrop.BACKGROUND_WALLPAPER
        set(value) {
            field = value
            showBackdrop()
        }

    private val settingsObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                sample?.refresh()
            }
        }

    // The glass's window doesn't move or fade with the page by itself
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
        val view = inflater.inflate(R.layout.remixer_glass_stage, parent, false)
        val resources = view.resources
        strip = view.findViewById(R.id.glass_stage)
        strip.stageHeight = resources.getDimensionPixelSize(R.dimen.glass_stage_height)
        barRoom =
            resources.getDimensionPixelSize(R.dimen.glass_stage_bar_height) +
                2 * resources.getDimensionPixelSize(R.dimen.glass_stage_bar_margin)
        tabs = view.findViewById(R.id.glass_stage_tabs)
        behind = GlassPreviewBackdrop(inflater.context)
        behind.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        strip.addView(
            behind,
            0,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        for (id in BACKDROP_BUTTONS) {
            val button = view.findViewById<TextView>(id)
            val choice = backdropButtons.size
            button.setOnClickListener { backdrop = choice }
            button.accessibilityDelegate = RADIO
            backdropButtons.add(button)
        }
        realButton = view.findViewById(R.id.glass_stage_real)
        realButton.setOnClickListener { showRealOne() }
        showBackdrop()
        showRealName()
        // The page's view is gone: so is the glass over it
        strip.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    hideSample()
                }
            }
        )
        return view
    }

    private fun showBackdrop() {
        if (!::behind.isInitialized) return
        behind.choice = backdrop
        for (i in backdropButtons.indices) {
            backdropButtons[i].isSelected = i == backdrop
        }
    }

    private fun showRealName() {
        if (!::realButton.isInitialized) return
        realButton.setText(
            when (scene) {
                GlassScene.TOUCH,
                GlassScene.MOTION -> R.string.glass_stage_show_panel
                else -> R.string.glass_stage_open_shade
            }
        )
    }

    fun onResume() {
        resumed = true
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
        val shown = sample
        if (shown != null) {
            // What changed while the page was behind something else
            shown.refresh()
            shown.setRunning(true)
        } else {
            // After the first layout, also when the page opens with the activity
            strip.post { showSample() }
        }
    }

    /**
     * The page is behind something else, or on its way out. The glass stays over it: it leaves
     * with the page's view ([onDestroyView]), following it as it goes.
     */
    fun onPause() {
        resumed = false
        fragment.requireContext().contentResolver.unregisterContentObserver(settingsObserver)
        strip.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocusListener)
        sample?.setRunning(false)
        cancelSampleNotification()
    }

    fun onDestroyView() {
        hideSample()
    }

    private fun showSample() {
        if (window != null || !strip.isAttachedToWindow || strip.width == 0 ||
            strip.height <= barRoom) {
            return
        }
        // All of the strip but the bar of buttons, which stays the page's to touch
        val height = strip.height - barRoom
        val view = GlassSampleView(strip.context, strip.width, height)
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.onRebuilt = { behind.loupe = view.loupe }
        view.scene = scene
        view.setRunning(resumed)
        // As much as the page shows of itself, while it's still on its way in
        view.fade(shownAlpha(strip))
        strip.getLocationInWindow(location)
        window =
            PopupWindow(view, strip.width, height, false).apply {
                isTouchable = scene == GlassScene.TOUCH
                isClippingEnabled = false
                // It comes and goes with the page, not by itself
                animationStyle = 0
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
        behind.loupe = null
    }

    private fun followStrip() {
        val popup = window ?: return
        if (popup.width != strip.width || popup.height != strip.height - barRoom) {
            // The strip took another size
            hideSample()
            strip.post { showSample() }
            return
        }
        strip.getLocationInWindow(newLocation)
        if (!newLocation.contentEquals(location)) {
            newLocation.copyInto(location)
            popup.update(location[0], location[1], -1, -1)
        }
        sample?.fade(shownAlpha(strip))
    }

    /** How much of [view] shows, 0 to 1, as its page fades in or out around it. */
    private fun shownAlpha(view: View): Float {
        var alpha = 1f
        var at: View? = view
        while (at != null) {
            if (at.visibility != View.VISIBLE) return 0f
            alpha *= at.alpha * at.transitionAlpha
            at = at.parent as? View
        }
        return alpha
    }

    /** Opens the real thing over the page. It closes as it always does, back to the page. */
    private fun showRealOne() {
        val context = fragment.requireContext()
        when (scene) {
            GlassScene.TOUCH,
            GlassScene.MOTION ->
                context
                    .getSystemService(AudioManager::class.java)
                    ?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_SAME,
                        AudioManager.FLAG_SHOW_UI,
                    )
            else -> {
                // Android holds pop-ups back for a while after an unlock, so into the shade it goes
                postSampleNotification()
                context.getSystemService(StatusBarManager::class.java)?.expandNotificationsPanel()
            }
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

        /** Tells accessibility that a view picks one of a few: it's checked while selected. */
        val RADIO =
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

        // In the order of GlassPreviewBackdrop's BACKGROUND_ values
        private val BACKDROP_BUTTONS =
            intArrayOf(
                R.id.glass_backdrop_wallpaper,
                R.id.glass_backdrop_light,
                R.id.glass_backdrop_lines,
            )
        private const val SAMPLE_CHANNEL = "glass_preview"
        private const val SAMPLE_ID = 1
        private const val SAMPLE_TIMEOUT_MS = 60_000L
    }
}
