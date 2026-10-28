package com.kunal.waterisland;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Persisted settings and today's intake. Everything is counted in glasses, and 3 glasses make 1 litre.
 * The total resets when the calendar day changes.
 */
final class Store {
    static final int START_HOUR = 8;   // first reminder
    static final int END_HOUR = 22;    // no reminders at or after this hour
    static final int GLASSES_PER_LITRE = 3;
    private static final int MAX_GLASSES = 60;

    private Store() {}

    private static SharedPreferences p(Context c) {
        SharedPreferences s = c.getSharedPreferences("water", Context.MODE_PRIVATE);
        if (!s.getBoolean("inGlasses", false)) migrateToGlasses(s);
        return s;
    }

    /**
     * Older versions stored millilitres. A glass is a third of a litre, so convert today's total, every saved
     * day and today's log once, then drop the old keys.
     */
    private static void migrateToGlasses(SharedPreferences s) {
        SharedPreferences.Editor e = s.edit();
        for (Map.Entry<String, ?> en : s.getAll().entrySet()) {
            String k = en.getKey();
            if (k.startsWith("d_") && en.getValue() instanceof Integer) {
                e.putInt("dg_" + k.substring(2), mlToGlasses((Integer) en.getValue()));
                e.remove(k);
            }
        }
        if (s.contains("ml")) e.putInt("glasses", mlToGlasses(s.getInt("ml", 0)));
        String old = s.getString("log", "");
        if (!old.isEmpty()) {
            StringBuilder log = new StringBuilder();
            for (String entry : old.split(";")) {
                String[] parts = entry.split(":");
                if (parts.length != 2) continue;
                int ml = Integer.parseInt(parts[1]);
                int glasses = Math.max(1, mlToGlasses(Math.abs(ml))) * (ml < 0 ? -1 : 1);
                log.append(log.length() == 0 ? "" : ";").append(parts[0]).append(':').append(glasses);
            }
            e.putString("logg", log.toString());
        }
        e.remove("ml").remove("log").remove("glassMl").putBoolean("inGlasses", true).apply();
    }

    private static int mlToGlasses(int ml) { return Math.round(ml * (float) GLASSES_PER_LITRE / 1000f); }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    /** Litres for a number of glasses, using 3 glasses = 1 litre. */
    static float litres(int glasses) { return glasses / (float) GLASSES_PER_LITRE; }

    static int goalGlasses(Context c) { return p(c).getInt("goalGlasses", 10); }
    static void setGoalGlasses(Context c, int n) {
        p(c).edit().putInt("goalGlasses", Math.max(1, Math.min(30, n))).apply();
        WearSync.push(c);
    }

    static boolean enabled(Context c) { return p(c).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean on) { p(c).edit().putBoolean("enabled", on).apply(); }

    /** Seconds between reminders; 3600 means the top of every hour, anything shorter is for testing. */
    static final int[] INTERVALS_SEC = {15, 30, 60, 300, 900, 1800, 3600};
    static int intervalSec(Context c) { return p(c).getInt("intervalSec", 3600); }
    static void setIntervalSec(Context c, int s) { p(c).edit().putInt("intervalSec", s).apply(); }

    /**
     * True when reminders should use the Android 16 Live Update "island"; false means a normal notification.
     * Phones older than Android 16 have no island, so they always get the normal notification.
     */
    static boolean islandStyle(Context c) {
        return android.os.Build.VERSION.SDK_INT >= 36 && p(c).getBoolean("island", true);
    }
    static void setIslandStyle(Context c, boolean on) { p(c).edit().putBoolean("island", on).apply(); }

    /** Whether each reminder also buzzes the paired watch (and the phone's own copy stays off the watch). */
    static boolean watchReminders(Context c) { return p(c).getBoolean("watchReminders", true); }
    static void setWatchReminders(Context c, boolean on) { p(c).edit().putBoolean("watchReminders", on).apply(); }

    /** Look of the watch's Today page. The order matches the watch app's drawing code. */
    static final String[] WATCH_STYLES = com.kunal.waterisland.wear.TodayView.NAMES;
    static final int DEFAULT_WATCH_STYLE = 6;

    static int watchStyle(Context c) {
        int s = p(c).getInt("watchStyle", DEFAULT_WATCH_STYLE);
        return s >= 0 && s < WATCH_STYLES.length ? s : DEFAULT_WATCH_STYLE;
    }
    /** When the style last changed, in epoch ms; the newer of the phone's and the watch's choice wins. */
    static long watchStyleAt(Context c) { return p(c).getLong("watchStyleAt", 0L); }
    static void setWatchStyle(Context c, int style, long at) {
        if (style < 0 || style >= WATCH_STYLES.length) return;
        p(c).edit().putInt("watchStyle", style).putLong("watchStyleAt", at).apply();
        WearSync.push(c);
    }

