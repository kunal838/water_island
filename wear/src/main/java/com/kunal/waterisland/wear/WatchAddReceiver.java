package com.kunal.waterisland.wear;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The "+1 glass" button on the watch reminder: adds one glass and clears the card. */
public class WatchAddReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        WatchData.addDrink(c, 1);
        c.getSystemService(NotificationManager.class).cancel(WatchSyncService.NOTIF_ID);
    }
}
