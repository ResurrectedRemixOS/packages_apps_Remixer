package com.resurrection.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/** Thumbnail with a phone screen's 9:16 shape. */
public class PortraitThumbView extends ImageView {
    public PortraitThumbView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, width * 16 / 9);
    }
}
