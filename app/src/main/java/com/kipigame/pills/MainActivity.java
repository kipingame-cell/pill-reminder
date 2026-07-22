package com.kipigame.pills;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
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

    private static final int BG      = Color.rgb(14, 17, 22);
    private static final int CARD    = Color.rgb(26, 31, 39);
    private static final int CARD2   = Color.rgb(35, 42, 53);
    private static final int ACCENT  = Color.rgb(94, 196, 150);
    private static final int ACCENT2 = Color.rgb(108, 140, 255);
    private static final int FG      = Color.rgb(232, 234, 238);
    private static final int DIM     = Color.rgb(138, 143, 155);
    private static final int DANGER  = Color.rgb(229, 115, 115);

    private Store store;
    private LinearLayout content;
    private final TextView[] navBtns = new TextView[4];
    private static final String[] NAV = {"Таблетки", "Напоминания", "Календарь", "Статистика"};
    private int currentTab = 0;

    private static final int REQ_CUSTOM_SOUND = 42;
    private String pendingSoundRef = null;
    private String pendingSoundName = null;
    private int[] pendingReminder;
    private String pendingPill;

    private final Calendar calMonth = Calendar.getInstance(); // отображаемый месяц
    private String selectedDayKey = null;

    private final SimpleDateFormat dayKeyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat monthFmt = new SimpleDateFormat("LLLL yyyy", new Locale("ru"));
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat fullDayFmt = new SimpleDateFormat("d MMMM, EEEE", new Locale("ru"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(content);

        // Нижняя навигация
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(8), dp(10), dp(14));
        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(CARD);
        nav.setBackground(navBg);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(NAV[i]);
            t.setTextSize(12);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(8), 0, dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> showTab(idx));
            navBtns[i] = t;
            nav.addView(t);
        }
        root.addView(nav);

        setContentView(root);
        requestPermissionsIfNeeded();

        Calendar now = Calendar.getInstance();
        selectedDayKey = dayKeyFmt.format(now.getTime());
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

    // ================= КАРКАС =================

    private void showTab(int idx) {
        currentTab = idx;
        for (int i = 0; i < 4; i++) {
            TextView t = navBtns[i];
            if (i == idx) {
                GradientDrawable d = new GradientDrawable();
                d.setColor(CARD2);
                d.setCornerRadius(dp(12));
                t.setBackground(d);
                t.setTextColor(ACCENT);
                t.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                t.setBackground(null);
                t.setTextColor(DIM);
                t.setTypeface(Typeface.DEFAULT);
            }
        }
        content.removeAllViews();
        switch (idx) {
            case 0: showPills(); break;
            case 1: showReminders(); break;
            case 2: showCalendar(); break;
            case 3: showStats(); break;
        }
    }

    private LinearLayout page(String title, String subtitle) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(dp(18), dp(34), dp(18), dp(16));

        TextView h = new TextView(this);
        h.setText(title);
        h.setTextColor(FG);
        h.setTextSize(24);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        lay.addView(h);

        if (subtitle != null) {
            TextView s = dimText(subtitle, 13);
            s.setPadding(0, dp(2), 0, dp(12));
            lay.addView(s);
        } else {
            h.setPadding(0, 0, 0, dp(12));
        }
        sv.addView(lay);
        content.addView(sv);
        return lay;
    }

    // ================= ТАБЛЕТКИ =================

    private void showPills() {
        LinearLayout lay = page("Мои таблетки", "Тап по карточке — действия. Всё хранится на устройстве.");

        // Поле добавления
        LinearLayout addCard = card();
        addCard.setGravity(Gravity.CENTER_VERTICAL);
        final EditText et = new EditText(this);
        et.setHint("Название новой таблетки");
        et.setTextColor(FG);
        et.setHintTextColor(DIM);
        et.setBackground(null);
        et.setSingleLine(true);
        et.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addCard.addView(et);

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(Color.rgb(10, 12, 14));
        plus.setTextSize(24);
        plus.setTypeface(Typeface.DEFAULT_BOLD);
        plus.setGravity(Gravity.CENTER);
        GradientDrawable pd = new GradientDrawable();
        pd.setColor(ACCENT);
        pd.setCornerRadius(dp(12));
        plus.setBackground(pd);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(46), dp(46));
        plp.leftMargin = dp(10);
        plus.setLayoutParams(plp);
        plus.setOnClickListener(v -> {
            String name = et.getText().toString().trim();
            if (name.isEmpty()) return;
            store.addPill(name);
            et.setText("");
            showTab(0);
        });
        addCard.addView(plus);
        lay.addView(addCard);

        List<String> pills = store.getPills();
        if (pills.isEmpty()) {
            TextView empty = dimText("Пока пусто — добавь первую таблетку выше.", 14);
            empty.setPadding(dp(4), dp(14), 0, 0);
            lay.addView(empty);
        }

        for (final String pill : pills) {
            LinearLayout c = card();
            c.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView name = new TextView(this);
            name.setText(pill);
            name.setTextColor(FG);
            name.setTextSize(17);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            texts.addView(name);
            int remCount = countRemindersFor(pill);
            TextView sub = dimText(remCount == 0 ? "нет напоминаний" : "напоминаний: " + remCount, 12);
            sub.setPadding(0, dp(2), 0, 0);
            texts.addView(sub);
            c.addView(texts);

            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(DIM);
            arrow.setTextSize(24);
            c.addView(arrow);

            c.setOnClickListener(v -> pillActions(pill));
            lay.addView(c);
        }
    }

    private int countRemindersFor(String pill) {
        int n = 0;
        JSONArray a = store.getReminders();
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            if (r != null && pill.equals(r.optString("pill"))) n++;
        }
        return n;
    }

    private void pillActions(final String pill) {
        String[] actions = {"Создать напоминание", "Записать приём сейчас", "Удалить таблетку"};
        new AlertDialog.Builder(this)
                .setTitle(pill)
                .setItems(actions, (d, which) -> {
                    if (which == 0) reminderWizard(pill);
                    else if (which == 1) {
                        store.logTaken(pill, System.currentTimeMillis());
                        Toast.makeText(this, "Записано: " + timeFmt.format(new Date()), Toast.LENGTH_SHORT).show();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Удалить «" + pill + "»?")
                                .setMessage("Напоминания с этим названием останутся активными.")
                                .setPositiveButton("Удалить", (dd, w) -> { store.removePill(pill); showTab(0); })
                                .setNegativeButton("Отмена", null)
                                .show();
                    }
                })
                .show();
    }

    // ================= МАСТЕР НАПОМИНАНИЯ =================

    private void reminderWizard(String pill) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (tp, hour, minute) -> pickCycle(pill, hour, minute),
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private void pickCycle(final String pill, final int hour, final int minute) {
        String[] types = {"Каждый день", "Каждые N часов", "Каждые N дней", "По дням недели", "Курсом N дней"};
        new AlertDialog.Builder(this)
                .setTitle("Как часто?")
                .setItems(types, (d, which) -> {
                    switch (which) {
                        case 0: pickSound(pill, hour, minute, Scheduler.CYCLE_DAILY, 0); break;
                        case 1: askHours(n -> pickSound(pill, hour, minute, Scheduler.CYCLE_EVERY_N_HOURS, n)); break;
                        case 2: askNumber("Через сколько дней?", "2", n ->
                                pickSound(pill, hour, minute, Scheduler.CYCLE_EVERY_N_DAYS, n)); break;
                        case 3: askWeekdays(mask ->
                                pickSound(pill, hour, minute, Scheduler.CYCLE_WEEKDAYS, mask)); break;
                        case 4: askNumber("Длительность курса (дней)", "7", n ->
                                pickSound(pill, hour, minute, Scheduler.CYCLE_COURSE, n)); break;
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private interface IntCb { void on(int v); }

    private void askHours(IntCb cb) {
        final int[] values = {1, 2, 3, 4, 6, 8, 12};
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = hoursStr(values[i]);
        new AlertDialog.Builder(this)
                .setTitle("Интервал")
                .setItems(labels, (d, which) -> cb.on(values[which]))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static String hoursStr(int n) {
        if (n == 1) return "Каждый час";
        if (n >= 2 && n <= 4) return "Каждые " + n + " часа";
        return "Каждые " + n + " часов";
    }

    private void askNumber(String title, String def, IntCb cb) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(def);
        int p = dp(16);
        et.setPadding(p, p, p, p);
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
            if (currentTab == 1) showTab(1);
        } catch (JSONException ignored) {}
        pendingSoundRef = null;
        pendingSoundName = null;
    }

    // ================= НАПОМИНАНИЯ =================

    private void showReminders() {
        LinearLayout lay = page("Напоминания", "Свайп не нужен: переключатель — вкл/выкл, ✕ — удалить.");

        JSONArray a = store.getReminders();
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            if (r != null) items.add(r);
        }
        if (items.isEmpty()) {
            TextView empty = dimText("Пока пусто. Зайди во вкладку «Таблетки», тапни по таблетке и создай напоминание.", 14);
            empty.setPadding(dp(4), dp(10), 0, 0);
            lay.addView(empty);
            return;
        }

        for (final JSONObject r : items) {
            LinearLayout c = card();
            c.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView time = new TextView(this);
            time.setText(String.format(Locale.getDefault(), "%02d:%02d", r.optInt("hour"), r.optInt("minute")));
            time.setTextColor(r.optBoolean("active", true) ? ACCENT : DIM);
            time.setTextSize(26);
            time.setTypeface(Typeface.DEFAULT_BOLD);
            texts.addView(time);

            TextView pill = new TextView(this);
            pill.setText(r.optString("pill"));
            pill.setTextColor(FG);
            pill.setTextSize(16);
            pill.setPadding(0, dp(2), 0, dp(4));
            texts.addView(pill);

            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.addView(chip(cycleStr(r)));
            String snd = r.optString("soundName", "");
            if (!snd.isEmpty()) {
                TextView sc = chip(snd);
                ((LinearLayout.LayoutParams) sc.getLayoutParams()).leftMargin = dp(6);
                chips.addView(sc);
            }
            texts.addView(chips);
            c.addView(texts);

            final Switch sw = new Switch(this);
            sw.setChecked(r.optBoolean("active", true));
            sw.setOnCheckedChangeListener((btn, on) -> {
                try {
                    store.updateReminder(r.getLong("id"), "active", on);
                    r.put("active", on);
                    if (on) Scheduler.schedule(this, r);
                    else Scheduler.cancel(this, r);
                    time.setTextColor(on ? ACCENT : DIM);
                } catch (JSONException ignored) {}
            });
            c.addView(sw);

            TextView del = new TextView(this);
            del.setText("✕");
            del.setTextColor(DANGER);
            del.setTextSize(18);
            del.setPadding(dp(12), dp(6), dp(2), dp(6));
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Удалить напоминание?")
                    .setPositiveButton("Удалить", (d, w) -> {
                        Scheduler.cancel(this, r);
                        store.removeReminder(r.optLong("id"));
                        showTab(1);
                    })
                    .setNegativeButton("Отмена", null)
                    .show());
            c.addView(del);

            lay.addView(c);
        }
    }

    private String cycleStr(JSONObject r) {
        switch (r.optInt("cycle")) {
            case Scheduler.CYCLE_EVERY_N_HOURS: {
                int n = Math.max(1, r.optInt("param", 1));
                return hoursStr(n).toLowerCase(Locale.ROOT);
            }
            case Scheduler.CYCLE_EVERY_N_DAYS:
                return "каждые " + r.optInt("param") + " дн.";
            case Scheduler.CYCLE_WEEKDAYS:
                return weekdaysStr(r.optInt("param"));
            case Scheduler.CYCLE_COURSE:
                return "курс " + r.optInt("param") + " дн.";
            default:
                return "каждый день";
        }
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
        LinearLayout lay = page("Календарь", null);

        // Записи по дням
        Map<String, List<JSONObject>> byDay = new LinkedHashMap<>();
        JSONArray log = store.getLog();
        for (int i = 0; i < log.length(); i++) {
            JSONObject o = log.optJSONObject(i);
            if (o == null) continue;
            String key = dayKeyFmt.format(new Date(o.optLong("time")));
            if (!byDay.containsKey(key)) byDay.put(key, new ArrayList<JSONObject>());
            byDay.get(key).add(o);
        }

        // Шапка: месяц + стрелки
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = navArrow("‹");
        TextView next = navArrow("›");
        final TextView monthLabel = new TextView(this);
        monthLabel.setTextColor(FG);
        monthLabel.setTextSize(17);
        monthLabel.setTypeface(Typeface.DEFAULT_BOLD);
        monthLabel.setGravity(Gravity.CENTER);
        monthLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String m = monthFmt.format(calMonth.getTime());
        monthLabel.setText(m.substring(0, 1).toUpperCase(new Locale("ru")) + m.substring(1));
        prev.setOnClickListener(v -> { calMonth.add(Calendar.MONTH, -1); showTab(2); });
        next.setOnClickListener(v -> { calMonth.add(Calendar.MONTH, 1); showTab(2); });
        head.addView(prev);
        head.addView(monthLabel);
        head.addView(next);
        lay.addView(head);

        // Дни недели
        String[] wd = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        LinearLayout wdRow = new LinearLayout(this);
        wdRow.setPadding(0, dp(10), 0, dp(4));
        for (String d : wd) {
            TextView t = dimText(d, 12);
            t.setGravity(Gravity.CENTER);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            wdRow.addView(t);
        }
        lay.addView(wdRow);

        // Сетка месяца
        Calendar first = (Calendar) calMonth.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        String todayKey = dayKeyFmt.format(new Date());

        int day = 1;
        for (int row = 0; row < 6 && day <= daysInMonth + offset; row++) {
            LinearLayout rowLay = new LinearLayout(this);
            for (int col = 0; col < 7; col++) {
                int cellIndex = row * 7 + col;
                final TextView cell = new TextView(this);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(14);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
                lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                cell.setLayoutParams(lp);

                if (cellIndex >= offset && day <= daysInMonth) {
                    final int thisDay = day;
                    Calendar c = (Calendar) first.clone();
                    c.set(Calendar.DAY_OF_MONTH, thisDay);
                    final String key = dayKeyFmt.format(c.getTime());

                    cell.setText(String.valueOf(thisDay));
                    boolean hasEntries = byDay.containsKey(key);
                    boolean isSelected = key.equals(selectedDayKey);
                    boolean isToday = key.equals(todayKey);

                    GradientDrawable bgd = new GradientDrawable();
                    bgd.setCornerRadius(dp(10));
                    if (isSelected) {
                        bgd.setColor(CARD2);
                        bgd.setStroke(dp(1), ACCENT);
                    } else if (hasEntries) {
                        bgd.setColor(CARD2);
                    } else {
                        bgd.setColor(Color.TRANSPARENT);
                    }
                    cell.setBackground(bgd);
                    cell.setTextColor(hasEntries ? ACCENT : (isToday ? ACCENT2 : FG));
                    if (hasEntries || isToday) cell.setTypeface(Typeface.DEFAULT_BOLD);

                    cell.setOnClickListener(v -> {
                        selectedDayKey = key;
                        showTab(2);
                    });
                    day++;
                }
                rowLay.addView(cell);
            }
            lay.addView(rowLay);
        }

        // Записи выбранного дня
        LinearLayout dayCard = card();
        dayCard.setOrientation(LinearLayout.VERTICAL);
        TextView dayTitle = new TextView(this);
        String dt = "Записи за " + fullDayFmt.format(parseDayKey(selectedDayKey));
        dayTitle.setText(dt);
        dayTitle.setTextColor(FG);
        dayTitle.setTextSize(15);
        dayTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dayCard.addView(dayTitle);

        List<JSONObject> entries = byDay.get(selectedDayKey);
        if (entries == null || entries.isEmpty()) {
            TextView none = dimText("В этот день приёмов не записано.", 13);
            none.setPadding(0, dp(8), 0, 0);
            dayCard.addView(none);
        } else {
            for (JSONObject o : entries) {
                LinearLayout row = new LinearLayout(this);
                row.setPadding(0, dp(8), 0, 0);
                TextView t = new TextView(this);
                t.setText(timeFmt.format(new Date(o.optLong("time"))));
                t.setTextColor(ACCENT);
                t.setTextSize(14);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setLayoutParams(new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));
                row.addView(t);
                TextView p = new TextView(this);
                p.setText(o.optString("pill"));
                p.setTextColor(FG);
                p.setTextSize(14);
                row.addView(p);
                dayCard.addView(row);
            }
        }
        lay.addView(dayCard);
    }

    private Date parseDayKey(String key) {
        try { return dayKeyFmt.parse(key); } catch (Exception e) { return new Date(); }
    }

    private TextView navArrow(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(ACCENT);
        t.setTextSize(26);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), 0, dp(14), 0);
        return t;
    }

    // ================= СТАТИСТИКА =================

    private void showStats() {
        LinearLayout lay = page("Статистика", null);

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

        // Три мини-карточки
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(statCard(String.valueOf(log.length()), "всего"));
        row.addView(statCard(String.valueOf(last7), "за 7 дней"));
        row.addView(statCard(String.valueOf(last30), "за 30 дней"));
        lay.addView(row);

        if (perPill.isEmpty()) {
            TextView empty = dimText("Данных пока нет — статистика появится после первых отметок «Принял».", 14);
            empty.setPadding(dp(4), dp(14), 0, 0);
            lay.addView(empty);
            return;
        }

        int max = 1;
        for (int v : perPill.values()) max = Math.max(max, v);

        TextView sect = dimText("ПО ТАБЛЕТКАМ", 11);
        sect.setPadding(dp(4), dp(18), 0, dp(6));
        lay.addView(sect);

        for (Map.Entry<String, Integer> e : perPill.entrySet()) {
            LinearLayout c = card();
            c.setOrientation(LinearLayout.VERTICAL);

            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(e.getKey());
            name.setTextColor(FG);
            name.setTextSize(15);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(name);

            List<Integer> mins = minutesByPill.get(e.getKey());
            int avg = 0;
            for (int mm : mins) avg += mm;
            avg /= Math.max(1, mins.size());
            TextView info = dimText(e.getValue() + " раз · ср. " +
                    String.format(Locale.getDefault(), "%02d:%02d", avg / 60, avg % 60), 12);
            top.addView(info);
            c.addView(top);

            // Бар
            LinearLayout bar = new LinearLayout(this);
            bar.setPadding(0, dp(8), 0, 0);
            View fill = new View(this);
            GradientDrawable fd = new GradientDrawable();
            fd.setColor(ACCENT);
            fd.setCornerRadius(dp(4));
            fill.setBackground(fd);
            bar.addView(fill, new LinearLayout.LayoutParams(0, dp(8), (float) e.getValue() / max));
            View rest = new View(this);
            GradientDrawable rd = new GradientDrawable();
            rd.setColor(CARD2);
            rd.setCornerRadius(dp(4));
            rest.setBackground(rd);
            bar.addView(rest, new LinearLayout.LayoutParams(0, dp(8), 1f - (float) e.getValue() / max));
            c.addView(bar);

            lay.addView(c);
        }

        int active = 0;
        JSONArray rems = store.getReminders();
        for (int i = 0; i < rems.length(); i++) {
            JSONObject r = rems.optJSONObject(i);
            if (r != null && r.optBoolean("active", true)) active++;
        }
        TextView foot = dimText("Активных напоминаний: " + active, 13);
        foot.setPadding(dp(4), dp(14), 0, 0);
        lay.addView(foot);
    }

    private View statCard(String value, String label) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        c.setLayoutParams(lp);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(ACCENT);
        v.setTextSize(24);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        c.addView(v);
        TextView l = dimText(label, 12);
        l.setGravity(Gravity.CENTER);
        c.addView(l);
        return c;
    }

    // ================= UI helpers =================

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(dp(14));
        c.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        c.setLayoutParams(lp);
        return c;
    }

    private TextView chip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(DIM);
        t.setTextSize(11);
        t.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD2);
        d.setCornerRadius(dp(8));
        t.setBackground(d);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private TextView dimText(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(DIM);
        t.setTextSize(sp);
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
