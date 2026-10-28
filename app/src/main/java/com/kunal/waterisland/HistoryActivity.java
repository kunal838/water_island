package com.kunal.waterisland;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.function.IntConsumer;

/**
 * Every day, week, month and year of water. A day lists each glass with its time; a week or month lists its
 * days and a year its months, and a tap on a bar or row opens that day or month.
 */
public class HistoryActivity extends Activity {
    /** Opens straight on this day (ISO yyyy-MM-dd). Without it the history opens on the current week. */
    static final String EXTRA_DATE = "date";

    private static final int DAY = 0, WEEK = 1, MONTH = 2, YEAR = 3;
    private static final String[] MODES = {"Day", "Week", "Month", "Year"};
    private static final int GRAY = 0xFF9AA5B1;
    private static final int LINE = 0x1AFFFFFF;
    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private int mode = WEEK;
    private final Calendar cur = Calendar.getInstance();
    private TreeMap<String, Integer> totals = new TreeMap<>();
    private LinearLayout content;
    private TextView[] tabs;
    private TextView label;
    private TextView next;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    // ---- periods ----------------------------------------------------------------------------

    private static String iso(Calendar c) { return ISO.format(c.getTime()); }

    private static Calendar copy(Calendar c) { return (Calendar) c.clone(); }

