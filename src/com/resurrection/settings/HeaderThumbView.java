package com.resurrection.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/** Thumbnail with the 8:3 shape of a quick settings header. */
public class HeaderThumbView extends ImageView {
    public HeaderThumbView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, width * 3 / 8);
    }
}
