package com.kunal.waterisland;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Minimal mono main page: glasses today (3 glasses = 1 litre), the +1 / -1 glass buttons, weekly graphs and
 * today's log. Settings are separate.
 */
public class MainActivity extends Activity {
    private static final int GRAY = 0xFF9AA5B1;
    private static final int LINE = 0x1AFFFFFF;

    // Header sizes in dp, without the status bar height.
    private static final int HEADER_EXPANDED = 160;
    private static final int HEADER_COLLAPSED = 62;
    private static final int COLLAPSE_RANGE = 110;   // scroll distance over which the header collapses
    private static final int HIDE_BELOW = 120;       // scroll distance past which the status bar hides

    private LinearLayout page;
    private ScrollView sv;
    private FrameLayout header;
    private LinearLayout bigRow;
    private LinearLayout bar;
    private View spacer;
    private View headerLine;
    private ImageView gear;
    private int topInset;
    private boolean barsHidden;
    private LinearLayout buttons;
    private LinearLayout logBox;
    private TextView dayLabel;
    private TextView bigNumber;
    private TextView bigUnit;
    private TextView sub;
    private View barFill;
    private static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private TextView weekAvg;
    private TextView weekBest;
    private TextView weekTotal;
    private TextView weekDelta;
    private WeekChart weekBars;
    private WeekChart weekLine;

    /** Glasses per day for the week: bars with a dashed goal line, or a line through the days so far. */
    private static final class WeekChart extends View {
        private static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private final boolean line;
        private float[] glasses = new float[7];
        private int today;
        private int goal = 10;
        /** Called with the day index (Monday = 0) when a day up to today is tapped. */
        java.util.function.IntConsumer onDay;

        WeekChart(Context c, boolean line) {
            super(c);
            this.line = line;
            d = c.getResources().getDisplayMetrics().density;
            setClickable(true);
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            if (e.getAction() == android.view.MotionEvent.ACTION_UP && onDay != null) {
                int index = Math.max(0, Math.min(6, (int) (e.getX() / (getWidth() / 7f))));
                if (index <= today) {
                    performClick();
                    onDay.accept(index);
                }
            }
            return super.onTouchEvent(e);
        }

        /** Days up to today with water logged, plus today itself; earlier empty days have no history. */
        private boolean hasData(int i) {
            return i <= today && (glasses[i] > 0 || i == today);
        }

        void set(float[] glasses, int today, int goal) {
            this.glasses = glasses;
            this.today = today;
            this.goal = goal;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float top = 26 * d;
            float bottom = h - 26 * d;
            float max = goal;
            for (float g : glasses) max = Math.max(max, g);
            float slot = w / 7f;

            float gy = bottom - (bottom - top) * goal / max;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d);
            paint.setColor(0x55FFFFFF);
            paint.setPathEffect(new DashPathEffect(new float[]{4 * d, 4 * d}, 0));
            c.drawLine(0, gy, w, gy, paint);
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(11 * d);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(GRAY);
            c.drawText("goal " + goal, w, gy - 4 * d, paint);

