package com.kipigame.pills;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(16, 18, 22);
    private static final int CARD = Color.rgb(28, 31, 38);
    private static final int ACCENT = Color.rgb(94, 196, 150);
    private static final int FG = Color.rgb(232, 234, 238);
    private static final int DIM = Color.rgb(140, 145, 155);

    private Store store;
    private FrameLayout content;
    private final Button[] tabs = new Button[4];
    private static final String[] TAB_NAMES = {"Таблетки", "Напоминания", "Календарь", "Статистика"};

    private static final int REQ_CUSTOM_SOUND = 42;
    private String pendingSoundRef = null;
    private String pendingSoundName = null;
    private int[] pendingReminder;
    private String pendingPill;

    private final SimpleDateFormat dayFmt = new SimpleDateFormat("d MMMM, EEEE", new Locale("ru"));
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(14);
        root.setPadding(pad, dp(36), pad, pad);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(TAB_NAMES[i]);
            b.setAllCaps(false);
            b.setTextSize(13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> showTab(idx));
            tabs[i] = b;
            tabRow.addView(b);
        }
        root.addView(tabRow);

        content = new FrameLayout(this);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        clp.topMargin = dp(12);
        content.setLayoutParams(clp);
        root.addView(content);

        setContentView(root);

        requestPermissionsIfNeeded();
        showTab(0);
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                } catch (Exception ignored) {}
            }
        }
    }

    private void showTab(int idx) {
        for (int i = 0; i < 4; i++) styleTab(tabs[i], i == idx);
        content.removeAllViews();
        switch (idx) {
            case 0: showPills(); break;
            case 1: showReminders(); break;
            case 2: showCalendar(); break;
            case 3: showStats(); break;
        }
    }

    private void styleTab(Button b, boolean active) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(active ? ACCENT : CARD);
        d.setCornerRadius(dp(10));
        b.setBackground(d);
        b.setTextColor(active ? Color.rgb(10, 12, 14) : FG);
    }

    // ================= ТАБЛЕТКИ =================

    private void showPills() {
        LinearLayout lay = vbox();

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        final EditText et = new EditText(this);
        et.setHint("Название таблетки");
        et.setTextColor(FG);
        et.setHintTextColor(DIM);
        et.setBackground(cardBg());
        et.setPadding(dp(12), 0, dp(12), 0);
        et.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1f));
        inputRow.addView(et);

        Button add = accentButton("+");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(56), dp(48));
        alp.leftMargin = dp(8);
        add.setLayoutParams(alp);
        add.setOnClickListener(v -> {
            String name = et.getText().toString().trim();
            if (name.isEmpty()) return;
            store.addPill(name);
            et.setText("");
            showTab(0);
        });
        inputRow.addView(add);
        lay.addView(inputRow);

        TextView hint = dimText("Нажми на таблетку — создать напоминание. Долгое нажатие — удалить.");
        hint.setPadding(0, dp(10), 0, dp(6));
        lay.addView(hint);

        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(dp(8));
        final List<String> pills = store.getPills();
        lv.setAdapter(stringAdapter(pills));
        lv.setOnItemClickListener((p, v, pos, id) -> reminderWizard(pills.get(pos)));
        lv.setOnItemLongClickListener((p, v, pos, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Удалить «" + pills.get(pos) + "»?")
                    .setPositiveButton("Удалить", (d, w) -> { store.removePill(pills.get(pos)); showTab(0); })
                    .setNegativeButton("Отмена", null)
                    .show();
            return true;
        });
        lay.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(lay);
    }

    // ================= МАСТЕР НАПОМИНАНИЯ =================

    private void reminderWizard(String pill) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (tp, hour, minute) -> pickCycle(pill, hour, minute),
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private void pickCycle(String pill, int hour, int minute) {
        String[] types = {"Каждый день", "Каждые N дней", "По дням недели", "Курсом N дней"};
        new AlertDialog.Builder(this)
                .setTitle("Цикличность")
                .setItems(types, (d, which) -> {
                    switch (which) {
                        case 0: pickSound(pill, hour, minute, Scheduler.CYCLE_DAILY, 0); break;
                        case 1: askNumber("Через сколько дней?", "2", n ->
                                pickSound(pill, hour, minute, Scheduler.CYCLE_EVERY_N_DAYS, n)); break;
                        case 2: askWeekdays(mask ->
                                pickSound(pill, hour, minute, Scheduler.CYCLE_WEEKDAYS, mask)); break;
                        case 3: askNumber("Длительность курса (дней)", "7", n ->
                                pickSound(pill, hour, minute, Scheduler.CYCLE_COURSE, n)); break;
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private interface IntCb { void on(int v); }

    private void askNumber(String title, String def, IntCb cb) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(def);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("Далее", (d, w) -> {
                    int n;
                    try { n = Math.max(1, Integer.parseInt(et.getText().toString().trim())); }
                    catch (Exception e) { n = 1; }
                    cb.on(n);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void askWeekdays(IntCb cb) {
        String[] days = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        final boolean[] checked = {true, true, true, true, true, true, true};
        new AlertDialog.Builder(this)
                .setTitle("Дни недели")
                .setMultiChoiceItems(days, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Далее", (d, w) -> {
                    int mask = 0;
                    for (int i = 0; i < 7; i++) if (checked[i]) mask |= (1 << i);
                    if (mask == 0) mask = 127;
                    cb.on(mask);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void pickSound(final String pill, final int hour, final int minute, final int cycle, final int param) {
        List<String> names = Store.builtinSoundNames(this);
        List<String> items = new ArrayList<>(names);
        items.add("Свой звук с телефона…");
        final String custom = store.getCustomSound();
        if (custom != null) items.add("Свой звук (выбран ранее)");

        new AlertDialog.Builder(this)
                .setTitle("Звук напоминания")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which < names.size()) {
                        pendingSoundRef = "builtin:" + which;
                        pendingSoundName = names.get(which);
                        finishReminder(pill, hour, minute, cycle, param);
                    } else if (which == names.size()) {
                        pendingReminder = new int[]{hour, minute, cycle, param};
                        pendingPill = pill;
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("audio/*");
                        startActivityForResult(i, REQ_CUSTOM_SOUND);
                    } else {
                        pendingSoundRef = custom;
                        pendingSoundName = "Свой звук";
                        finishReminder(pill, hour, minute, cycle, param);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_CUSTOM_SOUND && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                store.setCustomSound(uri.toString());
                pendingSoundRef = uri.toString();
                pendingSoundName = "Свой звук";
                if (pendingPill != null && pendingReminder != null) {
                    finishReminder(pendingPill, pendingReminder[0], pendingReminder[1],
                            pendingReminder[2], pendingReminder[3]);
                    pendingPill = null;
                    pendingReminder = null;
                }
            }
        }
    }

    private void finishReminder(String pill, int hour, int minute, int cycle, int param) {
        try {
            JSONObject r = new JSONObject();
            r.put("pill", pill);
            r.put("hour", hour);
            r.put("minute", minute);
            r.put("cycle", cycle);
            r.put("param", param);
            r.put("sound", pendingSoundRef);
            r.put("soundName", pendingSoundName);
            r.put("active", true);
            r.put("startDate", System.currentTimeMillis());
            long id = store.addReminder(r);
            r.put("id", id);
            Scheduler.schedule(this, r);
            Toast.makeText(this, "Напоминание создано", Toast.LENGTH_SHORT).show();
        } catch (JSONException ignored) {}
        pendingSoundRef = null;
        pendingSoundName = null;
    }

    // ================= НАПОМИНАНИЯ =================

    private void showReminders() {
        LinearLayout lay = vbox();
        lay.addView(dimText("Нажми — вкл/выкл. Долгое нажатие — удалить."));

        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(dp(8));

        JSONArray a = store.getReminders();
        final List<JSONObject> items = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            if (r == null) continue;
            items.add(r);
            rows.add(describeReminder(r));
        }
        if (rows.isEmpty()) rows.add("Пока пусто. Добавь таблетку и создай напоминание.");

        lv.setAdapter(stringAdapter(rows));
        lv.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= items.size()) return;
            JSONObject r = items.get(pos);
            try {
                boolean active = !r.optBoolean("active", true);
                store.updateReminder(r.getLong("id"), "active", active);
                r.put("active", active);
                if (active) Scheduler.schedule(this, r);
                else Scheduler.cancel(this, r);
            } catch (JSONException ignored) {}
            showTab(1);
        });
        lv.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos >= items.size()) return true;
            final JSONObject r = items.get(pos);
            new AlertDialog.Builder(this)
                    .setTitle("Удалить напоминание?")
                    .setPositiveButton("Удалить", (d, w) -> {
                        Scheduler.cancel(this, r);
                        store.removeReminder(r.optLong("id"));
                        showTab(1);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            return true;
        });
        lay.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(lay);
    }

    private String describeReminder(JSONObject r) {
        String time = String.format(Locale.getDefault(), "%02d:%02d",
                r.optInt("hour"), r.optInt("minute"));
        String cycle;
        switch (r.optInt("cycle")) {
            case Scheduler.CYCLE_EVERY_N_DAYS:
                cycle = "каждые " + r.optInt("param") + " дн."; break;
            case Scheduler.CYCLE_WEEKDAYS:
                cycle = weekdaysStr(r.optInt("param")); break;
            case Scheduler.CYCLE_COURSE:
                cycle = "курс " + r.optInt("param") + " дн."; break;
            default:
                cycle = "каждый день";
        }
        String state = r.optBoolean("active", true) ? "●" : "○";
        String sound = r.optString("soundName", "по умолчанию");
        return state + "  " + r.optString("pill") + " — " + time + "\n" + cycle + " · звук: " + sound;
    }

    private String weekdaysStr(int mask) {
        String[] d = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(d[i]);
        }
        return sb.length() == 0 ? "по дням недели" : sb.toString();
    }

    // ================= КАЛЕНДАРЬ =================

    private void showCalendar() {
        LinearLayout lay = vbox();
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(dp(8));

        JSONArray log = store.getLog();
        Map<String, List<String>> byDay = new LinkedHashMap<>();
        for (int i = log.length() - 1; i >= 0; i--) {
            JSONObject o = log.optJSONObject(i);
            if (o == null) continue;
            long t = o.optLong("time");
            String day = dayFmt.format(new Date(t));
            if (!byDay.containsKey(day)) byDay.put(day, new ArrayList<String>());
            byDay.get(day).add(timeFmt.format(new Date(t)) + " — " + o.optString("pill"));
        }
        List<String> rows = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byDay.entrySet()) {
            StringBuilder sb = new StringBuilder(e.getKey());
            for (String s : e.getValue()) sb.append("\n   ").append(s);
            rows.add(sb.toString());
        }
        if (rows.isEmpty()) rows.add("Журнал пуст. Отмечай приём кнопкой «Принял» в уведомлении.");

        lv.setAdapter(stringAdapter(rows));
        lay.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(lay);
    }

    // ================= СТАТИСТИКА =================

    private void showStats() {
        ScrollView sv = new ScrollView(this);
        LinearLayout lay = vbox();

        JSONArray log = store.getLog();
        Map<String, Integer> perPill = new LinkedHashMap<>();
        Map<String, List<Integer>> minutesByPill = new LinkedHashMap<>();
        int last7 = 0, last30 = 0;
        long now = System.currentTimeMillis();

        for (int i = 0; i < log.length(); i++) {
            JSONObject o = log.optJSONObject(i);
            if (o == null) continue;
            String pill = o.optString("pill");
            long t = o.optLong("time");
            Integer cnt = perPill.get(pill);
            perPill.put(pill, cnt == null ? 1 : cnt + 1);
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t);
            if (!minutesByPill.containsKey(pill)) minutesByPill.put(pill, new ArrayList<Integer>());
            minutesByPill.get(pill).add(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE));
            long days = (now - t) / 86400000L;
            if (days < 7) last7++;
            if (days < 30) last30++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Всего приёмов: ").append(log.length()).append("\n\n");
        sb.append("За 7 дней: ").append(last7).append("\n");
        sb.append("За 30 дней: ").append(last30).append("\n\n");
        sb.append("По таблеткам:\n");
        if (perPill.isEmpty()) sb.append("  пока нет данных\n");
        for (Map.Entry<String, Integer> e : perPill.entrySet()) {
            List<Integer> mins = minutesByPill.get(e.getKey());
            int avg = 0;
            for (int m : mins) avg += m;
            avg /= Math.max(1, mins.size());
            sb.append("  ").append(e.getKey()).append(": ").append(e.getValue())
                    .append(" раз, среднее время ")
                    .append(String.format(Locale.getDefault(), "%02d:%02d", avg / 60, avg % 60))
                    .append("\n");
        }

        int active = 0;
        JSONArray rems = store.getReminders();
        for (int i = 0; i < rems.length(); i++) {
            JSONObject r = rems.optJSONObject(i);
            if (r != null && r.optBoolean("active", true)) active++;
        }
        sb.append("\nАктивных напоминаний: ").append(active);

        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextColor(FG);
        tv.setTextSize(16);
        tv.setLineSpacing(0, 1.25f);
        tv.setPadding(dp(6), dp(6), dp(6), dp(6));
        lay.addView(tv);
        sv.addView(lay);
        content.addView(sv);
    }

    // ================= UI helpers =================

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private ArrayAdapter<String> stringAdapter(List<String> items) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                View v = super.getView(pos, convert, parent);
                TextView tv = (TextView) v;
                tv.setTextColor(FG);
                tv.setBackground(cardBg());
                tv.setPadding(dp(14), dp(12), dp(14), dp(12));
                tv.setTextSize(15);
                return v;
            }
        };
    }

    private Button accentButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.rgb(10, 12, 14));
        b.setTextSize(20);
        GradientDrawable d = new GradientDrawable();
        d.setColor(ACCENT);
        d.setCornerRadius(dp(10));
        b.setBackground(d);
        return b;
    }

    private TextView dimText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(DIM);
        t.setTextSize(12);
        return t;
    }

    private GradientDrawable cardBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(dp(10));
        return d;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
