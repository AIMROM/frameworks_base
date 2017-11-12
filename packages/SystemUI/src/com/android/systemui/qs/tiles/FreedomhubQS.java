package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.widget.Toast;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import android.service.quicksettings.Tile;
import com.android.systemui.qs.QSTileView;

public class FreedomhubQS extends QSTileImpl<BooleanState> {
    private boolean mListening;

    private static final Intent FREEDOMHUB = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$freedomhubSettingsActivity"));

    public FreedomhubQS(Host host) {
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
                R.string.quick_freedomhub_toast),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_freedomhub_label);
    }

    protected void startfreedomhub() {
        mHost.startActivityDismissingKeyguard(FREEDOMHUB);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_freedomhub);
        state.label = mContext.getString(R.string.quick_freedomhub_label);
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}
