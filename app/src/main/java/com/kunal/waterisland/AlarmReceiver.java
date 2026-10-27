package com.kunal.waterisland;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Store.enabled(c)) return;
        Alarms.scheduleNext(c);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < Store.START_HOUR || hour >= Store.END_HOUR) return;
        c.startForegroundService(new Intent(c, IslandService.class).setAction(IslandService.ACTION_REMIND));
    }
}
