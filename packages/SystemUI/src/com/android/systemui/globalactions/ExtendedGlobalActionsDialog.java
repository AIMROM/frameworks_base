/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2017 Cosmic-OS
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.globalactions;

import com.android.internal.R;
import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.colorextraction.ColorExtractor.GradientColors;
import com.android.systemui.Dependency;
import com.android.systemui.HardwareUiLayout;
import com.android.systemui.Interpolators;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.GlobalActions.GlobalActionsManager;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.volume.VolumeDialogMotion.LogAccelerateInterpolator;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.IActivityManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.colorextraction.drawable.GradientDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper to show the global actions dialog.  Each item is an {@link Action} that
 * may show depending on whether the keyguard is showing, and whether the device
 * is provisioned.
 */
class ExtendedGlobalActionsDialog implements DialogInterface.OnDismissListener, DialogInterface.OnClickListener {

    static public final String SYSTEM_DIALOG_REASON_KEY = "reason";
    static public final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";

    private static final String TAG = "ExtendedGlobalActionsDialog";

    /* Valid settings for global actions keys.
     * see custom_config.xml config_extendedGlobalActionsList */
    private static final String GLOBAL_ACTION_KEY_QUICK_RESTART = "quick_restart";
    private static final String GLOBAL_ACTION_KEY_RECOVERY = "recovery";
    private static final String GLOBAL_ACTION_KEY_BOOTLOADER = "bootloader";

    private final Context mContext;
    private final GlobalActionsManager mWindowManagerFuncs;

    private ArrayList<Action> mItems;
    private ExtendedActionsDialog mDialog;

    private ExtendedAdapter mExtendedAdapter;

    private boolean mKeyguardShowing = false;
    private boolean mDeviceProvisioned = false;

    /**
     * @param context everything needs a context :(
     */
    public ExtendedGlobalActionsDialog(Context context, GlobalActionsManager windowManagerFuncs) {
        mContext = new ContextThemeWrapper(context, com.android.systemui.R.style.qs_theme);
        mWindowManagerFuncs = windowManagerFuncs;

        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Show the global actions dialog (creating if necessary)
     *
     * @param keyguardShowing True if keyguard is showing
     */
    public void showDialog(boolean keyguardShowing, boolean isDeviceProvisioned) {
        mKeyguardShowing = keyguardShowing;
        mDeviceProvisioned = isDeviceProvisioned;
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
            // Show delayed, so that the dismiss of the previous dialog completes
            mHandler.sendEmptyMessage(MESSAGE_SHOW);
        } else {
            handleShow();
        }
    }

    private void handleShow() {
        mDialog = createDialog();
        prepareDialog();

        // If we only have 1 item and it's a simple press action, just do this action.
        if (mExtendedAdapter.getCount() == 1
                && mExtendedAdapter.getItem(0) instanceof SinglePressAction
                && !(mExtendedAdapter.getItem(0) instanceof LongPressAction)) {
            ((SinglePressAction) mExtendedAdapter.getItem(0)).onPress();
        } else {
            WindowManager.LayoutParams attrs = mDialog.getWindow().getAttributes();
            attrs.setTitle("ExtendedActionsDialog");
            mDialog.getWindow().setAttributes(attrs);
            mDialog.show();
            mWindowManagerFuncs.onGlobalActionsShown();
        }
    }

