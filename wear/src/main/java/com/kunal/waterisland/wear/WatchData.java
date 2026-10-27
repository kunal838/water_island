package com.kunal.waterisland.wear;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.TileService;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;

import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The watch's copy of the phone's state, plus drinks sent to the phone that it hasn't confirmed yet.
 * Shared by the app, the tile and the complication so all three show and send the same thing.
 */
final class WatchData {
    private static final long PENDING_MAX_AGE_MS = 2L * 24 * 60 * 60 * 1000;

    private WatchData() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("watch", Context.MODE_PRIVATE);
    }

    static int get(Context c, String key, int def) { return prefs(c).getInt(key, def); }

    static int[] week(Context c) {
        int[] out = new int[7];
        String[] parts = prefs(c).getString("week", "").split(",");
        for (int i = 0; i < 7 && i < parts.length; i++) {
            try { out[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    private static List<String> pendingEntries(Context c) {
        String raw = prefs(c).getString("pending", "");
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) out.addAll(Arrays.asList(raw.split(";")));
        return out;
    }

    static int pendingMl(Context c) {
        int sum = 0;
        for (String e : pendingEntries(c)) {
            String[] p = e.split("\\|");
            if (p.length == 3) sum += Integer.parseInt(p[1]);
        }
        return sum;
    }

    /** Today's millilitres as the watch should show them: the phone's count plus unconfirmed drinks. */
    static int totalMl(Context c) { return get(c, "todayMl", 0) + pendingMl(c); }

    static void saveState(Context c, DataMap m) {
        List<String> applied = m.getStringArrayList("applied");
        List<String> keep = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String e : pendingEntries(c)) {
            String[] p = e.split("\\|");
            if (p.length != 3) continue;
            boolean confirmed = applied != null && applied.contains(p[0]);
            boolean stale = now - Long.parseLong(p[2]) > PENDING_MAX_AGE_MS;
            if (!confirmed && !stale) keep.add(e);
        }
        ArrayList<Integer> week = m.getIntegerArrayList("week");
        StringBuilder w = new StringBuilder();
        if (week != null) for (int ml : week) w.append(w.length() == 0 ? "" : ",").append(ml);
        prefs(c).edit()
                .putInt("todayMl", m.getInt("todayMl"))
                .putInt("goalMl", m.getInt("goalMl", 3330))
                .putInt("glassMl", m.getInt("glassMl", 333))
                .putInt("goalGlasses", m.getInt("goalGlasses", 10))
                .putInt("streak", m.getInt("streak"))
                .putInt("todayIdx", m.getInt("todayIdx"))
                .putString("week", w.toString())
                .putString("pending", String.join(";", keep))
                .apply();
        requestRefresh(c);
    }

    /** Records a drink locally and sends it to the phone, which stays the master copy. */
    static void addDrink(Context c, int ml) {
        String id = UUID.randomUUID().toString();
        List<String> pending = pendingEntries(c);
        pending.add(id + "|" + ml + "|" + System.currentTimeMillis());
        prefs(c).edit().putString("pending", String.join(";", pending)).apply();

        PutDataMapRequest req = PutDataMapRequest.create("/add/" + id);
        req.getDataMap().putInt("delta", ml);
        Wearable.getDataClient(c).putDataItem(req.asPutDataRequest().setUrgent());
        requestRefresh(c);
    }

    /** Asks the watch to redraw the complication and the tile with the latest numbers. */
    static void requestRefresh(Context c) {
        try {
            ComplicationDataSourceUpdateRequester
                    .create(c, new ComponentName(c, WaterComplicationService.class))
                    .requestUpdateAll();
            TileService.getUpdater(c).requestUpdate(WaterTileService.class);
        } catch (RuntimeException ignored) {
            // Not on a watch face or tile carousel right now.
        }
    }
}
