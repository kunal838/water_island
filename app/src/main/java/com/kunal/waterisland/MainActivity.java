package com.kunal.waterisland;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.view.Window;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Minimal mono main page: litres today, quick-add chips, weekly graphs and today's log. Settings are separate. */
public class MainActivity extends Activity {
    private static final int GRAY = 0xFF9AA5B1;
    private static final int LINE = 0x1AFFFFFF;
    private static final int[] GLASS_SIZES = {250, 333, 500};

    private LinearLayout page;
    private LinearLayout chips;
    private LinearLayout logBox;
    private TextView dayLabel;
    private TextView bigNumber;
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

        WeekChart(Context c, boolean line) {
            super(c);
            this.line = line;
            d = c.getResources().getDisplayMetrics().density;
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
        page.setPadding(dp(24), dp(28), dp(24), dp(32));
        page.setBackgroundColor(0xFF111111);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        dayLabel = text("", 14, GRAY, false);
        top.addView(dayLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setContentDescription("Settings");
        gear.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x1AFFFFFF);
        gear.setBackground(ring);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(gear, new LinearLayout.LayoutParams(dp(38), dp(38)));
        page.addView(top);

        LinearLayout bigRow = new LinearLayout(this);
        bigRow.setGravity(Gravity.BOTTOM);
        bigNumber = text("0.0", 64, Color.WHITE, false);
        bigNumber.setIncludeFontPadding(false);
        bigRow.addView(bigNumber);
        TextView unit = text(" L", 20, GRAY, false);
        unit.setPadding(0, 0, 0, dp(10));
        bigRow.addView(unit);
        TextView editHint = text("   Tap to edit", 12, 0xFF6B7682, false);
        editHint.setPadding(0, 0, 0, dp(12));
        bigRow.addView(editHint);
        bigRow.setOnClickListener(v -> showEditSheet());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.topMargin = dp(8);
        page.addView(bigRow, bp);

        sub = text("", 14, GRAY, false);
        sub.setPadding(0, dp(2), 0, dp(14));
        page.addView(sub);

        LinearLayout bar = new LinearLayout(this);
        bar.setWeightSum(1f);
        bar.setBackground(round(0x26FFFFFF, 2));
        barFill = new View(this);
        barFill.setBackground(round(Color.WHITE, 2));
        bar.addView(barFill, new LinearLayout.LayoutParams(0, -1, 0f));
        page.addView(bar, new LinearLayout.LayoutParams(-1, dp(4)));

        chips = new LinearLayout(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(20);
        page.addView(chips, cp);

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
        weekCard.addView(weekBars, new LinearLayout.LayoutParams(-1, dp(170)));
        LinearLayout.LayoutParams wcp = new LinearLayout.LayoutParams(-1, -2);
        wcp.topMargin = dp(28);
        page.addView(weekCard, wcp);

        weekDelta = text("", 12, GRAY, false);
        LinearLayout trendCard = card("Trend", weekDelta);
        weekLine = new WeekChart(this, true);
        trendCard.addView(weekLine, new LinearLayout.LayoutParams(-1, dp(150)));
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(-1, -2);
        tcp.topMargin = dp(16);
        page.addView(trendCard, tcp);

        TextView logTitle = text("Today", 13, GRAY, false);
        logTitle.setPadding(0, dp(28), 0, dp(6));
        page.addView(logTitle);
        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(logBox);

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

    /** Bottom sheet: custom amount in 50 ml steps with Add, Remove and Undo last drink. */
    private void showEditSheet() {
        Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(12), dp(20), dp(28));
        float r = dp(22);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1B1B1D);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);

        View grab = new View(this);
        grab.setBackground(round(0x40FFFFFF, 2));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(36), dp(4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(grab, gp);

        TextView title = text("Custom amount", 13, GRAY, false);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, dp(8));
        sheet.addView(title, new LinearLayout.LayoutParams(-1, -2));

        final int[] amount = {Math.max(50, Math.round(Store.glassMl(this) / 50f) * 50)};
        TextView amountView = text(amount[0] + " ml", 28, Color.WHITE, true);
        amountView.setGravity(Gravity.CENTER);

        TextView minus = text("−", 24, Color.WHITE, false);
        TextView plus = text("+", 24, Color.WHITE, false);
        for (TextView t : new TextView[]{minus, plus}) {
            t.setGravity(Gravity.CENTER);
            t.setBackground(round(0x1AFFFFFF, 24));
        }
        minus.setOnClickListener(v -> {
            amount[0] = Math.max(50, amount[0] - 50);
            amountView.setText(amount[0] + " ml");
        });
        plus.setOnClickListener(v -> {
            amount[0] = Math.min(2000, amount[0] + 50);
            amountView.setText(amount[0] + " ml");
        });

        LinearLayout stepper = new LinearLayout(this);
        stepper.setGravity(Gravity.CENTER);
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        stepper.addView(amountView, new LinearLayout.LayoutParams(dp(150), -2));
        stepper.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        sheet.addView(stepper, new LinearLayout.LayoutParams(-1, -2));