    /**
     * Create the global actions dialog.
     *
     * @return A new dialog.
     */
    private ExtendedActionsDialog createDialog() {

        mItems = new ArrayList<Action>();
        String[] defaultActions = mContext.getResources().getStringArray(
                R.array.config_extendedGlobalActionsList);

        ArraySet<String> addedKeys = new ArraySet<String>();
        for (int i = 0; i < defaultActions.length; i++) {
            String actionKey = defaultActions[i];
            if (addedKeys.contains(actionKey)) {
                // If we already have added this, don't add it again.
                continue;
            }
            if (GLOBAL_ACTION_KEY_QUICK_RESTART.equals(actionKey)) {
                mItems.add(new QuickRestartAction());
            } else if (GLOBAL_ACTION_KEY_RECOVERY.equals(actionKey)) {
                mItems.add(new RecoveryAction());
            } else if (GLOBAL_ACTION_KEY_BOOTLOADER.equals(actionKey)) {
                mItems.add(new BootloaderAction());
            } else {
                Log.e(TAG, "Invalid extended global action key " + actionKey);
            }
            // Add here so we don't add more than one.
            addedKeys.add(actionKey);
        }

        mExtendedAdapter = new ExtendedAdapter();

        OnItemLongClickListener onItemLongClickListener = new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                    long id) {
                final Action action = mExtendedAdapter.getItem(position);
                if (action instanceof LongPressAction) {
                    mDialog.dismiss();
                    return ((LongPressAction) action).onLongPress();
                }
                return false;
            }
        };
        ExtendedActionsDialog dialog = new ExtendedActionsDialog(mContext, this, mExtendedAdapter, onItemLongClickListener);
        dialog.setCanceledOnTouchOutside(false); // Handled by the custom class.
        dialog.setKeyguardShowing(mKeyguardShowing);

        dialog.setOnDismissListener(this);

        return dialog;
    }

    private class QuickRestartAction extends SinglePressAction {

        public QuickRestartAction() {
            super(R.drawable.ic_restart_quick, R.string.global_action_quick_restart);
        }

        @Override
        public void onPress() {
            // don't actually trigger the bugreport if we are running stability
            // tests via monkey
            try {
                final IActivityManager am =
                      ActivityManagerNative.asInterface(ServiceManager.checkService("activity"));
                if (am != null) {
                    am.restart();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "failure trying to perform hot reboot", e);
            }
        }

        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }
    }

    private class RecoveryAction extends SinglePressAction {

        public RecoveryAction() {
            super(R.drawable.ic_restart_recovery, R.string.global_action_recovery);
        }

        @Override
        public void onPress() {
            mWindowManagerFuncs.rebootRecovery(false /* confirm */);
        }

        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }
    }

    private class BootloaderAction extends SinglePressAction {

        public BootloaderAction() {
            super(R.drawable.ic_restart_bootloader, R.string.global_action_bootloader);
        }

        @Override
        public void onPress() {
            mWindowManagerFuncs.rebootBootloader(false /* confirm */);
        }

        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }
    }

    private void prepareDialog() {
        mExtendedAdapter.notifyDataSetChanged();
    }

    /** {@inheritDoc} */
    public void onDismiss(DialogInterface dialog) {
        mWindowManagerFuncs.onGlobalActionsHidden();
    }

    /** {@inheritDoc} */
    public void onClick(DialogInterface dialog, int which) {
        Action item = mExtendedAdapter.getItem(which);
        dialog.dismiss();
        item.onPress();
    }

    /**
     * The adapter used for the list within the global actions dialog, taking
     * into account whether the keyguard is showing via
     * {@link com.android.systemui.globalactions.ExtendedGlobalActionsDialog#mKeyguardShowing} and whether
     * the device is provisioned
     * via {@link com.android.systemui.globalactions.ExtendedGlobalActionsDialog#mDeviceProvisioned}.
     */
    private class ExtendedAdapter extends BaseAdapter {

        public int getCount() {
            int count = 0;

            for (int i = 0; i < mItems.size(); i++) {
                final Action action = mItems.get(i);

                if (mKeyguardShowing && !action.showDuringKeyguard()) {
                    continue;
                }
                if (!mDeviceProvisioned && !action.showBeforeProvisioning()) {
                    continue;
                }
                count++;
            }
            return count;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).isEnabled();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        public Action getItem(int position) {

            int filteredPos = 0;
            for (int i = 0; i < mItems.size(); i++) {
                final Action action = mItems.get(i);
                if (mKeyguardShowing && !action.showDuringKeyguard()) {
                    continue;
                }
                if (!mDeviceProvisioned && !action.showBeforeProvisioning()) {
                    continue;
                }
                if (filteredPos == position) {
                    return action;
                }
                filteredPos++;
            }

            throw new IllegalArgumentException("position " + position
                    + " out of range of showable actions"
                    + ", filtered count=" + getCount()
                    + ", keyguardshowing=" + mKeyguardShowing
                    + ", provisioned=" + mDeviceProvisioned);
        }


        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Action action = getItem(position);
            View view = action.create(mContext, convertView, parent, LayoutInflater.from(mContext));
            if (position == 2) {
                HardwareUiLayout.get(parent).setDivisionView(view);
            }
            return view;
        }
    }

    // note: the scheme below made more sense when we were planning on having
    // 8 different things in the global actions dialog.  seems overkill with
    // only 3 items now, but may as well keep this flexible approach so it will
    // be easy should someone decide at the last minute to include something
    // else, such as 'enable wifi', or 'enable bluetooth'

    /**
     * What each item in the global actions dialog must be able to support.
     */
    private interface Action {
        /**
         * @return Text that will be announced when dialog is created.  null
         * for none.
         */
        CharSequence getLabelForAccessibility(Context context);

        View create(Context context, View convertView, ViewGroup parent, LayoutInflater inflater);

        void onPress();

        /**
         * @return whether this action should appear in the dialog when the keygaurd
         * is showing.
         */
        boolean showDuringKeyguard();

        /**
         * @return whether this action should appear in the dialog before the
         * device is provisioned.
         */
        boolean showBeforeProvisioning();

        boolean isEnabled();
    }

    /**
     * An action that also supports long press.
     */
    private interface LongPressAction extends Action {
        boolean onLongPress();
    }

    /**
     * A single press action maintains no state, just responds to a press
     * and takes an action.
     */
    private static abstract class SinglePressAction implements Action {
        private final int mIconResId;
        private final Drawable mIcon;
        private final int mMessageResId;
        private final CharSequence mMessage;

        protected SinglePressAction(int iconResId, int messageResId) {
            mIconResId = iconResId;
            mMessageResId = messageResId;
            mMessage = null;
            mIcon = null;
        }

        protected SinglePressAction(int iconResId, Drawable icon, CharSequence message) {
            mIconResId = iconResId;
            mMessageResId = 0;
            mMessage = message;
            mIcon = icon;
        }

        public boolean isEnabled() {
            return true;
        }

        public String getStatus() {
            return null;
        }

        abstract public void onPress();

        public CharSequence getLabelForAccessibility(Context context) {
            if (mMessage != null) {
                return mMessage;
            } else {
                return context.getString(mMessageResId);
            }
        }

        public View create(
                Context context, View convertView, ViewGroup parent, LayoutInflater inflater) {
            View v = inflater.inflate(com.android.systemui.R.layout.global_actions_item, parent,
                    false);

            ImageView icon = (ImageView) v.findViewById(R.id.icon);
            TextView messageView = (TextView) v.findViewById(R.id.message);

            TextView statusView = (TextView) v.findViewById(R.id.status);
            final String status = getStatus();
            if (!TextUtils.isEmpty(status)) {
                statusView.setText(status);
            } else {
                statusView.setVisibility(View.GONE);
            }
            if (mIcon != null) {
                icon.setImageDrawable(mIcon);
                icon.setScaleType(ScaleType.CENTER_CROP);
            } else if (mIconResId != 0) {
                icon.setImageDrawable(context.getDrawable(mIconResId));
            }
            if (mMessage != null) {
                messageView.setText(mMessage);
            } else {
                messageView.setText(mMessageResId);
            }

            return v;
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
                if (!SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS.equals(reason)) {
                    mHandler.sendEmptyMessage(MESSAGE_DISMISS);
                }
            }
        }
    };

    private static final int MESSAGE_DISMISS = 0;
    private static final int MESSAGE_REFRESH = 1;
    private static final int MESSAGE_SHOW = 2;
    private static final int DIALOG_DISMISS_DELAY = 300; // ms

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_DISMISS:
                    if (mDialog != null) {
                        mDialog.dismiss();
                        mDialog = null;
                    }
                    break;
                case MESSAGE_REFRESH:
                    mExtendedAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_SHOW:
                    handleShow();
                    break;
            }
        }
    };

    private static final class ExtendedActionsDialog extends Dialog implements DialogInterface,
            ColorExtractor.OnColorsChangedListener {

        private final Context mContext;
        private final ExtendedAdapter mExtendedAdapter;
        private final LinearLayout mListView;
        private final HardwareUiLayout mHardwareLayout;
        private final OnClickListener mClickListener;
        private final OnItemLongClickListener mLongClickListener;
        private final GradientDrawable mGradientDrawable;
        private final ColorExtractor mColorExtractor;
        private boolean mKeyguardShowing;

        public ExtendedActionsDialog(Context context, OnClickListener clickListener, ExtendedAdapter adapter,
                OnItemLongClickListener longClickListener) {
            super(context, com.android.systemui.R.style.Theme_SystemUI_Dialog_GlobalActions);
            mContext = context;
            mExtendedAdapter = adapter;
            mClickListener = clickListener;
            mLongClickListener = longClickListener;
            mGradientDrawable = new GradientDrawable(mContext);
            mColorExtractor = Dependency.get(SysuiColorExtractor.class);

            // Window initialization
            Window window = getWindow();
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            window.setBackgroundDrawable(mGradientDrawable);
            window.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);

            setContentView(com.android.systemui.R.layout.global_actions_wrapped);
            mListView = findViewById(android.R.id.list);
            mHardwareLayout = HardwareUiLayout.get(mListView);
            mHardwareLayout.setOutsideTouchListener(view -> dismiss());
        }

        private void updateList() {
            mListView.removeAllViews();
            for (int i = 0; i < mExtendedAdapter.getCount(); i++) {
                View v = mExtendedAdapter.getView(i, null, mListView);
                final int pos = i;
                v.setOnClickListener(view -> mClickListener.onClick(this, pos));
                v.setOnLongClickListener(view ->
                        mLongClickListener.onItemLongClick(null, v, pos, 0));
                mListView.addView(v);
            }
        }

        @Override
        protected void onStart() {
            super.setCanceledOnTouchOutside(true);
            super.onStart();
            updateList();

            Point displaySize = new Point();
            mContext.getDisplay().getRealSize(displaySize);
            mColorExtractor.addOnColorsChangedListener(this);
            mGradientDrawable.setScreenSize(displaySize.x, displaySize.y);
            GradientColors colors = mColorExtractor.getColors(mKeyguardShowing ?
                    WallpaperManager.FLAG_LOCK : WallpaperManager.FLAG_SYSTEM);
            mGradientDrawable.setColors(colors, false);
        }

        @Override
        protected void onStop() {
            super.onStop();
            mColorExtractor.removeOnColorsChangedListener(this);
        }

        @Override
        public void show() {
            super.show();
            mGradientDrawable.setAlpha(0);
            mHardwareLayout.setTranslationX(getAnimTranslation());
            mHardwareLayout.setAlpha(0);
            mHardwareLayout.animate()
                    .alpha(1)
                    .translationX(0)
                    .setDuration(300)
                    .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                    .setUpdateListener(animation -> {
                        int alpha = (int) ((Float) animation.getAnimatedValue()
                                * ScrimController.GRADIENT_SCRIM_ALPHA * 255);
                        mGradientDrawable.setAlpha(alpha);
                    })
                    .withEndAction(() -> getWindow().getDecorView().requestAccessibilityFocus())
                    .start();
        }

        @Override
        public void dismiss() {
            mHardwareLayout.setTranslationX(0);
            mHardwareLayout.setAlpha(1);
            mHardwareLayout.animate()
                    .alpha(0)
                    .translationX(getAnimTranslation())
                    .setDuration(300)
                    .withEndAction(() -> super.dismiss())
                    .setInterpolator(new LogAccelerateInterpolator())
                    .setUpdateListener(animation -> {
                        int alpha = (int) ((1f - (Float) animation.getAnimatedValue())
                                * ScrimController.GRADIENT_SCRIM_ALPHA * 255);
                        mGradientDrawable.setAlpha(alpha);
                    })
                    .start();
        }

        private float getAnimTranslation() {
            return getContext().getResources().getDimension(
                    com.android.systemui.R.dimen.global_actions_panel_width) / 2;
        }

        @Override
        public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                for (int i = 0; i < mExtendedAdapter.getCount(); ++i) {
                    CharSequence label =
                            mExtendedAdapter.getItem(i).getLabelForAccessibility(getContext());
                    if (label != null) {
                        event.getText().add(label);
                    }
                }
            }
            return super.dispatchPopulateAccessibilityEvent(event);
        }

        @Override
        public void onColorsChanged(ColorExtractor extractor, int which) {
            if (mKeyguardShowing) {
                if ((WallpaperManager.FLAG_LOCK & which) != 0) {
                    mGradientDrawable.setColors(extractor.getColors(WallpaperManager.FLAG_LOCK));
                }
            } else {
                if ((WallpaperManager.FLAG_SYSTEM & which) != 0) {
                    mGradientDrawable.setColors(extractor.getColors(WallpaperManager.FLAG_SYSTEM));
                }
            }
        }

        public void setKeyguardShowing(boolean keyguardShowing) {
            mKeyguardShowing = keyguardShowing;
        }
    }
}
