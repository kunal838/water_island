package com.kunal.waterisland;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Store.enabled(c)) return;
        try {
            c.startForegroundService(new Intent(c, IslandService.class).setAction(IslandService.ACTION_START));
        } catch (RuntimeException e) {
            // Background start refused by the OS; the next hourly alarm or opening the app restarts it.
            Alarms.scheduleNext(c);
        }
    }
}
