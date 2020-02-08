/*
* Copyright (C) 2019 The OmniROM Project
* Copyright (C) 2019 The ion-OS Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.aim;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.WallpaperColors;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.palette.graphics.Palette;

import com.android.systemui.R;

public class NotificationLightsView extends RelativeLayout {

    private static final boolean DEBUG = false;
    private static final String TAG = "NotificationLightsView";
    private View mNotificationAnimView;
    private ValueAnimator mLightAnimatorLeft;
    private ValueAnimator mLightAnimatorRight;
    private WallpaperManager mWallManager;

    private boolean mPulsing;
    private boolean mNotification;
    private boolean mAccentColorLeft;
    private boolean mAccentColorRight;
    private boolean mAutoColorLeft;
    private boolean mAutoColorRight;
    private int lColor;
    private int rColor;
    private int lDuration;
    private int rDuration;
    private int lAnimation;
    private int rAnimation;
    private int mLayout;

    public NotificationLightsView(Context context) {
        this(context, null);
    }

    public NotificationLightsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (DEBUG) Log.d(TAG, "new");
    }

    private Runnable mLightUpdate = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.d(TAG, "run");
            animateNotification(mNotification);
        }
    };

    public void setPulsing(boolean pulsing) {
        if (mPulsing == pulsing) {
            return;
        }
        mPulsing = pulsing;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (DEBUG) Log.d(TAG, "draw");
    }

    public void animateNotification(boolean mNotification) {
        mAccentColorLeft = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_NOTIFICATION_LIGHT_ACCENT_LEFT, 1,
                UserHandle.USER_CURRENT) == 1;
        mAccentColorRight = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_NOTIFICATION_LIGHT_ACCENT_RIGHT, 1,
                UserHandle.USER_CURRENT) == 1;
        mAutoColorLeft = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_AUTO_COLOR_LEFT, 0,
                UserHandle.USER_CURRENT) == 1;
        mAutoColorRight = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_AUTO_COLOR_RIGHT, 0,
                UserHandle.USER_CURRENT) == 1;
        lColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_COLOR_LEFT, 0xFF3980FF,
                UserHandle.USER_CURRENT);
        rColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_COLOR_RIGHT, 0xFF3980FF,
                UserHandle.USER_CURRENT);
        lDuration = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_LEFT_DURATION, 2000,
                UserHandle.USER_CURRENT);
        rDuration = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_RIGHT_DURATION, 2000,
                UserHandle.USER_CURRENT);
        lAnimation = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_REPEAT_MODE_LEFT, 0,
                UserHandle.USER_CURRENT);
        rAnimation = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_REPEAT_MODE_RIGHT, 0,
                UserHandle.USER_CURRENT);
        mLayout = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_LAYOUT, 0,
                UserHandle.USER_CURRENT);

        if (mAutoColorLeft || mAutoColorRight) {
            try {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                WallpaperInfo wallpaperInfo = wallpaperManager.getWallpaperInfo();
                if (wallpaperInfo == null) {
                    Drawable wallpaperDrawable = wallpaperManager.getDrawable();
                    Bitmap bitmap = ((BitmapDrawable)wallpaperDrawable).getBitmap();
                    if (bitmap != null) {
                        Palette p = Palette.from(bitmap).generate();
                        int wallColorL = p.getDominantColor(lColor);
                        if (mAutoColorLeft)
                            lColor = wallColorL;
                        int wallColorR = p.getDominantColor(rColor);
                        if (mAutoColorRight)
                            rColor = wallColorR;
                    }
                }
            } catch (Exception e) { }
        }

        // left edge
        StringBuilder lsb = new StringBuilder();
        lsb.append("animateNotification lColor ");
        lsb.append(Integer.toHexString(lColor));
        if (DEBUG) Log.d("NotificationLeftLightView", lsb.toString());
        if (mAccentColorLeft) lColor = getResources().getColor(R.color.accent_device_default_light);
        ImageView leftViewSolid = (ImageView) findViewById(R.id.notification_animation_left_solid);
        ImageView leftViewFaded = (ImageView) findViewById(R.id.notification_animation_left_faded);
        leftViewSolid.setColorFilter(lColor);
        leftViewFaded.setColorFilter(lColor);
        leftViewSolid.setVisibility(mLayout == 0 ? View.VISIBLE : View.GONE);
        leftViewFaded.setVisibility(mLayout == 1 ? View.VISIBLE : View.GONE);
        mLightAnimatorLeft = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
        mLightAnimatorLeft.setDuration(lDuration);
        //Infinite animation only on Always On Notifications
        if (mNotification) mLightAnimatorLeft.setRepeatCount(ValueAnimator.INFINITE);
        mLightAnimatorLeft.setRepeatMode(lAnimation == 0 ? ValueAnimator.RESTART : ValueAnimator.REVERSE);
        mLightAnimatorLeft.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                if (DEBUG) Log.d("NotificationLeftLightView", "onAnimationUpdate");
                float progress = ((Float) animation.getAnimatedValue()).floatValue();
                leftViewSolid.setScaleY(progress);
                leftViewFaded.setScaleY(progress);
                float alpha = 1.0f;
                if (progress <= 0.3f) {
                    alpha = progress / 0.3f;
                } else if (progress >= 1.0f) {
                    alpha = 2.0f - progress;
                }
                leftViewSolid.setAlpha(alpha);
                leftViewFaded.setAlpha(alpha);
            }
        });
        if (DEBUG) Log.d("NotificationLeftLightView", "start");
        mLightAnimatorLeft.start();

        // right edge
        StringBuilder rsb = new StringBuilder();
        rsb.append("animateNotification rColor ");
        rsb.append(Integer.toHexString(rColor));
        if (DEBUG) Log.d("NotificationRightLightView", rsb.toString());
        if (mAccentColorRight) rColor = getResources().getColor(R.color.accent_device_default_light);
        ImageView rightViewSolid = (ImageView) findViewById(R.id.notification_animation_right_solid);
        ImageView rightViewFaded = (ImageView) findViewById(R.id.notification_animation_right_faded);
        rightViewSolid.setColorFilter(rColor);
        rightViewFaded.setColorFilter(rColor);
        rightViewSolid.setVisibility(mLayout == 0 ? View.VISIBLE : View.GONE);
        rightViewFaded.setVisibility(mLayout == 1 ? View.VISIBLE : View.GONE);
        mLightAnimatorRight = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
        mLightAnimatorRight.setDuration(rDuration);
        //Infinite animation only on Always On Notifications
        if (mNotification) mLightAnimatorRight.setRepeatCount(ValueAnimator.INFINITE);
        mLightAnimatorRight.setRepeatMode(rAnimation == 0 ? ValueAnimator.RESTART : ValueAnimator.REVERSE);
        mLightAnimatorRight.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                if (DEBUG) Log.d("NotificationRightLightView", "onAnimationUpdate");
                float progress = ((Float) animation.getAnimatedValue()).floatValue();
                rightViewSolid.setScaleY(progress);
                rightViewFaded.setScaleY(progress);
                float alpha = 1.0f;
                if (progress <= 0.3f) {
                    alpha = progress / 0.3f;
                } else if (progress >= 1.0f) {
                    alpha = 2.0f - progress;
                }
                rightViewSolid.setAlpha(alpha);
                rightViewFaded.setAlpha(alpha);
            }
        });
        if (DEBUG) Log.d("NotificationRightLightView", "start");
        mLightAnimatorRight.start();
    }

    public void stopNotification() {
        try {
            mLightAnimatorLeft.end();
            mLightAnimatorRight.end();
        } catch (Exception e) { }
    }
}
