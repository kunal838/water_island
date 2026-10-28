package com.kunal.waterisland.wear;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Today's screen. One of {@link #NAMES} is drawn, chosen on the phone or the watch. Every style shows the
 * glass count against the goal and plays its own animation when a glass is added. Styles other than
 * "Rising water" are drawn in a 200 x 200 space that is scaled to the round display.
 */
public final class TodayView extends View {
    public static final String[] NAMES = {
            "Triple arcs", "Cookie fill", "Wavy ring", "Squircle liquid", "Gauge and pills", "Bloom",
            "Rising water", "Ring and drop", "Big glass", "Aquarium", "Growing plant", "Orbit"};
    public static final int WATER = 6;
    private static final int DEMO_GOAL = 8;
    private static final long DEMO_STEP_MS = 800;

    private static final int BLUE = 0xFFA8C7FA;
    private static final int GREEN = 0xFFC4EED0;
    private static final int PINK = 0xFFFFB8D1;
    private static final int YELLOW = 0xFFFDE293;
    private static final int GRAY = 0xFF9AA5B1;
    private static final int WATER_BLUE = 0xFF4FC3F7;
    private static final String HINT = "Tap to add";
    private static final int BUBBLES = 9;
    private static final int PETALS = 12;

    private final Paint front = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crest = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();
    private final Path crestLine = new Path();
    private final Path circle = new Path();
    private final Path work = new Path();
    private final Path cookie = new Path();
    private final Path squircle = new Path();
    private final Path dropShape = new Path();
    private final Path glassShape = new Path();
    private final RectF oval = new RectF();
    private final java.util.Random random = new java.util.Random();
    private final float[] bx = new float[BUBBLES];
    private final float[] by = new float[BUBBLES];
    private final float[] br = new float[BUBBLES];
    private final float[] bs = new float[BUBBLES];
    private final float[] bp = new float[BUBBLES];
    private boolean seeded;
    private long lastFrame;
    private long startNanos;
    private final float d;

    private int style = WATER;
    private float shown;          // progress now, 0 to 1, springs towards the target
    private float phase;          // wave position for the water style, 0 to 2*pi
    private float elapsed;        // seconds since the screen came on, drives the other animations
    private float splash = 1f;    // water style: 0 to 1 while a splash plays
    private float pulse = 1f;     // other styles: 0 to 1 after a glass is added, 1 when idle
    private int glasses;
    private int goal = 10;
    private float weekFrac;
    private float streakFrac;
    private String hint = HINT;
    private ValueAnimator waveAnim;
    private ValueAnimator levelAnim;
    private ValueAnimator splashAnim;
    private ValueAnimator pulseAnim;
    private Runnable demoLoop;
    private int demoGlasses;
    private boolean demo;

