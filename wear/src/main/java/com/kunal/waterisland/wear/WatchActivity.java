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
 * Watch app, three swipe pages: today's ring, this week's bars, and quick add.
 * The phone is the master copy: this app reads "/state" from it and sends "/add/<id>" commands back.
 */
public class WatchActivity extends Activity implements DataClient.OnDataChangedListener {
    private static final int GRAY = 0xFF9AA5B1;
    private static final int[] AMOUNTS = {250, 333, 500};
    private static final long PENDING_MAX_AGE_MS = 2L * 24 * 60 * 60 * 1000;

    private SharedPreferences prefs;
    private HorizontalScrollView pager;
    private int pageW;
    private TodayView todayView;
    private WeekView weekView;
    private DotsView dots;
    private TextView addTitle;
    private LinearLayout pills;

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    // ---- state (phone's last state plus commands the phone has not confirmed yet) ---------

    private int stateInt(String key, int def) { return WatchData.get(this, key, def); }

    private int[] stateWeek() { return WatchData.week(this); }

    private int pendingMl() { return WatchData.pendingMl(this); }

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

    private void sendAdd(int ml) {
        WatchData.addDrink(this, ml);

        addTitle.setText("Added +" + ml);
        addTitle.postDelayed(() -> addTitle.setText("Add water"), 1500);
        refresh();
    }

    // ---- views ---------------------------------------------------------------------------

    /**
     * Today's screen: the round display is a glass. The water rises with your progress and its surface
     * waves while the screen is on (the wave stops when the screen dims).
     */
    private static final class TodayView extends View {
        private static final String HINT = "Tap to add";
        private static final int BUBBLES = 9;
        private final Paint front = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint crest = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint drop = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path wave = new Path();
        private final Path crestLine = new Path();
        private final Path circle = new Path();
        private final java.util.Random random = new java.util.Random();
        private final float[] bx = new float[BUBBLES];     // bubble position and look
        private final float[] by = new float[BUBBLES];
        private final float[] br = new float[BUBBLES];
        private final float[] bs = new float[BUBBLES];     // rising speed in px per second
        private final float[] bp = new float[BUBBLES];     // wobble offset
        private boolean seeded;
        private long lastFrame;
        private final float d;
        private float shown;      // water level now, 0 to 1, eased towards the target
        private float phase;      // wave position, 0 to 2*pi
        private float splash = 1f; // 0 to 1 while a splash plays, 1 when idle
        private int glasses;
        private int goal = 10;
        private String hint = HINT;
        private ValueAnimator waveAnim;
        private ValueAnimator levelAnim;
        private ValueAnimator splashAnim;

        TodayView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            front.setColor(0xFF4FC3F7);
            back.setColor(0x881D6FE0);
            crest.setStyle(Paint.Style.STROKE);
            crest.setStrokeWidth(2.5f * d);
            crest.setStrokeCap(Paint.Cap.ROUND);
            crest.setColor(0x99FFFFFF);
            bubble.setStyle(Paint.Style.STROKE);
            bubble.setStrokeWidth(1.5f * d);
            bubble.setColor(0x80FFFFFF);
            drop.setColor(0xFFFFFFFF);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(2 * d);
            rim.setColor(0x33FFFFFF);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(Color.WHITE);
            setClickable(true);
        }

        void set(float pct, int glasses, int goal) {
            this.glasses = glasses;
            this.goal = goal;
            float target = Math.max(0f, Math.min(1f, pct));
            if (levelAnim != null) levelAnim.cancel();
            levelAnim = ValueAnimator.ofFloat(shown, target);
            levelAnim.setDuration(700);
            levelAnim.addUpdateListener(a -> {
                shown = (Float) a.getAnimatedValue();
                invalidate();
            });
            levelAnim.start();
        }

        void startWave() {
            if (waveAnim == null) {
                waveAnim = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
                waveAnim.setDuration(2800);
                waveAnim.setRepeatCount(ValueAnimator.INFINITE);
                waveAnim.setInterpolator(new LinearInterpolator());
                waveAnim.addUpdateListener(a -> {
                    phase = (Float) a.getAnimatedValue();
                    invalidate();
                });
            }
            if (!waveAnim.isRunning()) waveAnim.start();
        }

        void stopWave() {
            if (waveAnim != null) waveAnim.cancel();
            lastFrame = 0;
        }

        /** Shows a short message such as "+333" in place of the hint, and plays the splash. */
        void flash(String message) {
            hint = message;
            if (splashAnim != null) splashAnim.cancel();
            splashAnim = ValueAnimator.ofFloat(0f, 1f);
            splashAnim.setDuration(1500);
            splashAnim.setInterpolator(new LinearInterpolator());
            splashAnim.addUpdateListener(a -> {
                splash = (Float) a.getAnimatedValue();
                invalidate();
            });
            splashAnim.start();
            postDelayed(() -> {
                hint = HINT;
                invalidate();
            }, 1400);
        }

        /**
         * Draws one wave layer. {@code speed} sets how fast and which way it travels, {@code cycles} how
         * many crests fit across the screen. When {@code withCrest} is set it also builds the surface line.
         */
        private void drawWave(Canvas c, float level, float amplitude, float shift, float speed,
                float cycles, Paint paint, boolean withCrest) {
            float w = getWidth(), h = getHeight();
            wave.reset();
            wave.moveTo(0, h);
            crestLine.reset();
            boolean first = true;
            for (float x = 0; x <= w + 8 * d; x += 8 * d) {
                float y = level + amplitude * (float) Math.sin(speed * phase + shift + x / w * 2 * Math.PI * cycles);
                wave.lineTo(x, y);
                if (first) crestLine.moveTo(x, y); else crestLine.lineTo(x, y);
                first = false;
            }
            wave.lineTo(w, h);
            wave.close();
            c.drawPath(wave, paint);
            if (withCrest) c.drawPath(crestLine, crest);
        }

