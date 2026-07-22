package com.kipigame.pills;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Хранилище: таблетки, напоминания, журнал приёма. Всё в SharedPreferences как JSON. */
public class Store {
    private static final String PREFS = "data";
    private final SharedPreferences sp;

    public Store(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- Таблетки ----------
    public List<String> getPills() {
        JSONArray a = readArray("pills");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    public void addPill(String name) {
        JSONArray a = readArray("pills");
        for (int i = 0; i < a.length(); i++)
            if (a.optString(i).equalsIgnoreCase(name)) return;
        a.put(name);
        writeArray("pills", a);
    }

    public void removePill(String name) {
        JSONArray a = readArray("pills");
        JSONArray b = new JSONArray();
        for (int i = 0; i < a.length(); i++)
            if (!a.optString(i).equals(name)) b.put(a.optString(i));
        writeArray("pills", b);
    }

    // ---------- Напоминания ----------
    public JSONArray getReminders() { return readArray("reminders"); }

    public long addReminder(JSONObject r) throws JSONException {
        JSONArray a = readArray("reminders");
        long id = System.currentTimeMillis();
        r.put("id", id);
        a.put(r);
        writeArray("reminders", a);
        return id;
    }

    public void updateReminder(long id, String key, Object value) throws JSONException {
        JSONArray a = readArray("reminders");
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i);
            if (r.getLong("id") == id) { r.put(key, value); break; }
        }
        writeArray("reminders", a);
    }

    public void removeReminder(long id) {
        JSONArray a = readArray("reminders");
        JSONArray b = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            if (r == null || r.optLong("id") != id) b.put(r == null ? JSONObject.NULL : r);
        }
        writeArray("reminders", b);
    }

    // ---------- Журнал приёма ----------
    public void logTaken(String pill, long time) {
        JSONArray a = readArray("log");
        JSONObject o = new JSONObject();
        try {
            o.put("pill", pill);
            o.put("time", time);
        } catch (JSONException ignored) {}
        a.put(o);
        writeArray("log", a);
    }

    public JSONArray getLog() { return readArray("log"); }

    // ---------- Свой звук ----------
    public void setCustomSound(String uri) { sp.edit().putString("custom_sound", uri).apply(); }
    public String getCustomSound() { return sp.getString("custom_sound", null); }

    // ---------- helpers ----------
    private JSONArray readArray(String key) {
        try { return new JSONArray(sp.getString(key, "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    private void writeArray(String key, JSONArray a) {
        sp.edit().putString(key, a.toString()).apply();
    }

    /** 10 встроенных звуков уведомлений системы. */
    public static List<Uri> builtinSounds(Context ctx) {
        List<Uri> out = new ArrayList<>();
        RingtoneManager rm = new RingtoneManager(ctx);
        rm.setType(RingtoneManager.TYPE_NOTIFICATION);
        android.database.Cursor c = rm.getCursor();
        for (int i = 0; i < c.getCount() && out.size() < 10; i++) {
            out.add(rm.getRingtoneUri(i));
        }
        return out;
    }

    /** Заголовок звука по индексу из системного курсора. */
    public static List<String> builtinSoundNames(Context ctx) {
        List<String> out = new ArrayList<>();
        RingtoneManager rm = new RingtoneManager(ctx);
        rm.setType(RingtoneManager.TYPE_NOTIFICATION);
        android.database.Cursor c = rm.getCursor();
        for (int i = 0; i < c.getCount() && out.size() < 10; i++) {
            c.moveToPosition(i);
            out.add(c.getString(RingtoneManager.TITLE_COLUMN_INDEX));
        }
        return out;
    }
}
