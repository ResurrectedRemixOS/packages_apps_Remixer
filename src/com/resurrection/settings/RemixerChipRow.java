package com.resurrection.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Lays children out left to right and wraps onto new rows, like a chip group.
 */
public class RemixerChipRow extends ViewGroup {
    private final int mGap;

    public RemixerChipRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGap = Math.round(8 * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (x > 0 && x + child.getMeasuredWidth() > width) {
                x = 0;
                y += rowHeight + mGap;
                rowHeight = 0;
            }
            x += child.getMeasuredWidth() + mGap;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                y + rowHeight + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            if (x > 0 && x + w > width) {
                x = 0;
                y += rowHeight + mGap;
                rowHeight = 0;
            }
            int left = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? width - x - w : x;
            child.layout(getPaddingLeft() + left, getPaddingTop() + y,
                    getPaddingLeft() + left + w, getPaddingTop() + y + h);
            x += w + mGap;
            rowHeight = Math.max(rowHeight, h);
        }
    }
}