        private void respawnBubble(int i, float w, float h, float level, boolean anywhere) {
            br[i] = (2f + 3f * random.nextFloat()) * d;
            bx[i] = w * (0.15f + 0.7f * random.nextFloat());
            by[i] = anywhere ? level + 12 * d + random.nextFloat() * Math.max(1f, h - level - 12 * d)
                    : h + br[i];
            bs[i] = (14f + 20f * random.nextFloat()) * d;
            bp[i] = random.nextFloat() * 6.28f;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            long now = System.nanoTime();
            float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1e9f);
            lastFrame = now;

            circle.reset();
            circle.addCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, Path.Direction.CW);
            c.save();
            c.clipPath(circle);
            // Keep a thin sliver of water even at zero, and stop just short of the top at full.
            float level = h - h * (0.05f + 0.90f * shown);

            // A splash makes the surface slosh: bigger waves that settle back down.
            float slosh = splash >= 0.3f && splash < 1f ? 1f - (splash - 0.3f) / 0.7f : 0f;
            float boost = 1f + 2f * slosh;
            drawWave(c, level, 7 * d * boost, 1.3f, 1f, 1.0f, back, false);
            drawWave(c, level + 4 * d, 5 * d * boost, 0f, -2f, 1.4f, front, true);

            // Bubbles drift up through the water and start again at the bottom.
            if (!seeded) {
                for (int i = 0; i < BUBBLES; i++) respawnBubble(i, w, h, level, true);
                seeded = true;
            }
            for (int i = 0; i < BUBBLES; i++) {
                by[i] -= bs[i] * dt;
                if (by[i] < level + 10 * d) respawnBubble(i, w, h, level, false);
                float x = bx[i] + 4 * d * (float) Math.sin(phase * 2 + bp[i]);
                c.drawCircle(x, by[i], br[i], bubble);
            }

            // A drop falls into the water, then a ripple spreads from where it lands.
            if (splash < 0.3f) {
                float t = splash / 0.3f;
                c.drawCircle(w / 2f, h * 0.14f + (level - h * 0.14f) * t * t, 6 * d, drop);
            } else if (splash < 0.8f) {
                float t = (splash - 0.3f) / 0.5f;
                float rx = t * 0.35f * w;
                crest.setAlpha((int) (200 * (1f - t)));
                c.drawOval(w / 2f - rx, level + 4 * d - rx * 0.18f, w / 2f + rx, level + 4 * d + rx * 0.18f, crest);
                crest.setAlpha(0x99);
            }
            c.restore();
            c.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f - d, rim);

            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(64 * d);
            c.drawText(String.valueOf(glasses), w / 2f, h / 2f + 16 * d, text);
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(14 * d);
            c.drawText("of " + goal + " glasses", w / 2f, h / 2f + 42 * d, text);
            text.setTextSize(12 * d);
            c.drawText(hint, w / 2f, h * 0.88f, text);
        }
    }

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
            for (int i = 0; i < 3; i++) {
                paint.setColor(i == index ? Color.WHITE : 0x55FFFFFF);
                c.drawCircle(getWidth() / 2f + (i - 1) * 14 * d, getHeight() / 2f, 3.5f * d, paint);
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
        // Tapping the water adds one glass of your usual size.
        todayView.setOnClickListener(v -> {
            int glassMl = Math.max(1, stateInt("glassMl", 333));
            WatchData.addDrink(this, glassMl);
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            todayView.flash("+" + glassMl);
            refresh();
        });
        weekView = new WeekView(this);
        strip.addView(todayView, new LinearLayout.LayoutParams(pageW, pageH));
        strip.addView(weekView, new LinearLayout.LayoutParams(pageW, pageH));
        strip.addView(buildAddPage(), new LinearLayout.LayoutParams(pageW, pageH));

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
        addTitle.setText("Add water");
        addTitle.setTextColor(GRAY);
        addTitle.setTextSize(15);
        addTitle.setGravity(Gravity.CENTER);
        page.addView(addTitle, new LinearLayout.LayoutParams(-2, -2));

        pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.VERTICAL);
        pills.setGravity(Gravity.CENTER);
        page.addView(pills, new LinearLayout.LayoutParams(-2, -2));
        return page;
    }

    private void refresh() {
        int glassMl = Math.max(1, stateInt("glassMl", 333));
        int goalMl = Math.max(1, stateInt("goalMl", 3330));
        int total = stateInt("todayMl", 0) + pendingMl();

        todayView.set((float) total / goalMl, Math.round((float) total / glassMl), stateInt("goalGlasses", 10));

        int[] week = stateWeek();
        int todayIdx = stateInt("todayIdx", 0);
        week[todayIdx] = total;
        float[] glasses = new float[7];
        for (int i = 0; i < 7; i++) glasses[i] = (float) week[i] / glassMl;
        weekView.set(glasses, todayIdx, stateInt("goalGlasses", 10));

        pills.removeAllViews();
        for (int amount : AMOUNTS) {
            boolean preferred = amount == glassMl;
            TextView pill = new TextView(this);
            pill.setText(amount + " ml");
            pill.setTextSize(15);
            pill.setTypeface(Typeface.DEFAULT_BOLD);
            pill.setGravity(Gravity.CENTER);
            pill.setTextColor(preferred ? Color.BLACK : Color.WHITE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(preferred ? Color.WHITE : 0x1FFFFFFF);
            bg.setCornerRadius(dp(18));
            pill.setBackground(bg);
            pill.setOnClickListener(v -> sendAdd(amount));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) dp(110), (int) dp(38));
            lp.topMargin = (int) dp(6);
            pills.addView(pill, lp);
        }
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
