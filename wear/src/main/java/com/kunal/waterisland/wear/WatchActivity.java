package com.kunal.waterisland.wear;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.animation.LinearInterpolator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Watch app, three swipe pages: today's water (3 glasses = 1 litre), this week's bars, and +1 / -1 glass.
 * The phone is the master copy: this app reads "/state" from it and sends "/add/<id>" commands back.
 */
public class WatchActivity extends Activity implements DataClient.OnDataChangedListener {
    private static final int GRAY = 0xFF9AA5B1;
    private static final String TITLE = "Change today";
    private static final int PAGES = 4;   // Today, This week, Change today, Style
    private static final long PENDING_MAX_AGE_MS = 2L * 24 * 60 * 60 * 1000;

    private SharedPreferences prefs;
    private HorizontalScrollView pager;
    private int pageW;
    private TodayView todayView;
    private TextView[] styleRows;
    private float tapX;
    private float tapY;
    private WeekView weekView;
    private DotsView dots;
    private TextView addTitle;

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    // ---- state (phone's last state plus commands the phone has not confirmed yet) ---------

    private int stateInt(String key, int def) { return WatchData.get(this, key, def); }

    private int[] stateWeek() { return WatchData.week(this); }

    private void saveState(DataMap m) { WatchData.saveState(this, m); }

    // ---- sync ----------------------------------------------------------------------------

    private void fetchState() {
        Uri uri = new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path("/state").build();
        Wearable.getDataClient(this).getDataItems(uri).addOnSuccessListener(buffer -> {
            try {
                for (DataItem item : buffer) {
                    saveState(DataMapItem.fromDataItem(item).getDataMap());
                }
            } finally {
                buffer.release();
            }
            refresh();
        });
    }

    @Override
    public void onDataChanged(DataEventBuffer events) {
        for (DataEvent e : events) {
            if (e.getType() == DataEvent.TYPE_CHANGED && "/state".equals(e.getDataItem().getUri().getPath())) {
                saveState(DataMapItem.fromDataItem(e.getDataItem()).getDataMap());
            }
        }
        runOnUiThread(this::refresh);
    }

    private void sendChange(int delta) {
        WatchData.addDrink(this, delta);

        addTitle.setText(delta > 0 ? "Added 1 glass" : "Removed 1 glass");
        addTitle.postDelayed(() -> addTitle.setText(TITLE), 1500);
        refresh();
    }

    // ---- views ---------------------------------------------------------------------------

    private static final class WeekView extends View {
        private static final String[] DAYS = {"M", "T", "W", "T", "F", "S", "S"};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private float[] glasses = new float[7];
        private int today;
        private int goal = 10;

        WeekView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
        }

