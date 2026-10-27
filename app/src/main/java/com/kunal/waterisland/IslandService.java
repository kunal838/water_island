package com.kunal.waterisland;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.Calendar;
import java.util.Locale;

/**
 * Foreground service that keeps the hourly reminders alive and posts the Android 16 Live Update
 * notification that the system (Vivo's island) renders around the camera.
 */
public class IslandService extends Service {
    static final String ACTION_START = "start";
    static final String ACTION_REMIND = "remind";
    static final String ACTION_REFRESH = "refresh";
    static final String ACTION_STOP = "stop";

    private static final int NOTIF_FG = 1;
    private static final int NOTIF_LIVE = 3;
    private static final String CH_FG = "island";
    private static final String CH_LIVE = "live";
    private static final String CH_LIVE_QUIET = "live_quiet";
    private static final int FLAME = 0xFFFF9F43;

    private static final int LIVE_PROMPT = 0;   // reminder: chip says "Drink!"
    private static final int LIVE_SETTLED = 1;  // chip shows litres, no new alert
    private static final int LIVE_LOGGED = 2;   // just logged water; clears shortly after

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable settleLive = () -> postLiveUpdate(LIVE_SETTLED);
    private final Runnable clearLive = () ->
            getSystemService(NotificationManager.class).cancel(NOTIF_LIVE);

    private static IslandService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    /**
     * Updates the island card after a drink arrives from the watch. Does nothing when the service isn't
     * running, which also avoids starting a foreground service from the background.
     */
    static void refreshFromWatch() {
        IslandService s = instance;
        if (s != null) s.handler.post(() -> s.postLiveUpdate(LIVE_LOGGED));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundNow();
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            Store.setEnabled(this, false);
            Alarms.cancel(this);
            handler.removeCallbacksAndMessages(null);
            getSystemService(NotificationManager.class).cancel(NOTIF_LIVE);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        Store.setEnabled(this, true);
        if (ACTION_START.equals(action)) Alarms.scheduleNext(this);
        else if (ACTION_REMIND.equals(action)) postLiveUpdate(LIVE_PROMPT);
        else if (ACTION_REFRESH.equals(action)) postLiveUpdate(LIVE_LOGGED);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private PendingIntent openApp() {
        return PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_FG, "Island running",
                NotificationManager.IMPORTANCE_MIN));
        nm.createNotificationChannel(new NotificationChannel(CH_LIVE, "Water live update",
                NotificationManager.IMPORTANCE_HIGH));
        NotificationChannel quiet = new NotificationChannel(CH_LIVE_QUIET, "Water live update (silent)",
                NotificationManager.IMPORTANCE_HIGH);
        quiet.setSound(null, null);
        quiet.enableVibration(false);
        nm.createNotificationChannel(quiet);
    }

    private void startForegroundNow() {
        createChannels();
        Notification n = new Notification.Builder(this, CH_FG)
                .setSmallIcon(R.drawable.ic_drop)
                .setContentTitle("Water Island is on")
                .setContentText("Reminding you every hour")
                .setContentIntent(openApp())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_FG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_FG, n);
        }
    }

    /**
     * Android 16 Live Update: an ongoing, promoted notification with progress. The system draws it as
     * the island chip and expanded card. Shown on each reminder, cleared after a while.
     */
    private void postLiveUpdate(int mode) {
        int ml = Store.todayMl(this);
        int glass = Store.glassMl(this);
        int goalGlasses = Store.goalGlasses(this);
        int streak = Store.streakDays(this);
        // Progress counts partial glasses (100 per glass) so the bar fills smoothly.
        int progress = Math.min(goalGlasses * 100, Math.round(100f * ml / glass));
        boolean island = Store.islandStyle(this);

        PendingIntent add = PendingIntent.getBroadcast(this, 1,
                new Intent(this, AddReceiver.class).putExtra("ml", glass),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean prompt = mode == LIVE_PROMPT;
        if (prompt) WearSync.remind(this, Store.glassesDrunk(this), goalGlasses);
        Notification.Builder b = new Notification.Builder(this, Store.sound(this) ? CH_LIVE : CH_LIVE_QUIET)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                // With watch reminders on, the watch gets its own card, so don't mirror this one too.
                .setLocalOnly(Store.watchReminders(this))
                .setSmallIcon(R.drawable.ic_drop)
                .setContentTitle(streakTitle(streak))
                .setContentText(streakText(streak))
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setOngoing(island)
                .setOnlyAlertOnce(!prompt)
                .setContentIntent(openApp())
                .addAction(new Notification.Action.Builder(null, "+1 glass", add).build());
        if (island) {
            String chip = prompt ? "Drink!" : streak == 0 ? "Start!" : streak + (streak == 1 ? " day" : " days");
            applyIsland(b, chip, goalGlasses, progress);
        } else {
            // Normal notification: a plain progress bar, and it can be swiped away. Some phones draw their
            // own generic app picture here, so give it the app icon explicitly.
            b.setProgress(goalGlasses * 100, progress, false).setAutoCancel(true).setLargeIcon(appIcon());
        }
        getSystemService(NotificationManager.class).notify(NOTIF_LIVE, b.build());

        handler.removeCallbacks(settleLive);
        if (prompt) {
            // "Drink!" shows briefly, then the chip settles back to litres without alerting again.
            handler.postDelayed(settleLive, 12_000L);
            handler.removeCallbacks(clearLive);
            handler.postDelayed(clearLive, 15 * 60_000L);
        } else if (mode == LIVE_LOGGED) {
            handler.removeCallbacks(clearLive);
            handler.postDelayed(clearLive, 8_000L);
        }
    }

    /**
     * Android 16 only: turns the notification into a Live Update, which the system draws as the island chip
     * (short text) and expanded card (one block per glass). Callers check {@link Store#islandStyle} first.
     */
    private void applyIsland(Notification.Builder b, String chip, int goalGlasses, int progress) {
        Notification.ProgressStyle style = new Notification.ProgressStyle().setStyledByProgress(true);
        for (int i = 0; i < goalGlasses; i++) {
            style.addProgressSegment(new Notification.ProgressStyle.Segment(100).setColor(FLAME));
        }
        style.setProgress(progress);
        b.setShortCriticalText(chip).setStyle(style);
        b.getExtras().putBoolean("android.requestPromotedOngoing", true);
    }

    /** The launcher icon (white drop outline on black) as a round picture for the notification. */
    private Bitmap appIcon() {
        int size = 192;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        Drawable drop = getDrawable(R.drawable.ic_launcher_fg);
        int pad = (int) (size * 0.35f);   // the drop sits in the middle third of its canvas, so enlarge it
        drop.setBounds(-pad, -pad, size + pad, size + pad);
        drop.draw(canvas);
        return bitmap;
    }

    private String streakTitle(int streak) {
        if (streak == 0) return "Start a streak";
        return streak + " day streak";
    }

    /** Today's progress and what is needed to keep or start the streak. */
    private String streakText(int streak) {
        int goal = Store.goalGlasses(this);
        int drunk = Store.glassesDrunk(this);
        if (drunk >= goal) return "Goal done today, streak safe";
        int left = goal - drunk;
        return drunk + " of " + goal + " today · " + left + (left == 1 ? " glass" : " glasses")
                + (streak == 0 ? " to start it" : " to keep it alive");
    }
}
