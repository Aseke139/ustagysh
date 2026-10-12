package kz.askhat.ustagysh;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Главный экран. Всё крупно, жирно, без ползунков: только кнопки. */
public class MainActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int CREAM = Color.parseColor("#FBF6EA");
    private static final int NAVY = Color.parseColor("#002B3D");
    private static final int SKY = Color.parseColor("#00AFCA");

    private static final String PHONE = "+77754406747";
    private static final String PHONE_SHOWN = "8 775 440 67 47";

    private SharedPreferences p;
    private float dp;
    private TextView onBtn, offBtn, summary, sumVal, kmPriceVal, kmVal;
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
                ViewGroup.LayoutParams.MATCH_PARENT, px(28)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(px(14), px(6), px(14), px(24));
        page.addView(body);

        // Рұқсат (только если не дано)
        LinearLayout ac = card();
        TextView at = text("Рұқсат жоқ", 22, Overlays.RED);
        ac.addView(at);
        TextView accessBtn = bigButton("РҰҚСАТ БЕРУ", Overlays.RED, 22);
        accessBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        ac.addView(accessBtn, full(px(64), 10));
        accessCard = ac;
        body.addView(ac);

        // ҚОСУ / ӨШІРУ
        LinearLayout pc = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        onBtn = bigButton("ҚОСУ", Overlays.GREEN, 26);
        offBtn = bigButton("ӨШІРУ", Overlays.RED, 26);
        LinearLayout.LayoutParams h1 = new LinearLayout.LayoutParams(0, px(80), 1f);
        h1.setMargins(0, 0, px(6), 0);
        LinearLayout.LayoutParams h2 = new LinearLayout.LayoutParams(0, px(80), 1f);
        h2.setMargins(px(6), 0, 0, 0);
        row.addView(onBtn, h1);
        row.addView(offBtn, h2);
        onBtn.setOnClickListener(v -> Prefs.turnOn(p));
        offBtn.setOnClickListener(v -> p.edit().putBoolean(Prefs.ENABLED, false).apply());
        pc.addView(row);
        summary = text("", 18, Color.DKGRAY);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(0, px(10), 0, 0);
        pc.addView(summary);
        body.addView(pc);

        // Сүзгі: − / +
        LinearLayout fc = card();
        sumVal = stepper(fc, "Баға", Prefs.MIN_SUM, 50, 0, 10000);
        kmPriceVal = stepper(fc, "1 км бағасы", Prefs.MIN_PER_KM, 10, 0, 1000);
        kmVal = stepper(fc, "Жолдың ұзындығы", Prefs.MAX_KM, 1, 0, Prefs.MAX_KM_LIMIT);
        body.addView(fc);

        // Әзірлеуші
        LinearLayout dc = card();
        TextView dq = text("Қосымша керек пе?", 20, NAVY);
        dq.setGravity(Gravity.CENTER);
        dc.addView(dq);
        TextView dn = text("Әзірлеуші — Асхат\n" + PHONE_SHOWN, 20, Color.BLACK);
        dn.setGravity(Gravity.CENTER);
        dn.setPadding(0, px(4), 0, px(10));
        dc.addView(dn);
        LinearLayout drow = new LinearLayout(this);
        drow.setOrientation(LinearLayout.HORIZONTAL);
        TextView call = bigButton("ҚОҢЫРАУ", NAVY, 18);
        TextView wa = bigButton("WHATSAPP", Color.parseColor("#25A244"), 18);
        LinearLayout.LayoutParams d1 = new LinearLayout.LayoutParams(0, px(60), 1f);
        d1.setMargins(0, 0, px(6), 0);
        LinearLayout.LayoutParams d2 = new LinearLayout.LayoutParams(0, px(60), 1f);
        d2.setMargins(px(6), 0, 0, 0);
        drow.addView(call, d1);
        drow.addView(wa, d2);
        call.setOnClickListener(v -> open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + PHONE))));
        wa.setOnClickListener(v -> open(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/" + PHONE.substring(1)))));
        dc.addView(drow);
        body.addView(dc);

        TextView footer = text("Жолың болсын, батыр!", 20, NAVY);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, px(14), 0, px(18));
        body.addView(footer);

        // Сынақ режимі — мелко, в самом низу (для проверки)
        Switch test = new Switch(this);
        test.setText("Сынақ режимі (тек тексеру үшін)");
        test.setTextSize(13);
        test.setTextColor(Color.GRAY);
        test.setChecked(Prefs.test(p));
        test.setThumbTintList(ColorStateList.valueOf(SKY));
        test.setOnCheckedChangeListener((v, on) -> p.edit().putBoolean(Prefs.TEST, on).apply());
        body.addView(test);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(CREAM);
        sv.addView(page);
        setContentView(sv);
    }

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{SKY, Color.parseColor("#005F80"), NAVY}));
        h.setPadding(px(16), px(16), px(16), px(8));
        TextView title = text(getString(R.string.app_name), 28, Color.WHITE);
        h.addView(title);
        h.addView(text("Тапсырысты бірінші ұста", 17, Overlays.GOLD));
        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.batyr_banner);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(170));
        al.setMargins(0, px(6), 0, 0);
        h.addView(art, al);
        return h;
    }

    /** Строка «название / [−] значение [+]». Возвращает поле значения. */
    private TextView stepper(LinearLayout parent, String name, String key, int step, int min, int max) {
        TextView head = text(name, 20, NAVY);
        head.setPadding(0, px(10), 0, px(6));
        parent.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = bigButton("−", NAVY, 34);
        TextView plus = bigButton("+", NAVY, 34);
        TextView val = text("", 26, Color.BLACK);
        val.setGravity(Gravity.CENTER);
        row.addView(minus, new LinearLayout.LayoutParams(px(72), px(64)));
        row.addView(val, new LinearLayout.LayoutParams(0, px(64), 1f));
        row.addView(plus, new LinearLayout.LayoutParams(px(72), px(64)));
        parent.addView(row);

        minus.setOnClickListener(v -> {
            int cur = p.getInt(key, defaultFor(key));
            p.edit().putInt(key, Math.max(min, cur - step)).apply();
        });
        plus.setOnClickListener(v -> {
            int cur = p.getInt(key, defaultFor(key));
            p.edit().putInt(key, Math.min(max, cur + step)).apply();
        });
        return val;
    }

    private int defaultFor(String key) {
        if (Prefs.MIN_SUM.equals(key)) return Prefs.minSum(p);
        if (Prefs.MIN_PER_KM.equals(key)) return Prefs.minPerKm(p);
        return Prefs.maxKm(p);
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
        onBtn.setBackground(round(on ? Overlays.GREEN : Overlays.RED));
        offBtn.setBackground(round(on ? Overlays.RED : Overlays.GREEN));
        summary.setText(on ? "Тапсырыс ұсталып жатыр" : "Тапсырыс ұсталмайды");
        summary.setTextColor(on ? Overlays.GREEN : Color.DKGRAY);
        sumVal.setText(Prefs.minSum(p) + " ₸+");
        kmPriceVal.setText(Prefs.minPerKm(p) + " ₸+");
        kmVal.setText(Prefs.maxKm(p) == 0 ? "шектеусіз" : Prefs.maxKm(p) + " км-ге дейін");
        accessCard.setVisibility(serviceOn() ? View.GONE : View.VISIBLE);
    }

    // ---------- элементы ----------

    private void open(Intent i) {
        try { startActivity(i); } catch (Throwable ignored) {}
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(16), px(14), px(16), px(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(px(16));
        bg.setStroke(px(1), Color.parseColor("#E8D9A8"));
        c.setBackground(bg);
        c.setLayoutParams(full(ViewGroup.LayoutParams.WRAP_CONTENT, 10));
        return c;
    }

    private TextView bigButton(String s, int color, int sp) {
        TextView t = text(s, sp, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(color));
        return t;
    }

    private GradientDrawable round(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(14));
        return d;
    }

    private LinearLayout.LayoutParams full(int h, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(0, px(topDp), 0, 0);
        return lp;
    }

    /** Весь текст в приложении жирный. */
    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
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
