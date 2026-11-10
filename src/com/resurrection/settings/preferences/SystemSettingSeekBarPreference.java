package com.resurrection.settings.preferences;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.widget.SliderPreference;
import com.google.android.material.slider.Slider;
import com.resurrection.settings.SliderReset;

/** A slider kept as an int in Settings.System, with a hold-to-reset button beside its title. */
public class SystemSettingSeekBarPreference extends SliderPreference {

    private static final String RESET_TAG = "slider_reset";

    @Nullable
    private Integer mDefault;

    public SystemSettingSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (getSliderIncrement() == 0) {
            setSliderIncrement(1);
        }
        ContentResolver resolver = context.getContentResolver();
        setPreferenceDataStore(new PreferenceDataStore() {
            @Override
            public void putInt(String key, int value) {
                Settings.System.putIntForUser(resolver, key, value, UserHandle.USER_CURRENT);
            }

            @Override
            public int getInt(String key, int defValue) {
                return snapToStep(Settings.System.getIntForUser(resolver, key, defValue,
                        UserHandle.USER_CURRENT));
            }
        });
    }

    /**
     * The step nearest a saved value. Material's slider throws on a value between its steps,
     * which a page whose steps changed, or adb, can leave saved.
     */
    private int snapToStep(int value) {
        int step = getSliderIncrement();
        int min = getMin();
        if (step <= 1 || value <= min || value >= getMax()) {
            return value;
        }
        return min + Math.round((value - min) / (float) step) * step;
    }

    @Override
    protected @Nullable Object onGetDefaultValue(@NonNull TypedArray a, int index) {
        Object value = super.onGetDefaultValue(a, index);
        mDefault = (Integer) value;
        return value;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title == null || !(title.getParent() instanceof RelativeLayout)) {
            return;
        }
        RelativeLayout row = (RelativeLayout) title.getParent();
        // Holders are shared between slider rows, so the button may already be there.
        ImageButton reset = row.findViewWithTag(RESET_TAG);
        if (reset == null) {
            reset = SliderReset.create(getContext(), () -> {});
            reset.setTag(RESET_TAG);
            reset.setId(View.generateViewId());
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    Math.round(36 * getContext().getResources().getDisplayMetrics().density),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
            lp.addRule(RelativeLayout.ALIGN_TOP, android.R.id.title);
            lp.addRule(RelativeLayout.ALIGN_BOTTOM, android.R.id.title);
            row.addView(reset, lp);
            // Both edges, or RelativeLayout right-aligns the title and the summary and
            // slider (aligned to its start) collapse with it.
            RelativeLayout.LayoutParams titleParams =
                    (RelativeLayout.LayoutParams) title.getLayoutParams();
            titleParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            titleParams.addRule(RelativeLayout.START_OF, reset.getId());
        }
        if (mDefault == null) {
            reset.setVisibility(View.GONE);
            return;
        }
        final ImageButton button = reset;
        button.setOnLongClickListener(v -> {
            resetToDefault();
            return true;
        });
        SliderReset.show(button, getValue() != mDefault);
        Slider slider = getSlider();
        if (slider != null) {
            slider.addOnChangeListener((s, value, fromUser) ->
                    SliderReset.show(button, Math.round(value) != mDefault));
        }
    }

    private void resetToDefault() {
        if (mDefault == null || getValue() == mDefault) {
            return;
        }
        if (callChangeListener(mDefault)) {
            setValue(mDefault);
        }
    }
}