    private static void midnight(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    /** First day of the period that contains {@code cur}. Weeks run Monday to Sunday. */
    private Calendar start() {
        Calendar s = copy(cur);
        midnight(s);
        if (mode == WEEK) s.add(Calendar.DAY_OF_YEAR, -((s.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        if (mode == MONTH) s.set(Calendar.DAY_OF_MONTH, 1);
        if (mode == YEAR) s.set(Calendar.DAY_OF_YEAR, 1);
        return s;
    }

    /** The day after the period ends. */
    private Calendar end(Calendar start) {
        Calendar e = copy(start);
        if (mode == DAY) e.add(Calendar.DAY_OF_YEAR, 1);
        if (mode == WEEK) e.add(Calendar.DAY_OF_YEAR, 7);
        if (mode == MONTH) e.add(Calendar.MONTH, 1);
        if (mode == YEAR) e.add(Calendar.YEAR, 1);
        return e;
    }

    private static Calendar todayMidnight() {
        Calendar t = Calendar.getInstance();
        midnight(t);
        return t;
    }

    private int glassesOn(Calendar day) {
        Integer g = totals.get(iso(day));
        return g == null ? 0 : g;
    }

    // ---- screen -----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String date = getIntent().getStringExtra(EXTRA_DATE);
        if (date != null) {
            try {
                cur.setTime(ISO.parse(date));
                mode = DAY;
            } catch (ParseException ignored) {
                // Keep the current week.
            }
        }

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
        header.addView(text("History", 22, Color.WHITE, true));
        page.addView(header);

        // Day / Week / Month / Year.
        LinearLayout seg = new LinearLayout(this);
        seg.setBackground(round(0x1AFFFFFF, 22));
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs = new TextView[MODES.length];
        for (int i = 0; i < MODES.length; i++) {
            final int which = i;
            TextView t = text(MODES[i], 14, Color.WHITE, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(9), 0, dp(9));
            t.setOnClickListener(v -> {
                mode = which;
                render();
            });
            seg.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            tabs[i] = t;
        }
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(16);
        page.addView(seg, sp);

        // ‹  period  ›
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = text("‹", 28, Color.WHITE, false);
        prev.setPadding(dp(14), 0, dp(14), dp(4));
        prev.setOnClickListener(v -> step(-1));
        label = text("", 16, Color.WHITE, true);
        label.setGravity(Gravity.CENTER);
        next = text("›", 28, Color.WHITE, false);
        next.setPadding(dp(14), 0, dp(14), dp(4));
        next.setOnClickListener(v -> step(1));
        nav.addView(prev);
        nav.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(next);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(10);
        page.addView(nav, np);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        page.addView(content);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF111111);
        sv.addView(page);
        sv.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        totals = Store.dailyTotals(this);
        render();
    }

    /** Moves the period back or forward by one; the future is not reachable. */
    private void step(int dir) {
        int field = mode == YEAR ? Calendar.YEAR : mode == MONTH ? Calendar.MONTH : Calendar.DAY_OF_YEAR;
        int amount = mode == WEEK ? 7 * dir : dir;
        Calendar probe = copy(cur);
        probe.add(field, amount);
        Calendar saved = copy(cur);
        cur.setTime(probe.getTime());
        if (dir > 0 && start().after(todayMidnight())) cur.setTime(saved.getTime());
        render();
    }

    private void render() {
        Calendar s = start();
        Calendar e = end(s);
        // Forward is allowed only once the next period has begun.
        next.setAlpha(e.after(todayMidnight()) ? 0.25f : 1f);
        for (int i = 0; i < tabs.length; i++) {
            boolean on = i == mode;
            tabs[i].setBackground(on ? round(Color.WHITE, 18) : null);
            tabs[i].setTextColor(on ? Color.BLACK : Color.WHITE);
        }
        Calendar last = copy(e);
        last.add(Calendar.DAY_OF_YEAR, -1);
        label.setText(periodLabel(s, last));

        content.removeAllViews();
        if (mode == DAY) renderDay(s);
        else renderPeriod(s, e);
    }

    private String periodLabel(Calendar first, Calendar last) {
        Locale l = Locale.getDefault();
        if (mode == DAY) {
            String day = new SimpleDateFormat("EEEE, MMM d", l).format(first.getTime());
            return iso(first).equals(iso(todayMidnight())) ? "Today, " + new SimpleDateFormat("MMM d", l).format(first.getTime()) : day;
        }
        if (mode == WEEK) {
            SimpleDateFormat f = new SimpleDateFormat("MMM d", l);
            return f.format(first.getTime()) + " – " + f.format(last.getTime());
        }
        if (mode == MONTH) return new SimpleDateFormat("MMMM yyyy", l).format(first.getTime());
        return new SimpleDateFormat("yyyy", l).format(first.getTime());
    }

    // ---- day ---------------------------------------------------------------------------------

    private void renderDay(Calendar day) {
        int goal = Store.goalGlasses(this);
        int total = glassesOn(day);
        content.addView(summary(total, String.format(Locale.US, "= %.1f L  ·  goal %d glasses%s", Store.litres(total), goal,
                total >= goal ? "  ·  reached" : ""), null));

        List<long[]> log = Store.dayLog(this, iso(day));
        SimpleDateFormat fmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
        content.addView(title("Log"));
        if (log.isEmpty()) {
            content.addView(note(total > 0
                    ? "Only the total was saved for this day. Times are recorded from the day this update was installed."
                    : "No water recorded on this day."));
            return;
        }
        int running = 0;
        int[] after = new int[log.size()];
        for (int i = 0; i < log.size(); i++) {
            running += (int) log.get(i)[1];
            after[i] = running;
        }
        for (int i = log.size() - 1; i >= 0; i--) {
            long change = log.get(i)[1];
            LinearLayout r = row();
            r.addView(text(fmt.format(new Date(log.get(i)[0])), 15, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(text((change >= 0 ? "+" : "−") + Math.abs(change) + (Math.abs(change) == 1 ? " glass" : " glasses"),
                    15, change >= 0 ? Color.WHITE : GRAY, false));
            TextView tot = text("  →  " + after[i], 13, GRAY, false);
            r.addView(tot);
            content.addView(divider());
            content.addView(r);
        }
        content.addView(divider());
    }

    // ---- week, month, year -----------------------------------------------------------------

    private void renderPeriod(Calendar s, Calendar e) {
        int goal = Store.goalGlasses(this);
        Calendar today = todayMidnight();
        int bars = mode == WEEK ? 7 : mode == MONTH ? daysBetween(s, e) : 12;
        float[] values = new float[bars];
        String[] labels = new String[bars];
        Calendar[] starts = new Calendar[bars];
        int total = 0, daysWithWater = 0, goalDays = 0, best = -1;
        String[] dayNames = {"M", "T", "W", "T", "F", "S", "S"};
        String[] monthNames = {"J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"};
        int todayIndex = -1;
        for (int i = 0; i < bars; i++) {
            Calendar from = copy(s);
            if (mode == YEAR) from.add(Calendar.MONTH, i); else from.add(Calendar.DAY_OF_YEAR, i);
            starts[i] = from;
            float v;
            if (mode == YEAR) {
                Calendar to = copy(from);
                to.add(Calendar.MONTH, 1);
                v = 0;
                for (Calendar d = copy(from); d.before(to); d.add(Calendar.DAY_OF_YEAR, 1)) v += glassesOn(d);
            } else {
                v = glassesOn(from);
                if (v >= goal) goalDays++;
            }
            values[i] = v;
            total += v;
            if (v > 0) daysWithWater += mode == YEAR ? 0 : 1;
            if (best < 0 || v > values[best]) best = i;
            if (!from.after(today) && (mode == YEAR ? from.get(Calendar.MONTH) == today.get(Calendar.MONTH) && from.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    : iso(from).equals(iso(today)))) todayIndex = i;
            if (mode == WEEK) labels[i] = dayNames[i];
            else if (mode == YEAR) labels[i] = monthNames[i];
            else labels[i] = (i + 1) % 5 == 0 || i == 0 ? String.valueOf(i + 1) : "";
        }
        String detail = mode == YEAR
                ? String.format(Locale.US, "%.1f L total", Store.litres(total))
                : String.format(Locale.US, "%.1f L total  ·  %s", Store.litres(total), daysWithWater == 0 ? "no days yet"
                        : String.format(Locale.US, "%.1f glasses a day", total / (float) daysWithWater));
        content.addView(summary(total, detail, mode == YEAR ? null
                : goalDays + (goalDays == 1 ? " day" : " days") + " reached the goal"));

        HistoryChart chart = new HistoryChart(this);
        chart.set(values, labels, todayIndex, mode == YEAR ? 0 : goal, mode != MONTH);
        chart.onTap = i -> {
            if (starts[i].after(today)) return;
            cur.setTime(starts[i].getTime());
            mode = mode == YEAR ? MONTH : DAY;
            render();
        };
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(8));
        card.setBackground(round(0xFF1B1B1D, 20));
        card.addView(chart, new LinearLayout.LayoutParams(-1, dp(mode == MONTH ? 150 : 170)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(14);
        content.addView(card, cp);

        content.addView(title(mode == YEAR ? "Months" : "Days"));
        boolean any = false;
        for (int i = bars - 1; i >= 0; i--) {
            if (values[i] <= 0 || starts[i].after(today)) continue;
            any = true;
            final Calendar from = starts[i];
            String name = mode == YEAR ? new SimpleDateFormat("MMMM", Locale.getDefault()).format(from.getTime())
                    : new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(from.getTime());
            int g = (int) values[i];
            LinearLayout r = row();
            r.addView(text(name, 15, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(text(g + (g == 1 ? " glass" : " glasses") + String.format(Locale.US, "  ·  %.1f L", Store.litres(g)), 14, GRAY, false));
            r.setOnClickListener(v -> {
                cur.setTime(from.getTime());
                mode = mode == YEAR ? MONTH : DAY;
                render();
            });
            content.addView(divider());
            content.addView(r);
        }
        if (any) content.addView(divider());
        else content.addView(note("No water recorded in this period."));
    }

    private static int daysBetween(Calendar a, Calendar b) {
        return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86_400_000.0);
    }

    // ---- small pieces ----------------------------------------------------------------------

    private LinearLayout summary(int glasses, String detail, String extra) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(round(0xFF1B1B1D, 20));
        LinearLayout big = new LinearLayout(this);
        big.setGravity(Gravity.BOTTOM);
        TextView n = text(String.valueOf(glasses), 44, Color.WHITE, false);
        n.setIncludeFontPadding(false);
        big.addView(n);
        TextView u = text(glasses == 1 ? "  glass" : "  glasses", 16, GRAY, false);
        u.setPadding(0, 0, 0, dp(6));
        big.addView(u);
        c.addView(big);
        TextView d = text(detail, 13, GRAY, false);
        d.setPadding(0, dp(4), 0, 0);
        c.addView(d);
        if (extra != null) {
            TextView x = text(extra, 13, GRAY, false);
            x.setPadding(0, dp(2), 0, 0);
            c.addView(x);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView title(String s) {
        TextView t = text(s, 13, GRAY, false);
        t.setPadding(dp(4), dp(24), 0, dp(6));
        return t;
    }

    private TextView note(String s) {
        TextView t = text(s, 14, GRAY, false);
        t.setPadding(dp(4), dp(8), dp(4), dp(8));
        return t;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(4), dp(14), dp(4), dp(14));
        return r;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        return v;
    }

    /** Bars for a week, a month or a year with a dashed goal line; tapping a bar reports its index. */
    private static final class HistoryChart extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private float[] values = new float[0];
        private String[] labels = new String[0];
        private int today = -1;
        private int goal;
        private boolean showValues;
        IntConsumer onTap;

        HistoryChart(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            setClickable(true);
        }

        void set(float[] values, String[] labels, int today, int goal, boolean showValues) {
            this.values = values;
            this.labels = labels;
            this.today = today;
            this.goal = goal;
            this.showValues = showValues;
            invalidate();
        }

        @Override
        public boolean performClick() { return super.performClick(); }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP && onTap != null && values.length > 0) {
                int i = Math.max(0, Math.min(values.length - 1, (int) (e.getX() / (getWidth() / (float) values.length))));
                performClick();
                onTap.accept(i);
            }
            return super.onTouchEvent(e);
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = values.length;
            if (n == 0) return;
            float w = getWidth(), h = getHeight();
            float top = 22 * d, bottom = h - 22 * d;
            float max = goal;
            for (float v : values) max = Math.max(max, v);
            if (max <= 0) max = 1;
            float slot = w / n;
            if (goal > 0) {
                float gy = bottom - (bottom - top) * goal / max;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(d);
                paint.setColor(0x55FFFFFF);
                paint.setPathEffect(new DashPathEffect(new float[]{4 * d, 4 * d}, 0));
                c.drawLine(0, gy, w, gy, paint);
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextAlign(Paint.Align.RIGHT);
                paint.setTextSize(11 * d);
                paint.setColor(0xFF9AA5B1);
                c.drawText("goal " + goal, w, gy - 4 * d, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < n; i++) {
                float cx = slot * i + slot / 2f;
                float bw = Math.max(2 * d, slot * (n > 12 ? 0.62f : 0.6f));
                float y = bottom - (bottom - top) * values[i] / max;
                paint.setColor(today >= 0 && i > today ? 0x22FFFFFF : i == today ? Color.WHITE : 0x77FFFFFF);
                c.drawRoundRect(new RectF(cx - bw / 2f, Math.min(y, bottom - 2 * d), cx + bw / 2f, bottom), 3 * d, 3 * d, paint);
                if (showValues && values[i] > 0) {
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(11 * d);
                    c.drawText(String.valueOf(Math.round(values[i])), cx, y - 5 * d, paint);
                }
                if (!labels[i].isEmpty()) {
                    paint.setColor(i == today ? Color.WHITE : 0xFF9AA5B1);
                    paint.setTextSize(11 * d);
                    c.drawText(labels[i], cx, h - 6 * d, paint);
                }
            }
        }
    }
}
