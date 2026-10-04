package com.resurrection.settings;

import android.app.ListActivity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Resurrection Remix accent seeds. Wallpaper restores Material You from the wallpaper.
 */
public class AccentActivity extends ListActivity {

    private AccentController mAccents;
    private String[] mNames;
    private String[] mColors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAccents = new AccentController(this);
        mNames = getResources().getStringArray(R.array.accent_names);
        mColors = getResources().getStringArray(R.array.accent_colors);
        setListAdapter(new ArrayAdapter<String>(this, R.layout.accent_row, R.id.label, mNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                View swatch = view.findViewById(R.id.swatch);
                String color = mColors[position];
                if ("wallpaper".equals(color)) {
                    swatch.setBackgroundColor(0xFF9E9E9E);
                } else {
                    swatch.setBackgroundColor(Color.parseColor(color));
                }
                TextView label = view.findViewById(R.id.label);
                boolean selected = selected(color);
                label.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        selected ? android.R.drawable.checkbox_on_background : 0, 0, 0, 0);
                return view;
            }
        });
        getListView().setOnItemClickListener((parent, view, position, id) -> apply(mColors[position]));
    }

    private boolean selected(String color) {
        String current = mAccents.current();
        if ("wallpaper".equals(color)) {
            return current == null;
        }
        if (current == null) {
            return false;
        }
        String hex = String.format("%06X", Color.parseColor(color) & 0xFFFFFF);
        return current.equals(hex);
    }

    private void apply(String color) {
        try {
            mAccents.apply("wallpaper".equals(color) ? null : color);
            Toast.makeText(this, R.string.accent_applied, Toast.LENGTH_SHORT).show();
            ((ArrayAdapter<?>) getListAdapter()).notifyDataSetChanged();
        } catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(this, R.string.accent_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
