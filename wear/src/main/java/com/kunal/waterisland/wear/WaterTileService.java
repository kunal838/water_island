package com.kunal.waterisland.wear;

import androidx.annotation.NonNull;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Watch tile: today's glasses, with 250, 333 and 500 ml buttons that add water straight away. */
public class WaterTileService extends TileService {
    private static final String RES_VERSION = "1";
    private static final int[] AMOUNTS = {250, 333, 500};

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(
            @NonNull RequestBuilders.TileRequest request) {
        String clicked = request.getCurrentState().getLastClickableId();
        if (clicked != null && clicked.startsWith("add")) {
            WatchData.addDrink(this, Integer.parseInt(clicked.substring(3)));
        }

        int glassMl = Math.max(1, WatchData.get(this, "glassMl", 333));
        int glasses = Math.round((float) WatchData.totalMl(this) / glassMl);
        int goal = Math.max(1, WatchData.get(this, "goalGlasses", 10));

        LayoutElementBuilders.Row.Builder row = new LayoutElementBuilders.Row.Builder();
        for (int i = 0; i < AMOUNTS.length; i++) {
            if (i > 0) row.addContent(spacer(6));
            row.addContent(pill(AMOUNTS[i], AMOUNTS[i] == glassMl));
        }

        LayoutElementBuilders.Column column = new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(text("Today", 13, 0xFF9AA5B1, false))
                .addContent(text(glasses + " / " + goal, 36, 0xFFFFFFFF, true))
                .addContent(spacer(10))
                .addContent(row.build())
                .build();

        LayoutElementBuilders.Box root = new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(column)
                .build();

        return Futures.immediateFuture(new TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build());
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(
            @NonNull RequestBuilders.ResourcesRequest request) {
        return Futures.immediateFuture(new ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build());
    }

    private static LayoutElementBuilders.LayoutElement text(String s, float sp, int color, boolean bold) {
        return new LayoutElementBuilders.Text.Builder()
                .setText(s)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(sp))
                        .setColor(ColorBuilders.argb(color))
                        .setWeight(bold ? LayoutElementBuilders.FONT_WEIGHT_BOLD : LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .build())
                .build();
    }

    private static LayoutElementBuilders.LayoutElement spacer(float dp) {
        return new LayoutElementBuilders.Spacer.Builder()
                .setWidth(DimensionBuilders.dp(dp))
                .setHeight(DimensionBuilders.dp(dp))
                .build();
    }

    private static LayoutElementBuilders.LayoutElement pill(int ml, boolean preferred) {
        ModifiersBuilders.Clickable click = new ModifiersBuilders.Clickable.Builder()
                .setId("add" + ml)
                .setOnClick(new ActionBuilders.LoadAction.Builder().build())
                .build();
        ModifiersBuilders.Modifiers modifiers = new ModifiersBuilders.Modifiers.Builder()
                .setClickable(click)
                .setBackground(new ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(preferred ? 0xFFFFFFFF : 0x33FFFFFF))
                        .setCorner(new ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(18)).build())
                        .build())
                .build();
        return new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(58))
                .setHeight(DimensionBuilders.dp(38))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(modifiers)
                .addContent(text(String.valueOf(ml), 14, preferred ? 0xFF000000 : 0xFFFFFFFF, true))
                .build();
    }
}