        TextView remove = text("Remove", 15, 0xFFFF9A9A, true);
        remove.setGravity(Gravity.CENTER);
        remove.setBackground(round(0x26FF6B6B, 14));
        remove.setOnClickListener(v -> { Store.adjustMl(this, -amount[0]); changed(dlg); });
        TextView add = text("Add", 15, Color.BLACK, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(round(Color.WHITE, 14));
        add.setOnClickListener(v -> { Store.adjustMl(this, amount[0]); changed(dlg); });
        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        rp.rightMargin = dp(6);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(50), 1f);
        ap.leftMargin = dp(6);
        actions.addView(remove, rp);
        actions.addView(add, ap);
        LinearLayout.LayoutParams actp = new LinearLayout.LayoutParams(-1, -2);
        actp.topMargin = dp(18);
        sheet.addView(actions, actp);

        boolean canUndo = !Store.todayLog(this).isEmpty();
        TextView undo = text(canUndo ? "Undo last drink" : "Nothing to undo", 14,
                canUndo ? Color.WHITE : 0xFF6B7682, false);
        undo.setGravity(Gravity.CENTER);
        undo.setPadding(0, dp(18), 0, dp(4));
        if (canUndo) undo.setOnClickListener(v -> { Store.undoLast(this); changed(dlg); });
        sheet.addView(undo, new LinearLayout.LayoutParams(-1, -2));

        dlg.setContentView(sheet);
        Window w = dlg.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.BOTTOM);
        dlg.show();
    }

    private void changed(Dialog dlg) {
        dlg.dismiss();
        notifyIsland();
        refresh();
    }

    private void notifyIsland() {
        if (Store.enabled(this)) {
            startForegroundService(new Intent(this, IslandService.class).setAction(IslandService.ACTION_REFRESH));
        }
    }

    private void refresh() {
        int ml = Store.todayMl(this);
        int goal = Store.goalMl(this);
        dayLabel.setText(new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date()));
        bigNumber.setText(String.format(Locale.US, "%.1f", ml / 1000f));
        sub.setText(String.format(Locale.US, "of %.1f L  ·  %d of %d glasses", goal / 1000f,
                Store.glassesDrunk(this), Store.goalGlasses(this)));
        LinearLayout.LayoutParams fp = (LinearLayout.LayoutParams) barFill.getLayoutParams();
        fp.weight = goal == 0 ? 0f : Math.max(0f, Math.min(1f, (float) ml / goal));
        barFill.setLayoutParams(fp);

        chips.removeAllViews();
        int preferred = Store.glassMl(this);
        for (int amount : GLASS_SIZES) {
            boolean on = amount == preferred;
            TextView chip = text(String.valueOf(amount), 15, on ? Color.BLACK : Color.WHITE, true);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(round(on ? Color.WHITE : 0x1AFFFFFF, 14));
            chip.setOnClickListener(v -> {
                Store.addMl(this, amount);
                notifyIsland();
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            chips.addView(chip, lp);
        }

        int[] weekMl = Store.weekMl(this);
        int todayIdx = Store.todayIndex();
        float[] glasses = new float[7];
        float sum = 0f;
        int counted = 0;
        for (int i = 0; i < 7; i++) {
            glasses[i] = (float) weekMl[i] / Store.glassMl(this);
            if (i <= todayIdx && weekMl[i] > 0) { sum += glasses[i]; counted++; }
        }
        weekAvg.setText(counted == 0 ? "-" : String.format(Locale.US, "%.1f", sum / counted));
        int best = -1;
        for (int i = 0; i <= todayIdx; i++) if (weekMl[i] > 0 && (best < 0 || weekMl[i] > weekMl[best])) best = i;
        weekBest.setText(best < 0 ? "Best day: none yet"
                : "Best " + DAY_NAMES[best] + " " + Math.round(glasses[best]));
        weekTotal.setText("Total " + Math.round(sum));

        int[] lastMl = Store.weekMl(this, 1);
        float lastSum = 0f;
        int lastCounted = 0;
        for (int i = 0; i < 7; i++) {
            if (lastMl[i] > 0) { lastSum += (float) lastMl[i] / Store.glassMl(this); lastCounted++; }
        }
        if (counted == 0 || lastCounted == 0) {
            weekDelta.setText("No last week data yet");
        } else {
            float diff = sum / counted - lastSum / lastCounted;
            weekDelta.setText(String.format(Locale.US, "vs last week %s%.1f", diff >= 0 ? "+" : "-", Math.abs(diff)));
        }
        weekBars.set(glasses, todayIdx, Store.goalGlasses(this));
        weekLine.set(glasses, todayIdx, Store.goalGlasses(this));

        logBox.removeAllViews();
        List<long[]> log = Store.todayLog(this);
        SimpleDateFormat fmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
        if (log.isEmpty()) {
            logBox.addView(divider());
            TextView none = text("Nothing yet. Tap an amount above.", 14, GRAY, false);
            none.setPadding(0, dp(12), 0, dp(12));
            logBox.addView(none);
        }
        for (int i = log.size() - 1, shown = 0; i >= 0 && shown < 6; i--, shown++) {
            logBox.addView(divider());
            LinearLayout r = new LinearLayout(this);
            r.setPadding(0, dp(12), 0, dp(12));
            r.addView(text(fmt.format(new Date(log.get(i)[0])), 14, GRAY, false), new LinearLayout.LayoutParams(0, -2, 1f));
            long amount = log.get(i)[1];
            r.addView(text((amount >= 0 ? "+" : "−") + Math.abs(amount), 14, GRAY, false));
            logBox.addView(r);
        }
    }
}
