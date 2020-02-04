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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Typeface;
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
import android.widget.TextView;

import com.android.systemui.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class CustomTextClock extends TextView {

    private String mDescFormat;
    private String[] mHours;
    private final String[] mMinutes;
    private final Resources mResources;
    private final Calendar mTime = Calendar.getInstance(TimeZone.getDefault());
    private TimeZone mTimeZone;

    private boolean h24;
    private int mAccentColor;
    private int hours;
    private int mClockSize = 54;
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
        mDescFormat = ((SimpleDateFormat) DateFormat.getTimeFormat(context)).toLocalizedPattern();
        mResources = context.getResources();
        h24 = DateFormat.is24HourFormat(getContext());
        if (!h24) mHours = mResources.getStringArray(R.array.type_clock_hours_12);
            else mHours = mResources.getStringArray(R.array.type_clock_hours_24);
        mMinutes = mResources.getStringArray(R.array.type_clock_minutes);
        mAccentColor = mResources.getColor(R.color.accent_device_default_light);
    }

    public void onTimeChanged() {
        h24 = DateFormat.is24HourFormat(getContext());
        mTime.setTimeInMillis(System.currentTimeMillis());
        setContentDescription(DateFormat.format(mDescFormat, mTime));
        if (!h24) {
             mHours = mResources.getStringArray(R.array.type_clock_hours_12);
             hours = mTime.get(Calendar.HOUR) % 12;
        } else {
             mHours = mResources.getStringArray(R.array.type_clock_hours_24);
             hours = mTime.get(Calendar.HOUR_OF_DAY);
        }
        final int minutes = mTime.get(Calendar.MINUTE) % 60;
        SpannedString rawFormat = (SpannedString) mResources.getQuantityText(R.plurals.type_clock_header, hours);
        Annotation[] annotationArr = (Annotation[]) rawFormat.getSpans(0, rawFormat.length(), Annotation.class);
        SpannableString colored = new SpannableString(rawFormat);
        for (Annotation annotation : annotationArr) {
            if ("color".equals(annotation.getValue())) {
                colored.setSpan(new ForegroundColorSpan(mAccentColor),
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

    private void updateTextSize(int lockClockSize) {
        if (lockClockSize == 10) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9));
        } else if (lockClockSize == 11) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9_2));
        } else if (lockClockSize == 12) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9_4));
        } else if (lockClockSize == 13) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9_6));
        } else if (lockClockSize == 14) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9_8));
        } else if (lockClockSize == 15) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10));
        } else if (lockClockSize == 16) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10_2));
        } else if (lockClockSize == 17) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10_4));
        } else if (lockClockSize == 18) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10_6));
        } else if (lockClockSize == 19) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10_8));
        } else if (lockClockSize == 20) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11));
        } else if (lockClockSize == 21) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11_2));
        } else if (lockClockSize == 22) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11_4));
        } else if (lockClockSize == 23) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11_6));
        } else if (lockClockSize == 24) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11_8));
        } else if (lockClockSize == 25) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12));
        } else if (lockClockSize == 26) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12_2));
        } else if (lockClockSize == 27) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12_4));
        } else if (lockClockSize == 28) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12_6));
        } else if (lockClockSize == 29) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12_8));
        } else if (lockClockSize == 30) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13));
        } else if (lockClockSize == 31) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13_2));
        } else if (lockClockSize == 32) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13_4));
        } else if (lockClockSize == 33) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13_6));
        } else if (lockClockSize == 34) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13_8));
        } else if (lockClockSize == 35) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14));
        } else if (lockClockSize == 36) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14_2));
        } else if (lockClockSize == 37) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14_4));
        } else if (lockClockSize == 38) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14_6));
        } else if (lockClockSize == 39) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14_8));
        } else if (lockClockSize == 40) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15));
        } else if (lockClockSize == 41) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15_2));
        } else if (lockClockSize == 42) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15_4));
        } else if (lockClockSize == 43) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15_6));
        } else if (lockClockSize == 44) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15_8));
        } else if (lockClockSize == 45) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16));
        } else if (lockClockSize == 46) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16_2));
        } else if (lockClockSize == 47) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16_4));
        } else if (lockClockSize == 48) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16_6));
        } else if (lockClockSize == 49) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16_8));
        } else if (lockClockSize == 50) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17));
        } else if (lockClockSize == 51) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17_2));
        } else if (lockClockSize == 52) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17_4));
        } else if (lockClockSize == 53) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17_8));
        } else if (lockClockSize == 54) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
        } else if (lockClockSize == 55) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18_3));
        } else if (lockClockSize == 56) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18_6));
        } else if (lockClockSize == 57) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19));
        } else if (lockClockSize == 58) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19_3));
        } else if (lockClockSize == 59) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19_6));
        } else if (lockClockSize == 60) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
        } else if (lockClockSize == 61) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20_3));
        } else if (lockClockSize == 62) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20_6));
        } else if (lockClockSize == 63) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
        } else if (lockClockSize == 64) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21_3));
        } else if (lockClockSize == 65) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21_6));
        } else if (lockClockSize == 66) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
        } else if (lockClockSize == 67) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22_3));
        } else if (lockClockSize == 68) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22_6));
        } else if (lockClockSize == 69) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
        } else if (lockClockSize == 70) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23_3));
        } else if (lockClockSize == 71) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23_6));
        } else if (lockClockSize == 72) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
        } else if (lockClockSize == 73) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24_3));
        } else if (lockClockSize == 74) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24_6));
        } else if (lockClockSize == 75) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
        } else if (lockClockSize == 76) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25_3));
        } else if (lockClockSize == 77) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25_6));
        } else if (lockClockSize == 78) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26));
        } else if (lockClockSize == 79) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26_3));
        } else if (lockClockSize == 80) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26_6));
        } else if (lockClockSize == 81) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27));
        } else if (lockClockSize == 82) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27_3));
        } else if (lockClockSize == 83) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27_6));
        } else if (lockClockSize == 84) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28));
        } else if (lockClockSize == 85) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28_3));
        } else if (lockClockSize == 86) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28_6));
        } else if (lockClockSize == 87) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29));
        } else if (lockClockSize == 88) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29_3));
        } else if (lockClockSize == 89) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29_6));
        } else if (lockClockSize == 90) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30));
        } else if (lockClockSize == 91) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30_3));
        } else if (lockClockSize == 92) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30_6));
        } else if (lockClockSize == 93) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31));
        } else if (lockClockSize == 94) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31_3));
        } else if (lockClockSize == 95) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31_6));
        } else if (lockClockSize == 96) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32));
        } else if (lockClockSize == 97) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32_3));
        } else if (lockClockSize == 98) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32_6));
        } else if (lockClockSize == 99) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_33));
        } else if (lockClockSize == 100) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_33_3));
        }
    }

    public void updateClockSize() {
        mClockSize = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKCLOCK_FONT_SIZE, 54,
                UserHandle.USER_CURRENT);
        updateTextSize(mClockSize);
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
