/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.resurrection.settings.nowbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Lays children out in rows, wrapping when a row is full. */
public class NowBarFlowLayout extends ViewGroup {
    private final int mGap;

    public NowBarFlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGap = Math.round(8 * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
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
        setMeasuredDimension(width, y + rowHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = r - l;
        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            final int w = child.getMeasuredWidth();
            final int h = child.getMeasuredHeight();
            if (x > 0 && x + w > width) {
                x = 0;
                y += rowHeight + mGap;
                rowHeight = 0;
            }
            final int left = rtl ? width - x - w : x;
            child.layout(left, y, left + w, y + h);
            x += w + mGap;
            rowHeight = Math.max(rowHeight, h);
        }
    }
}
