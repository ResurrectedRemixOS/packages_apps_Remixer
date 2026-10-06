package com.resurrection.settings;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;

/**
 * Slim recents switch. SystemUI and Launcher3 read the same Settings.System name.
 */
public class SlimActivity extends Activity {
    /** Matches com.android.systemui.rr.slim.SlimRecents.KEY. */
    static final String KEY = "slim_recents_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(16), pad, pad);

        Switch toggle = new Switch(this);
        toggle.setText(R.string.slim_switch);
        toggle.setChecked(Settings.System.getInt(getContentResolver(), KEY, 0) == 1);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (button.getTag() != null) {
                return;
            }
            try {
                if (Settings.System.putInt(getContentResolver(), KEY, checked ? 1 : 0)) {
                    return;
                }
            } catch (SecurityException e) {
                // Fall through and put the switch back.
            }
            button.setTag(Boolean.TRUE);
            button.setChecked(!checked);
            button.setTag(null);
            Toast.makeText(this, R.string.slim_failed, Toast.LENGTH_SHORT).show();
        });
        root.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView summary = new TextView(this);
        summary.setText(R.string.slim_switch_summary);
        summary.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        summary.setPadding(0, dp(8), 0, 0);
        root.addView(summary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
