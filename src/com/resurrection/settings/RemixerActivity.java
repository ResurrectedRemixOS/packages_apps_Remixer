package com.resurrection.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;

/**
 * Resurrection Remix personalization hub.
 */
public class RemixerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer);
        LinearLayout list = findViewById(R.id.list);
        addRow(list, R.string.remixer_fonts, R.string.remixer_fonts_summary,
                new Intent(this, FontActivity.class));
        addRow(list, R.string.remixer_wallpapers, R.string.remixer_wallpapers_summary,
                new Intent(this, WallpaperActivity.class));
        addRow(list, R.string.remixer_accents, R.string.remixer_accents_summary,
                new Intent(this, AccentActivity.class));
        addRow(list, R.string.remixer_doze, R.string.remixer_doze_summary,
                external("com.resurrection.doze", "com.resurrection.doze.DozeActivity"));
        addRow(list, R.string.remixer_pulse, R.string.remixer_pulse_summary,
                new Intent(this, PulseActivity.class));
        addRow(list, R.string.remixer_headers, R.string.remixer_headers_summary,
                new Intent(this, HeaderActivity.class));
        addRow(list, R.string.remixer_style_walls, R.string.remixer_style_walls_summary,
                external("org.omnirom.omnistyle", "org.omnirom.omnistyle.BrowseWallsActivity"));
        addRow(list, R.string.remixer_weather, R.string.remixer_weather_summary,
                external("org.omnirom.omnijaws", "org.omnirom.omnijaws.SettingsActivity"));
    }

    private static Intent external(String pkg, String cls) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(pkg, cls);
        return intent;
    }

    private void addRow(LinearLayout list, int title, int summary, Intent intent) {
        View row = LayoutInflater.from(this).inflate(R.layout.remixer_row, list, false);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.summary)).setText(summary);
        row.setOnClickListener(v -> {
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.missing_app, Toast.LENGTH_SHORT).show();
            }
        });
        list.addView(row);
    }
}
