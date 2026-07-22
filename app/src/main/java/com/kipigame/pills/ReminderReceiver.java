package com.kipigame.pills;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_TAKEN = "com.kipigame.pills.TAKEN";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Store store = new Store(ctx);

        if (ACTION_TAKEN.equals(intent.getAction())) {
            // Нажали «Принял» в уведомлении
            store.logTaken(intent.getStringExtra("pill"), System.currentTimeMillis());
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(intent.getIntExtra("notifId", 0));
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
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(channelId, "Напоминания", NotificationManager.IMPORTANCE_HIGH);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build();
            ch.setSound(sound, attrs);
            nm.createNotificationChannel(ch);

            Intent taken = new Intent(ctx, ReminderReceiver.class);
            taken.setAction(ACTION_TAKEN);
            taken.putExtra("pill", pill);
            taken.putExtra("notifId", notifId);
            PendingIntent takenPi = PendingIntent.getBroadcast(ctx, notifId, taken,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent open = new Intent(ctx, MainActivity.class);
            PendingIntent openPi = PendingIntent.getActivity(ctx, notifId, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            android.app.Notification n = new android.app.Notification.Builder(ctx, channelId)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Время принять: " + pill)
                    .setContentText("Нажми «Принял», чтобы записать в журнал")
                    .setContentIntent(openPi)
                    .setAutoCancel(true)
                    .addAction(0, "Принял", takenPi)
                    .build();
            nm.notify(notifId, n);

            // Перепланируем следующее срабатывание
            Scheduler.schedule(ctx, r);
        } catch (JSONException ignored) {}
    }
}
