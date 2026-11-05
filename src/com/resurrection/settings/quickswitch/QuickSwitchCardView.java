package com.resurrection.settings.quickswitch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;

import java.util.Date;
import java.util.Locale;

/**
 * One launcher on the Quick switch page: a small home screen drawn in that launcher's manner, its
 * state and its name. RR Launcher gets neon rings on a dark red wall, Pixel Launcher gets
 * colored icons, the date over a blue wall and the Google bar.
 */
public class QuickSwitchCardView extends View {

    private static final int[] PIXEL_ICONS = {
        0xFF4285F4, 0xFF34A853, 0xFFFBBC05, 0xFFEA4335
    };

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Path mClip = new Path();
    private final float mDp;
    private final int mAccent;
    private final String mDate;

    private boolean mPixel;
    private boolean mInUse;
    private boolean mSelected;
    private String mName = "";
    private String mTag;

    public QuickSwitchCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDp = getResources().getDisplayMetrics().density;
        mAccent = context.getColor(android.R.color.system_accent1_300);
        mDate = DateFormat.format(DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "EEEMMMd"), new Date()).toString();
        mLine.setStyle(Paint.Style.STROKE);
    }

    /** Sets what this card shows. */
    public void bind(boolean pixel, String name, boolean inUse, boolean selected, String tag) {
        mPixel = pixel;
        mName = name;
        mInUse = inUse;
        mSelected = selected;
        mTag = tag;
        setSelected(selected);
        setContentDescription(tag != null ? name + ", " + tag : name);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        // A 9:15 home screen, its margins and the name under it.
        int height = (int) ((width - 12 * mDp) * 15f / 9f + 12 * mDp + 34 * mDp);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();

        mRect.set(0, 0, width, height);
        mFill.setShader(null);
        mFill.setColor(0xFF15161A);
        canvas.drawRoundRect(mRect, 20 * mDp, 20 * mDp, mFill);

        float inset = 6 * mDp;
        mRect.set(inset, inset, width - inset, height - 34 * mDp);
        drawHome(canvas, new RectF(mRect));

        if (mSelected) {
            mRect.set(1 * mDp, 1 * mDp, width - 1 * mDp, height - 1 * mDp);
            mLine.setColor(mAccent);
            mLine.setStrokeWidth(2 * mDp);
            mLine.clearShadowLayer();
            canvas.drawRoundRect(mRect, 19 * mDp, 19 * mDp, mLine);
        }

        if (mTag != null) {
            drawTag(canvas, inset + 6 * mDp, inset + 6 * mDp);
        }

        float rowY = height - 17 * mDp;
        float radioX = inset + 12 * mDp;
        mLine.setColor(mSelected ? mAccent : 0xFF7C7D84);
        mLine.setStrokeWidth(2 * mDp);
        canvas.drawCircle(radioX, rowY, 8 * mDp, mLine);
        if (mSelected) {
            mFill.setColor(mAccent);
            canvas.drawCircle(radioX, rowY, 4.5f * mDp, mFill);
        }
        mText.setColor(Color.WHITE);
        mText.setTextSize(13 * mDp);
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        canvas.drawText(mName, radioX + 14 * mDp, rowY + 4.5f * mDp, mText);
    }

    private void drawTag(Canvas canvas, float x, float y) {
        mText.setTextSize(10.5f * mDp);
        mText.setTypeface(Typeface.DEFAULT_BOLD);
        float textWidth = mText.measureText(mTag);
        mRect.set(x, y, x + textWidth + 14 * mDp, y + 20 * mDp);
        mFill.setShader(null);
        mFill.setColor(mInUse ? 0xE0FFFFFF : mAccent);
        canvas.drawRoundRect(mRect, 10 * mDp, 10 * mDp, mFill);
        mText.setColor(0xFF05101F);
        canvas.drawText(mTag, x + 7 * mDp, y + 14 * mDp, mText);
    }

    private void drawHome(Canvas canvas, RectF home) {
        float radius = 14 * mDp;
        mClip.reset();
        mClip.addRoundRect(home, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(mClip);
        if (mPixel) {
            mFill.setShader(new LinearGradient(home.left, home.top, home.right, home.bottom,
                    new int[] {0xFF2D3A55, 0xFF16202F, 0xFF0E141E}, null, Shader.TileMode.CLAMP));
        } else {
            mFill.setShader(new RadialGradient(home.centerX(), home.bottom + home.height() * 0.12f,
                    Math.max(home.width(), home.height()) * 0.9f,
                    new int[] {0xFFB0002E, 0xFF3A000D, Color.BLACK},
                    new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(home, mFill);
        mFill.setShader(null);

        mText.setColor(Color.WHITE);
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mText.setTextSize(home.width() * 0.078f);
        float textX = home.left + home.width() * 0.08f;
        canvas.drawText(mDate, textX, home.top + home.height() * 0.2f, mText);

        float rowsTop = home.bottom - home.height() * (mPixel ? 0.42f : 0.34f);
        drawIconRow(canvas, home, rowsTop, false);
        drawIconRow(canvas, home, home.bottom - home.height() * (mPixel ? 0.26f : 0.16f), true);
        if (mPixel) {
            drawGoogleBar(canvas, home);
        }
        canvas.restore();
    }

    private void drawIconRow(Canvas canvas, RectF home, float top, boolean dock) {
        float gap = home.width() * 0.05f;
        float side = home.width() * 0.08f;
        float size = (home.width() - 2 * side - 3 * gap) / 4;
        for (int i = 0; i < 4; i++) {
            float cx = home.left + side + size / 2 + i * (size + gap);
            float cy = top + size / 2;
            if (mPixel) {
                mFill.setColor(PIXEL_ICONS[dock ? 3 - i : i]);
                canvas.drawCircle(cx, cy, size / 2, mFill);
                mFill.setColor(0xCCFFFFFF);
                canvas.drawCircle(cx, cy, size / 5, mFill);
            } else {
                mFill.setColor(0xFF14090C);
                canvas.drawCircle(cx, cy, size / 2, mFill);
                mLine.setColor(mAccent);
                mLine.setStrokeWidth(1.5f * mDp);
                mLine.setShadowLayer(4 * mDp, 0, 0, mAccent);
                canvas.drawCircle(cx, cy, size / 2 - mDp, mLine);
                mLine.clearShadowLayer();
                mFill.setColor(mAccent);
                canvas.drawCircle(cx, cy, size / 6, mFill);
            }
        }
    }

    private void drawGoogleBar(Canvas canvas, RectF home) {
        float side = home.width() * 0.08f;
        float height = home.height() * 0.085f;
        float top = home.bottom - height - home.height() * 0.05f;
        mRect.set(home.left + side, top, home.right - side, top + height);
        mFill.setColor(0x33FFFFFF);
        canvas.drawRoundRect(mRect, height / 2, height / 2, mFill);
        mText.setColor(Color.WHITE);
        mText.setTypeface(Typeface.DEFAULT_BOLD);
        mText.setTextSize(height * 0.6f);
        canvas.drawText("G", mRect.left + height * 0.35f, mRect.bottom - height * 0.3f, mText);
    }
}
