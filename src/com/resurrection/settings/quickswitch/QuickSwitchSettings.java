package com.resurrection.settings.quickswitch;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.rr.Utils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.widget.LayoutPreference;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import lineageos.providers.LineageSettings;

/**
 * Remixer's Quick switch page: RR Launcher or Pixel Launcher side by side. The framework reads the
 * choice at startup, so switching restarts the system.
 */
public class QuickSwitchSettings extends SettingsPreferenceFragment {

    private static final String TAG = "QuickSwitchSettings";

    /** Index into config_launcherComponents, read by the framework at startup. */
    private static final String PROP_LAUNCHER = "persist.sys.default_launcher";
    /** Set on builds that carry Pixel Launcher: without it there is nothing to switch to. */
    private static final String PROP_PIXEL_SHIPPED = "persist.sys.quickswitch_pixel_shipped";

    private static final int RR_LAUNCHER = 0;
    private static final int PIXEL_LAUNCHER = 1;

    private static final String KEY_PICKER = "quick_switch_picker";
    private static final String KEY_RESTART = "quick_switch_restart";
    private static final String KEY_DOUBLE_TAP = "double_tap_sleep_gesture";
    private static final String KEY_HIDE_PILL = "quick_switch_hide_pill";

    private static final String PIXEL_PACKAGE = "com.google.android.apps.nexuslauncher";
    private static final String NO_GESTURE_HINT_OVERLAY =
            "com.google.android.apps.nexuslauncher.overlay.nogesturehint";

    private int mInUse;
    private int mSelected;

    /** Whether this build has a second launcher to offer. */
    public static boolean isAvailable() {
        return SystemProperties.getBoolean(PROP_PIXEL_SHIPPED, false);
    }

    /** The name of the launcher in use, for the row in Miscellaneous. */
    public static CharSequence currentName(Context context) {
        return context.getText(currentLauncher() == PIXEL_LAUNCHER
                ? R.string.quick_switch_pixel_launcher : R.string.quick_switch_rr_launcher);
    }

    private static int currentLauncher() {
        return SystemProperties.getInt(PROP_LAUNCHER, RR_LAUNCHER) == PIXEL_LAUNCHER
                ? PIXEL_LAUNCHER : RR_LAUNCHER;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.remixer_quick_switch);
        mInUse = currentLauncher();
        mSelected = mInUse;

        final LayoutPreference picker = findPreference(KEY_PICKER);
        picker.findViewById(R.id.quick_switch_card_rr)
                .setOnClickListener(v -> select(RR_LAUNCHER));
        picker.findViewById(R.id.quick_switch_card_pixel)
                .setOnClickListener(v -> select(PIXEL_LAUNCHER));

        final LayoutPreference restart = findPreference(KEY_RESTART);
        restart.findViewById(R.id.quick_switch_restart_button)
                .setOnClickListener(v -> confirmSwitch());

        final SwitchPreferenceCompat hidePill = findPreference(KEY_HIDE_PILL);
        hidePill.setChecked(LineageSettings.System.getInt(getContentResolver(),
                LineageSettings.System.NAVIGATION_BAR_HINT, 1) == 0);
        hidePill.setOnPreferenceChangeListener((preference, value) -> {
            final boolean hide = (Boolean) value;
            LineageSettings.System.putInt(getContentResolver(),
                    LineageSettings.System.NAVIGATION_BAR_HINT, hide ? 0 : 1);
            Utils.toggleOverlay(getContext(), NO_GESTURE_HINT_OVERLAY, hide);
            if (mInUse == PIXEL_LAUNCHER) {
                Utils.restartApp(PIXEL_PACKAGE, getContext());
            }
            return true;
        });
        update();
    }

    private void select(int launcher) {
        mSelected = launcher;
        update();
    }

    private void update() {
        final LayoutPreference picker = findPreference(KEY_PICKER);
        bindCard(picker.findViewById(R.id.quick_switch_card_rr), RR_LAUNCHER);
        bindCard(picker.findViewById(R.id.quick_switch_card_pixel), PIXEL_LAUNCHER);

        // These two only reach Pixel Launcher; RR Launcher has its own in Home settings.
        final boolean pixel = mSelected == PIXEL_LAUNCHER;
        setVisible(KEY_DOUBLE_TAP, pixel);
        setVisible(KEY_HIDE_PILL, pixel);

        final boolean changing = mSelected != mInUse;
        setVisible(KEY_RESTART, changing);
        if (changing) {
            final LayoutPreference restart = findPreference(KEY_RESTART);
            final Button button = restart.findViewById(R.id.quick_switch_restart_button);
            button.setText(getString(R.string.quick_switch_restart_into, name(mSelected)));
        }
    }

    private void bindCard(QuickSwitchCardView card, int launcher) {
        final boolean inUse = launcher == mInUse;
        final boolean selected = launcher == mSelected;
        final String tag = inUse ? getString(R.string.quick_switch_in_use)
                : selected ? getString(R.string.quick_switch_selected) : null;
        card.bind(launcher == PIXEL_LAUNCHER, name(launcher).toString(), inUse, selected, tag);
    }

    private CharSequence name(int launcher) {
        return getText(launcher == PIXEL_LAUNCHER
                ? R.string.quick_switch_pixel_launcher : R.string.quick_switch_rr_launcher);
    }

    private void setVisible(String key, boolean visible) {
        final Preference preference = findPreference(key);
        if (preference != null) {
            preference.setVisible(visible);
        }
    }

    private void confirmSwitch() {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(getString(R.string.quick_switch_confirm_title, name(mSelected)))
                .setMessage(getString(R.string.quick_switch_confirm_message, name(mInUse)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.quick_switch_confirm_restart, (dialog, which) -> {
                    SystemProperties.set(PROP_LAUNCHER, Integer.toString(mSelected));
                    restartSystem();
                })
                .show();
    }

    private static void restartSystem() {
        try {
            IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE)).restart();
        } catch (RemoteException e) {
            Log.e(TAG, "Could not restart the system", e);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}
