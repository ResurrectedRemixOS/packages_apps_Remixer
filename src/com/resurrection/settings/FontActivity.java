package com.resurrection.settings;

import android.animation.LayoutTransition;
import android.content.om.OverlayInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks a Resurrected Remix font overlay. Tapping a font previews it in the hero; Apply switches
 * the system font, so browsing does not restart the UI on every tap.
 */
public class FontActivity extends CollapsingToolbarBaseActivity {
    private static final int GRID_SPAN = 2;
    private static final int CLOCK_SP = 76;
    private static final int CLOCK_COMPACT_SP = 52;
    /** Scroll distance after which the pinned preview drops its date and notification. */
    private static final int COMPACT_AFTER_DP = 48;
    private static final String PREFIX = "com.resurrection.theme.font.";

    private static final int ALL = 0;
    private static final int SANS = 1;
    private static final int SERIF = 2;
    private static final int DISPLAY = 3;
    private static final int SCRIPT = 4;
    private static final int MONO = 5;
    private static final int[] FILTER_LABELS = {R.string.font_filter_all,
            R.string.font_filter_sans, R.string.font_filter_serif, R.string.font_filter_display,
            R.string.font_filter_script, R.string.font_filter_mono};

    /** Category of each font overlay, by package suffix. Unlisted fonts only show under All. */
    private static final Map<String, Integer> CATEGORIES = Map.ofEntries(
            Map.entry("android", SANS),
            Map.entry("antipastopro", SANS),
            Map.entry("bariol", SANS),
            Map.entry("cagliostro", SANS),
            Map.entry("cocon", SANS),
            Map.entry("comfortaa", SANS),
            Map.entry("evolvesans", SANS),
            Map.entry("exotwo", SANS),
            Map.entry("googlesans", SANS),
            Map.entry("lgsmartgothic", SANS),
            Map.entry("linotte", SANS),
            Map.entry("nokiapure", SANS),
            Map.entry("nunito", SANS),
            Map.entry("oneplusslate", SANS),
            Map.entry("samsungone", SANS),
            Map.entry("shamshung", SANS),
            Map.entry("ubuntu", SANS),
            Map.entry("amarante", SERIF),
            Map.entry("quando", SERIF),
            Map.entry("rosemary", SERIF),
            Map.entry("aclonica", DISPLAY),
            Map.entry("burnstown", DISPLAY),
            Map.entry("dumbledor", DISPLAY),
            Map.entry("fifa2018", DISPLAY),
            Map.entry("fucek", DISPLAY),
            Map.entry("gobold", DISPLAY),
            Map.entry("goboldbold", DISPLAY),
            Map.entry("goboldbolditalic", DISPLAY),
            Map.entry("gobolditalic", DISPLAY),
            Map.entry("goboldthinlight", DISPLAY),
            Map.entry("goboldthinlightitalic", DISPLAY),
            Map.entry("lemonmilk", DISPLAY),
            Map.entry("mexcellent", DISPLAY),
            Map.entry("neoneon", DISPLAY),
            Map.entry("oduda", DISPLAY),
            Map.entry("phantombold", DISPLAY),
            Map.entry("reemkufi", DISPLAY),
            Map.entry("roadrage", DISPLAY),
            Map.entry("snowstorm", DISPLAY),
            Map.entry("storopia", DISPLAY),
            Map.entry("surfer", DISPLAY),
            Map.entry("comicsans", SCRIPT),
            Map.entry("coolstory", SCRIPT),
            Map.entry("grandhotel", SCRIPT),
            Map.entry("redressed", SCRIPT),
            Map.entry("simpleday", SCRIPT),
            Map.entry("sonysketch", SCRIPT),
            Map.entry("vcrosdmono", MONO));

    private final List<String> mPackages = new ArrayList<>();
    private final List<String> mLabels = new ArrayList<>();
    private final List<Typeface> mTypefaces = new ArrayList<>();
    private final List<Integer> mCategories = new ArrayList<>();
    /** Indices into the lists above that the current filter shows. */
    private final List<Integer> mVisible = new ArrayList<>();
    private FontController mFonts;
    private String mEnabled;
    private int mPreview;
    private String mSample;
    private Adapter mAdapter;

