package com.kunal.waterisland;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the "+ glass" action on the fallback reminder notification. */
public class AddReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Store.addMl(c, intent.getIntExtra("ml", Store.glassMl(c)));
        c.startForegroundService(new Intent(c, IslandService.class).setAction(IslandService.ACTION_REFRESH));
    }
}
