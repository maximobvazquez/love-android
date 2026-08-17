/**
 * Copyright (c) 2006-2024 LOVE Development Team
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 */
package org.love2d.android;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Map;

public class NotificationScheduler {
    private static final String PREFS_NAME = "love_notifications";

    /**
     * Schedule a notification to be shown after a delay.
     */
    public static void schedule(Context context, int id, long delaySeconds, String title, String message) {
        long triggerTime = System.currentTimeMillis() + delaySeconds * 1000L;

        // Save notification data for rescheduling after reboot
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("trigger_" + id, triggerTime);
        editor.putString("title_" + id, title);
        editor.putString("message_" + id, message);
        editor.apply();

        // Schedule the alarm
        doSchedule(context, id, triggerTime, title, message);
    }

    /**
     * Cancel a scheduled notification.
     */
    public static void cancel(Context context, int id) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pending);
        }

        // Remove from preferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("trigger_" + id);
        editor.remove("title_" + id);
        editor.remove("message_" + id);
        editor.apply();
    }

    /**
     * Reschedule all pending notifications (called after device reboot).
     */
    public static void rescheduleAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        long now = System.currentTimeMillis();

        for (String key : all.keySet()) {
            if (key.startsWith("trigger_")) {
                String idStr = key.substring("trigger_".length());
                try {
                    int id = Integer.parseInt(idStr);
                    long trigger = prefs.getLong(key, 0);

                    if (trigger > now) {
                        String title = prefs.getString("title_" + id, "");
                        String message = prefs.getString("message_" + id, "");
                        doSchedule(context, id, trigger, title, message);
                    } else {
                        // Notification already passed, remove it
                        cancel(context, id);
                    }
                } catch (NumberFormatException e) {
                    // Invalid ID, skip
                }
            }
        }
    }

    private static void doSchedule(Context context, int id, long triggerAt, String title, String message) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra("notification_id", id);
        intent.putExtra("title", title);
        intent.putExtra("message", message);

        PendingIntent pending = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            // Use setExactAndAllowWhileIdle for exact timing even in Doze mode
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
    }
}