    static boolean sound(Context c) { return p(c).getBoolean("sound", true); }
    static void setSound(Context c, boolean on) { p(c).edit().putBoolean("sound", on).apply(); }

    static int todayGlasses(Context c) {
        SharedPreferences s = p(c);
        return today().equals(s.getString("day", "")) ? s.getInt("glasses", 0) : 0;
    }

    /**
     * Adds (positive) or removes (negative) glasses for today. The total stays between 0 and a sane maximum,
     * and the log records the change that was actually applied.
     */
    static void addGlasses(Context c, int delta) {
        SharedPreferences s = p(c);
        boolean sameDay = today().equals(s.getString("day", ""));
        String log = sameDay ? s.getString("logg", "") : "";
        int before = sameDay ? s.getInt("glasses", 0) : 0;
        int total = Math.max(0, Math.min(MAX_GLASSES, before + delta));
        int applied = total - before;
        if (applied == 0) return;
        log += (log.isEmpty() ? "" : ";") + System.currentTimeMillis() + ":" + applied;
        s.edit().putString("day", today()).putInt("glasses", total).putString("logg", log)
                .putInt(dayKey(new Date()), total).apply();
        WearSync.push(c);
    }

    /** Ids of watch commands already applied (newest last, capped), so a redelivered command counts once. */
    static List<String> wearApplied(Context c) {
        String raw = p(c).getString("wearApplied", "");
        List<String> ids = new ArrayList<>();
        if (!raw.isEmpty()) for (String id : raw.split(",")) ids.add(id);
        return ids;
    }

    /** Returns true the first time an id is seen; false if it was already applied. */
    static boolean wearMarkApplied(Context c, String id) {
        List<String> ids = wearApplied(c);
        if (ids.contains(id)) return false;
        ids.add(id);
        while (ids.size() > 30) ids.remove(0);
        p(c).edit().putString("wearApplied", String.join(",", ids)).apply();
        return true;
    }

    static void resetToday(Context c) {
        p(c).edit().putString("day", today()).putInt("glasses", 0).putString("logg", "")
                .putInt(dayKey(new Date()), 0).apply();
        WearSync.push(c);
    }

    private static String dayKey(Date d) {
        return "dg_" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(d);
    }

    /** Glasses per day for the current Monday-to-Sunday week; days not yet reached are 0. */
    static int[] weekGlasses(Context c) { return weekGlasses(c, 0); }

    /** Same as {@link #weekGlasses(Context)} for an earlier week; 1 is last week. Days with no history are 0. */
    static int[] weekGlasses(Context c, int weeksBack) {
        SharedPreferences s = p(c);
        int[] out = new int[7];
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        int back = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
        cal.add(Calendar.DAY_OF_YEAR, -back - 7 * weeksBack);
        for (int i = 0; i < 7; i++) {
            out[i] = s.getInt(dayKey(cal.getTime()), 0);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        if (weeksBack == 0) out[back] = todayGlasses(c);
        return out;
    }

    /** Every saved day as ISO date (yyyy-MM-dd) to glasses, oldest first; today is always included. */
    static TreeMap<String, Integer> dailyTotals(Context c) {
        TreeMap<String, Integer> out = new TreeMap<>();
        for (Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
            String k = e.getKey();
            if (k.startsWith("dg_") && k.length() == 11 && e.getValue() instanceof Integer) {
                out.put(k.substring(3, 7) + "-" + k.substring(7, 9) + "-" + k.substring(9, 11), (Integer) e.getValue());
            }
        }
        out.put(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()), todayGlasses(c));
        return out;
    }

    /**
     * Consecutive days that reached the daily goal. Today counts once it is met; if it isn't met yet the
     * streak is what ran up to yesterday, so it only breaks when a full day is missed.
     */
    static int streakDays(Context c) {
        int goal = goalGlasses(c);
        SharedPreferences s = p(c);
        int streak = todayGlasses(c) >= goal ? 1 : 0;
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 365; i++) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
            if (s.getInt(dayKey(cal.getTime()), 0) < goal) break;
            streak++;
        }
        return streak;
    }

    /** Index of today in the Monday-to-Sunday week (Monday = 0). */
    static int todayIndex() {
        return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    /** Today's changes, oldest first: each entry is {epochMs, glasses}, positive when added. */
    static List<long[]> todayLog(Context c) {
        SharedPreferences s = p(c);
        List<long[]> out = new ArrayList<>();
        String log = today().equals(s.getString("day", "")) ? s.getString("logg", "") : "";
        if (log.isEmpty()) return out;
        for (String e : log.split(";")) {
            String[] parts = e.split(":");
            if (parts.length == 2) out.add(new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])});
        }
        return out;
    }
}