        void set(float[] glasses, int today, int goal) {
            this.glasses = glasses;
            this.today = today;
            this.goal = goal;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(GRAY);
            paint.setTextSize(15 * d);
            c.drawText("This week", w / 2f, 50 * d, paint);

            float left = w * 0.16f, right = w * 0.84f;
            float top = h * 0.30f, bottom = h * 0.70f;
            float max = goal;
            for (float g : glasses) max = Math.max(max, g);
            float slot = (right - left) / 7f;

            float gy = bottom - (bottom - top) * goal / max;
            paint.setColor(0x44FFFFFF);
            paint.setStrokeWidth(d);
            c.drawLine(left, gy, right, gy, paint);

            for (int i = 0; i < 7; i++) {
                float cx = left + slot * i + slot / 2f;
                float bh = (bottom - top) * glasses[i] / max;
                paint.setColor(i == today ? Color.WHITE : i > today ? 0x22FFFFFF : 0x66FFFFFF);
                c.drawRoundRect(new RectF(cx - slot * 0.3f, bottom - Math.max(bh, 3 * d), cx + slot * 0.3f, bottom),
                        3 * d, 3 * d, paint);
                paint.setColor(i == today ? Color.WHITE : GRAY);
                paint.setTextSize(12 * d);
                c.drawText(DAYS[i], cx, bottom + 20 * d, paint);
            }
        }
    }

    private static final class DotsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private int index;

        DotsView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
        }

        void setIndex(int i) {
            if (i != index) {
                index = i;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            for (int i = 0; i < PAGES; i++) {
                paint.setColor(i == index ? Color.WHITE : 0x55FFFFFF);
                c.drawCircle(getWidth() / 2f + (i - (PAGES - 1) / 2f) * 14 * d, getHeight() / 2f, 3.5f * d, paint);
            }
        }
    }

    // ---- screen --------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("watch", MODE_PRIVATE);
        // Reminder cards need the notification permission; the watch asks once, on first open.
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        pageW = dm.widthPixels;
        int pageH = dm.heightPixels;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout strip = new LinearLayout(this);
        todayView = new TodayView(this);
        // Tapping the water adds one glass.
        todayView.setOnTouchListener((v, e) -> {
            tapX = e.getX();
            tapY = e.getY();
            return false;
        });
        todayView.setOnClickListener(v -> {
            int delta = todayView.tapDelta(tapX, tapY);
            WatchData.addDrink(this, delta);
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            todayView.flash(delta > 0 ? "+1 glass" : "−1 glass");
            refresh();
        });
        weekView = new WeekView(this);
        strip.addView(todayView, new LinearLayout.LayoutParams(pageW, pageH));
        strip.addView(weekView, new LinearLayout.LayoutParams(pageW, pageH));
        strip.addView(buildAddPage(), new LinearLayout.LayoutParams(pageW, pageH));
        strip.addView(buildStylePage(), new LinearLayout.LayoutParams(pageW, pageH));

        pager = new HorizontalScrollView(this);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.addView(strip);
        pager.setOnScrollChangeListener((v, x, y, ox, oy) -> dots.setIndex(Math.round(x / (float) pageW)));
        pager.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                pager.post(() -> pager.smoothScrollTo(Math.round(pager.getScrollX() / (float) pageW) * pageW, 0));
            }
            return false;
        });
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));

        dots = new DotsView(this);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(-1, (int) dp(14));
        dp.gravity = Gravity.BOTTOM;
        dp.bottomMargin = (int) dp(8);
        root.addView(dots, dp);

        setContentView(root);
    }

    private View buildAddPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);

        addTitle = new TextView(this);
        addTitle.setText(TITLE);
        addTitle.setTextColor(GRAY);
        addTitle.setTextSize(15);
        addTitle.setGravity(Gravity.CENTER);
        page.addView(addTitle, new LinearLayout.LayoutParams(-2, -2));

        // The only two controls: add one glass, take one glass off.
        page.addView(changeButton("+ 1 glass", 1, true), buttonParams());
        page.addView(changeButton("− 1 glass", -1, false), buttonParams());
        return page;
    }

    /** Fourth page: a list of the Today looks. Picking one applies it here and on the phone. */
    private View buildStylePage() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, (int) dp(52), 0, (int) dp(64));
        TextView title = new TextView(this);
        title.setText("Style");
        title.setTextColor(GRAY);
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));
        styleRows = new TextView[TodayView.NAMES.length];
        for (int i = 0; i < styleRows.length; i++) {
            final int which = i;
            TextView row = new TextView(this);
            row.setTextSize(15);
            row.setGravity(Gravity.CENTER);
            row.setOnClickListener(v -> {
                WatchData.setStyle(this, which);
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) dp(150), (int) dp(40));
            lp.topMargin = (int) dp(6);
            col.addView(row, lp);
            styleRows[i] = row;
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        return sv;
    }

    private void showStyle(int selected) {
        for (int i = 0; i < styleRows.length; i++) {
            boolean on = i == selected;
            styleRows[i].setText(TodayView.NAMES[i]);
            styleRows[i].setTextColor(on ? Color.BLACK : Color.WHITE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(on ? Color.WHITE : 0x1FFFFFFF);
            bg.setCornerRadius(dp(20));
            styleRows[i].setBackground(bg);
        }
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) dp(130), (int) dp(46));
        lp.topMargin = (int) dp(8);
        return lp;
    }

    private TextView changeButton(String label, int delta, boolean primary) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(primary ? Color.BLACK : Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? Color.WHITE : 0x1FFFFFFF);
        bg.setCornerRadius(dp(23));
        button.setBackground(bg);
        button.setOnClickListener(v -> sendChange(delta));
        return button;
    }

    private void refresh() {
        int goal = Math.max(1, stateInt("goalGlasses", 10));
        int total = WatchData.totalGlasses(this);

        int[] week = stateWeek();
        int todayIdx = stateInt("todayIdx", 0);
        week[todayIdx] = total;
        float[] glasses = new float[7];
        float sum = 0f;
        int days = 0;
        for (int i = 0; i < 7; i++) {
            glasses[i] = week[i];
            if (i <= todayIdx && week[i] > 0) { sum += week[i]; days++; }
        }
        float weekFrac = days == 0 ? 0f : Math.min(1f, sum / days / goal);
        float streakFrac = Math.min(1f, stateInt("streak", 0) / 7f);
        int style = WatchData.style(this);
        todayView.setStyle(style);
        todayView.set((float) total / goal, total, goal, weekFrac, streakFrac);
        showStyle(style);
        weekView.set(glasses, todayIdx, goal);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getDataClient(this).addListener(this);
        todayView.startWave();
        refresh();
        fetchState();
    }

    @Override
    protected void onPause() {
        Wearable.getDataClient(this).removeListener(this);
        todayView.stopWave();
        super.onPause();
    }
}
