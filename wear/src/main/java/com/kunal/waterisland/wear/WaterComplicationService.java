package com.kunal.waterisland.wear;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.RemoteException;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.NoDataComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.RangedValueComplicationData;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;

/** Watch-face complication: a ring or short text with today's glasses against the goal. */
public class WaterComplicationService extends ComplicationDataSourceService {

    @Override
    public ComplicationData getPreviewData(ComplicationType type) {
        return build(type, 6, 10);
    }

    @Override
    public void onComplicationRequest(ComplicationRequest request, ComplicationRequestListener listener) {
        int glassMl = Math.max(1, WatchData.get(this, "glassMl", 333));
        int glasses = Math.round((float) WatchData.totalMl(this) / glassMl);
        int goal = Math.max(1, WatchData.get(this, "goalGlasses", 10));
        try {
            listener.onComplicationData(build(request.getComplicationType(), glasses, goal));
        } catch (RemoteException ignored) {
            // The watch face went away; nothing to update.
        }
    }

    private ComplicationData build(ComplicationType type, int glasses, int goal) {
        PlainComplicationText description =
                new PlainComplicationText.Builder(glasses + " of " + goal + " glasses").build();
        PlainComplicationText text = new PlainComplicationText.Builder(glasses + "/" + goal).build();
        MonochromaticImage icon = new MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.ic_drop)).build();
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, WatchActivity.class), PendingIntent.FLAG_IMMUTABLE);

        if (type == ComplicationType.RANGED_VALUE) {
            return new RangedValueComplicationData.Builder(Math.min(glasses, goal), 0f, goal, description)
                    .setText(text)
                    .setMonochromaticImage(icon)
                    .setTapAction(open)
                    .build();
        }
        if (type == ComplicationType.SHORT_TEXT) {
            return new ShortTextComplicationData.Builder(text, description)
                    .setMonochromaticImage(icon)
                    .setTapAction(open)
                    .build();
        }
        return new NoDataComplicationData();
    }
}
