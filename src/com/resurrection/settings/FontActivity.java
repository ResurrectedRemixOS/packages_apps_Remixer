package com.resurrection.settings;

import android.content.om.OverlayInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a Resurrection Remix font overlay.
 */
public class FontActivity extends CollapsingToolbarBaseActivity {
    private static final int GRID_SPAN = 2;

    private final List<String> mPackages = new ArrayList<>();
    private final List<String> mLabels = new ArrayList<>();
    private final List<Typeface> mTypefaces = new ArrayList<>();
    private FontController mFonts;
    private String mEnabled;
    private Adapter mAdapter;
    private TextView mPreviewName;
    private TextView mPreviewGlyphs;
    private TextView mPreviewText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remixer_fonts);
        mFonts = new FontController(this);
        add("android");
        for (OverlayInfo info : mFonts.fonts()) {
            add(info.packageName);
        }
        mEnabled = mFonts.enabledPackage();

        mPreviewName = findViewById(R.id.font_preview_name);
        mPreviewGlyphs = findViewById(R.id.font_preview_glyphs);
        mPreviewText = findViewById(R.id.font_preview_text);
        RecyclerView grid = findViewById(R.id.fonts_grid);
        grid.setLayoutManager(new GridLayoutManager(this, GRID_SPAN));
        mAdapter = new Adapter();
        grid.setAdapter(mAdapter);
        updatePreview();
    }

    private void add(String pkg) {
        mPackages.add(pkg);
        mLabels.add(mFonts.label(pkg));
        mTypefaces.add(mFonts.typeface(pkg));
    }

    private void updatePreview() {
        int index = Math.max(0, mPackages.indexOf(mEnabled));
        Typeface typeface = mTypefaces.get(index);
        mPreviewName.setText(mLabels.get(index));
        mPreviewGlyphs.setTypeface(typeface);
        mPreviewText.setTypeface(typeface);
    }

    private void apply(String pkg) {
        if (pkg.equals(mEnabled)) {
            return;
        }
        try {
            mFonts.apply(pkg);
        } catch (RemoteException | SecurityException e) {
            Toast.makeText(this, R.string.font_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        mEnabled = pkg;
        updatePreview();
        mAdapter.notifyDataSetChanged();
        Toast.makeText(this, R.string.font_applied, Toast.LENGTH_SHORT).show();
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView glyphs;
        final TextView name;
        final View check;

        Holder(View view) {
            super(view);
            card = view.findViewById(R.id.font_card);
            glyphs = view.findViewById(R.id.font_glyphs);
            name = view.findViewById(R.id.font_name);
            check = view.findViewById(R.id.font_check);
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return mPackages.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_font_item, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            String pkg = mPackages.get(position);
            boolean selected = pkg.equals(mEnabled);
            Typeface typeface = mTypefaces.get(position);
            holder.glyphs.setTypeface(typeface);
            holder.name.setTypeface(typeface);
            holder.name.setText(mLabels.get(position));
            holder.check.setVisibility(selected ? View.VISIBLE : View.GONE);
            holder.card.setStrokeColor(MaterialColors.getColor(holder.card,
                    androidx.appcompat.R.attr.colorPrimary));
            holder.card.setStrokeWidth(selected ? dp(3) : 0);
            holder.card.setSelected(selected);
            holder.card.setContentDescription(mLabels.get(position));
            holder.card.setOnClickListener(v -> apply(pkg));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
