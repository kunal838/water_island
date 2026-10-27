package com.kunal.waterisland.wear;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.StandardCharsets;

/**
 * Background link to the phone: keeps the watch's copy fresh for the tile and complication, and turns the
 * phone's "/remind" message into a buzzing reminder card with a +1 glass button.
 */
public class WatchSyncService extends WearableListenerService {
    static final int NOTIF_ID = 1;
    private static final String CHANNEL = "reminder";

    @Override
    public void onDataChanged(DataEventBuffer events) {
        for (DataEvent e : events) {
            if (e.getType() == DataEvent.TYPE_CHANGED && "/state".equals(e.getDataItem().getUri().getPath())) {
                WatchData.saveState(this, DataMapItem.fromDataItem(e.getDataItem()).getDataMap());
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        if ("/remind".equals(event.getPath())) {
            showReminder(new String(event.getData(), StandardCharsets.UTF_8));
        }
    }

    private void showReminder(String payload) {
        String[] parts = payload.split("\\|");
        String text = parts.length == 2 ? parts[0] + " of " + parts[1] + " glasses today" : "Have a glass of water";

        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Water reminders",
                NotificationManager.IMPORTANCE_HIGH);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 300, 150, 300});
        nm.createNotificationChannel(channel);

        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, WatchActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent add = PendingIntent.getBroadcast(this, 1, new Intent(this, WatchAddReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_drop)
                .setContentTitle("Time to drink water")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "+1 glass", add).build())
                .build();
        nm.notify(NOTIF_ID, n);
        buzz();
    }

    /**
     * Vibrates directly. The system can switch off notification vibration for every app (it does this
     * while the watch isn't worn), so the reminder doesn't rely on the notification to get your attention.
     */
    private void buzz() {
        Vibrator vibrator = getSystemService(VibratorManager.class).getDefaultVibrator();
        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 150, 300}, -1));
    }
}
