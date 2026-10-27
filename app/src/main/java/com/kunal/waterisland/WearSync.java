package com.kunal.waterisland;

import android.content.Context;

import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;

/**
 * Publishes the phone's state to the watch over the Wear Data Layer. The phone is the master copy:
 * the watch only sends "add" commands (see {@link WearSyncService}) and reads this state back.
 */
final class WearSync {
    static final String PATH_STATE = "/state";
    static final String PATH_ADD = "/add/";

    private WearSync() {}

    static final String PATH_REMIND = "/remind";

    /** Tells every connected watch to buzz and show the reminder. Does nothing if the switch is off. */
    static void remind(Context c, int glasses, int goal) {
        if (!Store.watchReminders(c)) return;
        try {
            byte[] payload = (glasses + "|" + goal).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Wearable.getNodeClient(c).getConnectedNodes().addOnSuccessListener(nodes -> {
                for (com.google.android.gms.wearable.Node node : nodes) {
                    Wearable.getMessageClient(c).sendMessage(node.getId(), PATH_REMIND, payload);
                }
            });
        } catch (RuntimeException ignored) {
            // No Play services or no watch: the phone reminder still shows.
        }
    }

    static void push(Context c) {
        try {
            PutDataMapRequest req = PutDataMapRequest.create(PATH_STATE);
            DataMap m = req.getDataMap();
            m.putInt("todayMl", Store.todayMl(c));
            m.putInt("goalMl", Store.goalMl(c));
            m.putInt("glassMl", Store.glassMl(c));
            m.putInt("goalGlasses", Store.goalGlasses(c));
            m.putInt("streak", Store.streakDays(c));
            m.putInt("todayIdx", Store.todayIndex());
            ArrayList<Integer> week = new ArrayList<>();
            for (int ml : Store.weekMl(c)) week.add(ml);
            m.putIntegerArrayList("week", week);
            m.putStringArrayList("applied", new ArrayList<>(Store.wearApplied(c)));
            m.putLong("sentAt", System.currentTimeMillis());   // makes every push a change
            Wearable.getDataClient(c).putDataItem(req.asPutDataRequest().setUrgent());
        } catch (RuntimeException ignored) {
            // No Play services or no watch: the phone app works on its own.
        }
    }
}
