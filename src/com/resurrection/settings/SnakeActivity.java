/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.android.settings.R;

import java.util.Locale;
import java.util.Random;

/**
 * Snake, the old phone game, behind Ring sync: tap the RR mark there five times. A drawn game
 * screen over a keypad: 2 4 6 8 steer, 5 picks and pauses, C goes to the menu, and a swipe on the
 * screen steers too. Levels set the speed, every fifth bite brings out a bonus bug for a while,
 * and each level and maze keeps its own top score.
 */
public class SnakeActivity extends Activity {
    private static final String PREFS = "snake";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_MAZE = "maze";
    private static final String KEY_SCREEN = "screen";
    // Ring sync's, so one switch covers both games
    private static final String RING_SYNC_PREFS = "ring_sync";
    private static final String KEY_VIBRATE = "vibrate";

    private static final int COLS = 20;
    private static final int ROWS = 11;
    // The size everything is laid out on, in dp, scaled to fit the screen
    private static final float DESIGN_W = 411f;
    private static final float DESIGN_H = 900f;
    // How far a finger goes before it counts as a swipe, in dp
    private static final float SWIPE_DP = 24f;

    private static final int MENU = 0;
    private static final int PLAY = 1;
    private static final int PAUSE = 2;
    private static final int OVER = 3;
    private static final int MENU_ROWS = 5;

    // The screens: a green backlight, a blue one, and the accent on black
    private static final int CLASSIC = 0;
    private static final int NIGHT = 2;
    private static final int[] LCD_BG = {0xFFC7F0D8, 0xFFA6C8EC, 0xFF0B0D10};
    private static final int[] LCD_INK = {0xFF43523D, 0xFF1B2B46, 0};
    private static final int[] SCREEN_NAMES = {R.string.snake_screen_classic,
            R.string.snake_screen_blue, R.string.snake_screen_night};
    private static final int[] MAZE_NAMES = {R.string.snake_maze_none, R.string.snake_maze_box,
            R.string.snake_maze_tunnel};