    private TextClock mClock;
    private TextView mDate;
    private TextView mTitle;
    private TextView mBody;
    private TextView mState;
    private TextView mName;
    private MaterialButton mApply;
    private View mNotification;
    private boolean mCompact;

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
        mPreview = Math.max(0, mPackages.indexOf(mEnabled));
        mSample = getString(R.string.font_sample);

        mClock = findViewById(R.id.font_preview_clock);
        mDate = findViewById(R.id.font_preview_date);
        mTitle = findViewById(R.id.font_preview_title);
        mBody = findViewById(R.id.font_preview_body);
        mState = findViewById(R.id.font_preview_state);
        mName = findViewById(R.id.font_preview_name);
        mApply = findViewById(R.id.font_apply);
        mNotification = findViewById(R.id.font_preview_notification);
        mDate.setText(DateFormat.format(DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "EEEEMMMMd"), new Date()));

        RecyclerView grid = findViewById(R.id.fonts_grid);
        grid.setLayoutManager(new GridLayoutManager(this, GRID_SPAN));
        mAdapter = new Adapter();
        grid.setAdapter(mAdapter);
        setupHero();
        setupScroll();
        setupText();
        setupFilters();
        filter(ALL);
        updatePreview();
    }

    private void add(String pkg) {
        mPackages.add(pkg);
        mLabels.add(mFonts.label(pkg));
        mTypefaces.add(mFonts.typeface(pkg));
        String key = pkg.startsWith(PREFIX) ? pkg.substring(PREFIX.length()) : pkg;
        mCategories.add(CATEGORIES.getOrDefault(key, ALL));
    }

    private void setupHero() {
        findViewById(R.id.font_preview_notification).setBackground(round(0x24FFFFFF, 22));
        GradientDrawable chip = new GradientDrawable();
        chip.setShape(GradientDrawable.OVAL);
        chip.setColor(getColor(android.R.color.system_accent1_600));
        ((ImageView) findViewById(R.id.font_preview_app)).setBackground(chip);
        mApply.setOnClickListener(v -> apply());
    }

    /** The preview stays pinned; scrolling the list shrinks it to the clock and Apply row. */
    private void setupScroll() {
        ((ViewGroup) findViewById(R.id.font_root)).getLayoutTransition()
                .enableTransitionType(LayoutTransition.CHANGING);
        ((ViewGroup) findViewById(R.id.font_hero)).getLayoutTransition()
                .enableTransitionType(LayoutTransition.CHANGING);
        NestedScrollView scroll = findViewById(R.id.font_scroll);
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, x, y, oldX, oldY) -> {
                    if (y > dp(COMPACT_AFTER_DP)) {
                        setCompact(true);
                    } else if (y == 0) {
                        setCompact(false);
                    }
                });
    }

    private void setCompact(boolean compact) {
        if (compact == mCompact) {
            return;
        }
        mCompact = compact;
        mDate.setVisibility(compact ? View.GONE : View.VISIBLE);
        mNotification.setVisibility(compact ? View.GONE : View.VISIBLE);
        mClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? CLOCK_COMPACT_SP : CLOCK_SP);
    }

    private void setupText() {
        View box = findViewById(R.id.font_text_box);
        box.setBackground(round(MaterialColors.getColor(box,
                com.google.android.material.R.attr.colorSurfaceContainerHigh), 27));
        ((ImageView) findViewById(R.id.font_text_icon)).setImageTintList(ColorStateList.valueOf(
                MaterialColors.getColor(box,
                        com.google.android.material.R.attr.colorOnSurfaceVariant)));
        EditText text = findViewById(R.id.font_text);
        text.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mSample = s.toString().trim().isEmpty()
                        ? getString(R.string.font_sample) : s.toString();
                mAdapter.notifyItemRangeChanged(0, mVisible.size());
            }
        });
    }

    private void setupFilters() {
        ChipGroup group = findViewById(R.id.font_filters);
        LayoutInflater inflater = LayoutInflater.from(group.getContext());
        for (int filter = ALL; filter <= MONO; filter++) {
            if (filter != ALL && !mCategories.contains(filter)) {
                continue;
            }
            Chip chip = (Chip) inflater.inflate(R.layout.remixer_font_chip, group, false);
            chip.setText(FILTER_LABELS[filter]);
            chip.setId(View.generateViewId());
            chip.setTag(filter);
            group.addView(chip);
            if (filter == ALL) {
                group.check(chip.getId());
            }
        }
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (!ids.isEmpty()) {
                filter((Integer) g.findViewById(ids.get(0)).getTag());
            }
        });
    }

    private void filter(int filter) {
        mVisible.clear();
        for (int i = 0; i < mPackages.size(); i++) {
            if (filter == ALL || mCategories.get(i) == filter) {
                mVisible.add(i);
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    private void updatePreview() {
        Typeface typeface = mTypefaces.get(mPreview);
        mClock.setTypeface(typeface);
        mDate.setTypeface(typeface);
        mTitle.setTypeface(Typeface.create(typeface, Typeface.BOLD));
        mBody.setTypeface(typeface);
        mName.setText(mLabels.get(mPreview));
        boolean current = mPackages.get(mPreview).equals(mEnabled);
        mState.setText(current ? R.string.font_current : R.string.font_previewing);
        mApply.setEnabled(!current);
        mApply.setText(current ? R.string.font_apply_done : R.string.font_apply);
        mApply.setBackgroundTintList(ColorStateList.valueOf(current ? 0x2EFFFFFF : 0xFFFFFFFF));
        mApply.setTextColor(current ? 0xCCFFFFFF : 0xFF141416);
    }

    private void preview(int index) {
        if (index == mPreview) {
            return;
        }
        int old = mPreview;
        mPreview = index;
        notifyFont(old);
        notifyFont(index);
        updatePreview();
    }

    private void notifyFont(int index) {
        int position = mVisible.indexOf(index);
        if (position >= 0) {
            mAdapter.notifyItemChanged(position);
        }
    }

    private void apply() {
        String pkg = mPackages.get(mPreview);
        if (pkg.equals(mEnabled)) {
            return;
        }
        try {
            mFonts.apply(pkg);
        } catch (RemoteException | SecurityException e) {
            Toast.makeText(this, R.string.font_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        int old = mPackages.indexOf(mEnabled);
        mEnabled = pkg;
        notifyFont(old);
        notifyFont(mPreview);
        updatePreview();
        Toast.makeText(this, R.string.font_applied, Toast.LENGTH_SHORT).show();
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView sample;
        final TextView name;
        final TextView badge;

        Holder(View view) {
            super(view);
            card = view.findViewById(R.id.font_card);
            sample = view.findViewById(R.id.font_sample);
            name = view.findViewById(R.id.font_name);
            badge = view.findViewById(R.id.font_badge);
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return mVisible.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            Holder holder = new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.remixer_font_item, parent, false));
            int primary = MaterialColors.getColor(holder.card,
                    androidx.appcompat.R.attr.colorPrimary);
            holder.badge.setBackground(round(primary, 10));
            holder.badge.setTextColor(MaterialColors.getColor(holder.card,
                    com.google.android.material.R.attr.colorOnPrimary));
            holder.card.setStrokeColor(primary);
            return holder;
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            int index = mVisible.get(position);
            boolean previewing = index == mPreview;
            holder.sample.setTypeface(mTypefaces.get(index));
            holder.sample.setText(mSample);
            holder.name.setText(mLabels.get(index));
            holder.badge.setVisibility(
                    mPackages.get(index).equals(mEnabled) ? View.VISIBLE : View.GONE);
            holder.card.setStrokeWidth(previewing ? dp(2) : 0);
            holder.card.setCardBackgroundColor(MaterialColors.getColor(holder.card, previewing
                    ? com.google.android.material.R.attr.colorSecondaryContainer
                    : com.google.android.material.R.attr.colorSurfaceContainerHigh));
            holder.card.setSelected(previewing);
            holder.card.setContentDescription(mLabels.get(index));
            holder.card.setOnClickListener(v -> preview(index));
        }
    }
}
