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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.graphics.Outline;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.settings.R;
import com.android.settingslib.Utils;

/**
 * Large "Settings" title with the user's photo, above the homepage search bar.
 */
public class HomepageTitleView extends LinearLayout {
    private final ImageView mAvatar;

    public HomepageTitleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.rr_homepage_title, this, true);
        mAvatar = findViewById(R.id.rr_homepage_avatar);
        mAvatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        mAvatar.setClipToOutline(true);
        mAvatar.setOnClickListener(v -> {
            try {
                getContext().startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS));
            } catch (ActivityNotFoundException e) {
                // No accounts page on this build.
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        UserManager um = getContext().getSystemService(UserManager.class);
        UserInfo info = um != null ? um.getUserInfo(UserHandle.myUserId()) : null;
        mAvatar.setImageDrawable(info != null
                ? Utils.getUserIcon(getContext(), um, info) : null);
    }
}
