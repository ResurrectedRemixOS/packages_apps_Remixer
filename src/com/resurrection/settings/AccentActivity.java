package com.resurrection.settings;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;


/**
 * Resurrection Remix accent seeds. Wallpaper restores Material You from the wallpaper.
 */
public class AccentActivity extends CollapsingToolbarBaseActivity {
    private static final String WALLPAPER = "wallpaper";
    private static final int GRID_SPAN = 4;
    private static final int[] WALLPAPER_SWEEP = {0xFFE02437, 0xFFFFC233, 0xFF3DDC84,
            0xFF3D8BFF, 0xFF8C5CFF, 0xFFE02437};

    private AccentController mAccents;
    private String[] mNames;
    private String[] mColors;
    private int mSelected;
    private Adapter mAdapter;
    private TextView mPreviewName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_accents);
        mAccents = new AccentController(this);
        mNames = getResources().getStringArray(R.array.accent_names);
        mColors = getResources().getStringArray(R.array.accent_colors);
        mSelected = findSelected();

        mPreviewName = findViewById(R.id.accent_preview_name);
        RecyclerView grid = findViewById(R.id.accents_grid);
        grid.setLayoutManager(new GridLayoutManager(this, GRID_SPAN));
        mAdapter = new Adapter();
        grid.setAdapter(mAdapter);
        updatePreview();
    }

    private int findSelected() {
        String current = mAccents.current();
        for (int i = 0; i < mColors.length; i++) {
            String color = mColors[i];
            if (WALLPAPER.equals(color)) {
                if (current == null) {
                    return i;
                }
            } else if (current != null
                    && current.equals(String.format("%06X", Color.parseColor(color) & 0xFFFFFF))) {
                return i;
            }
        }
        return -1;
    }

    private void updatePreview() {
        mPreviewName.setText(mSelected >= 0
                ? getString(R.string.accent_current, mNames[mSelected])
                : getString(R.string.accent_current_custom));
    }

    private void apply(int position) {
        if (position == mSelected) {
            return;
        }
        String color = mColors[position];
        try {
            mAccents.apply(WALLPAPER.equals(color) ? null : color);
        } catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(this, R.string.accent_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        int old = mSelected;
        mSelected = position;
        if (old >= 0) {
            mAdapter.notifyItemChanged(old);
        }
        mAdapter.notifyItemChanged(position);
        updatePreview();
        Toast.makeText(this, R.string.accent_applied, Toast.LENGTH_SHORT).show();
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final View item;
        final View ring;
        final ImageView swatch;
        final TextView name;

        Holder(View view) {
            super(view);
            item = view.findViewById(R.id.accent_item);
            ring = view.findViewById(R.id.accent_ring);
            swatch = view.findViewById(R.id.accent_swatch);
            name = view.findViewById(R.id.accent_name);
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return mColors.length;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_accent_item, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            String color = mColors[position];
            boolean selected = position == mSelected;
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            int value;
            if (WALLPAPER.equals(color)) {
                dot.setGradientType(GradientDrawable.SWEEP_GRADIENT);
                dot.setColors(WALLPAPER_SWEEP);
                value = Color.GRAY;
            } else {
                value = Color.parseColor(color);
                dot.setColor(value);
                if (Color.luminance(value) > 0.85f) {
                    dot.setStroke(dp(1), 0x33000000);
                }
            }
            holder.swatch.setBackground(dot);
            holder.swatch.setImageResource(selected ? R.drawable.remixer_ic_pulse_check : 0);
            holder.swatch.setImageTintList(ColorStateList.valueOf(
                    Color.luminance(value) > 0.6f ? Color.BLACK : Color.WHITE));
            holder.ring.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            holder.name.setText(mNames[position]);
            holder.item.setSelected(selected);
            holder.item.setContentDescription(mNames[position]);
            holder.item.setOnClickListener(v -> apply(holder.getBindingAdapterPosition()));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
