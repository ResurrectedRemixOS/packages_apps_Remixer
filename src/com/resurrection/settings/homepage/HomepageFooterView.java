/*
 * Copyright (C) 2026 The Resurrected Remix Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.resurrection.settings.homepage;

import android.content.Context;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.settings.R;

/**
 * ROM version line at the bottom of the Settings homepage.
 */
public class HomepageFooterView extends TextView {
    public HomepageFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setText(context.getString(R.string.rr_homepage_footer,
                SystemProperties.get("ro.rr.version")));
    }
}