    public TodayView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        front.setColor(WATER_BLUE);
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
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        buildShapes();
        setClickable(true);
    }

    /** Shapes that never change, built once in the 200 x 200 space. */
    private void buildShapes() {
        for (int i = 0; i <= 120; i++) {
            double a = i / 120.0 * 2 * Math.PI;
            double r = 78 * (1 + 0.06 * Math.cos(10 * a));
            float x = (float) (100 + r * Math.cos(a)), y = (float) (100 + r * Math.sin(a));
            if (i == 0) cookie.moveTo(x, y); else cookie.lineTo(x, y);
        }
        cookie.close();
        squircle.addRoundRect(new RectF(40, 40, 160, 160), 44, 44, Path.Direction.CW);
        dropShape.moveTo(100, 48);
        dropShape.cubicTo(100, 48, 74, 82, 74, 100);
        dropShape.arcTo(new RectF(74, 74, 126, 126), 180, -180);
        dropShape.cubicTo(126, 82, 100, 48, 100, 48);
        dropShape.close();
        glassShape.moveTo(56, 34);
        glassShape.lineTo(144, 34);
        glassShape.lineTo(132, 168);
        glassShape.quadTo(131, 176, 123, 176);
        glassShape.lineTo(77, 176);
        glassShape.quadTo(69, 176, 68, 168);
        glassShape.close();
    }

    public void setStyle(int style) {
        if (style < 0 || style >= NAMES.length || style == this.style) return;
        this.style = style;
        invalidate();
    }

    /**
     * Gallery preview: instead of real data, adds a glass every moment up to a small goal and starts over,
     * so each style shows its fill animation on a loop. Call {@link #stopDemo} when the view goes away.
     */
    public void startDemo() {
        stopDemo();
        demoGlasses = 0;
        demo = true;
        demoLoop = new Runnable() {
            @Override
            public void run() {
                demoGlasses = demoGlasses >= DEMO_GOAL ? 0 : demoGlasses + 1;
                set((float) demoGlasses / DEMO_GOAL, demoGlasses, DEMO_GOAL, 0.6f, 0.5f);
                if (demoGlasses > 0) flash("");
                postDelayed(this, demoGlasses >= DEMO_GOAL ? 2 * DEMO_STEP_MS : DEMO_STEP_MS);
            }
        };
        post(demoLoop);
        startWave();
    }

    public void stopDemo() {
        if (demoLoop != null) removeCallbacks(demoLoop);
        demoLoop = null;
        stopWave();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopDemo();
        super.onDetachedFromWindow();
    }

    public void set(float pct, int glasses, int goal, float weekFrac, float streakFrac) {
        boolean added = glasses > this.glasses;
        this.glasses = glasses;
        this.goal = goal;
        this.weekFrac = weekFrac;
        this.streakFrac = streakFrac;
        float target = Math.max(0f, Math.min(1f, pct));
        if (levelAnim != null) levelAnim.cancel();
        levelAnim = ValueAnimator.ofFloat(shown, target);
        levelAnim.setDuration(800);
        levelAnim.setInterpolator(new OvershootInterpolator(1.3f));
        levelAnim.addUpdateListener(a -> {
            shown = (Float) a.getAnimatedValue();
            invalidate();
        });
        levelAnim.start();
        if (added) {
            if (pulseAnim != null) pulseAnim.cancel();
            pulseAnim = ValueAnimator.ofFloat(0f, 1f);
            pulseAnim.setDuration(650);
            pulseAnim.addUpdateListener(a -> pulse = (Float) a.getAnimatedValue());
            pulseAnim.start();
        }
    }

    public void startWave() {
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

    public void stopWave() {
        if (waveAnim != null) waveAnim.cancel();
        lastFrame = 0;
    }

    /** +1 normally; -1 when the "Gauge and pills" style is showing and the minus pill is tapped. */
    public int tapDelta(float x, float y) {
        if (style != 4) return 1;
        float u = Math.min(getWidth(), getHeight()) / 200f;
        float gx = (x - (getWidth() - 200 * u) / 2f) / u, gy = (y - (getHeight() - 200 * u) / 2f) / u;
        return gy >= 132 && gx < 96 ? -1 : 1;
    }

    /** Shows a short message in place of the hint and plays the splash (water style). */
    public void flash(String message) {
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

    // ---- drawing -------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        long now = System.nanoTime();
        if (startNanos == 0) startNanos = now;
        elapsed = (now - startNanos) / 1e9f;
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        if (style == WATER) {
            drawWater(c, w, h, dt);
            return;
        }
        float u = Math.min(w, h) / 200f;
        c.save();
        c.translate((w - 200 * u) / 2f, (h - 200 * u) / 2f);
        c.scale(u, u);
        float f = Math.max(0f, Math.min(1f, shown));
        int lit = Math.min(PETALS, Math.round(f * PETALS));
        float bump = 1f + 0.16f * (float) Math.sin(Math.PI * pulse);
        switch (style) {
            case 0: drawArcs(c, f, bump); break;
            case 1: drawCookie(c, f, bump); break;
            case 2: drawWavyRing(c, f, bump); break;
            case 3: drawSquircle(c, f, bump); break;
            case 4: drawGauge(c, f, bump); break;
            case 5: drawBloom(c, lit); break;
            case 7: drawRingDrop(c, f, bump); break;
            case 8: drawBigGlass(c, lit, bump); break;
            case 9: drawAquarium(c); break;
            case 10: drawPlant(c, f, lit); break;
            default: drawOrbit(c, lit, bump); break;
        }
        c.restore();
    }

    private void label(Canvas c, String s, float x, float y, float size, int color, float scale, boolean medium) {
        text.setTypeface(medium ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        text.setTextSize(size);
        text.setColor(color);
        c.save();
        c.scale(scale, scale, x, y - size * 0.3f);
        c.drawText(s, x, y, text);
        c.restore();
    }

    private void ring(Canvas c, float r, float width, int track, int color, float start, float sweep, float f) {
        oval.set(100 - r, 100 - r, 100 + r, 100 + r);
        stroke.setStrokeWidth(width);
        stroke.setColor(track);
        c.drawArc(oval, start, sweep, false, stroke);
        if (f > 0.004f) {
            stroke.setColor(color);
            c.drawArc(oval, start, sweep * Math.min(1f, f), false, stroke);
        }
    }

    private void ripple(Canvas c, int color) {
        if (pulse >= 1f) return;
        stroke.setStrokeWidth(3);
        stroke.setColor(color);
        stroke.setAlpha((int) (200 * (1f - pulse)));
        c.drawCircle(100, 100, 40 + 50 * pulse, stroke);
    }

    /** Three nested arcs like a Fitbit or Pixel Watch activity screen: today, this week, streak. */
    private void drawArcs(Canvas c, float f, float bump) {
        ring(c, 78, 12, 0xFF1D2433, BLUE, 135, 270, f);
        ring(c, 62, 12, 0xFF17261D, GREEN, 135, 270, weekFrac);
        ring(c, 46, 12, 0xFF33202A, PINK, 135, 270, streakFrac);
        label(c, String.valueOf(glasses), 100, 112, 40, Color.WHITE, bump, true);
        label(c, glasses == 1 ? "glass" : "glasses", 100, 132, 12, GRAY, 1f, false);
    }

    /** A scalloped cookie that slowly turns and fills with colour from the bottom. */
    private void drawCookie(Canvas c, float f, float bump) {
        float y = 170 - f * 150;
        fill.setColor(0xFF16231B);
        c.save();
        c.scale(bump, bump, 100, 100);
        c.rotate(elapsed * 9f, 100, 100);
        c.drawPath(cookie, fill);
        c.restore();
        c.save();
        c.clipRect(0, y, 200, 220);
        c.scale(bump, bump, 100, 100);
        c.rotate(elapsed * 9f, 100, 100);
        fill.setColor(GREEN);
        c.drawPath(cookie, fill);
        c.restore();
        label(c, String.valueOf(glasses), 100, 114, 46, Color.WHITE, bump, true);
        c.save();
        c.clipRect(0, y, 200, 220);
        label(c, String.valueOf(glasses), 100, 114, 46, 0xFF0B2A18, bump, true);
        c.restore();
    }

    /** A thin track with a wavy progress line that keeps flowing. */
    private void drawWavyRing(Canvas c, float f, float bump) {
        stroke.setStrokeWidth(3);
        stroke.setColor(0xFF2A2618);
        c.drawCircle(100, 100, 74, stroke);
        if (f > 0.004f) {
            work.reset();
            int n = Math.max(8, (int) (f * 180));
            for (int i = 0; i <= n; i++) {
                double a = -Math.PI / 2 + (double) i / n * f * 2 * Math.PI;
                double r = 74 + 4 * Math.sin(a * 14 + elapsed * 3.6);
                float x = (float) (100 + r * Math.cos(a)), y = (float) (100 + r * Math.sin(a));
                if (i == 0) work.moveTo(x, y); else work.lineTo(x, y);
            }
            stroke.setStrokeWidth(10);
            stroke.setColor(YELLOW);
            c.drawPath(work, stroke);
        }
        label(c, String.valueOf(glasses), 100, 112, 44, Color.WHITE, bump, true);
        label(c, "of " + goal + " glasses", 100, 134, 12, GRAY, 1f, false);
    }

    /** A rounded tile filling with moving water; the number turns dark where the water covers it. */
    private void drawSquircle(Canvas c, float f, float bump) {
        fill.setColor(0xFF14202E);
        c.drawPath(squircle, fill);
        float level = 160 - f * 120;
        c.save();
        c.clipPath(squircle);
        work.reset();
        work.moveTo(0, 200);
        for (int x = 0; x <= 200; x += 5) {
            work.lineTo(x, level + 4 * (float) Math.sin(x / 200f * 4 * Math.PI + elapsed * 2.6f));
        }
        work.lineTo(200, 200);
        work.close();
        fill.setColor(BLUE);
        c.drawPath(work, fill);
        c.restore();
        label(c, String.valueOf(glasses), 100, 112, 46, Color.WHITE, bump, true);
        c.save();
        c.clipPath(squircle);
        c.clipRect(0, level + 4, 200, 220);
        label(c, String.valueOf(glasses), 100, 112, 46, 0xFF0A2A5C, bump, true);
        c.restore();
    }

    /** Half gauge on top, and two pills below: minus and plus one. */
    private void drawGauge(Canvas c, float f, float bump) {
        oval.set(38, 44, 162, 168);
        stroke.setStrokeWidth(12);
        stroke.setColor(0xFF1D2433);
        c.drawArc(oval, 180, 180, false, stroke);
        if (f > 0.004f) {
            stroke.setColor(BLUE);
            c.drawArc(oval, 180, 180 * f, false, stroke);
        }
        label(c, String.valueOf(glasses), 100, 100, 38, Color.WHITE, bump, true);
        fill.setColor(0xFF2B2F3A);
        oval.set(30, 138, 92, 170);
        c.drawRoundRect(oval, 16, 16, fill);
        label(c, "−", 61, 161, 17, Color.WHITE, 1f, false);
        c.save();
        c.scale(bump, bump, 136, 154);
        fill.setColor(BLUE);
        oval.set(100, 138, 172, 170);
        c.drawRoundRect(oval, 16, 16, fill);
        label(c, "+1", 136, 160, 15, 0xFF0A2A5C, 1f, true);
        c.restore();
    }

    /** A flower: one petal opens per twelfth of the goal, the newest with a little spring. */
    private void drawBloom(Canvas c, int lit) {
        int[] on = {PINK, 0xFFF8A1C4, 0xFFFFD8E4};
        for (int i = 0; i < PETALS; i++) {
            c.save();
            c.rotate(i * 360f / PETALS, 100, 100);
            if (i == lit - 1 && pulse < 1f) {
                float s = pulse < 0.6f ? pulse / 0.6f * 1.25f : 1.25f - 0.25f * (pulse - 0.6f) / 0.4f;
                c.scale(s, s, 100, 46);
            }
            fill.setColor(i < lit ? on[i % 3] : 0xFF2A1D24);
            oval.set(85, 20, 115, 72);
            c.drawOval(oval, fill);
            c.restore();
        }
        fill.setColor(0xFFFFD8E4);
        c.drawCircle(100, 100, 34, fill);
        label(c, String.valueOf(glasses), 100, 112, 34, 0xFF4A1D2D, 1f, true);
    }

    /** A ring that fills around a water drop; the drop pulses and a ripple spreads on each glass. */
    private void drawRingDrop(Canvas c, float f, float bump) {
        ring(c, 82, 12, 0xFF222222, WATER_BLUE, -90, 360, f);
        c.save();
        c.scale(bump, bump, 100, 100);
        fill.setColor(WATER_BLUE);
        c.drawPath(dropShape, fill);
        c.restore();
        ripple(c, WATER_BLUE);
        label(c, String.valueOf(glasses), 100, 152, 30, Color.WHITE, 1f, true);
    }

    /** A tall glass that fills one band per twelfth of the goal. */
    private void drawBigGlass(Canvas c, int lit, float bump) {
        stroke.setStrokeWidth(3);
        stroke.setColor(0xFF666666);
        c.drawPath(glassShape, stroke);
        c.save();
        c.clipPath(glassShape);
        for (int i = 0; i < lit; i++) {
            float y1 = 176 - i * 11.8f;
            fill.setColor(i == lit - 1 && pulse < 1f ? 0xFFB3E5FC : 0xD94FC3F7);
            c.drawRect(40, y1 - 10.3f, 160, y1, fill);
        }
        c.restore();
        label(c, String.valueOf(glasses), 100, 112, 46, Color.WHITE, bump, true);
    }

    /** A fish for every glass, swimming back and forth, with bubbles rising from the sand. */
    private void drawAquarium(Canvas c) {
        fill.setColor(0xFF06202E);
        c.drawRect(-40, -40, 240, 240, fill);
        int[] colors = {0xFFFF9F43, 0xFFFFD166, 0xFFEF476F, 0xFF06D6A0};
        int fish = Math.min(glasses, 24);
        for (int i = 0; i < fish; i++) {
            float sc = 0.8f + (i % 3) * 0.2f;
            float y = 60 + ((i + 1) * 37) % 100;
            float dur = 5 + i % 5;
            float ph = ((elapsed + (i + 1) * 1.3f) / dur) % 1f;
            float pos = ph < 0.5f ? -46 + 92 * ph * 2 : 46 - 92 * (ph - 0.5f) * 2;
            float dir = ph < 0.5f ? 1f : -1f;
            c.save();
            c.translate(100 + pos, y);
            c.scale(sc * dir, sc);
            fill.setColor(colors[i % 4]);
            oval.set(-12, -7, 12, 7);
            c.drawOval(oval, fill);
            work.reset();
            work.moveTo(-10, 0);
            work.lineTo(-20, -6);
            work.lineTo(-20, 6);
            work.close();
            c.drawPath(work, fill);
            fill.setColor(Color.BLACK);
            c.drawCircle(6, -2, 1.6f, fill);
            c.restore();
        }
        stroke.setStrokeWidth(1);
        stroke.setColor(0x99BFEAFF);
        for (int i = 0; i < 4; i++) {
            float by = 170 - (elapsed * 30 + i * 40) % 150;
            c.drawCircle(30 + i * 45, by, 3 + (i % 2) * 2, stroke);
        }
        fill.setColor(0xFFC9B27C);
        c.drawRect(-40, 176, 240, 240, fill);
        label(c, glasses == 1 ? "1 fish" : glasses + " fish", 100, 44, 26, Color.WHITE, 1f, true);
    }

    /** A sprout in a pot that grows taller and sets leaves, then flowers when the goal is reached. */
    private void drawPlant(Canvas c, float f, int lit) {
        fill.setColor(0xFF0E1A12);
        c.drawRect(-40, -40, 240, 240, fill);
        float height = f * 90;
        c.save();
        c.rotate((float) Math.sin(elapsed * 2) * 2f, 100, 150);
        stroke.setStrokeWidth(4);
        stroke.setColor(0xFF4CAF50);
        c.drawLine(100, 150, 100, 150 - height, stroke);
        int leaves = Math.min(5, (int) Math.ceil(lit / 2.4));
        fill.setColor(0xFF66BB6A);
        for (int i = 0; i < leaves; i++) {
            float ly = 146 - (i + 1) * (height / (leaves + 1));
            int side = i % 2 == 0 ? -1 : 1;
            c.save();
            c.translate(100 + side * 13, ly);
            c.rotate(-side * 25);
            oval.set(-12, -5, 12, 5);
            c.drawOval(oval, fill);
            c.restore();
        }
        float top = 150 - height;
        if (f >= 1f) {
            fill.setColor(0xFFFF80AB);
            for (int i = 0; i < 8; i++) {
                double a = i / 8.0 * 2 * Math.PI;
                c.drawCircle((float) (100 + Math.cos(a) * 13), (float) (top + Math.sin(a) * 13), 8, fill);
            }
            fill.setColor(0xFFFFD54F);
            c.drawCircle(100, top, 7, fill);
        } else {
            fill.setColor(0xFF81C784);
            c.drawCircle(100, top, 5 + lit * 0.4f, fill);
        }
        c.restore();
        fill.setColor(0xFFB5651D);
        work.reset();
        work.moveTo(78, 150);
        work.lineTo(122, 150);
        work.lineTo(116, 182);
        work.lineTo(84, 182);
        work.close();
        c.drawPath(work, fill);
        label(c, glasses + " / " + goal, 100, 38, 24, Color.WHITE, 1f, true);
    }

    /** Twelve dots on an orbit that light up one by one while the whole orbit slowly turns. */
    private void drawOrbit(Canvas c, int lit, float bump) {
        c.save();
        c.rotate(elapsed * 26f, 100, 100);
        for (int i = 0; i < PETALS; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / PETALS;
            fill.setColor(i < lit ? WATER_BLUE : 0xFF2A2A2A);
            c.drawCircle((float) (100 + 80 * Math.cos(a)), (float) (100 + 80 * Math.sin(a)), 6, fill);
        }
        fill.setColor(Color.WHITE);
        c.drawCircle(100, 20, 3, fill);
        c.restore();
        ripple(c, WATER_BLUE);
        label(c, String.valueOf(glasses), 100, 116, 44, Color.WHITE, bump, true);
    }

    // ---- rising water (the original style, drawn in real pixels) -------------------------

    private void drawWave(Canvas c, float w, float h, float level, float amplitude, float shift, float speed,
            float cycles, Paint paint, boolean withCrest) {
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
        by[i] = anywhere ? level + 12 * d + random.nextFloat() * Math.max(1f, h - level - 12 * d) : h + br[i];
        bs[i] = (14f + 20f * random.nextFloat()) * d;
        bp[i] = random.nextFloat() * 6.28f;
    }

    private void drawWater(Canvas c, float w, float h, float dt) {
        circle.reset();
        circle.addCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, Path.Direction.CW);
        c.save();
        c.clipPath(circle);
        float level = h - h * (0.05f + 0.90f * Math.max(0f, Math.min(1f, shown)));

        float slosh = splash >= 0.3f && splash < 1f ? 1f - (splash - 0.3f) / 0.7f : 0f;
        float boost = 1f + 2f * slosh;
        drawWave(c, w, h, level, 7 * d * boost, 1.3f, 1f, 1.0f, back, false);
        drawWave(c, w, h, level + 4 * d, 5 * d * boost, 0f, -2f, 1.4f, front, true);

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

        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(64 * d);
        c.drawText(String.valueOf(glasses), w / 2f, h / 2f + 16 * d, text);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(14 * d);
        c.drawText("of " + goal + " glasses", w / 2f, h / 2f + 42 * d, text);
        text.setTextSize(12 * d);
        if (!demo) c.drawText(hint, w / 2f, h * 0.88f, text);
    }
}
