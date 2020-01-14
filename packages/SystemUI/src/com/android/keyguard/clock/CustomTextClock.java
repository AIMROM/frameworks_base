/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.keyguard.clock;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.colorextraction.ColorExtractor.GradientColors;
import com.android.internal.graphics.ColorUtils;
import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.colorextraction.SysuiColorExtractor;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class CustomTextClock extends TextView implements ColorExtractor.OnColorsChangedListener {

    private String mDescFormat;
    private final String[] mHours;
    private final String[] mMinutes;
    private final Resources mResources;
    private final Calendar mTime;
    private TimeZone mTimeZone;
    private SysuiColorExtractor mColorExtractor;

    private int mPrimaryColor;
    private int mAmbientColor;
    private int mSystemAccent;
    private int mFallbackColor;
    private int mCurrentAccent;
    private float mDarkAmount;
    private float[] mHslOut = new float[3];

    private int mClockSize = 40;
    private SettingsObserver mSettingsObserver;

    private final BroadcastReceiver mTimeZoneChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                onTimeZoneChanged(TimeZone.getTimeZone(tz));
                onTimeChanged();
            }
        }
    };

    public CustomTextClock(Context context) {
        this(context, null);
    }

    public CustomTextClock(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public CustomTextClock(Context context, AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);

        mColorExtractor = Dependency.get(SysuiColorExtractor.class);
        mColorExtractor.addOnColorsChangedListener(this);
        mTime = Calendar.getInstance(TimeZone.getDefault());
        mDescFormat = ((SimpleDateFormat) DateFormat.getTimeFormat(context)).toLocalizedPattern();
        mResources = context.getResources();
        mHours = mResources.getStringArray(R.array.type_clock_hours);
        mMinutes = mResources.getStringArray(R.array.type_clock_minutes);
        mSystemAccent = mResources.getColor(R.color.accent_device_default_light, null);
        mFallbackColor = mResources.getColor(R.color.custom_text_clock_top_fallback_color, null);
        onColorsChanged(mColorExtractor, 0);
    }

    public void onTimeChanged() {
        mTime.setTimeInMillis(System.currentTimeMillis());
        setContentDescription(DateFormat.format(mDescFormat, mTime));
        int hours = mTime.get(Calendar.HOUR) % 12;
        int minutes = mTime.get(Calendar.MINUTE) % 60;
        SpannedString rawFormat = (SpannedString) mResources.getQuantityText(R.plurals.type_clock_header, hours);
        Annotation[] annotationArr = (Annotation[]) rawFormat.getSpans(0, rawFormat.length(), Annotation.class);
        SpannableString colored = new SpannableString(rawFormat);
        for (Annotation annotation : annotationArr) {
            if ("color".equals(annotation.getValue())) {
                colored.setSpan(new ForegroundColorSpan(mCurrentAccent),
                        colored.getSpanStart(annotation),
                        colored.getSpanEnd(annotation),
                        Spanned.SPAN_POINT_POINT);
            }
        }
        setText(TextUtils.expandTemplate(colored, new CharSequence[]{mHours[hours], mMinutes[minutes]}));
    }

    public void onTimeZoneChanged(TimeZone timeZone) {
        mTimeZone = timeZone;
        mTime.setTimeZone(timeZone);
    }

    @Override
    public void onColorsChanged(ColorExtractor extractor, int which) {
        GradientColors colors = extractor.getColors(WallpaperManager.FLAG_LOCK);
        setWallpaperColors(colors.getMainColor(), colors.supportsDarkText(), colors.getColorPalette());
    }

    private void setWallpaperColors(int mainColor, boolean supportsDarkText, int[] colorPalette) {
        int scrimColor = supportsDarkText ? Color.WHITE : Color.BLACK;
        int scrimTinted = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(scrimColor, mainColor, 0.5f), 64);
        int bgColor = ColorUtils.compositeColors(scrimTinted, mainColor);

        int paletteColor = getColorFromPalette(colorPalette);
        bgColor = ColorUtils.compositeColors(bgColor, Color.BLACK);
        mPrimaryColor = findColor(paletteColor, bgColor, !supportsDarkText, mSystemAccent, mFallbackColor);
        mAmbientColor = findColor(paletteColor, Color.BLACK, true, mSystemAccent, mFallbackColor);

        setDarkAmount(mDarkAmount);
    }

    private int getColorFromPalette(int[] palette) {
        if (palette != null && palette.length != 0) {
            return palette[Math.max(0, palette.length - 5)];
        } else {
            return mSystemAccent;
        }
    }

    private int findColor(int color, int background, boolean againstDark, int accent, int fallback) {
        if (!isGreyscale(color)) {
            return color;
        } else {
            return fallback;
        }
    }

    private boolean isGreyscale(int color) {
        ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), mHslOut);
        return mHslOut[1] < 0.1f || mHslOut[2] < 0.1f;
    }

    public void setDarkAmount(float dark) {
        mDarkAmount = dark;
        mCurrentAccent = ColorUtils.blendARGB(mPrimaryColor, mAmbientColor, mDarkAmount);
        onTimeChanged();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Calendar calendar = mTime;
        TimeZone timeZone = mTimeZone;
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }
        calendar.setTimeZone(timeZone);
        onTimeChanged();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(mTimeZoneChangedReceiver, filter);

        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(new Handler());
        }
        mSettingsObserver.observe();
        updateClockSize();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(mTimeZoneChangedReceiver);
        mColorExtractor.removeOnColorsChangedListener(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        refreshLockFont();
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 0);
    }

    private void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 0;

        if (lockClockFont == 0) {
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockClockFont == 1) {
            setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockClockFont == 2) {
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockClockFont == 3) {
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 4) {
            setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockClockFont == 5) {
            setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockClockFont == 6) {
            setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (lockClockFont == 7) {
            setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (lockClockFont == 8) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockClockFont == 9) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockClockFont == 10) {
            setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (lockClockFont == 11) {
            setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (lockClockFont == 12) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockClockFont == 13) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 14) {
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockClockFont == 15) {
            setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockClockFont == 16) {
            setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (lockClockFont == 17) {
            setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        if (lockClockFont == 18) {
            setTypeface(Typeface.create("abelreg", Typeface.NORMAL));
        }
        if (lockClockFont == 19) {
            setTypeface(Typeface.create("adamcg-pro", Typeface.NORMAL));
        }
        if (lockClockFont == 20) {
            setTypeface(Typeface.create("adventpro", Typeface.NORMAL));
        }
        if (lockClockFont == 21) {
            setTypeface(Typeface.create("alexana-neue", Typeface.NORMAL));
        }
        if (lockClockFont == 22) {
            setTypeface(Typeface.create("alien-league", Typeface.NORMAL));
        }
        if (lockClockFont == 23) {
            setTypeface(Typeface.create("archivonar", Typeface.NORMAL));
        }
        if (lockClockFont == 24) {
            setTypeface(Typeface.create("autourone", Typeface.NORMAL));
        }
        if (lockClockFont == 25) {
            setTypeface(Typeface.create("azedo-light", Typeface.NORMAL));
        }
        if (lockClockFont == 26) {
            setTypeface(Typeface.create("badscript", Typeface.NORMAL));
        }
        if (lockClockFont == 27) {
            setTypeface(Typeface.create("bignoodle-regular", Typeface.NORMAL));
        }
        if (lockClockFont == 28) {
            setTypeface(Typeface.create("biko", Typeface.NORMAL));
        }
        if (lockClockFont == 29) {
            setTypeface(Typeface.create("blern", Typeface.NORMAL));
        }
        if (lockClockFont == 30) {
            setTypeface(Typeface.create("cherryswash", Typeface.NORMAL));
        }
        if (lockClockFont == 31) {
            setTypeface(Typeface.create("cocon", Typeface.NORMAL));
        }
        if (lockClockFont == 32) {
            setTypeface(Typeface.create("codystar", Typeface.NORMAL));
        }
        if (lockClockFont == 33) {
            setTypeface(Typeface.create("fester", Typeface.NORMAL));
        }
        if (lockClockFont == 34) {
            setTypeface(Typeface.create("fox-and-cat", Typeface.NORMAL));
        }
        if (lockClockFont == 35) {
            setTypeface(Typeface.create("ginora-sans", Typeface.NORMAL));
        }
        if (lockClockFont == 36) {
            setTypeface(Typeface.create("gobold-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 37) {
            setTypeface(Typeface.create("ibmplex-mono", Typeface.NORMAL));
        }
        if (lockClockFont == 38) {
            setTypeface(Typeface.create("inkferno", Typeface.NORMAL));
        }
        if (lockClockFont == 39) {
            setTypeface(Typeface.create("instruction", Typeface.NORMAL));
        }
        if (lockClockFont == 40) {
            setTypeface(Typeface.create("jacklane", Typeface.NORMAL));
        }
        if (lockClockFont == 41) {
            setTypeface(Typeface.create("jura-reg", Typeface.NORMAL));
        }
        if (lockClockFont == 42) {
            setTypeface(Typeface.create("kellyslab", Typeface.NORMAL));
        }
        if (lockClockFont == 43) {
            setTypeface(Typeface.create("metropolis1920", Typeface.NORMAL));
        }
        if (lockClockFont == 44) {
            setTypeface(Typeface.create("monad", Typeface.NORMAL));
        }
        if (lockClockFont == 45) {
            setTypeface(Typeface.create("neonneon", Typeface.NORMAL));
        }
        if (lockClockFont == 46) {
            setTypeface(Typeface.create("noir", Typeface.NORMAL));
        }
        if (lockClockFont == 47) {
            setTypeface(Typeface.create("northfont", Typeface.NORMAL));
        }
        if (lockClockFont == 48) {
            setTypeface(Typeface.create("outrun-future", Typeface.NORMAL));
        }
        if (lockClockFont == 49) {
            setTypeface(Typeface.create("pompiere", Typeface.NORMAL));
        }
        if (lockClockFont == 50) {
            setTypeface(Typeface.create("qontra", Typeface.NORMAL));
        }
        if (lockClockFont == 51) {
            setTypeface(Typeface.create("raleway-light", Typeface.NORMAL));
        }
        if (lockClockFont == 52) {
            setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
        }
        if (lockClockFont == 53) {
            setTypeface(Typeface.create("riviera", Typeface.NORMAL));
        }
        if (lockClockFont == 54) {
            setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 55) {
            setTypeface(Typeface.create("satisfy", Typeface.NORMAL));
        }
        if (lockClockFont == 56) {
            setTypeface(Typeface.create("seaweedsc", Typeface.NORMAL));
        }
        if (lockClockFont == 57) {
            setTypeface(Typeface.create("sedgwick-ave", Typeface.NORMAL));
        }
        if (lockClockFont == 58) {
            setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 59) {
            setTypeface(Typeface.create("source-sans-pro", Typeface.NORMAL));
        }
        if (lockClockFont == 60) {
            setTypeface(Typeface.create("themeable-clock", Typeface.NORMAL));
        }
        if (lockClockFont == 61) {
            setTypeface(Typeface.create("the-outbox", Typeface.NORMAL));
        }
        if (lockClockFont == 62) {
            setTypeface(Typeface.create("unionfont", Typeface.NORMAL));
        }
        if (lockClockFont == 63) {
            setTypeface(Typeface.create("vibur", Typeface.NORMAL));
        }
        if (lockClockFont == 64) {
            setTypeface(Typeface.create("voltaire", Typeface.NORMAL));
        }
    }

    public void updateClockSize() {
        mClockSize = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKCLOCK_FONT_SIZE, 40,
                UserHandle.USER_CURRENT);
            setTextSize(mClockSize);
            onTimeChanged();
    }

    protected class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKCLOCK_FONT_SIZE),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
	    updateClockSize();
        }
    }
}
