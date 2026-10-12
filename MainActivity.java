package kz.askhat.ustagysh;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Главный экран: батыр, кнопки ҚОСУ / ӨШІРУ, фильтр. Всё на казахском. */
public class MainActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int CREAM = Color.parseColor("#FBF6EA");
    private static final int NAVY = Color.parseColor("#002B3D");
    private static final int SKY = Color.parseColor("#00AFCA");

    private SharedPreferences p;
    private float dp;
    private TextView summary, access, onBtn, offBtn;
    private View accessCard;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Prefs.get(this);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(NAVY);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CREAM);

        page.addView(header());
        page.addView(new OrnamentView(this, NAVY), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(30)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(px(14), px(8), px(14), px(20));
        page.addView(body);

        // Рұқсат
        LinearLayout ac = card();
        access = text("Рұқсат жоқ. Төмендегі батырманы басып, «Тапсырыс ұстағыш» қызметін қосыңыз", 15, false);
        access.setTextColor(Overlays.RED);
        ac.addView(access);
        TextView accessBtn = bigButton("Рұқсат беру", Overlays.RED);
        accessBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        ac.addView(accessBtn, margins(0, 10, 0, 0));
        accessCard = ac;
        body.addView(ac);

        // ҚОСУ / ӨШІРУ
        LinearLayout pc = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        onBtn = bigButton("ҚОСУ", Overlays.GREEN);
        offBtn = bigButton("ӨШІРУ", Overlays.RED);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, px(64), 1f);
        half.setMargins(0, 0, px(6), 0);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, px(64), 1f);
        half2.setMargins(px(6), 0, 0, 0);
        row.addView(onBtn, half);
        row.addView(offBtn, half2);
        onBtn.setOnClickListener(v -> Prefs.turnOn(p));
        offBtn.setOnClickListener(v -> p.edit().putBoolean(Prefs.ENABLED, false).apply());
        pc.addView(row);
        summary = text("", 14, false);
        summary.setPadding(0, px(10), 0, 0);
        pc.addView(summary);
        body.addView(pc);

        // Сынақ режимі
        LinearLayout tc = card();
        Switch test = new Switch(this);
        test.setText("Сынақ режимі\nтек дыбыс береді, ештеңе баспайды");
        test.setTextSize(15);
        test.setTextColor(Color.BLACK);
        test.setChecked(Prefs.test(p));
        test.setThumbTintList(ColorStateList.valueOf(SKY));
        test.setOnCheckedChangeListener((v, on) -> p.edit().putBoolean(Prefs.TEST, on).apply());
        tc.addView(test);
        body.addView(tc);

        // Сүзгі
        LinearLayout fc = card();
        fc.addView(text("Сүзгі", 18, true));
        fc.addView(slider("Ең аз сома", "Бұдан арзан тапсырыс алынбайды",
                Prefs.MIN_SUM, Prefs.minSum(p), 50, 200, v -> v + " ₸"));
        fc.addView(slider("1 км бағасы", "Карточкадағы ₸/км осыдан кем болмасын",
                Prefs.MIN_PER_KM, Prefs.minPerKm(p), 10, 100, v -> v + " ₸"));
        fc.addView(slider("Жолдың ұзындығы", "Бұдан ұзақ жол алынбайды",
                Prefs.MAX_KM, Prefs.maxKm(p), 1, Prefs.MAX_KM_LIMIT,
                v -> v == 0 ? "шектеусіз" : v + " км-ге дейін"));
        body.addView(fc);

        // Подсказка про плавающие кнопки
        LinearLayout hc = card();
        hc.addView(text("Экрандағы батырмалар", 16, true));
        TextView hint = text("Дөңгелек батыр батырмасын бассаңыз, ҚОСУ / ӨШІРУ тақтасы ашылады не жабылады.\n"
                + "Тақтаны төмен сүйреп ✕-ке тастасаңыз, ол жасырылады.\n"
                + "Батыр батырмасын ✕-ке тастасаңыз, қосымшадан шығу сұралады.", 14, false);
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(0, px(6), 0, 0);
        hc.addView(hint);
        body.addView(hc);

        TextView footer = text("Жолың болсын, батыр!", 16, true);
        footer.setGravity(Gravity.CENTER);
        footer.setTextColor(NAVY);
        footer.setPadding(0, px(12), 0, px(4));
        body.addView(footer);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(CREAM);
        sv.addView(page);
        setContentView(sv);
    }

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{SKY, Color.parseColor("#005F80"), NAVY});
        h.setBackground(g);
        h.setPadding(px(16), px(18), px(16), px(10));

        TextView title = text(getString(R.string.app_name), 26, true);
        title.setTextColor(Color.WHITE);
        h.addView(title);
        TextView sub = text("Тапсырысты бірінші ұста", 15, true);
        sub.setTextColor(Overlays.GOLD);
        h.addView(sub);

        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.batyr_banner);
        art.setAdjustViewBounds(true);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(190));
        al.setMargins(0, px(8), 0, 0);
        h.addView(art, al);
        return h;
    }

    @Override
    protected void onResume() {
        super.onResume();
        p.registerOnSharedPreferenceChangeListener(this);
        refresh();
    }

    @Override
    protected void onPause() {
        p.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        refresh();
    }

    private void refresh() {
        boolean on = Prefs.enabled(p);
        onBtn.setBackground(round(on ? Overlays.GREEN : Overlays.RED, 14));
        offBtn.setBackground(round(on ? Overlays.RED : Overlays.GREEN, 14));
        summary.setText(on ? Prefs.summary(p) : "Тапсырыс ұсталмайды");
        summary.setTextColor(on ? Overlays.GREEN : Color.DKGRAY);
        accessCard.setVisibility(serviceOn() ? View.GONE : View.VISIBLE);
    }

    // ---------- элементы ----------

    private interface Fmt { String f(int v); }

    private View slider(String name, String hint, String key, int value, int step, int maxSteps, Fmt fmt) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, px(14), 0, 0);
        TextView head = text(name + ": " + fmt.f(value), 17, true);
        head.setTextColor(NAVY);
        TextView sub = text(hint, 13, false);
        sub.setTextColor(Color.GRAY);
        SeekBar sb = new SeekBar(this);
        sb.setMax(maxSteps);
        sb.setProgress(value / step);
        sb.setProgressTintList(ColorStateList.valueOf(Overlays.GOLD));
        sb.setThumbTintList(ColorStateList.valueOf(SKY));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                int v = prog * step;
                head.setText(name + ": " + fmt.f(v));
                if (user) p.edit().putInt(key, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(head);
        box.addView(sub);
        box.addView(sb);
        return box;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(16), px(14), px(16), px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(px(16));
        bg.setStroke(px(1), Color.parseColor("#E8D9A8"));
        c.setBackground(bg);
        c.setLayoutParams(margins(0, 10, 0, 0));
        return c;
    }

    private TextView bigButton(String s, int color) {
        TextView t = text(s, 20, true);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(8), px(14), px(8), px(14));
        t.setBackground(round(color, 14));
        return t;
    }

    private GradientDrawable round(int color, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(r));
        return d;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(l), px(t), px(r), px(b));
        return lp;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.BLACK);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int px(float v) { return (int) (v * dp); }

    private boolean serviceOn() {
        String list = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(list)) return false;
        ComponentName cn = new ComponentName(this, CatcherService.class);
        for (String s : list.split(":")) {
            if (s.equalsIgnoreCase(cn.flattenToString()) || s.equalsIgnoreCase(cn.flattenToShortString())) return true;
        }
        return false;
    }
}
