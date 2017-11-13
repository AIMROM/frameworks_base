package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Toast;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.SysUIToast;
import android.service.quicksettings.Tile;
import com.android.systemui.qs.tileimpl.QSTileView;

public class FreedomhubQS extends QSTileImpl<BooleanState> {
    private boolean mListening;

//    private static final Intent FREEDOMHUB = new Intent().setComponent(new ComponentName(
//            "com.android.settings", "com.android.settings.Settings$freedomhubSettingsActivity"));

    public FreedomhubQS(QSHost host) {
        super(host);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.FREEDOMHUB;
    }

    @Override
    protected void handleClick() {
        mHost.collapsePanels();
        startfreedomhub();
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleLongClick() {
        // Collapse the panels, so the user can see the toast.
        mHost.collapsePanels();
        SysUIToast.makeText(mContext, mContext.getString(
                R.string.quick_freedom_toast),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_freedom_label);
    }

    protected Intent startfreedomhub() {
//        mHost.startActivityDismissingKeyguard(FREEDOMHUB);
        return new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$freedomhubSettingsActivity"));

    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_freedomhub);
        state.label = mContext.getString(R.string.quick_freedom_label);
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}
