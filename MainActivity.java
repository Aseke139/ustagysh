package kz.askhat.ustagysh;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Главный экран: ползунок вкл/выкл и фильтр. Всё на казахском. */
public class MainActivity extends Activity {

    private SharedPreferences p;
    private TextView summary, access;
    private Button accessBtn;
    private Switch power;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Prefs.get(this);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);
        col.setBackgroundColor(Color.WHITE);

        TextView title = text(getString(R.string.app_name), 24, true);
        col.addView(title);

        // Рұқсат
        access = text("", 15, false);
        access.setPadding(0, (int) (12 * d), 0, (int) (4 * d));
        col.addView(access);
        accessBtn = new Button(this);
        accessBtn.setText("Рұқсат беру");
        accessBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        col.addView(accessBtn);

        // Негізгі ползунок
        power = new Switch(this);
        power.setTextSize(22);
        power.setTypeface(Typeface.DEFAULT_BOLD);
        power.setPadding(0, (int) (20 * d), 0, (int) (4 * d));
        power.setChecked(Prefs.enabled(p));
        power.setOnCheckedChangeListener((CompoundButton v, boolean on) -> {
            p.edit().putBoolean(Prefs.ENABLED, on).apply();
            refresh();
        });
        col.addView(power);

        summary = text("", 15, false);
        summary.setTextColor(Color.parseColor("#2E7D32"));
        col.addView(summary);

        // Сынақ режимі
        Switch test = new Switch(this);
        test.setText("Сынақ режимі: тек дыбыс береді, баспайды");
        test.setTextSize(15);
        test.setPadding(0, (int) (16 * d), 0, (int) (8 * d));
        test.setChecked(Prefs.test(p));
        test.setOnCheckedChangeListener((v, on) -> p.edit().putBoolean(Prefs.TEST, on).apply());
        col.addView(test);

        // Фильтр
        col.addView(slider("Ең аз сома", "Бұдан арзан тапсырыс алынбайды",
                Prefs.MIN_SUM, Prefs.minSum(p), 50, 200, v -> v + " ₸"));
        col.addView(slider("1 км бағасы", "Карточкадағы ₸/км осыдан кем болмасын",
                Prefs.MIN_PER_KM, Prefs.minPerKm(p), 10, 100, v -> v + " ₸"));
        col.addView(slider("Жолдың ұзындығы", "Бұдан ұзақ жол алынбайды",
                Prefs.MAX_KM, Prefs.maxKm(p), 1, Prefs.MAX_KM_LIMIT,
                v -> v == 0 ? "шектеусіз" : v + " км-ге дейін"));

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (power != null) power.setChecked(Prefs.enabled(p));
        refresh();
    }

    private interface Fmt { String f(int v); }

    private View slider(String name, String hint, String key, int value, int step, int maxSteps, Fmt fmt) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, (int) (18 * d), 0, 0);

        TextView head = text(name + ": " + fmt.f(value), 18, true);
        TextView sub = text(hint, 13, false);
        sub.setTextColor(Color.GRAY);
        SeekBar sb = new SeekBar(this);
        sb.setMax(maxSteps);
        sb.setProgress(value / step);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                int v = prog * step;
                head.setText(name + ": " + fmt.f(v));
                p.edit().putInt(key, v).apply();
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(head);
        box.addView(sub);
        box.addView(sb);
        return box;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.BLACK);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void refresh() {
        boolean on = Prefs.enabled(p);
        power.setText(on ? "Қосулы" : "Өшірулі");
        summary.setText(on ? Prefs.summary(p) : "Тапсырыс ұсталмайды");
        boolean ok = serviceOn();
        access.setText(ok ? "Рұқсат бар ✓" : "Рұқсат жоқ. Батырманы басып, «Тапсырыс ұстағыш» қызметін қосыңыз");
        access.setTextColor(ok ? Color.parseColor("#2E7D32") : Color.RED);
        accessBtn.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    private boolean serviceOn() {
        String list = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(list)) return false;
        String me = new ComponentName(this, CatcherService.class).flattenToString();
        String meShort = new ComponentName(this, CatcherService.class).flattenToShortString();
        for (String s : list.split(":")) {
            if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }
}
