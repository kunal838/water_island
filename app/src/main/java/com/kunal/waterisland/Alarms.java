package com.kunal.waterisland;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Schedules one alarm at the top of the next hour; the receiver reschedules itself. */
final class Alarms {
    private Alarms() {}

    private static PendingIntent pending(Context c) {
        return PendingIntent.getBroadcast(c, 0, new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean canScheduleExact(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    static void scheduleNext(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        Calendar cal = Calendar.getInstance();
        int every = Store.intervalSec(c);
        if (every >= 3600) {
            cal.add(Calendar.HOUR_OF_DAY, 1);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        } else {
            cal.add(Calendar.SECOND, every);
        }
        long at = cal.getTimeInMillis();
        if (canScheduleExact(c)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c));
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c));
        }
    }

    static void cancel(Context c) {
        c.getSystemService(AlarmManager.class).cancel(pending(c));
    }
}
