package com.kunal.waterisland;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.io.OutputStream;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** All settings, reminders and permissions live here; the main page only shows water and graphs. */
public class SettingsActivity extends Activity {
    private static final int GRAY = 0xFF9AA5B1;
    private static final int LINE = 0x1AFFFFFF;
    private static final int[] GLASS_SIZES = {250, 333, 500};

    private LinearLayout list;
    private TextView remindersValue;
    private TextView glassValue;
    private TextView goalValue;
    private TextView intervalValue;
    private TextView soundValue;
    private TextView watchValue;
    private TextView styleValue;
    private final List<Runnable> permRefreshers = new ArrayList<>();

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
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
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(20), dp(24), dp(32));
        page.setBackgroundColor(0xFF111111);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, Color.WHITE, false);
        back.setPadding(0, 0, dp(14), dp(4));
        back.setOnClickListener(v -> finish());
        header.addView(back);
        header.addView(text("Settings", 22, Color.WHITE, true));
        page.addView(header);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list);
        build();

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
    }

    private void section(String title) {
        TextView t = text(title, 13, GRAY, false);
        t.setPadding(0, dp(28), 0, dp(6));
        list.addView(t);
    }

    private TextView row(String label, Runnable click) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(14), 0, dp(14));
        r.addView(text(label, 15, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text("", 14, GRAY, false);
        r.addView(value);
        if (click != null) r.setOnClickListener(v -> click.run());
        list.addView(r);
        list.addView(divider());
        return value;
    }

    private void permRow(String label, BooleanSupplier granted, Runnable grant) {
        TextView value = row(label, grant);
        permRefreshers.add(() -> value.setText(granted.getAsBoolean() ? "✓" : "Grant"));
    }

    private void build() {
        section("Reminders");
        remindersValue = row("Reminders", () -> {
            String a = Store.enabled(this) ? IslandService.ACTION_STOP : IslandService.ACTION_START;
            startForegroundService(new Intent(this, IslandService.class).setAction(a));
            list.postDelayed(this::refresh, 300);
        });
        intervalValue = row("Remind every", () -> {
            int[] all = Store.INTERVALS_SEC;
            int cur = Store.intervalSec(this);
            int next = all[0];
            for (int i = 0; i < all.length; i++) if (all[i] == cur) next = all[(i + 1) % all.length];
            Store.setIntervalSec(this, next);
            if (Store.enabled(this)) Alarms.scheduleNext(this);
            refresh();
        });
        soundValue = row("Reminder sound", () -> { Store.setSound(this, !Store.sound(this)); refresh(); });
        watchValue = row("Remind on watch", () -> {
            Store.setWatchReminders(this, !Store.watchReminders(this));
            refresh();
        });
        styleValue = row("Reminder style", () -> {
            if (Build.VERSION.SDK_INT < 36) {
                Toast.makeText(this, "The island needs Android 16 or newer. This phone uses normal notifications.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Store.setIslandStyle(this, !Store.islandStyle(this));
            refresh();
        });

        section("Water");
        glassValue = row("Glass size", () -> {
            int cur = Store.glassMl(this);
            int next = GLASS_SIZES[0];
            for (int i = 0; i < GLASS_SIZES.length; i++) if (GLASS_SIZES[i] == cur) next = GLASS_SIZES[(i + 1) % GLASS_SIZES.length];
            Store.setGlassMl(this, next);
            refresh();
        });

        LinearLayout goalRow = new LinearLayout(this);
        goalRow.setGravity(Gravity.CENTER_VERTICAL);
        goalRow.setPadding(0, dp(8), 0, dp(8));
        goalRow.addView(text("Daily goal", 15, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView minus = text("−", 22, Color.WHITE, false);
        minus.setPadding(dp(16), dp(6), dp(16), dp(6));
        minus.setOnClickListener(v -> { Store.setGoalGlasses(this, Store.goalGlasses(this) - 1); refresh(); });
        goalValue = text("", 14, GRAY, false);
        goalValue.setGravity(Gravity.CENTER);
        goalValue.setMinWidth(dp(90));
        TextView plus = text("+", 22, Color.WHITE, false);
        plus.setPadding(dp(16), dp(6), dp(16), dp(6));
        plus.setOnClickListener(v -> { Store.setGoalGlasses(this, Store.goalGlasses(this) + 1); refresh(); });
        goalRow.addView(minus);
        goalRow.addView(goalValue);
        goalRow.addView(plus);
        list.addView(goalRow);
        list.addView(divider());

        section("Permissions");
        if (Build.VERSION.SDK_INT >= 36) {
            permRow("Live updates (system island)", () ->
                    getSystemService(NotificationManager.class).canPostPromotedNotifications(), () ->
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS,
                            Uri.parse("package:" + getPackageName()))));
        }
        permRow("Notifications", () -> getSystemService(NotificationManager.class).areNotificationsEnabled(), () -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            } else {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        });
        permRow("Exact alarms", () -> Alarms.canScheduleExact(this), () -> {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        permRow("Ignore battery optimisation", () ->
                getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName()), () ->
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()))));

        section("Data");
        row("Export data", this::chooseExport).setText("Excel, CSV, JSON");

        section("Actions");
        row("Test reminder now", () ->
                startForegroundService(new Intent(this, IslandService.class).setAction(IslandService.ACTION_REMIND)));
        row("Reset today", () -> {
            Store.resetToday(this);
            if (Store.enabled(this)) {
                startForegroundService(new Intent(this, IslandService.class).setAction(IslandService.ACTION_REFRESH));
            }
        });
    }

    private static final int REQ_EXPORT = 7;
    private int pendingFormat = -1;

    private void chooseExport() {
        new AlertDialog.Builder(this)
                .setTitle("Export as")
                .setItems(new String[]{"Excel (.xlsx)", "CSV (.csv)", "JSON (.json)"}, (d, which) -> {
                    pendingFormat = which;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(Exporter.mime(which))
                            .putExtra(Intent.EXTRA_TITLE, Exporter.fileName(which));
                    startActivityForResult(i, REQ_EXPORT);
                })
                .show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_EXPORT || result != RESULT_OK || data == null || data.getData() == null
                || pendingFormat < 0) {
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            out.write(Exporter.build(this, pendingFormat));
            Toast.makeText(this, "Saved " + Exporter.fileName(pendingFormat), Toast.LENGTH_LONG).show();
        } catch (IOException | JSONException | RuntimeException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        remindersValue.setText(Store.enabled(this) ? "On" : "Off");
        int every = Store.intervalSec(this);
        intervalValue.setText(every >= 3600 ? "1 hour" : every >= 60 ? (every / 60) + " min (test)" : every + " sec (test)");
        soundValue.setText(Store.sound(this) ? "On" : "Off");
        watchValue.setText(Store.watchReminders(this) ? "On" : "Off");
        styleValue.setText(Store.islandStyle(this) ? "Island" : "Normal notification");
        glassValue.setText(Store.glassMl(this) + " ml");
        goalValue.setText(Store.goalGlasses(this) + " glasses");
        for (Runnable r : permRefreshers) r.run();
    }
}
