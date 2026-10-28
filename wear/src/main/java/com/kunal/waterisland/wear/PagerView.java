package com.kunal.waterisland.wear;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal pager for the watch. The pages follow the finger, resist at the ends, and on release settle on
 * the page the fling points at (a quick flick moves one page even on a short drag). The neighbouring page
 * slides in smaller and dimmer, and the rotary dial moves one page per click with a tick.
 */
final class PagerView extends ViewGroup {
    interface Listener {
        /** Position in pages while dragging or settling, 0 for the first page. */
        void onPosition(float position);
    }

    private final List<View> pages = new ArrayList<>();
    private final RotaryStepper rotary = new RotaryStepper();
    private final VelocityTracker[] tracker = new VelocityTracker[1];
    private final int touchSlop;
    private final int minFling;
    private final int maxFling;
    private Listener listener;
    private ValueAnimator settle;
    private float position;       // current scroll in pages
    private int page;             // page being shown or settling on
    private float downX;
    private float downY;
    private float lastX;
    private boolean dragging;

    PagerView(Context c) {
        super(c);
        ViewConfiguration vc = ViewConfiguration.get(c);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
        maxFling = vc.getScaledMaximumFlingVelocity();
        setFocusable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(true);
    }

    void setListener(Listener l) { listener = l; }

    void addPage(View v) {
        pages.add(v);
        addView(v);
    }

    int page() { return page; }

    // ---- layout ---------------------------------------------------------------------------

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        for (View v : pages) {
            v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        for (int i = 0; i < pages.size(); i++) pages.get(i).layout(i * w, 0, (i + 1) * w, h);
        apply();
    }

    /** Scrolls to the current position and gives each page its distance-based size and brightness. */
    private void apply() {
        int w = getWidth();
        scrollTo(Math.round(position * w), 0);
        for (int i = 0; i < pages.size(); i++) {
            float away = Math.min(1f, Math.abs(i - position));
            View v = pages.get(i);
            v.setPivotX(v.getWidth() / 2f);
            v.setPivotY(v.getHeight() / 2f);
            v.setScaleX(1f - 0.12f * away);
            v.setScaleY(1f - 0.12f * away);
            v.setAlpha(1f - 0.65f * away);
        }
        if (listener != null) listener.onPosition(position);
    }

    private float maxPosition() { return Math.max(0, pages.size() - 1); }

    // ---- touch ----------------------------------------------------------------------------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = settle != null && settle.isRunning();
                if (dragging) settle.cancel();
                startTracking(e);
                return dragging;
            case MotionEvent.ACTION_MOVE:
                tracker[0].addMovement(e);
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    dragging = true;
                    lastX = e.getX();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stopTracking();
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (tracker[0] == null) startTracking(e);
        tracker[0].addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = lastX - e.getX();
                lastX = e.getX();
                float step = dx / Math.max(1, getWidth());
                // Past the first or last page the pages drag at a fraction of the finger, like a rubber band.
                if ((position < 0 && step < 0) || (position > maxPosition() && step > 0)) step *= 0.35f;
                position = Math.max(-0.3f, Math.min(maxPosition() + 0.3f, position + step));
                apply();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                tracker[0].computeCurrentVelocity(1000, maxFling);
                float vx = tracker[0].getXVelocity();
                stopTracking();
                dragging = false;
                int target = Math.round(position);
                if (Math.abs(vx) > minFling) {
                    // A flick left (negative velocity) moves forward from where the drag started.
                    target = vx < 0 ? (int) Math.floor(position) + 1 : (int) Math.ceil(position) - 1;
                    if (Math.abs(target - page) > 1) target = page + (target > page ? 1 : -1);
                }
                settleTo(target);
                return true;
            }
            default:
                return true;
        }
    }

    private void startTracking(MotionEvent e) {
        stopTracking();
        tracker[0] = VelocityTracker.obtain();
        tracker[0].addMovement(e);
    }

    private void stopTracking() {
        if (tracker[0] != null) {
            tracker[0].recycle();
            tracker[0] = null;
        }
    }

    // ---- settling and the dial ------------------------------------------------------------

    /** Moves to a page with a quick ease-out, or straight there when there is no layout yet. */
    void settleTo(int target) {
        target = Math.max(0, Math.min(pages.size() - 1, target));
        page = target;
        if (settle != null) settle.cancel();
        if (getWidth() == 0) {
            position = target;
            return;
        }
        float from = position;
        settle = ValueAnimator.ofFloat(from, target);
        settle.setDuration(Math.round(200 + 160 * Math.min(1f, Math.abs(target - from))));
        settle.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        settle.addUpdateListener(a -> {
            position = (Float) a.getAnimatedValue();
            apply();
        });
        settle.start();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        return handleRotary(e) || super.onGenericMotionEvent(e);
    }

    /** One page per click of the dial, with a tick. Returns whether the event was a dial turn. */
    boolean handleRotary(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_SCROLL) return false;
        int step = rotary.onEvent(getContext(), e);
        if (step != 0 && ((step > 0 && page < pages.size() - 1) || (step < 0 && page > 0))) {
            settleTo(page + step);
            performHapticFeedback(RotaryStepper.tick());
        }
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestFocus();   // the dial sends its events to the focused view
    }

    @Override
    protected void onDetachedFromWindow() {
        if (settle != null) settle.cancel();
        super.onDetachedFromWindow();
    }
}
