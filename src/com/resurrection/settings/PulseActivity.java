package com.resurrection.settings;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Navbar pulse. SystemUI reads the same Settings.System names.
 */
public class PulseActivity extends Activity {
    /** Matches com.android.systemui.rr.pulse.PulseRenderer.KEY_ENABLED. */
    static final String KEY_ENABLED = "pulse_navbar_enabled";
    /** Matches com.android.systemui.rr.pulse.PulseRenderer.KEY_STYLE. 0 lines, 1 fading. */
    static final String KEY_STYLE = "pulse_style";

    private boolean mGuard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(16), pad, pad);

        Switch toggle = new Switch(this);
        toggle.setText(R.string.pulse_switch);
        toggle.setChecked(Settings.System.getInt(getContentResolver(), KEY_ENABLED, 0) == 1);
        toggle.setOnCheckedChangeListener((button, checked) -> write(
                button, KEY_ENABLED, checked ? 1 : 0, checked));
        root.addView(toggle, match());

        TextView summary = new TextView(this);
        summary.setText(R.string.pulse_switch_summary);
        summary.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        summary.setPadding(0, dp(8), 0, dp(20));
        root.addView(summary, match());

        TextView styleLabel = new TextView(this);
        styleLabel.setText(R.string.pulse_style);
        styleLabel.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        styleLabel.setPadding(0, 0, 0, dp(8));
        root.addView(styleLabel, match());

        RadioGroup styles = new RadioGroup(this);
        styles.setOrientation(LinearLayout.VERTICAL);
        RadioButton lines = new RadioButton(this);
        lines.setId(1);
        lines.setText(R.string.pulse_style_lines);
        RadioButton fading = new RadioButton(this);
        fading.setId(2);
        fading.setText(R.string.pulse_style_fading);
        styles.addView(lines);
        styles.addView(fading);
        int style = Settings.System.getInt(getContentResolver(), KEY_STYLE, 0);
        styles.check(style == 1 ? 2 : 1);
        styles.setOnCheckedChangeListener((group, id) -> {
            if (mGuard) {
                return;
            }
            int value = id == 2 ? 1 : 0;
            try {
                if (!Settings.System.putInt(getContentResolver(), KEY_STYLE, value)) {
                    revertStyle(group, value);
                }
            } catch (SecurityException e) {
                revertStyle(group, value);
            }
        });
        root.addView(styles, match());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void write(CompoundButton button, String key, int value, boolean checked) {
        if (mGuard || button.getTag() != null) {
            return;
        }
        try {
            if (Settings.System.putInt(getContentResolver(), key, value)) {
                return;
            }
        } catch (SecurityException e) {
            // Fall through and put the switch back.
        }
        button.setTag(Boolean.TRUE);
        button.setChecked(!checked);
        button.setTag(null);
        Toast.makeText(this, R.string.pulse_failed, Toast.LENGTH_SHORT).show();
    }

    private void revertStyle(RadioGroup group, int failedValue) {
        mGuard = true;
        group.check(failedValue == 1 ? 1 : 2);
        mGuard = false;
        Toast.makeText(this, R.string.pulse_failed, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