            paint.setTextAlign(Paint.Align.CENTER);
            Path path = new Path();
            for (int i = 0; i < 7; i++) {
                float cx = slot * i + slot / 2f;
                float v = glasses[i];
                float y = bottom - (bottom - top) * v / max;
                if (!line) {
                    float bw = slot * 0.6f;
                    RectF r = new RectF(cx - bw / 2f, Math.min(y, bottom - 2 * d), cx + bw / 2f, bottom);
                    paint.setColor(i > today ? 0x22FFFFFF : i == today ? Color.WHITE : 0x55FFFFFF);
                    c.drawRoundRect(r, 4 * d, 4 * d, paint);
                    if (i <= today && v > 0) {
                        paint.setColor(Color.WHITE);
                        paint.setTextSize(12 * d);
                        c.drawText(String.valueOf(Math.round(v)), cx, r.top - 6 * d, paint);
                    }
                } else if (hasData(i)) {
                    if (path.isEmpty()) path.moveTo(cx, y); else path.lineTo(cx, y);
                }
                paint.setTextSize(11 * d);
                paint.setColor(i == today ? Color.WHITE : GRAY);
                c.drawText(DAYS[i], cx, h - 6 * d, paint);
            }
            if (line) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(Color.WHITE);
                c.drawPath(path, paint);
                for (int i = 0; i <= today; i++) {
                    if (!hasData(i)) continue;
                    float cx = slot * i + slot / 2f;
                    float y = bottom - (bottom - top) * glasses[i] / max;
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(i == today ? Color.WHITE : 0xFF111111);
                    c.drawCircle(cx, y, (i == today ? 5 : 4) * d, paint);
                    if (i != today) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(Color.WHITE);
                        c.drawCircle(cx, y, 4 * d, paint);
                    }
                    if (glasses[i] > 0) {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(GRAY);
                        paint.setTextSize(11 * d);
                        c.drawText(String.valueOf(Math.round(glasses[i])), cx, y - 10 * d, paint);
                    }
                }
            }
        }
    }

    /** Opens that day of the current week in the history, with its log. */
    private void openDay(int index) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, index - Store.todayIndex());
        startActivity(new Intent(this, HistoryActivity.class)
                .putExtra(HistoryActivity.EXTRA_DATE, new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime())));
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Small rounded stat chip used under the big number in the weekly card. */
    private TextView statChip() {
        TextView t = text("", 12, 0xFFCFD6DD, false);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        t.setBackground(round(0x14FFFFFF, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    /** Filled rounded card with a title on the left and a small note on the right. */
    private LinearLayout card(String title, TextView note) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(12));
        c.setBackground(round(0xFF1B1B1D, 20));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(title, 14, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(note);
        c.addView(head);
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        return v;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Reminders are notifications, so a new install asks for that permission once (Android 13+).
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), 0, dp(24), dp(32));
        page.setBackgroundColor(0xFF111111);

        // Pinned header: the big count shrinks into a slim bar as the page scrolls (see applyScroll).
        header = new FrameLayout(this);
        header.setBackgroundColor(0xFF111111);
        dayLabel = text("", 14, GRAY, false);
        header.addView(dayLabel, headerParams(-2, -2, Gravity.TOP | Gravity.START));
        gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setContentDescription("Settings");
        gear.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x1AFFFFFF);
        gear.setBackground(ring);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(gear, headerParams(dp(38), dp(38), Gravity.TOP | Gravity.END));

        bigRow = new LinearLayout(this);
        bigRow.setGravity(Gravity.BOTTOM);
        bigNumber = text("0", 64, Color.WHITE, false);
        bigNumber.setIncludeFontPadding(false);
        bigRow.addView(bigNumber);
        bigUnit = text(" glasses", 20, GRAY, false);
        bigUnit.setPadding(0, 0, 0, dp(2));
        bigRow.addView(bigUnit);
        header.addView(bigRow, headerParams(-2, -2, Gravity.TOP | Gravity.START));

        sub = text("", 14, GRAY, false);
        header.addView(sub, headerParams(-2, -2, Gravity.TOP | Gravity.START));

        bar = new LinearLayout(this);
        bar.setWeightSum(1f);
        bar.setBackground(round(0x26FFFFFF, 2));
        barFill = new View(this);
        barFill.setBackground(round(Color.WHITE, 2));
        bar.addView(barFill, new LinearLayout.LayoutParams(0, -1, 0f));
        FrameLayout.LayoutParams barLp = headerParams(-1, dp(4), Gravity.TOP);
        barLp.leftMargin = dp(24);
        barLp.rightMargin = dp(24);
        header.addView(bar, barLp);

        headerLine = divider();
        header.addView(headerLine, new FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM));

        // The page starts with an empty block as tall as the expanded header.
        spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(-1, dp(HEADER_EXPANDED)));

        // The only controls for the count: remove one glass, add one glass.
        buttons = new LinearLayout(this);
        TextView minus = text("− 1 glass", 16, Color.WHITE, true);
        minus.setGravity(Gravity.CENTER);
        minus.setBackground(round(0x1AFFFFFF, 14));
        minus.setOnClickListener(v -> change(-1));
        TextView plus = text("+ 1 glass", 16, Color.BLACK, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(round(Color.WHITE, 14));
        plus.setOnClickListener(v -> change(1));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        mp.rightMargin = dp(6);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        pp.leftMargin = dp(6);
        buttons.addView(minus, mp);
        buttons.addView(plus, pp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(20);
        page.addView(buttons, cp);

        LinearLayout weekCard = card("This week", text("Mon to Sun", 12, GRAY, false));
        LinearLayout statRow = new LinearLayout(this);
        statRow.setGravity(Gravity.BOTTOM);
        statRow.setPadding(0, dp(8), 0, 0);
        weekAvg = text("-", 26, Color.WHITE, true);
        weekAvg.setIncludeFontPadding(false);
        statRow.addView(weekAvg);
        TextView avgUnit = text("  glasses a day", 12, GRAY, false);
        avgUnit.setPadding(0, 0, 0, dp(3));
        statRow.addView(avgUnit);
        weekCard.addView(statRow);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setPadding(0, dp(10), 0, dp(4));
        weekBest = statChip();
        weekTotal = statChip();
        chipRow.addView(weekBest);
        chipRow.addView(weekTotal);
        weekCard.addView(chipRow);
        weekBars = new WeekChart(this, false);
        weekBars.onDay = this::openDay;
        weekCard.addView(weekBars, new LinearLayout.LayoutParams(-1, dp(170)));
        LinearLayout.LayoutParams wcp = new LinearLayout.LayoutParams(-1, -2);
        wcp.topMargin = dp(28);
        page.addView(weekCard, wcp);

        weekDelta = text("", 12, GRAY, false);
        LinearLayout trendCard = card("Trend", weekDelta);
        weekLine = new WeekChart(this, true);
        weekLine.onDay = this::openDay;
        trendCard.addView(weekLine, new LinearLayout.LayoutParams(-1, dp(150)));
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(-1, -2);
        tcp.topMargin = dp(16);
        page.addView(trendCard, tcp);

        // Every day, week, month and year, with the time of each glass.
        LinearLayout historyRow = new LinearLayout(this);
        historyRow.setGravity(Gravity.CENTER_VERTICAL);
        historyRow.setPadding(dp(18), dp(16), dp(18), dp(16));
        historyRow.setBackground(round(0xFF1B1B1D, 20));
        historyRow.addView(text("History", 14, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        historyRow.addView(text("Day, week, month, year  ›", 12, GRAY, false));
        historyRow.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(16);
        page.addView(historyRow, hp);

        TextView logTitle = text("Today", 13, GRAY, false);
        logTitle.setPadding(0, dp(28), 0, dp(6));
        page.addView(logTitle);
        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(logBox);

        sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF111111);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(page);
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> applyScroll());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF111111);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.addView(header, new FrameLayout.LayoutParams(-1, dp(HEADER_EXPANDED)));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            // Hiding the status bar reports a zero top inset; keep the last real one so nothing jumps.
            int top = insets.getSystemWindowInsetTop();
            if (top > 0) topInset = top;
            sv.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
            applyScroll();
            return insets;
        });
        setContentView(root);
        applyScroll();
    }

    private FrameLayout.LayoutParams headerParams(int w, int h, int gravity) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, gravity);
        lp.leftMargin = dp(24);
        lp.rightMargin = dp(24);
        return lp;
    }

    /**
     * Collapses the header as the page scrolls: the count shrinks and moves up, the date and the goal line
     * fade out, a quick +1 appears, and the status bar hides (swipe down from the top edge brings it back).
     */
    private void applyScroll() {
        int y = sv.getScrollY();
        float p = Math.min(1f, y / (float) dp(COLLAPSE_RANGE));
        float fade = Math.max(0f, 1f - p * 1.6f);
        int t = topInset;
        int expanded = t + dp(HEADER_EXPANDED);
        int collapsed = t + dp(HEADER_COLLAPSED);
        int h = Math.round(expanded - p * (expanded - collapsed));
        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) header.getLayoutParams();
        if (hp.height != h) {
            hp.height = h;
            header.setLayoutParams(hp);
        }
        spacer.getLayoutParams().height = expanded + dp(4);
        spacer.requestLayout();

        bigNumber.setTextSize(64 - 36 * p);
        bigUnit.setTextSize(20 - 7 * p);
        bigRow.setTranslationY(t + dp(40) - p * dp(28));
        dayLabel.setTranslationY(t + dp(20));
        dayLabel.setAlpha(fade);
        sub.setTranslationY(t + dp(118));
        sub.setAlpha(fade);
        bar.setTranslationY(h - dp(14) + p * dp(10));
        headerLine.setAlpha(p > 0.95f ? 1f : 0f);
        gear.setTranslationY(t + dp(12));

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                boolean hide = barsHidden ? y > dp(HIDE_BELOW) - dp(40) : y > dp(HIDE_BELOW);
                if (hide != barsHidden) {
                    barsHidden = hide;
                    if (hide) c.hide(WindowInsets.Type.statusBars()); else c.show(WindowInsets.Type.statusBars());
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        WearSync.push(this);
        // An app update or a force-stop clears alarms, so opening the app always re-arms the next reminder.
        if (Store.enabled(this)) Alarms.scheduleNext(this);
        // Redraw when the count changes while this page is open, for example from the watch.
        getSharedPreferences("water", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    protected void onPause() {
        getSharedPreferences("water", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener);
        super.onPause();
    }

    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> runOnUiThread(this::refresh);

    /** Adds or removes one glass, then updates the island card and this page. */
    private void change(int delta) {
        Store.addGlasses(this, delta);
        notifyIsland();
        refresh();
    }

    private void notifyIsland() {
        if (Store.enabled(this)) {
            startForegroundService(new Intent(this, IslandService.class).setAction(IslandService.ACTION_REFRESH));
        }
    }

    private void refresh() {
        int drunk = Store.todayGlasses(this);
        int goal = Store.goalGlasses(this);
        dayLabel.setText(new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date()));
        bigNumber.setText(String.valueOf(drunk));
        bigUnit.setText(drunk == 1 ? " glass" : " glasses");
        sub.setText(String.format(Locale.US, "= %.1f L  ·  goal %d glasses (%.1f L)",
                Store.litres(drunk), goal, Store.litres(goal)));
        LinearLayout.LayoutParams fp = (LinearLayout.LayoutParams) barFill.getLayoutParams();
        fp.weight = goal == 0 ? 0f : Math.max(0f, Math.min(1f, (float) drunk / goal));
        barFill.setLayoutParams(fp);

        int[] week = Store.weekGlasses(this);
        int todayIdx = Store.todayIndex();
        float[] glasses = new float[7];
        float sum = 0f;
        int counted = 0;
        for (int i = 0; i < 7; i++) {
            glasses[i] = week[i];
            if (i <= todayIdx && week[i] > 0) { sum += glasses[i]; counted++; }
        }
        weekAvg.setText(counted == 0 ? "-" : String.format(Locale.US, "%.1f", sum / counted));
        int best = -1;
        for (int i = 0; i <= todayIdx; i++) if (week[i] > 0 && (best < 0 || week[i] > week[best])) best = i;
        weekBest.setText(best < 0 ? "Best day: none yet" : "Best " + DAY_NAMES[best] + " " + week[best]);
        weekTotal.setText("Total " + Math.round(sum));

        int[] lastWeek = Store.weekGlasses(this, 1);
        float lastSum = 0f;
        int lastCounted = 0;
        for (int i = 0; i < 7; i++) {
            if (lastWeek[i] > 0) { lastSum += lastWeek[i]; lastCounted++; }
        }
        if (counted == 0 || lastCounted == 0) {
            weekDelta.setText("No last week data yet");
        } else {
            float diff = sum / counted - lastSum / lastCounted;
            weekDelta.setText(String.format(Locale.US, "vs last week %s%.1f", diff >= 0 ? "+" : "-", Math.abs(diff)));
        }
        weekBars.set(glasses, todayIdx, goal);
        weekLine.set(glasses, todayIdx, goal);

        logBox.removeAllViews();
        List<long[]> log = Store.todayLog(this);
        SimpleDateFormat fmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
        if (log.isEmpty()) {
            logBox.addView(divider());
            TextView none = text("Nothing yet. Tap + 1 glass.", 14, GRAY, false);
            none.setPadding(0, dp(12), 0, dp(12));
            logBox.addView(none);
        }
        for (int i = log.size() - 1, shown = 0; i >= 0 && shown < 6; i--, shown++) {
            logBox.addView(divider());
            LinearLayout r = new LinearLayout(this);
            r.setPadding(0, dp(12), 0, dp(12));
            r.addView(text(fmt.format(new Date(log.get(i)[0])), 14, GRAY, false), new LinearLayout.LayoutParams(0, -2, 1f));
            long change = log.get(i)[1];
            r.addView(text((change >= 0 ? "+" : "−") + Math.abs(change) + (Math.abs(change) == 1 ? " glass" : " glasses"),
                    14, GRAY, false));
            logBox.addView(r);
        }
    }
}
