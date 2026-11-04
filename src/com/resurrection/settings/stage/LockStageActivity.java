/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.stage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;

import com.android.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lock Stage: drag the lock screen's pieces around a grid, resize them by the corner, add the
 * neon complications, and save. SystemUI's LockStageSection reads the result from rr_lock_stage.
 */
public class LockStageActivity extends CollapsingToolbarBaseActivity {
    static final String KEY = "rr_lock_stage";
    /** The Nightstand stage, the same JSON; SystemUI's NightstandController switches to it. */
    static final String KEY_NIGHT = "rr_lock_stage_night";
    public static final String EXTRA_NIGHT = "night";
    static final int COLS = 6;
    static final int ROWS = 12;
    private static final String AUTHORITY = "com.android.systemui.clockfaces";
    private static final String[] IDS = {"clock", "glance", "weather", "nowbar", "shortcuts",
            "battery", "alarm", "toggles"};
    private static final int[] LABELS = {R.string.stage_tile_clock, R.string.stage_tile_glance,
            R.string.stage_tile_weather, R.string.stage_tile_nowbar, R.string.stage_tile_shortcuts,
            R.string.stage_tile_battery, R.string.stage_tile_alarm, R.string.stage_tile_toggles};

    static class Tile {
        String id;
        int x, y, w, h;
        boolean aod;

