package com.resurrection.settings;

import android.app.ListActivity;
import android.content.om.OverlayInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a Resurrection Remix font overlay.
 */
public class FontActivity extends ListActivity {

    private FontController mFonts;
    private final List<String> mPackages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFonts = new FontController(this);
        mPackages.add("android");
        for (OverlayInfo info : mFonts.fonts()) {
            mPackages.add(info.packageName);
        }
        setListAdapter(new ArrayAdapter<String>(this, R.layout.font_row, R.id.label, mPackages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                String pkg = getItem(position);
                TextView label = view.findViewById(R.id.label);
                label.setText(mFonts.label(pkg));
                label.setTypeface(mFonts.typeface(pkg));
                boolean enabled = pkg.equals(mFonts.enabledPackage());
                label.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        enabled ? android.R.drawable.checkbox_on_background : 0, 0, 0, 0);
                return view;
            }
        });
        getListView().setOnItemClickListener((parent, view, position, id) -> apply(mPackages.get(position)));
    }

    private void apply(String pkg) {
        try {
            mFonts.apply(pkg);
            Toast.makeText(this, R.string.font_applied, Toast.LENGTH_SHORT).show();
            ((ArrayAdapter<?>) getListAdapter()).notifyDataSetChanged();
        } catch (RemoteException | SecurityException e) {
            Toast.makeText(this, R.string.font_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
