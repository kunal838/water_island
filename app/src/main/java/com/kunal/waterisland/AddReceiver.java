package com.kunal.waterisland;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the "+1 glass" and "-1 glass" actions on the reminder notification. */
public class AddReceiver extends BroadcastReceiver {
    static final String EXTRA_DELTA = "delta";

    @Override
    public void onReceive(Context c, Intent intent) {
        Store.addGlasses(c, intent.getIntExtra(EXTRA_DELTA, 1) < 0 ? -1 : 1);
        c.startForegroundService(new Intent(c, IslandService.class).setAction(IslandService.ACTION_REFRESH));
    }
}