        Tile(String id, int x, int y, int w, int h, boolean aod) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.aod = aod;
        }
    }

    private Context mThemed;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** The tiles of the page being edited; one of [mPages] on the lock screen. */
    private List<Tile> mTiles = new ArrayList<>();
    private final List<List<Tile>> mPages = new ArrayList<>();
    private int mPage;
    private static final int MAX_PAGES = 3;
    private Tile mSelected;
    private StageView mStage;
    private LinearLayout mToolbar;
    private MaterialButton mAodButton;
    private ChipGroup mTray;
    private boolean mNight;
    private ChipGroup mTabs;
    private Bitmap mClock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.lock_stage_title);
        mThemed = new ContextThemeWrapper(this,
                com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight);
        NestedScrollView scroll = new NestedScrollView(mThemed);
        LinearLayout list = new LinearLayout(mThemed);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(list);
        setContentView(scroll);

        mNight = getIntent().getBooleanExtra(EXTRA_NIGHT, false);
        load();

        mTabs = new ChipGroup(mThemed);
        mTabs.setSingleSelection(true);
        mTabs.setSelectionRequired(true);
        list.addView(mTabs);
        fillTabs();

        // The stage keeps the phone's shape but leaves room under it for the tray and Save.
        int height = Math.min(getResources().getDisplayMetrics().widthPixels * 39 / 18,
                getResources().getDisplayMetrics().heightPixels / 2);
        int width = height * 18 / 39;
        mStage = new StageView(mThemed);
        list.addView(mStage, new LinearLayout.LayoutParams(width, height) {{
            gravity = android.view.Gravity.CENTER_HORIZONTAL;
        }});

        TextView hint = new TextView(mThemed);
        hint.setText(R.string.lock_stage_hint);
        hint.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        hint.setPadding(dp(4), dp(10), dp(4), dp(4));
        list.addView(hint);

        mToolbar = new LinearLayout(mThemed);
        mToolbar.setOrientation(LinearLayout.HORIZONTAL);
        mToolbar.addView(button(R.string.lock_stage_remove, v -> remove()), weight());
        mToolbar.addView(button(R.string.lock_stage_center, v -> center()), weight());
        mAodButton = button(R.string.lock_stage_aod_on, v -> toggleAod());
        mToolbar.addView(mAodButton, weight());
        mToolbar.setVisibility(View.GONE);
        list.addView(mToolbar);

        TextView add = new TextView(mThemed);
        add.setText(R.string.lock_stage_add);
        add.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        add.setPadding(dp(4), dp(16), dp(4), dp(4));
        list.addView(add);
        mTray = new ChipGroup(mThemed);
        mTray.setSingleLine(false);
        list.addView(mTray);
        fillTray();

        LinearLayout buttons = new LinearLayout(mThemed);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(16), 0, 0);
        buttons.addView(button(R.string.lock_stage_reset, v -> reset()), weight());
        MaterialButton save = new MaterialButton(mThemed);
        save.setText(R.string.lock_stage_save);
        save.setOnClickListener(v -> save());
        buttons.addView(save, weight());
        list.addView(buttons);

        renderClock(width);
    }

    private Chip tab(CharSequence title) {
        Chip chip = new Chip(mThemed);
        chip.setId(View.generateViewId());
        chip.setCheckable(true);
        chip.setText(title);
        return chip;
    }

    /** Page 1, 2, 3 and a plus on the lock screen, then Nightstand; hold a page to remove it. */
    private void fillTabs() {
        mTabs.setOnCheckedStateChangeListener(null);
        mTabs.removeAllViews();
        List<Chip> pageChips = new ArrayList<>();
        if (mNight) {
            // One way back; the pages are listed once the lock screen is on show again.
            pageChips.add(tab(getString(R.string.lock_stage_tab_lock)));
            mTabs.addView(pageChips.get(0));
        }
        for (int i = 0; !mNight && i < mPages.size(); i++) {
            Chip chip = tab(getString(R.string.lock_stage_tab_page, i + 1));
            final int index = i;
            chip.setOnLongClickListener(v -> {
                removePage(index);
                return true;
            });
            pageChips.add(chip);
            mTabs.addView(chip);
        }
        Chip add = !mNight && mPages.size() < MAX_PAGES ? tab("+") : null;
        if (add != null) mTabs.addView(add);
        Chip night = tab(getString(R.string.lock_stage_tab_night));
        mTabs.addView(night);
        mTabs.check(mNight ? night.getId() : pageChips.get(Math.min(mPage, pageChips.size() - 1)).getId());
        mTabs.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (add != null && id == add.getId()) {
                addPage();
                return;
            }
            boolean toNight = id == night.getId();
            int toPage = toNight || mNight ? 0 : indexOf(pageChips, id);
            if (toNight == mNight && toPage == mPage) return;
            boolean reload = mNight != toNight;
            mNight = toNight;
            if (reload) load();
            mPage = toPage;
            mTiles = mPages.get(mPage);
            select(null);
            if (reload) fillTabs();
            fillTray();
            mStage.invalidate();
        });
    }

    private static int indexOf(List<Chip> chips, int id) {
        for (int i = 0; i < chips.size(); i++) if (chips.get(i).getId() == id) return i;
        return 0;
    }

    private void addPage() {
        if (mNight || mPages.size() >= MAX_PAGES) return;
        List<Tile> page = new ArrayList<>();
        page.add(new Tile("clock", 0, 1, 6, 2, true));
        mPages.add(page);
        mPage = mPages.size() - 1;
        mTiles = page;
        select(null);
        fillTabs();
        fillTray();
        mStage.invalidate();
    }

    private void removePage(int index) {
        if (mNight || mPages.size() <= 1) return;
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(getString(R.string.lock_stage_remove_page, index + 1))
                .setPositiveButton(R.string.lock_stage_remove, (d, w) -> {
                    mPages.remove(index);
                    mPage = Math.min(mPage, mPages.size() - 1);
                    mTiles = mPages.get(mPage);
                    select(null);
                    fillTabs();
                    fillTray();
                    mStage.invalidate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Model

    private String key() {
        return mNight ? KEY_NIGHT : KEY;
    }

    private void load() {
        mPages.clear();
        mPage = 0;
        mTiles = new ArrayList<>();
        try {
            String json = Settings.System.getStringForUser(getContentResolver(), key(),
                    UserHandle.USER_CURRENT);
            if (!TextUtils.isEmpty(json)) {
                JSONObject spec = new JSONObject(json);
                mTiles.addAll(tiles(spec.getJSONArray("tiles")));
                JSONArray more = spec.optJSONArray("pages");
                for (int i = 0; !mNight && more != null && i < more.length()
                        && mPages.size() < MAX_PAGES - 1; i++) {
                    List<Tile> page = tiles(more.getJSONObject(i).getJSONArray("tiles"));
                    if (!page.isEmpty()) mPages.add(page);
                }
            }
        } catch (Exception e) {
            mTiles.clear();
            mPages.clear();
        }
        if (mTiles.isEmpty()) defaults();
        mPages.add(0, mTiles);
    }

    private static List<Tile> tiles(JSONArray array) throws Exception {
        List<Tile> tiles = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject t = array.getJSONObject(i);
            tiles.add(new Tile(t.getString("id"), t.optInt("x"), t.optInt("y"),
                    t.optInt("w", 1), t.optInt("h", 1), t.optBoolean("aod", true)));
        }
        return tiles;
    }

    /** Where the stock lock screen puts things, near enough; a bare bedside stage at night. */
    private void defaults() {
        mTiles.clear();
        if (mNight) {
            mTiles.add(new Tile("clock", 0, 2, 6, 3, true));
            mTiles.add(new Tile("alarm", 1, 6, 4, 1, true));
            mTiles.add(new Tile("battery", 2, 8, 2, 2, true));
            return;
        }
        mTiles.add(new Tile("clock", 0, 1, 6, 3, true));
        mTiles.add(new Tile("weather", 0, 4, 6, 1, false));
        mTiles.add(new Tile("nowbar", 1, 10, 4, 1, true));
        mTiles.add(new Tile("shortcuts", 0, 11, 6, 1, false));
    }

    private String json() throws Exception {
        JSONObject spec = new JSONObject().put("cols", COLS).put("rows", ROWS)
                .put("tiles", array(mPages.get(0)));
        JSONArray pages = new JSONArray();
        for (int i = 1; i < mPages.size(); i++) {
            pages.put(new JSONObject().put("tiles", array(mPages.get(i))));
        }
        if (pages.length() > 0) spec.put("pages", pages);
        return spec.toString();
    }

    private static JSONArray array(List<Tile> tiles) throws Exception {
        JSONArray array = new JSONArray();
        for (Tile t : tiles) {
            array.put(new JSONObject().put("id", t.id).put("x", t.x).put("y", t.y).put("w", t.w)
                    .put("h", t.h).put("aod", t.aod));
        }
        return array;
    }

    private void save() {
        try {
            Settings.System.putStringForUser(getContentResolver(), key(), json(),
                    UserHandle.USER_CURRENT);
            Toast.makeText(this, R.string.lock_stage_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.lock_stage_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void reset() {
        new MaterialAlertDialogBuilder(mThemed)
                .setTitle(R.string.lock_stage_reset)
                .setMessage(R.string.lock_stage_reset_summary)
                .setPositiveButton(R.string.lock_stage_reset, (d, w) -> {
                    Settings.System.putStringForUser(getContentResolver(), key(), "",
                            UserHandle.USER_CURRENT);
                    load();
                    select(null);
                    fillTabs();
                    fillTray();
                    mStage.invalidate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private Tile find(String id) {
        for (Tile t : mTiles) if (t.id.equals(id)) return t;
        return null;
    }

    private int label(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return LABELS[i];
        return R.string.stage_tile_clock;
    }

    private void fillTray() {
        mTray.removeAllViews();
        for (int i = 0; i < IDS.length; i++) {
            if (find(IDS[i]) != null) continue;
            final String id = IDS[i];
            Chip chip = new Chip(mThemed);
            chip.setText(LABELS[i]);
            chip.setOnClickListener(v -> add(id));
            mTray.addView(chip);
        }
        if (mTray.getChildCount() == 0) {
            Chip all = new Chip(mThemed);
            all.setText(R.string.lock_stage_all_placed);
            all.setEnabled(false);
            mTray.addView(all);
        }
    }

    private void add(String id) {
        int w = "battery".equals(id) ? 2 : "alarm".equals(id) ? 3 : 6;
        int h = "clock".equals(id) ? 3 : "battery".equals(id) ? 2 : 1;
        // The first free row from the top, else the bottom.
        int y = 0;
        rows:
        for (; y <= ROWS - h; y++) {
            for (Tile t : mTiles) {
                if (y < t.y + t.h && y + h > t.y) continue rows;
            }
            break;
        }
        if (y > ROWS - h) y = ROWS - h;
        Tile tile = new Tile(id, 0, y, w, h, !"toggles".equals(id) && !"shortcuts".equals(id));
        mTiles.add(tile);
        select(tile);
        fillTray();
        mStage.invalidate();
    }

    private void select(Tile tile) {
        mSelected = tile;
        mToolbar.setVisibility(tile == null ? View.GONE : View.VISIBLE);
        if (tile != null) {
            mAodButton.setText(tile.aod ? R.string.lock_stage_aod_on : R.string.lock_stage_aod_off);
        }
        mStage.invalidate();
    }

    private void remove() {
        if (mSelected == null) return;
        mTiles.remove(mSelected);
        select(null);
        fillTray();
    }

    private void center() {
        if (mSelected == null) return;
        mSelected.x = (COLS - mSelected.w) / 2;
        mStage.invalidate();
    }

    private void toggleAod() {
        if (mSelected == null) return;
        mSelected.aod = !mSelected.aod;
        select(mSelected);
    }

    /** The current clock face, drawn by SystemUI, for the clock tile. */
    private void renderClock(int width) {
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                int style = Settings.Secure.getIntForUser(getContentResolver(),
                        Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE, 0, UserHandle.USER_CURRENT);
                if (style != 0) {
                    Bundle extras = new Bundle();
                    extras.putInt("style", style);
                    extras.putInt("width", width);
                    extras.putFloat("height_fraction", 0.5f);
                    Bundle result = getContentResolver().call(AUTHORITY, "render", null, extras);
                    byte[] png = result != null ? result.getByteArray("png") : null;
                    if (png != null) {
                        Bitmap full = BitmapFactory.decodeByteArray(png, 0, png.length);
                        bitmap = crop(full);
                    }
                }
            } catch (Exception e) {
                bitmap = null;
            }
            final Bitmap done = bitmap;
            mHandler.post(() -> {
                mClock = done;
                mStage.invalidate();
            });
        }, "LockStageClock").start();
    }

    /** The drawn part of a transparent render. */
    private static Bitmap crop(Bitmap source) {
        int left = source.getWidth(), top = source.getHeight(), right = 0, bottom = 0;
        int[] row = new int[source.getWidth()];
        for (int y = 0; y < source.getHeight(); y++) {
            source.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int x = 0; x < row.length; x++) {
                if ((row[x] >>> 24) > 16) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right <= left || bottom <= top) return source;
        return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1);
    }

    // Widgets

    private MaterialButton button(int title, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(mThemed, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(title);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(dp(6));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** The phone: grid, tiles, drag and corner resize. */
    private class StageView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private final Rect mSrc = new Rect();
        private final int mAccent = MaterialColors.getColor(mThemed,
                androidx.appcompat.R.attr.colorPrimary, Color.RED);
        private Tile mTouched;
        private boolean mResizing;
        private float mDownX, mDownY;
        private int mStartX, mStartY, mStartW, mStartH;

        StageView(Context context) {
            super(context);
            mText.setTextAlign(Paint.Align.CENTER);
            mText.setColor(Color.WHITE);
        }

        private float cellW() {
            return getWidth() / (float) COLS;
        }

        private float cellH() {
            return getHeight() / (float) ROWS;
        }

        private void rectOf(Tile t) {
            float pad = dp(3);
            mRect.set(t.x * cellW() + pad, t.y * cellH() + pad, (t.x + t.w) * cellW() - pad,
                    (t.y + t.h) * cellH() - pad);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setShader(new RadialGradient(w / 2f, h * 1.1f, h * 0.9f,
                    new int[] {0xFF8A0024, 0xFF2A000A, 0xFF000000}, new float[] {0f, 0.45f, 0.8f},
                    Shader.TileMode.CLAMP));
            mRect.set(0, 0, w, h);
            canvas.drawRoundRect(mRect, dp(22), dp(22), mPaint);
            mPaint.setShader(null);

            // The status bar sits on row 0.
            mText.setTextSize(dp(9));
            mText.setColor(0xB3FFFFFF);
            mText.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("RR", dp(14), cellH() * 0.65f, mText);
            mText.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("74%", w - dp(14), cellH() * 0.65f, mText);
            mText.setTextAlign(Paint.Align.CENTER);

            mPaint.setColor(0x14FFFFFF);
            mPaint.setStrokeWidth(1f);
            for (int c = 1; c < COLS; c++) canvas.drawLine(c * cellW(), 0, c * cellW(), h, mPaint);
            for (int r = 1; r < ROWS; r++) canvas.drawLine(0, r * cellH(), w, r * cellH(), mPaint);

            for (Tile t : mTiles) drawTile(canvas, t, t == mSelected);
        }

        private void drawTile(Canvas canvas, Tile t, boolean selected) {
            rectOf(t);
            float radius = dp("nowbar".equals(t.id) ? 24 : 12);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(0x1FFFFFFF);
            canvas.drawRoundRect(mRect, radius, radius, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(selected ? dp(2) : 1f);
            mPaint.setColor(selected ? mAccent : 0x33FFFFFF);
            canvas.drawRoundRect(mRect, radius, radius, mPaint);
            mPaint.setStyle(Paint.Style.FILL);

            float cx = mRect.centerX();
            float cy = mRect.centerY();
            if ("clock".equals(t.id) && mClock != null) {
                mSrc.set(0, 0, mClock.getWidth(), mClock.getHeight());
                float scale = Math.min((mRect.width() - dp(8)) / mClock.getWidth(),
                        (mRect.height() - dp(8)) / mClock.getHeight());
                float dw = mClock.getWidth() * scale;
                float dh = mClock.getHeight() * scale;
                RectF dst = new RectF(cx - dw / 2f, mRect.top + dp(4), cx + dw / 2f,
                        mRect.top + dp(4) + dh);
                canvas.drawBitmap(mClock, mSrc, dst, mPaint);
            } else if ("shortcuts".equals(t.id)) {
                float r = Math.min(mRect.height(), dp(44)) / 2f - dp(2);
                mPaint.setColor(0x33FFFFFF);
                canvas.drawCircle(mRect.left + r + dp(6), cy, r, mPaint);
                canvas.drawCircle(mRect.right - r - dp(6), cy, r, mPaint);
            } else if ("battery".equals(t.id)) {
                float r = Math.min(mRect.width(), mRect.height()) / 2f - dp(8);
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(dp(4));
                mPaint.setColor(0x33FFFFFF);
                canvas.drawCircle(cx, cy, r, mPaint);
                mPaint.setColor(mAccent);
                RectF ring = new RectF(cx - r, cy - r, cx + r, cy + r);
                canvas.drawArc(ring, -90, 266, false, mPaint);
                mPaint.setStyle(Paint.Style.FILL);
            }
            mText.setColor(selected ? Color.WHITE : 0xCCFFFFFF);
            mText.setTextSize(dp("clock".equals(t.id) && mClock != null ? 9 : 11));
            String label = getString(label(t.id));
            float ty = "clock".equals(t.id) && mClock != null ? mRect.bottom - dp(6)
                    : cy + mText.getTextSize() / 3f;
            if (!"clock".equals(t.id) || mClock == null) canvas.drawText(label, cx, ty, mText);
            if (!t.aod) {
                mText.setTextSize(dp(8));
                mText.setColor(0x99FFFFFF);
                canvas.drawText(getString(R.string.lock_stage_not_on_aod), cx,
                        mRect.bottom - dp(5), mText);
            }
            if (selected) {
                mPaint.setColor(mAccent);
                float s = dp(7);
                canvas.drawRoundRect(mRect.right - s, mRect.bottom - s, mRect.right + s,
                        mRect.bottom + s, dp(2), dp(2), mPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    mTouched = null;
                    float x = event.getX();
                    float y = event.getY();
                    // The selected tile's corner first, then the topmost tile under the finger.
                    if (mSelected != null) {
                        rectOf(mSelected);
                        if (Math.abs(x - mRect.right) < dp(18) && Math.abs(y - mRect.bottom) < dp(18)) {
                            mTouched = mSelected;
                            mResizing = true;
                        }
                    }
                    if (mTouched == null) {
                        for (int i = mTiles.size() - 1; i >= 0; i--) {
                            rectOf(mTiles.get(i));
                            if (mRect.contains(x, y)) {
                                mTouched = mTiles.get(i);
                                mResizing = false;
                                break;
                            }
                        }
                    }
                    select(mTouched);
                    // Only a touched tile holds the page still; empty cells let it scroll.
                    if (mTouched == null) return true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    mDownX = x;
                    mDownY = y;
                    mStartX = mTouched.x;
                    mStartY = mTouched.y;
                    mStartW = mTouched.w;
                    mStartH = mTouched.h;
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (mTouched == null) return true;
                    int dx = Math.round((event.getX() - mDownX) / cellW());
                    int dy = Math.round((event.getY() - mDownY) / cellH());
                    if (mResizing) {
                        mTouched.w = Math.max(1, Math.min(COLS - mTouched.x, mStartW + dx));
                        mTouched.h = Math.max(1, Math.min(ROWS - mTouched.y, mStartH + dy));
                    } else {
                        mTouched.x = Math.max(0, Math.min(COLS - mTouched.w, mStartX + dx));
                        mTouched.y = Math.max(0, Math.min(ROWS - mTouched.h, mStartY + dy));
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    mTouched = null;
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
