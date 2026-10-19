package com.resurrection.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.Slider;

import java.util.function.DoubleSupplier;

/** A small hold-to-reset button that shows once a slider leaves its default. */
public final class SliderReset {

    private static final int SIZE_DP = 36;
    private static final int ICON_DP = 18;
    /** Pulls the button into the label's height so the slider below never moves. */
    private static final int INSET_DP = 8;

    private SliderReset() {}

    /** For a Material slider whose listeners skip programmatic changes: [persist] saves [def]. */
    public static void attach(TextView label, Slider slider, float def, Runnable persist) {
        Runnable refresh = attach(label, def, slider::getValue, () -> {
            slider.setValue(def);
            persist.run();
        });
        slider.addOnChangeListener((s, value, fromUser) -> refresh.run());
    }

    /**
     * Puts the button at the end of [label]'s row. Returns a refresh to run when the value
     * changes.
     */
    public static Runnable attach(TextView label, float def, DoubleSupplier value,
            Runnable reset) {
        Context context = label.getContext();
        ImageButton button = create(context, reset);
        int size = dp(context, SIZE_DP);
        int inset = dp(context, INSET_DP);
        ViewGroup parent = (ViewGroup) label.getParent();
        if (parent instanceof LinearLayout
                && ((LinearLayout) parent).getOrientation() == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.topMargin = -inset;
            lp.bottomMargin = -inset;
            parent.addView(button, parent.indexOfChild(label) + 1, lp);
        } else {
            int index = parent.indexOfChild(label);
            ViewGroup.LayoutParams labelParams = label.getLayoutParams();
            parent.removeViewAt(index);
            FrameLayout row = new FrameLayout(context);
            row.addView(label, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            label.setPaddingRelative(label.getPaddingStart(), label.getPaddingTop(),
                    label.getPaddingEnd() + size, label.getPaddingBottom());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size,
                    Gravity.END | Gravity.CENTER_VERTICAL);
            lp.topMargin = -inset;
            lp.bottomMargin = -inset;
            row.addView(button, lp);
            parent.addView(row, index, labelParams);
        }
        Runnable refresh = () -> show(button, Math.abs(value.getAsDouble() - def) > 0.001);
        refresh.run();
        return refresh;
    }

    /** The bare button: a tap explains, a hold resets. */
    public static ImageButton create(Context context, Runnable reset) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(R.drawable.ic_slider_reset);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant, 0)));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = (dp(context, SIZE_DP) - dp(context, ICON_DP)) / 2;
        button.setPadding(pad, 0, pad, 0);
        android.util.TypedValue ripple = new android.util.TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        button.setBackgroundResource(ripple.resourceId);
        button.setContentDescription(context.getString(R.string.slider_reset));
        button.setOnClickListener(v -> Toast.makeText(context, R.string.slider_reset_hint,
                Toast.LENGTH_SHORT).show());
        button.setOnLongClickListener(v -> {
            reset.run();
            return true;
        });
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                    AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK,
                        context.getString(R.string.slider_reset)));
            }
        });
        button.setVisibility(View.INVISIBLE);
        return button;
    }

    /** Invisible rather than gone, so nothing shifts when it appears. */
    public static void show(View button, boolean visible) {
        button.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    private static int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
