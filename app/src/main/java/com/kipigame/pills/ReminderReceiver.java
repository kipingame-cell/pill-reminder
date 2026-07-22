package com.kipigame.pills;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_TAKEN = "com.kipigame.pills.TAKEN";
    public static final String ACTION_SNOOZE = "com.kipigame.pills.SNOOZE";
    private static final int ACCENT = 0xFF5EC496;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Store store = new Store(ctx);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        if (ACTION_TAKEN.equals(intent.getAction())) {
            store.logTaken(intent.getStringExtra("pill"), System.currentTimeMillis());
            nm.cancel(intent.getIntExtra("notifId", 0));
            return;
        }

        if (ACTION_SNOOZE.equals(intent.getAction())) {
            nm.cancel(intent.getIntExtra("notifId", 0));
            String json = intent.getStringExtra("reminder");
            if (json == null) return;
            try {
                JSONObject r = new JSONObject(json);
                Intent again = new Intent(ctx, ReminderReceiver.class);
                again.putExtra("reminder", r.toString());
                int req = (int) (r.optLong("id") % Integer.MAX_VALUE);
                PendingIntent pi = PendingIntent.getBroadcast(ctx, req, again,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 10 * 60 * 1000, pi);
            } catch (JSONException ignored) {}
            return;
        }

        String json = intent.getStringExtra("reminder");
        if (json == null) return;
        try {
            JSONObject r = new JSONObject(json);
            if (!r.optBoolean("active", true)) return;

            String pill = r.optString("pill", "Таблетка");
            long id = r.optLong("id");
            int notifId = (int) (id % Integer.MAX_VALUE);

            // Канал под конкретный звук (у канала звук неизменяем)
            Uri sound = Scheduler.resolveSound(ctx, r.optString("sound", null));
            String channelId = "pill_" + Math.abs(sound.toString().hashCode());
            NotificationChannel ch = new NotificationChannel(channelId, "Напоминания", NotificationManager.IMPORTANCE_HIGH);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build();
            ch.setSound(sound, attrs);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 250, 150, 250});
            nm.createNotificationChannel(ch);

            Intent taken = new Intent(ctx, ReminderReceiver.class);
            taken.setAction(ACTION_TAKEN);
            taken.putExtra("pill", pill);
            taken.putExtra("notifId", notifId);
            PendingIntent takenPi = PendingIntent.getBroadcast(ctx, notifId, taken,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent snooze = new Intent(ctx, ReminderReceiver.class);
            snooze.setAction(ACTION_SNOOZE);
            snooze.putExtra("reminder", r.toString());
            snooze.putExtra("notifId", notifId);
            PendingIntent snoozePi = PendingIntent.getBroadcast(ctx, notifId + 500000, snooze,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent open = new Intent(ctx, MainActivity.class);
            PendingIntent openPi = PendingIntent.getActivity(ctx, notifId, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification n = new Notification.Builder(ctx, channelId)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setColor(ACCENT)
                    .setColorized(true)
                    .setContentTitle("Время принять таблетку")
                    .setContentText(pill)
                    .setStyle(new Notification.BigTextStyle()
                            .setBigContentTitle("Время принять таблетку")
                            .bigText(pill + "\nНажми «Принял», чтобы записать в журнал, или отложи на 10 минут."))
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setContentIntent(openPi)
                    .setAutoCancel(true)
                    .addAction(0, "Принял", takenPi)
                    .addAction(0, "Отложить 10 мин", snoozePi)
                    .build();
            nm.notify(notifId, n);

            // Перепланируем следующее срабатывание
            Scheduler.schedule(ctx, r);
        } catch (JSONException ignored) {}
    }
}
