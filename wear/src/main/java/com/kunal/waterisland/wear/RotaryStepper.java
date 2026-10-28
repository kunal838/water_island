package com.kunal.waterisland.wear;

import android.content.Context;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * Turns the rotary dial (crown or touch bezel) into one step per click. Small turns add up until they
 * reach a click, a pause starts a fresh count, and a short cooldown stops one fast spin from skipping
 * several pages at once.
 */
final class RotaryStepper {
    private static final long IDLE_RESET_MS = 250;
    private static final long COOLDOWN_MS = 140;

    /** The light tick played for each click of the dial. */
    static int tick() {
        return android.os.Build.VERSION.SDK_INT >= 34
                ? android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                : android.view.HapticFeedbackConstants.CLOCK_TICK;
    }

    private float accumulated;
    private long lastEvent;
    private long lastStep;

    /** +1 for a click clockwise (towards the next item), -1 for anticlockwise, 0 when nothing to do. */
    int onEvent(Context c, MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_SCROLL || !e.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            return 0;
        }
        long now = e.getEventTime();
        if (now - lastEvent > IDLE_RESET_MS) accumulated = 0;
        lastEvent = now;
        // Clockwise reports a negative scroll value, like scrolling a list down.
        accumulated += -e.getAxisValue(MotionEvent.AXIS_SCROLL) * ViewConfiguration.get(c).getScaledVerticalScrollFactor();
        float click = ViewConfiguration.get(c).getScaledVerticalScrollFactor();
        if (Math.abs(accumulated) < click || now - lastStep < COOLDOWN_MS) return 0;
        int step = accumulated > 0 ? 1 : -1;
        accumulated = 0;
        lastStep = now;
        return step;
    }
}
