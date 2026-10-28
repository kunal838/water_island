package com.kunal.waterisland.wear;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Full-screen gallery of the Today styles. The current one plays its animation on a loop; swipe sideways
 * to flip to the next, and tap the button to use it. The choice is sent to the phone through
 * {@link WatchData#setStyle}.
 */
public class StyleGalleryActivity extends Activity {
    private TodayView preview;
    private TextView title;
    private TextView use;
    private int index;
    private int current;
    private float downX;
    private boolean animating;
    private boolean dragging;
    private VelocityTracker velocity;
    private final RotaryStepper rotary = new RotaryStepper();

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        current = WatchData.style(this);
        index = current;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // The style plays inside a circle in the middle, so the name above and the button below stay clear of it.
        preview = new TodayView(this);
        preview.setStyle(index);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(Color.BLACK);
        disc.setStroke((int) dp(2), 0x33FFFFFF);
        preview.setBackground(disc);
        preview.setClipToOutline(true);
        preview.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int size = (int) (0.62f * Math.min(dm.widthPixels, dm.heightPixels));
        root.addView(preview, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));

        title = pill(13, false);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, (int) dp(28), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tp.topMargin = (int) dp(10);
        root.addView(title, tp);

        use = pill(15, true);
        use.setOnClickListener(v -> {
            WatchData.setStyle(this, index);
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            current = index;
            show();
            v.postDelayed(this::finish, 350);
        });
        FrameLayout.LayoutParams up = new FrameLayout.LayoutParams((int) dp(120), (int) dp(34), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        up.bottomMargin = (int) dp(10);
        root.addView(use, up);

        // Swiping sideways flips between styles; the buttons keep handling their own taps.
        root.setOnTouchListener((v, e) -> onDrag(e));
        preview.setOnTouchListener((v, e) -> onDrag(e));
        setContentView(root);
        show();
    }

    /** The preview follows the finger; on release it flips if the drag was far or fast enough, else springs back. */
    private boolean onDrag(MotionEvent e) {
        float w = preview.getWidth();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                dragging = false;
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (velocity == null || animating) return true;
                velocity.addMovement(e);
                float dx = e.getRawX() - downX;
                if (Math.abs(dx) > ViewConfiguration.get(this).getScaledTouchSlop()) dragging = true;
                if (dragging) {
                    preview.setTranslationX(dx * 0.5f);
                    preview.setAlpha(1f - Math.min(1f, Math.abs(dx) / (w * 1.2f)) * 0.8f);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (velocity == null) return true;
                velocity.addMovement(e);
                velocity.computeCurrentVelocity(1000);
                float vx = velocity.getXVelocity();
                velocity.recycle();
                velocity = null;
                float dx = e.getRawX() - downX;
                if (!dragging || animating) return true;
                dragging = false;
                if (e.getActionMasked() == MotionEvent.ACTION_UP && (Math.abs(dx) > dp(56) || Math.abs(vx) > dp(300))) {
                    flip(dx < 0 || vx < -dp(300) ? 1 : -1);
                } else {
                    preview.animate().translationX(0f).alpha(1f).setDuration(180)
                            .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f)).start();
                }
                return true;
            }
            default:
                return true;
        }
    }

    /** The dial flips one style per click, with a tick. */
    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        int step = rotary.onEvent(this, e);
        if (step != 0) {
            preview.performHapticFeedback(RotaryStepper.tick());
            flip(step);
        }
        return e.getAction() == MotionEvent.ACTION_SCROLL || super.onGenericMotionEvent(e);
    }

    private TextView pill(int sp, boolean solid) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) dp(12), 0, (int) dp(12), 0);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(solid ? Color.WHITE : 0x99000000);
        bg.setCornerRadius(dp(20));
        t.setBackground(bg);
        t.setTextColor(solid ? Color.BLACK : Color.WHITE);
        return t;
    }

    private void show() {
        title.setText((index + 1) + "/" + TodayView.NAMES.length + "  " + TodayView.NAMES[index]);
        use.setText(index == current ? "In use ✓" : "Use this");
    }

    /** Slides the preview out from wherever it is and the next style in from the other side. */
    private void flip(int step) {
        if (animating) return;
        animating = true;
        float w = preview.getWidth();
        PathInterpolator ease = new PathInterpolator(0.2f, 0f, 0f, 1f);
        preview.animate().translationX(-step * w * 0.45f).alpha(0f).setDuration(120).withEndAction(() -> {
            index = (index + step + TodayView.NAMES.length) % TodayView.NAMES.length;
            preview.setStyle(index);
            show();
            preview.setTranslationX(step * w * 0.45f);
            preview.animate().translationX(0f).alpha(1f).setDuration(220).setInterpolator(ease)
                    .withEndAction(() -> animating = false).start();
        }).setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        preview.startDemo();
    }

    @Override
    protected void onPause() {
        preview.stopDemo();
        super.onPause();
    }
}
