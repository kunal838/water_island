package com.kunal.waterisland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * GitHub-style contribution grid: one square per day, a column per Monday-to-Sunday week, and a green that
 * gets darker the closer the day came to the goal. Days after today are left blank. A tap reports the day.
 */
final class HeatmapView extends View {
    private static final int[] LEVELS = {0xFF2A2F36, 0xFF0E4429, 0xFF006D32, 0xFF26A641, 0xFF39D353};
    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final String[] DAY_NAMES = {"Mon", "", "Wed", "", "Fri", "", ""};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float d;

    private Calendar firstColumn = Calendar.getInstance();   // Monday of the first week
    private Calendar first = Calendar.getInstance();
    private Calendar last = Calendar.getInstance();
    private Map<String, Integer> totals = new java.util.TreeMap<>();
    private int goal = 10;
    private int weeks = 1;
    private boolean dayLabels;
    private float fixedCellDp;   // 0 means the squares stretch to fill the width

    /** Called with the ISO date (yyyy-MM-dd) of a tapped day that is not in the future. */
    Consumer<String> onDay;

    HeatmapView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClickable(true);
    }

    /**
     * Shows the days from {@code first} to {@code last}. With {@code fixedCellDp} of 0 the squares fill the
     * width, otherwise they have that size and the view is as wide as it needs (put it in a scroller).
     */
    void set(Calendar first, Calendar last, Map<String, Integer> totals, int goal, boolean dayLabels, float fixedCellDp) {
        this.first = (Calendar) first.clone();
        this.last = (Calendar) last.clone();
        this.totals = totals;
        this.goal = Math.max(1, goal);
        this.dayLabels = dayLabels;
        this.fixedCellDp = fixedCellDp;
        firstColumn = monday(first);
        weeks = Math.max(1, (int) Math.round((monday(last).getTimeInMillis() - firstColumn.getTimeInMillis()) / (7 * 86_400_000.0)) + 1);
        requestLayout();
        invalidate();
    }

    private static Calendar monday(Calendar c) {
        Calendar m = (Calendar) c.clone();
        m.set(Calendar.HOUR_OF_DAY, 12);
        m.add(Calendar.DAY_OF_YEAR, -((m.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        return m;
    }

    private float gap() { return 3 * d; }
    private float labelW() { return dayLabels ? 30 * d : 0; }
    private float topH() { return 16 * d; }
    /** The scrolling year grid has no legend of its own; the screen shows one beside it that never scrolls. */
    private float legendH() { return fixedCellDp > 0 ? 0 : 24 * d; }

    static int levelColor(int level) { return LEVELS[Math.max(0, Math.min(LEVELS.length - 1, level))]; }

    private float cell(float width) {
        if (fixedCellDp > 0) return fixedCellDp * d;
        float c = (width - labelW() - gap() * (weeks - 1)) / weeks;
        return Math.max(4 * d, Math.min(26 * d, c));
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int width = MeasureSpec.getSize(wSpec);
        float c = cell(width);
        int wanted = fixedCellDp > 0 ? Math.round(labelW() + weeks * (c + gap())) : width;
        int h = Math.round(topH() + 7 * (c + gap()) + legendH());
        setMeasuredDimension(resolveSize(wanted, wSpec), h);
    }

    private static int level(int glasses, int goal) {
        if (glasses <= 0) return 0;
        float f = glasses / (float) goal;
        return f < 0.34f ? 1 : f < 0.67f ? 2 : f < 1f ? 3 : 4;
    }

    private Calendar dayAt(int col, int row) {
        Calendar day = (Calendar) firstColumn.clone();
        day.add(Calendar.DAY_OF_YEAR, col * 7 + row);
        return day;
    }

    private boolean inRange(Calendar day) {
        String s = ISO.format(day.getTime());
        return s.compareTo(ISO.format(first.getTime())) >= 0 && s.compareTo(ISO.format(last.getTime())) <= 0;
    }

    @Override
    protected void onDraw(Canvas c) {
        float cell = cell(getWidth());
        float x0 = labelW();
        String today = ISO.format(Calendar.getInstance().getTime());
        SimpleDateFormat month = new SimpleDateFormat("MMM", Locale.getDefault());
        paint.setTextSize(11 * d);
        paint.setStyle(Paint.Style.FILL);
        float labelEnd = -1;
        for (int col = 0; col < weeks; col++) {
            float x = x0 + col * (cell + gap());
            // A month name above the week that holds that month's first day (and above the first column),
            // skipped when it would run into the previous name.
            for (int r = 0; r < 7; r++) {
                Calendar probe = dayAt(col, r);
                if (!inRange(probe) || !(probe.get(Calendar.DAY_OF_MONTH) == 1 || col == 0 && r == 0)) continue;
                if (x >= labelEnd) {
                    String name = month.format(probe.getTime());
                    paint.setColor(0xFF9AA5B1);
                    paint.setTextAlign(Paint.Align.LEFT);
                    c.drawText(name, x, 11 * d, paint);
                    labelEnd = x + paint.measureText(name) + 6 * d;
                }
                break;
            }
            for (int row = 0; row < 7; row++) {
                Calendar day = dayAt(col, row);
                if (!inRange(day)) continue;
                String iso = ISO.format(day.getTime());
                if (iso.compareTo(today) > 0) continue;
                Integer g = totals.get(iso);
                paint.setColor(LEVELS[level(g == null ? 0 : g, goal)]);
                float y = topH() + row * (cell + gap());
                box.set(x, y, x + cell, y + cell);
                c.drawRoundRect(box, 2 * d, 2 * d, paint);
                if (iso.equals(today)) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.5f * d);
                    paint.setColor(Color.WHITE);
                    c.drawRoundRect(box, 2 * d, 2 * d, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }
        if (dayLabels) {
            paint.setColor(0xFF9AA5B1);
            paint.setTextAlign(Paint.Align.LEFT);
            for (int row = 0; row < 7; row++) {
                if (!DAY_NAMES[row].isEmpty()) c.drawText(DAY_NAMES[row], 0, topH() + row * (cell + gap()) + cell * 0.85f, paint);
            }
        }
        if (fixedCellDp > 0) return;
        // Less [] [] [] [] [] More
        float ly = topH() + 7 * (cell + gap()) + 6 * d;
        float sq = 11 * d;
        float right = getWidth();
        if (fixedCellDp > 0) right = Math.min(right, labelW() + weeks * (cell + gap()));
        float x = right - (5 * (sq + 3 * d)) - 34 * d;
        paint.setColor(0xFF9AA5B1);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Less", x - 3 * d, ly + sq - d, paint);
        for (int i = 0; i < LEVELS.length; i++) {
            paint.setColor(LEVELS[i]);
            box.set(x + i * (sq + 3 * d), ly, x + i * (sq + 3 * d) + sq, ly + sq);
            c.drawRoundRect(box, 2 * d, 2 * d, paint);
        }
        paint.setColor(0xFF9AA5B1);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText("More", x + 5 * (sq + 3 * d) + 3 * d, ly + sq - d, paint);
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && onDay != null) {
            float cell = cell(getWidth());
            int col = (int) ((e.getX() - labelW()) / (cell + gap()));
            int row = (int) ((e.getY() - topH()) / (cell + gap()));
            if (col >= 0 && col < weeks && row >= 0 && row < 7 && e.getX() >= labelW()) {
                Calendar day = dayAt(col, row);
                String iso = ISO.format(day.getTime());
                if (inRange(day) && iso.compareTo(ISO.format(Calendar.getInstance().getTime())) <= 0) {
                    performClick();
                    onDay.accept(iso);
                }
            }
        }
        return super.onTouchEvent(e);
    }
}