    // The keypad, left to right and top to bottom
    private static final int KEY_C = -1;
    private static final int KEY_HASH = -2;
    private static final int[] KEYS = {1, 2, 3, 4, 5, 6, 7, 8, 9, KEY_C, 0, KEY_HASH};
    private static final String[] KEY_LABELS =
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "#"};

    // Each maze's walls: none, a box round the edge, and two bars across the middle
    private static final boolean[][] WALLS = new boolean[3][COLS * ROWS];
    static {
        for (int x = 0; x < COLS; x++) {
            WALLS[1][x] = true;
            WALLS[1][(ROWS - 1) * COLS + x] = true;
        }
        for (int y = 0; y < ROWS; y++) {
            WALLS[1][y * COLS] = true;
            WALLS[1][y * COLS + COLS - 1] = true;
        }
        for (int x = 4; x <= 15; x++) {
            WALLS[2][3 * COLS + x] = true;
            WALLS[2][7 * COLS + x] = true;
        }
    }

    // The bonus bug, 11 by 5 pixels, its eyes left open
    private static final String[] BUG = {
        "..#######..",
        ".#########.",
        "###.###.###",
        ".#########.",
        "..#..#..#..",
    };
    // The little snake by the name in the menu: x, y, width, height on an 18 by 8 grid
    private static final float[][] MENU_SNAKE = {
        {0, 5, 3, 3}, {4, 5, 3, 3}, {8, 5, 3, 3}, {8, 1, 3, 3}, {12, 1, 3, 3}, {15, 2, 1, 1},
    };

    private GameView mGame;
    private final OnBackInvokedCallback mBack = this::back;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGame = new GameView(this);
        setContentView(mGame);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsController insets = getWindow().getInsetsController();
        if (insets != null) {
            insets.hide(WindowInsets.Type.systemBars());
            insets.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mBack);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGame.pause();
    }

    @Override
    protected void onDestroy() {
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mBack);
        super.onDestroy();
    }

    /** Back pauses a game under way, and otherwise goes back to Ring sync. */
    private void back() {
        if (!mGame.pause()) {
            finish();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private final class GameView extends View {
        private final float mDensity = getResources().getDisplayMetrics().density;
        private final int mAccent = getColor(android.R.color.system_accent1_200);
        private final SharedPreferences mPrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        private final Drawable mMark = getDrawable(R.drawable.remixer_rr_mark).mutate();
        private final Typeface mPixel = font(R.font.rr_silkscreen);
        private final Typeface mPixelBold = font(R.font.rr_silkscreen_bold);
        private final Random mRandom = new Random();
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mBg = new Paint();
        private final Path mPath = new Path();
        private final RectF mRect = new RectF();
        private final RectF mBezel = new RectF();
        private final RectF mLcd = new RectF();
        private final RectF mBackButton = new RectF();
        private final RectF[] mKeys = new RectF[KEYS.length];
        private final Shader[] mKeyShaders = new Shader[KEYS.length];
        private final String[] mKeySubs;
        private Shader mBezelShader;
        private Shader mLcdShade;
        private DashPathEffect mDash;
        // dp of the layout to pixels, and where the layout starts
        private float mU = 1f;
        private float mOx;
        private float mOy;
        // Night's screen glows: text and pixels drawn while this is set get a halo
        private boolean mGlow;
        private int mGlowColor;

        private int mScreen = MENU;
        private int mSel;
        private int mLevel;
        private int mMaze;
        private int mTheme;
        // The snake's cells, head first, each y * COLS + x
        private final int[] mBody = new int[COLS * ROWS];
        private int mLength;
        // The way it last moved, and the way it moves next
        private int mDx;
        private int mDy;
        private int mNextDx;
        private int mNextDy;
        private int mFood = -1;
        // The bonus bug's left cell, or -1, and the moves it has left
        private int mBonus = -1;
        private int mBonusT;
        private int mScore;
        private int mEaten;
        private boolean mNewBest;

        private int mPressedKey = -1;
        private boolean mOnBack;
        private boolean mTracking;
        private boolean mSwiped;
        private float mSwipeX;
        private float mSwipeY;

        private final Runnable mTick = new Runnable() {
            @Override
            public void run() {
                if (mScreen != PLAY) return;
                step();
                if (mScreen == PLAY) {
                    postDelayed(this, delay());
                }
            }
        };

        GameView(Context context) {
            super(context);
            mLevel = clamp(mPrefs.getInt(KEY_LEVEL, 5), 1, 9);
            mMaze = clamp(mPrefs.getInt(KEY_MAZE, 0), 0, MAZE_NAMES.length - 1);
            mTheme = clamp(mPrefs.getInt(KEY_SCREEN, CLASSIC), CLASSIC, NIGHT);
            mKeySubs = new String[] {"", upper(R.string.snake_key_up), "",
                    upper(R.string.snake_key_left), upper(R.string.snake_key_ok),
                    upper(R.string.snake_key_right), "", upper(R.string.snake_key_down), "",
                    upper(R.string.snake_menu), "", ""};
            for (int i = 0; i < mKeys.length; i++) {
                mKeys[i] = new RectF();
            }
            mLine.setStyle(Paint.Style.STROKE);
            mLine.setStrokeCap(Paint.Cap.ROUND);
            mMark.setTint(0xFF5A5C66);
            setHapticFeedbackEnabled(true);
            setContentDescription(getString(R.string.snake_title));
            reset();
        }

        private Typeface font(int id) {
            try {
                return getResources().getFont(id);
            } catch (Resources.NotFoundException e) {
                return Typeface.MONOSPACE;
            }
        }

        private String upper(int id) {
            return getString(id).toUpperCase(getResources().getConfiguration().getLocales().get(0));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            mU = Math.min(w / DESIGN_W, h / DESIGN_H);
            mOx = (w - DESIGN_W * mU) / 2f;
            mOy = (h - DESIGN_H * mU) / 2f;
            mBg.setShader(new RadialGradient(w / 2f, h * 0.3f, Math.max(w, h) * 0.7f,
                    0xFF1A1C22, 0xFF08090B, Shader.TileMode.CLAMP));
            box(mBezel, 16, 56, 379, 290);
            mBezelShader = new LinearGradient(0, mBezel.top, 0, mBezel.bottom,
                    0xFF24262C, 0xFF17181C, Shader.TileMode.CLAMP);
            box(mLcd, 32, 78, 347, 226);
            mLcdShade = new LinearGradient(0, mLcd.top, 0, mLcd.top + s(12),
                    0x2E000000, 0, Shader.TileMode.CLAMP);
            float keyW = (379 - 24) / 3f;
            for (int i = 0; i < mKeys.length; i++) {
                box(mKeys[i], 16 + (i % 3) * (keyW + 12), 404 + (i / 3) * 84, keyW, 72);
                mKeyShaders[i] = new LinearGradient(0, mKeys[i].top, 0, mKeys[i].bottom,
                        0xFF2A2C33, 0xFF1E2025, Shader.TileMode.CLAMP);
            }
            box(mBackButton, 95.5f, 806, 220, 48);
            mDash = new DashPathEffect(new float[] {s(4), s(4)}, 0);
        }

        private void box(RectF rect, float x, float y, float w, float h) {
            rect.set(px(x), py(y), px(x + w), py(y + h));
        }

        private float px(float x) {
            return mOx + x * mU;
        }

        private float py(float y) {
            return mOy + y * mU;
        }

        private float s(float value) {
            return value * mU;
        }

        /** Within the game screen. */
        private float lx(float x) {
            return mLcd.left + x * mU;
        }

        private float ly(float y) {
            return mLcd.top + y * mU;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(mTick);
        }

        // The game

        private void reset() {
            mLength = 5;
            for (int i = 0; i < mLength; i++) {
                mBody[i] = 5 * COLS + 8 - i;
            }
            mDx = mNextDx = 1;
            mDy = mNextDy = 0;
            mScore = 0;
            mEaten = 0;
            mNewBest = false;
            mFood = -1;
            mBonus = -1;
            mBonusT = 0;
            mFood = freeCell(1);
        }

        /** ms between moves: from 330 at level 1 to 90 at level 9. */
        private int delay() {
            return 360 - mLevel * 30;
        }

        private void start() {
            reset();
            mScreen = PLAY;
            go();
        }

        /** Sets the game going, after a start or a pause. */
        private void go() {
            removeCallbacks(mTick);
            postDelayed(mTick, delay());
            setKeepScreenOn(true);
            invalidate();
        }

        /** Pauses a game under way; false when there's none. */
        boolean pause() {
            if (mScreen != PLAY) return false;
            mScreen = PAUSE;
            stop();
            return true;
        }

        private void stop() {
            removeCallbacks(mTick);
            setKeepScreenOn(false);
            invalidate();
        }

        private void toMenu() {
            mScreen = MENU;
            stop();
        }

        private void step() {
            int head = mBody[0];
            int x = (head % COLS + mNextDx + COLS) % COLS;
            int y = (head / COLS + mNextDy + ROWS) % ROWS;
            mDx = mNextDx;
            mDy = mNextDy;
            int cell = y * COLS + x;
            boolean grow = cell == mFood;
            // The tail moves on, unless the snake grows
            int keep = grow ? mLength : mLength - 1;
            boolean crash = WALLS[mMaze][cell];
            for (int i = 0; i < keep && !crash; i++) {
                crash = mBody[i] == cell;
            }
            if (crash) {
                over();
                return;
            }
            System.arraycopy(mBody, 0, mBody, 1, keep);
            mBody[0] = cell;
            mLength = keep + 1;
            if (grow) {
                mScore += mLevel;
                mEaten++;
                buzz(HapticFeedbackConstants.CLOCK_TICK);
                mFood = freeCell(1);
                if (mFood < 0) {
                    // Nowhere left to put food: the snake fills the screen
                    over();
                    return;
                }
                if (mEaten % 5 == 0 && mBonus < 0) {
                    mBonus = freeCell(2);
                    mBonusT = mBonus < 0 ? 0 : 20;
                }
            }
            if (mBonus >= 0) {
                if (cell == mBonus || cell == mBonus + 1) {
                    // The sooner, the more it's worth
                    mScore += mLevel * 2 + mBonusT * 2;
                    mBonus = -1;
                    buzz(HapticFeedbackConstants.CONFIRM);
                } else if (--mBonusT <= 0) {
                    mBonus = -1;
                }
            }
            invalidate();
        }

        private void over() {
            mScreen = OVER;
            mNewBest = mScore > best();
            if (mNewBest) {
                mPrefs.edit().putInt(bestKey(), mScore).apply();
            }
            buzz(HapticFeedbackConstants.REJECT);
            stop();
        }

        private String bestKey() {
            return "best_" + mLevel + "_" + mMaze;
        }

        private int best() {
            return mPrefs.getInt(bestKey(), 0);
        }

        /** A random cell where something [width] cells wide fits, or -1 when there's none. */
        private int freeCell(int width) {
            boolean[] busy = WALLS[mMaze].clone();
            for (int i = 0; i < mLength; i++) {
                busy[mBody[i]] = true;
            }
            if (mFood >= 0) busy[mFood] = true;
            if (mBonus >= 0) {
                busy[mBonus] = true;
                busy[mBonus + 1] = true;
            }
            int[] free = new int[busy.length];
            int count = 0;
            for (int cell = 0; cell < busy.length; cell++) {
                if (cell % COLS + width > COLS) continue;
                boolean fits = true;
                for (int k = 0; k < width; k++) {
                    fits &= !busy[cell + k];
                }
                if (fits) free[count++] = cell;
            }
            return count == 0 ? -1 : free[mRandom.nextInt(count)];
        }

        private void press(int key) {
            switch (mScreen) {
                case PLAY:
                    if (key == 2) steer(0, -1);
                    else if (key == 8) steer(0, 1);
                    else if (key == 4) steer(-1, 0);
                    else if (key == 6) steer(1, 0);
                    else if (key == 5) pause();
                    else if (key == KEY_C) toMenu();
                    break;
                case PAUSE:
                    if (key == 5) {
                        mScreen = PLAY;
                        go();
                    } else if (key == KEY_C) {
                        toMenu();
                    }
                    break;
                case OVER:
                    if (key == 5) {
                        start();
                    } else if (key == KEY_C) {
                        mSel = 0;
                        toMenu();
                    }
                    break;
                default:
                    if (key == 2) mSel = (mSel + MENU_ROWS - 1) % MENU_ROWS;
                    else if (key == 8) mSel = (mSel + 1) % MENU_ROWS;
                    else if (key == 4) turn(-1);
                    else if (key == 6) turn(1);
                    else if (key == 5) {
                        if (mSel == 0) {
                            start();
                            return;
                        }
                        turn(1);
                    }
                    invalidate();
            }
        }

        private void steer(int dx, int dy) {
            // Never straight back into itself
            if (dx == -mDx && dy == -mDy) return;
            mNextDx = dx;
            mNextDy = dy;
        }

        /** Changes the menu row's value, one step back or on. */
        private void turn(int step) {
            if (mSel == 1) {
                mLevel = (mLevel - 1 + step + 9) % 9 + 1;
                mPrefs.edit().putInt(KEY_LEVEL, mLevel).apply();
            } else if (mSel == 2) {
                mMaze = (mMaze + step + MAZE_NAMES.length) % MAZE_NAMES.length;
                mPrefs.edit().putInt(KEY_MAZE, mMaze).apply();
            } else if (mSel == 3) {
                mTheme = (mTheme + step + LCD_BG.length) % LCD_BG.length;
                mPrefs.edit().putInt(KEY_SCREEN, mTheme).apply();
            }
        }

        private void buzz(int feedback) {
            if (getSharedPreferences(RING_SYNC_PREFS, MODE_PRIVATE)
                    .getBoolean(KEY_VIBRATE, true)) {
                performHapticFeedback(feedback);
            }
        }

        // Touch

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mPressedKey = keyAt(x, y);
                    mOnBack = mPressedKey < 0 && mBackButton.contains(x, y);
                    mTracking = mPressedKey < 0 && !mOnBack;
                    mSwiped = false;
                    mSwipeX = x;
                    mSwipeY = y;
                    if (mPressedKey >= 0) {
                        buzz(HapticFeedbackConstants.KEYBOARD_TAP);
                        press(KEYS[mPressedKey]);
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mTracking) {
                        float dx = x - mSwipeX;
                        float dy = y - mSwipeY;
                        float min = SWIPE_DP * mDensity;
                        if (Math.abs(dx) > min || Math.abs(dy) > min) {
                            press(Math.abs(dx) > Math.abs(dy)
                                    ? (dx > 0 ? 6 : 4) : (dy > 0 ? 8 : 2));
                            // Each further stretch of the same swipe steers again
                            mSwipeX = x;
                            mSwipeY = y;
                            mSwiped = true;
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (mOnBack && mBackButton.contains(x, y)) {
                        finish();
                    } else if (mTracking && !mSwiped) {
                        tap(x, y);
                    }
                    release();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    release();
                    break;
                default:
                    break;
            }
            return true;
        }

        private void release() {
            if (mPressedKey >= 0) {
                mPressedKey = -1;
                invalidate();
            }
            mOnBack = false;
            mTracking = false;
        }

        private int keyAt(float x, float y) {
            for (int i = 0; i < mKeys.length; i++) {
                if (mKeys[i].contains(x, y)) return i;
            }
            return -1;
        }

        /** A tap on the game screen works as 5 does: in the menu, on the row it lands on. */
        private void tap(float x, float y) {
            if (!mLcd.contains(x, y) || mScreen == PLAY) return;
            if (mScreen == MENU) {
                int row = (int) Math.floor((y - ly(50)) / s(33));
                if (row < 0 || row >= MENU_ROWS) return;
                mSel = row;
            }
            press(5);
        }

        // Drawing

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), mBg);
            int best = best();
            text(canvas, getString(R.string.snake_info, mLevel, getString(MAZE_NAMES[mMaze])),
                    px(24), py(37), 14, Typeface.DEFAULT, 0xFFA9ACA5, Paint.Align.LEFT, 0);
            text(canvas, getString(R.string.snake_top, best), px(387), py(37), 14,
                    Typeface.DEFAULT, 0xFFA9ACA5, Paint.Align.RIGHT, 0);

            // The case, the screen in it and the mark under the screen
            mFill.setShader(mBezelShader);
            canvas.drawRoundRect(mBezel, s(36), s(36), mFill);
            mFill.setShader(null);
            mLine.setColor(0x14FFFFFF);
            mLine.setStrokeWidth(s(1));
            canvas.drawLine(mBezel.left + s(36), mBezel.top + s(1), mBezel.right - s(36),
                    mBezel.top + s(1), mLine);
            drawScreen(canvas, best);
            float markW = s(26);
            float markH = markW * 522.7f / 715.3f;
            mMark.setBounds(Math.round(px(205.5f) - markW / 2), Math.round(py(316)),
                    Math.round(px(205.5f) + markW / 2), Math.round(py(316) + markH));
            mMark.draw(canvas);

            text(canvas, getString(R.string.snake_hint), px(205.5f), py(380), 13,
                    Typeface.DEFAULT, 0xFFA9ACA5, Paint.Align.CENTER, 0);
            drawKeys(canvas);
            drawBack(canvas);
        }

        private void text(Canvas canvas, String value, float x, float y, float size,
                Typeface face, int color, Paint.Align align, float spacing) {
            mText.setTypeface(face);
            mText.setTextSize(s(size));
            mText.setColor(color);
            mText.setTextAlign(align);
            mText.setLetterSpacing(spacing);
            if (mGlow) {
                mText.setShadowLayer(s(6), 0, 0, mGlowColor);
            } else {
                mText.clearShadowLayer();
            }
            canvas.drawText(value, x, y, mText);
        }

        private void drawScreen(Canvas canvas, int best) {
            int bg = LCD_BG[mTheme];
            int ink = mTheme == NIGHT ? mAccent : LCD_INK[mTheme];
            mFill.setColor(bg);
            canvas.drawRoundRect(mLcd, s(10), s(10), mFill);
            canvas.save();
            mPath.rewind();
            mPath.addRoundRect(mLcd, s(10), s(10), Path.Direction.CW);
            canvas.clipPath(mPath);
            if (mTheme != NIGHT) {
                // The pixel grid of an old screen
                mLine.setColor(withAlpha(ink, 13));
                mLine.setStrokeWidth(s(1));
                for (float v = 0; v < 347; v += 4) {
                    canvas.drawLine(lx(v), mLcd.top, lx(v), mLcd.bottom, mLine);
                }
                for (float v = 0; v < 226; v += 4) {
                    canvas.drawLine(mLcd.left, ly(v), mLcd.right, ly(v), mLine);
                }
            }
            mGlow = mTheme == NIGHT;
            mGlowColor = withAlpha(ink, 180);
            if (mScreen == MENU) {
                drawMenu(canvas, ink, bg, best);
            } else if (mScreen == OVER) {
                drawOver(canvas, ink, best);
            } else {
                drawField(canvas, ink, bg);
            }
            mGlow = false;
            canvas.restore();
            // A shade along the top, as if the screen sat under the case
            mFill.setShader(mLcdShade);
            canvas.drawRoundRect(mLcd, s(10), s(10), mFill);
            mFill.setShader(null);
        }

        private void glowFill() {
            if (mGlow) {
                mFill.setShadowLayer(s(6), 0, 0, mGlowColor);
            }
        }

        private void drawField(Canvas canvas, int ink, int bg) {
            float fx = lx(14);
            float fy = ly(36);
            float c = s(16);
            text(canvas, String.format(Locale.ROOT, "%04d", mScore), lx(14), ly(24), 15, mPixel,
                    ink, Paint.Align.LEFT, 0);
            if (mBonus >= 0) {
                // The bug's countdown while it's out
                String left = String.valueOf(mBonusT);
                text(canvas, left, lx(334), ly(24), 15, mPixel, ink, Paint.Align.RIGHT, 0);
                drawBug(canvas, lx(334) - mText.measureText(left) - s(28), ly(13), s(2), s(2),
                        ink);
            }
            // The edge, dashed where the snake comes out the other side
            mLine.setColor(ink);
            mLine.setStrokeWidth(s(2));
            mLine.setPathEffect(mMaze == 0 ? mDash : null);
            mRect.set(fx - s(2), fy - s(2), fx + s(322), fy + s(178));
            canvas.drawRect(mRect, mLine);
            mLine.setPathEffect(null);

            boolean[] walls = WALLS[mMaze];
            mFill.setColor(withAlpha(ink, 217));
            for (int cell = 0; cell < walls.length; cell++) {
                if (walls[cell]) cell(canvas, cell, fx, fy, c);
            }
            mFill.setColor(ink);
            glowFill();
            for (int i = 0; i < mLength; i++) {
                cell(canvas, mBody[i], fx, fy, c);
            }
            if (mFood >= 0) {
                float cx = fx + (mFood % COLS) * c + s(8);
                float cy = fy + (mFood / COLS) * c + s(8);
                canvas.save();
                canvas.rotate(45, cx, cy);
                canvas.drawRect(cx - s(4), cy - s(4), cx + s(4), cy + s(4), mFill);
                canvas.restore();
            }
            mFill.clearShadowLayer();
            // The head's eye, on the side it's going
            int head = mBody[0];
            float hl = fx + (head % COLS) * c + s(1);
            float ht = fy + (head / COLS) * c + s(1);
            float ex = mDx == 1 ? 8 : mDx == -1 ? 2 : 3;
            float ey = mDy == 1 ? 8 : mDy == -1 ? 2 : 3;
            mFill.setColor(bg);
            canvas.drawRect(hl + s(ex), ht + s(ey), hl + s(ex + 4), ht + s(ey + 4), mFill);
            if (mBonus >= 0) {
                drawBug(canvas, fx + (mBonus % COLS) * c + s(1), fy + (mBonus / COLS) * c + s(1),
                        s(30f / 11f), s(14f / 5f), ink);
            }
            if (mScreen == PAUSE) {
                mRect.set(fx + s(70), fy + s(48), fx + s(250), fy + s(128));
                mFill.setColor(bg);
                canvas.drawRect(mRect, mFill);
                mRect.inset(s(1), s(1));
                mLine.setStrokeWidth(s(2));
                canvas.drawRect(mRect, mLine);
                text(canvas, upper(R.string.snake_paused), fx + s(160), fy + s(84), 18, mPixel,
                        ink, Paint.Align.CENTER, 0);
                text(canvas, "5  " + upper(R.string.snake_continue), fx + s(160), fy + s(108),
                        11, mPixel, ink, Paint.Align.CENTER, 0);
            }
        }

        private void cell(Canvas canvas, int cell, float fx, float fy, float c) {
            float left = fx + (cell % COLS) * c + s(1);
            float top = fy + (cell / COLS) * c + s(1);
            canvas.drawRect(left, top, left + s(14), top + s(14), mFill);
        }

        private void drawBug(Canvas canvas, float left, float top, float pw, float ph, int ink) {
            mFill.setColor(ink);
            glowFill();
            for (int row = 0; row < BUG.length; row++) {
                for (int col = 0; col < BUG[row].length(); col++) {
                    if (BUG[row].charAt(col) == '#') {
                        canvas.drawRect(left + col * pw, top + row * ph, left + (col + 1) * pw,
                                top + (row + 1) * ph, mFill);
                    }
                }
            }
            mFill.clearShadowLayer();
        }

        private void drawMenu(Canvas canvas, int ink, int bg, int best) {
            String title = upper(R.string.snake_title);
            mText.setTypeface(mPixelBold);
            mText.setTextSize(s(24));
            mText.setLetterSpacing(2f / 24f);
            float titleW = mText.measureText(title);
            float left = mLcd.centerX() - (s(36) + s(10) + titleW) / 2;
            mFill.setColor(ink);
            glowFill();
            for (float[] r : MENU_SNAKE) {
                canvas.drawRect(left + s(2 * r[0]), ly(18) + s(2 * r[1]),
                        left + s(2 * (r[0] + r[2])), ly(18) + s(2 * (r[1] + r[3])), mFill);
            }
            mFill.clearShadowLayer();
            text(canvas, title, left + s(46), ly(36), 24, mPixelBold, ink, Paint.Align.LEFT,
                    2f / 24f);

            String[] labels = {upper(R.string.snake_new_game), upper(R.string.snake_level),
                    upper(R.string.snake_maze), upper(R.string.snake_screen),
                    upper(R.string.snake_top_score)};
            String[] values = {"", "< " + mLevel + " >", upper(MAZE_NAMES[mMaze]),
                    upper(SCREEN_NAMES[mTheme]), String.valueOf(best)};
            boolean glow = mGlow;
            for (int i = 0; i < MENU_ROWS; i++) {
                float top = ly(50 + i * 33);
                boolean picked = i == mSel;
                if (picked) {
                    mFill.setColor(ink);
                    canvas.drawRect(lx(14), top, lx(334), top + s(30), mFill);
                }
                mGlow = glow && !picked;
                int color = picked ? bg : ink;
                text(canvas, labels[i], lx(24), top + s(20), 13, mPixel, color, Paint.Align.LEFT,
                        0);
                text(canvas, values[i], lx(324), top + s(20), 13, mPixel, color,
                        Paint.Align.RIGHT, 0);
            }
            mGlow = glow;
        }

        private void drawOver(Canvas canvas, int ink, int best) {
            float cx = mLcd.centerX();
            text(canvas, upper(R.string.snake_game_over), cx, ly(60), 20, mPixelBold, ink,
                    Paint.Align.CENTER, 0);
            text(canvas, upper(R.string.snake_your_score), cx, ly(84), 12, mPixel, ink,
                    Paint.Align.CENTER, 0);
            text(canvas, String.valueOf(mScore), cx, ly(130), 44, mPixelBold, ink,
                    Paint.Align.CENTER, 0);
            text(canvas, mNewBest ? upper(R.string.snake_new_top)
                    : getString(R.string.snake_top, best).toUpperCase(
                            getResources().getConfiguration().getLocales().get(0)),
                    cx, ly(158), 12, mPixel, ink, Paint.Align.CENTER, 0);
            text(canvas, "5  " + upper(R.string.snake_play_again) + "    C  "
                    + upper(R.string.snake_menu), cx, ly(184), 11, mPixel, ink,
                    Paint.Align.CENTER, 0);
        }

        private void drawKeys(Canvas canvas) {
            for (int i = 0; i < mKeys.length; i++) {
                RectF key = mKeys[i];
                if (i == mPressedKey) {
                    mFill.setColor(0xFF383A42);
                } else {
                    mFill.setShader(mKeyShaders[i]);
                }
                canvas.drawRoundRect(key, s(26), s(26), mFill);
                mFill.setShader(null);
                mLine.setColor(0x17FFFFFF);
                mLine.setStrokeWidth(s(1));
                canvas.drawLine(key.left + s(26), key.top + s(1), key.right - s(26),
                        key.top + s(1), mLine);

                int code = KEYS[i];
                boolean arrow = code == 2 || code == 4 || code == 6 || code == 8;
                mText.setTypeface(mPixel);
                mText.setTextSize(s(24));
                mText.setLetterSpacing(0);
                float labelW = mText.measureText(KEY_LABELS[i]);
                float groupW = labelW + (arrow ? s(20) : 0);
                float start = key.centerX() - groupW / 2;
                float baseline = key.top + s(38);
                text(canvas, KEY_LABELS[i], start, baseline, 24, mPixel, 0xFFF2F2EF,
                        Paint.Align.LEFT, 0);
                if (arrow) {
                    drawArrow(canvas, code, start + labelW + s(13), baseline - s(9), s(6));
                }
                if (!mKeySubs[i].isEmpty()) {
                    text(canvas, mKeySubs[i], key.centerX(), key.top + s(57), 11,
                            Typeface.DEFAULT, 0xFFC9CBC4, Paint.Align.CENTER, 0.09f);
                }
            }
        }

        /** A small triangle pointing the way [key] steers, in the accent. */
        private void drawArrow(Canvas canvas, int key, float cx, float cy, float r) {
            mPath.rewind();
            if (key == 2) {
                mPath.moveTo(cx, cy - r);
                mPath.lineTo(cx + r, cy + r * 0.7f);
                mPath.lineTo(cx - r, cy + r * 0.7f);
            } else if (key == 8) {
                mPath.moveTo(cx, cy + r);
                mPath.lineTo(cx - r, cy - r * 0.7f);
                mPath.lineTo(cx + r, cy - r * 0.7f);
            } else if (key == 4) {
                mPath.moveTo(cx - r, cy);
                mPath.lineTo(cx + r * 0.7f, cy - r);
                mPath.lineTo(cx + r * 0.7f, cy + r);
            } else {
                mPath.moveTo(cx + r, cy);
                mPath.lineTo(cx - r * 0.7f, cy + r);
                mPath.lineTo(cx - r * 0.7f, cy - r);
            }
            mPath.close();
            mFill.setColor(mAccent);
            canvas.drawPath(mPath, mFill);
        }

        private void drawBack(Canvas canvas) {
            mLine.setColor(0xFF33353C);
            mLine.setStrokeWidth(s(1));
            mRect.set(mBackButton);
            mRect.inset(s(0.5f), s(0.5f));
            canvas.drawRoundRect(mRect, s(24), s(24), mLine);
            String label = getString(R.string.snake_back);
            mText.setTypeface(Typeface.DEFAULT);
            mText.setTextSize(s(14));
            mText.setLetterSpacing(0);
            float labelW = mText.measureText(label);
            float left = mBackButton.centerX() - (s(26) + labelW) / 2;
            float cy = mBackButton.centerY();
            mLine.setColor(0xFFC9CBC4);
            mLine.setStrokeWidth(s(2));
            canvas.drawLine(left + s(2), cy, left + s(16), cy, mLine);
            canvas.drawLine(left + s(2), cy, left + s(8), cy - s(6), mLine);
            canvas.drawLine(left + s(2), cy, left + s(8), cy + s(6), mLine);
            text(canvas, label, left + s(26), cy + s(5), 14, Typeface.DEFAULT, 0xFFC9CBC4,
                    Paint.Align.LEFT, 0);
        }
    }
}
