package com.kipigame.pills;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/** Планировщик будильников напоминаний. */
public class Scheduler {

    public static final int CYCLE_DAILY = 0;
    public static final int CYCLE_EVERY_N_DAYS = 1;
    public static final int CYCLE_WEEKDAYS = 2;    // param = битовая маска дней (Пн=1 ... Вс=64)
    public static final int CYCLE_COURSE = 3;      // param = длительность курса в днях от startDate
    public static final int CYCLE_EVERY_N_HOURS = 4; // param = интервал в часах от времени первого приёма

    public static void schedule(Context ctx, JSONObject r) {
        long next = nextTrigger(r, System.currentTimeMillis());
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(ctx, r);
        am.cancel(pi);
        if (next > 0 && r.optBoolean("active", true)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    public static void cancel(Context ctx, JSONObject r) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(ctx, r));
    }

    public static void rescheduleAll(Context ctx) {
        Store store = new Store(ctx);
        JSONArray a = store.getReminders();
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            if (r != null && r.optBoolean("active", true)) schedule(ctx, r);
        }
    }

    private static PendingIntent pending(Context ctx, JSONObject r) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra("reminder", r.toString());
        int req = (int) (r.optLong("id") % Integer.MAX_VALUE);
        return PendingIntent.getBroadcast(ctx, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Следующее срабатывание после from, или -1 если курс закончился. */
    public static long nextTrigger(JSONObject r, long from) {
        int hour = r.optInt("hour", 9);
        int minute = r.optInt("minute", 0);
        int type = r.optInt("cycle", CYCLE_DAILY);
        int param = r.optInt("param", 0);
        long startDate = r.optLong("startDate", from);

        // Каждые N часов: фаза задаётся временем первого приёма (hour:minute)
        if (type == CYCLE_EVERY_N_HOURS) {
            int n = Math.max(1, param);
            Calendar a = Calendar.getInstance();
            a.setTimeInMillis(from);
            a.set(Calendar.HOUR_OF_DAY, hour);
            a.set(Calendar.MINUTE, minute);
            a.set(Calendar.SECOND, 0);
            a.set(Calendar.MILLISECOND, 0);
            while (a.getTimeInMillis() > from) a.add(Calendar.HOUR_OF_DAY, -n);
            while (a.getTimeInMillis() <= from) a.add(Calendar.HOUR_OF_DAY, n);
            return a.getTimeInMillis();
        }

        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(from);
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);

        for (int dayOffset = 0; dayOffset < 370; dayOffset++) {
            Calendar c = (Calendar) base.clone();
            c.add(Calendar.DAY_OF_YEAR, dayOffset);
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            if (c.getTimeInMillis() <= from) continue;

            boolean ok;
            switch (type) {
                case CYCLE_EVERY_N_DAYS: {
                    int n = Math.max(1, param);
                    long daysFromStart = daysBetween(startDate, c.getTimeInMillis());
                    ok = daysFromStart % n == 0;
                    break;
                }
                case CYCLE_WEEKDAYS: {
                    int dow = weekdayBit(c);
                    ok = (param & dow) != 0;
                    break;
                }
                case CYCLE_COURSE: {
                    int n = Math.max(1, param);
                    long daysFromStart = daysBetween(startDate, c.getTimeInMillis());
                    ok = daysFromStart >= 0 && daysFromStart < n;
                    if (daysFromStart >= n) return -1; // курс завершён
                    break;
                }
                default:
                    ok = true;
            }
            if (ok) return c.getTimeInMillis();
        }
        return -1;
    }

    private static int weekdayBit(Calendar c) {
        int dow = c.get(Calendar.DAY_OF_WEEK);
        int idx = (dow + 5) % 7;
        return 1 << idx;
    }

    private static long daysBetween(long from, long to) {
        Calendar a = Calendar.getInstance(); a.setTimeInMillis(from);
        Calendar b = Calendar.getInstance(); b.setTimeInMillis(to);
        long da = a.get(Calendar.YEAR) * 1000L + a.get(Calendar.DAY_OF_YEAR);
        long db = b.get(Calendar.YEAR) * 1000L + b.get(Calendar.DAY_OF_YEAR);
        return db - da;
    }

    /** URI звука для напоминания: builtin:<index> или content://. */
    public static Uri resolveSound(Context ctx, String soundRef) {
        if (soundRef == null) {
            return android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
        }
        if (soundRef.startsWith("builtin:")) {
            int idx = Integer.parseInt(soundRef.substring(8));
            List<Uri> sounds = Store.builtinSounds(ctx);
            if (idx >= 0 && idx < sounds.size()) return sounds.get(idx);
        } else {
            return Uri.parse(soundRef);
        }
        return android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
    }
}
