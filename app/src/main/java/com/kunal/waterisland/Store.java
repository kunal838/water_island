package com.kunal.waterisland;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Persisted settings and today's intake. The total resets when the calendar day changes. */
final class Store {
    static final int START_HOUR = 8;   // first reminder
    static final int END_HOUR = 22;    // no reminders at or after this hour
    static final int[] CHOICES_ML = {150, 250, 333, 500};

    private Store() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("water", Context.MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    static int glassMl(Context c) { return p(c).getInt("glassMl", 333); }
    static void setGlassMl(Context c, int ml) {
        p(c).edit().putInt("glassMl", ml).apply();
        WearSync.push(c);
    }

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

    static boolean sound(Context c) { return p(c).getBoolean("sound", true); }
    static void setSound(Context c, boolean on) { p(c).edit().putBoolean("sound", on).apply(); }

    static int todayMl(Context c) {
        SharedPreferences s = p(c);
        return today().equals(s.getString("day", "")) ? s.getInt("ml", 0) : 0;
    }

    /** Adds water and records it in today's log as "epochMs:ml" entries separated by ';'. */
    static void addMl(Context c, int ml) { adjustMl(c, ml); }

    /**
     * Adds (positive) or removes (negative) water for today. The total never goes below 0, and the log
     * records the amount actually applied so undo restores exactly what changed.
     */
    static void adjustMl(Context c, int delta) {
        SharedPreferences s = p(c);
        boolean sameDay = today().equals(s.getString("day", ""));
        String log = sameDay ? s.getString("log", "") : "";
        int before = sameDay ? s.getInt("ml", 0) : 0;
        int total = Math.max(0, before + delta);
        int applied = total - before;
        if (applied == 0) return;
        log += (log.isEmpty() ? "" : ";") + System.currentTimeMillis() + ":" + applied;
        s.edit().putString("day", today()).putInt("ml", total).putString("log", log)
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

    /** Reverses the most recent entry in today's log. Returns false when there is nothing to undo. */
    static boolean undoLast(Context c) {
        List<long[]> entries = todayLog(c);
        if (entries.isEmpty()) return false;
        long[] last = entries.remove(entries.size() - 1);
        StringBuilder log = new StringBuilder();
        for (long[] e : entries) log.append(log.length() == 0 ? "" : ";").append(e[0]).append(':').append(e[1]);
        int total = Math.max(0, p(c).getInt("ml", 0) - (int) last[1]);
        p(c).edit().putInt("ml", total).putString("log", log.toString())
                .putInt(dayKey(new Date()), total).apply();
        WearSync.push(c);
        return true;
    }

    static void resetToday(Context c) {
        p(c).edit().putString("day", today()).putInt("ml", 0).putString("log", "")
                .putInt(dayKey(new Date()), 0).apply();
        WearSync.push(c);
    }

    private static String dayKey(Date d) {
        return "d_" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(d);
    }

    /** Millilitres per day for the current Monday-to-Sunday week; days not yet reached are 0. */
    static int[] weekMl(Context c) { return weekMl(c, 0); }

    /** Same as {@link #weekMl(Context)} for an earlier week; 1 is last week. Days with no history are 0. */
    static int[] weekMl(Context c, int weeksBack) {
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
        if (weeksBack == 0) out[back] = todayMl(c);
        return out;
    }

    /** Every saved day as ISO date (yyyy-MM-dd) to millilitres, oldest first; today is always included. */
    static java.util.TreeMap<String, Integer> dailyTotals(Context c) {
        java.util.TreeMap<String, Integer> out = new java.util.TreeMap<>();
        for (java.util.Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
            String k = e.getKey();
            if (k.startsWith("d_") && k.length() == 10 && e.getValue() instanceof Integer) {
                out.put(k.substring(2, 6) + "-" + k.substring(6, 8) + "-" + k.substring(8, 10), (Integer) e.getValue());
            }
        }
        out.put(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()), todayMl(c));
        return out;
    }

    /**
     * Consecutive days that reached the daily goal. Today counts once it is met; if it isn't met yet the
     * streak is what ran up to yesterday, so it only breaks when a full day is missed.
     */
    static int streakDays(Context c) {
        int goal = goalMl(c);
        if (goal <= 0) return 0;
        SharedPreferences s = p(c);
        int streak = todayMl(c) >= goal ? 1 : 0;
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

    /** Today's drinks, oldest first: each entry is {epochMs, ml}. */
    static List<long[]> todayLog(Context c) {
        SharedPreferences s = p(c);
        List<long[]> out = new ArrayList<>();
        String log = today().equals(s.getString("day", "")) ? s.getString("log", "") : "";
        if (log.isEmpty()) return out;
        for (String e : log.split(";")) {
            String[] parts = e.split(":");
            if (parts.length == 2) out.add(new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])});
        }
        return out;
    }

    static int goalMl(Context c) { return goalGlasses(c) * glassMl(c); }

    static int glassesDrunk(Context c) { return todayMl(c) / glassMl(c); }
}
