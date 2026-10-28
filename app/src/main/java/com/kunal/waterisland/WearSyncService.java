package com.kunal.waterisland;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/** Receives "/add/<id>" commands from the watch, applies each once, then removes it. */
public class WearSyncService extends WearableListenerService {
    @Override
    public void onDataChanged(DataEventBuffer events) {
        for (DataEvent event : events) {
            if (event.getType() != DataEvent.TYPE_CHANGED) continue;
            DataItem item = event.getDataItem();
            String path = item.getUri().getPath();
            if (path == null || !path.startsWith(WearSync.PATH_ADD)) continue;

            String id = path.substring(WearSync.PATH_ADD.length());
            int delta = DataMapItem.fromDataItem(item).getDataMap().getInt("delta");
            // The watch only sends +1 or -1 glass. Ignore anything else, and anything already applied.
            if (Math.abs(delta) <= 5 && delta != 0 && Store.wearMarkApplied(this, id)) {
                Store.addGlasses(this, delta);
                IslandService.refreshFromWatch();
            }
            Wearable.getDataClient(this).deleteDataItems(item.getUri());
        }
        WearSync.push(this);
    }
}
