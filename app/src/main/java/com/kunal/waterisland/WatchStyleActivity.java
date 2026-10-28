package com.kunal.waterisland;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kunal.waterisland.wear.TodayView;

import java.util.ArrayList;
import java.util.List;

/**
 * Gallery of the watch's Today styles. Every card plays that style's animation on a loop; tapping a card
 * makes it the watch's style (the watch follows through the Data Layer).
 */
public class WatchStyleActivity extends Activity {
    private static final int GRAY = 0xFF9AA5B1;

    private final List<TodayView> previews = new ArrayList<>();
    private final List<LinearLayout> cards = new ArrayList<>();
    private final List<TextView> names = new ArrayList<>();
    private final boolean[] running = new boolean[TodayView.NAMES.length];
    private ScrollView sv;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(32));
        page.setBackgroundColor(0xFF111111);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, Color.WHITE, false);
        back.setPadding(dp(4), 0, dp(14), dp(4));
        back.setOnClickListener(v -> finish());
        header.addView(back);
        header.addView(text("Watch style", 22, Color.WHITE, true));
        page.addView(header);
        TextView hint = text("Tap a style to use it on your watch.", 13, GRAY, false);
        hint.setPadding(dp(4), dp(6), 0, dp(14));
        page.addView(hint);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int cardW = (screenW - dp(40) - dp(12)) / 2;
        LinearLayout row = null;
        for (int i = 0; i < TodayView.NAMES.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.bottomMargin = dp(12);
                page.addView(row, rp);
            }
            final int which = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dp(10), dp(12), dp(10), dp(12));
            card.setOnClickListener(v -> {
                Store.setWatchStyle(this, which, System.currentTimeMillis());
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                refresh();
            });
            // The card dips a little while pressed, so a tap visibly lands. Returning false keeps the click.
            card.setOnTouchListener((v, e) -> {
                int a = e.getActionMasked();
                if (a == android.view.MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                }
                return false;
            });

            TodayView preview = new TodayView(this);
            preview.setStyle(which);
            preview.setBackground(circle(Color.BLACK));
            preview.setClipToOutline(true);
            preview.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
            });
            int size = cardW - dp(20);
            card.addView(preview, new LinearLayout.LayoutParams(size, size));

            TextView name = text(TodayView.NAMES[i], 14, Color.WHITE, false);
            name.setPadding(0, dp(10), 0, 0);
            card.addView(name);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(cardW, -2);
            if (i % 2 == 1) cp.leftMargin = dp(12);
            row.addView(card, cp);
            previews.add(preview);
            cards.add(card);
            names.add(name);
        }

        sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF111111);
        sv.addView(page);
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> updateVisible());
        sv.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(sv);
    }

    private GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Outlines the card in use and marks its name. */
    private void refresh() {
        int current = Store.watchStyle(this);
        for (int i = 0; i < cards.size(); i++) {
            boolean on = i == current;
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF1B1B1D);
            bg.setCornerRadius(dp(20));
            if (on) bg.setStroke(dp(2), Color.WHITE);
            cards.get(i).setBackground(bg);
            names.get(i).setText(on ? TodayView.NAMES[i] + "  ✓" : TodayView.NAMES[i]);
        }
    }

    /** Only the previews on screen animate; the rest stay still, so scrolling and taps stay smooth. */
    private void updateVisible() {
        android.graphics.Rect r = new android.graphics.Rect();
        for (int i = 0; i < previews.size(); i++) {
            TodayView v = previews.get(i);
            boolean on = v.getGlobalVisibleRect(r) && r.height() > v.getHeight() * 0.3f;
            if (on && !running[i]) v.startDemo();
            if (!on && running[i]) v.stopDemo();
            running[i] = on;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        java.util.Arrays.fill(running, false);
        sv.post(this::updateVisible);
    }

    @Override
    protected void onPause() {
        for (TodayView v : previews) v.stopDemo();
        java.util.Arrays.fill(running, false);
        super.onPause();
    }
}
