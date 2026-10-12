package kz.askhat.ustagysh;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.CycleInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;

import static kz.askhat.ustagysh.Ui.MATCH;
import static kz.askhat.ustagysh.Ui.WRAP;

/** Екі экран: құлып (кілт енгізу) және басты экран (ҚОСУ / ӨШІРУ, сүзгі). */
public class MainActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PHONE = "+77754406747";
    private static final String PHONE_SHOWN = "8 775 440 67 47";

    private SharedPreferences p;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Boolean shownActive = null;
    // басты экран элементтері
    private TextView onBtn, offBtn, status, sumVal, kmPriceVal;
    private View accessCard, batteryCard;
    private Switch testSwitch;
    private Ui.BarDrawable redA, greenA, redB, greenB;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Prefs.get(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Ui.SKY);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        show();
    }

    /** Кілт бар-жоғына қарай экранды таңдау. */
    private void show() {
        boolean active = License.isActive(this, p);
        if (shownActive != null && shownActive == active) return;
        shownActive = active;
        onBtn = null;
        setContentView(active ? buildMain() : buildLock());
        if (active) refresh();
    }

    private int px(float dp) { return Ui.px(this, dp); }

    private ScrollView scroll(LinearLayout page) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        sv.addView(page);
        return sv;
    }

    private LinearLayout page() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(Ui.BG);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        return l;
    }

    private ImageView header(int res) {
        ImageView h = new ImageView(this);
        h.setImageResource(res);
        h.setAdjustViewBounds(true);
        h.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.setLayoutParams(new LinearLayout.LayoutParams(MATCH, WRAP));
        secretHold(h);
        return h;
    }

    private View footer() {
        ImageView f = new ImageView(this);
        f.setImageResource(R.drawable.footer_batyr);
        f.setAdjustViewBounds(true);
        f.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(px(250), WRAP);
        l.gravity = Gravity.CENTER_HORIZONTAL;
        l.setMargins(0, px(14), 0, px(6));
        f.setLayoutParams(l);
        return f;
    }

    // =====================================================================
    //  ҚҰЛЫП ЭКРАНЫ
    // =====================================================================

    private View buildLock() {
        LinearLayout page = page();
        page.addView(header(R.drawable.hdr_lock));

        LinearLayout body = page();
        body.setPadding(px(18), 0, px(18), px(24));
        page.addView(body);

        String code = License.deviceCode(this);

        LinearLayout c1 = Ui.card(this);
        c1.setPadding(px(16), px(20), px(16), px(18));
        c1.addView(Ui.center(Ui.text(this, "Қосымша әлі\nіске қосылмаған", 27, Ui.NAVY, true)));
        TextView sub = Ui.center(Ui.text(this, "Асхат іске қосып береді", 17, Ui.MUTED, false));
        sub.setPadding(0, px(10), 0, 0);
        c1.addView(sub);
        body.addView(c1, Ui.lp(MATCH, WRAP, 0, px(6), 0, 0));

        ImageView req = Ui.imageButton(this, R.drawable.btn_request);
        req.setOnClickListener(v -> askOnWhatsApp(code));
        body.addView(req, Ui.lp(MATCH, WRAP, 0, px(18), 0, 0));

        TextView codeView = Ui.center(Ui.text(this, "Телефон коды: " + code, 15, Ui.MUTED, false));
        codeView.setPadding(0, px(8), 0, px(8));
        codeView.setOnClickListener(v -> copy(code));
        body.addView(codeView, Ui.lp(MATCH, WRAP, 0, px(2), 0, 0));

        LinearLayout c2 = Ui.card(this);
        c2.setPadding(px(18), px(18), px(18), px(20));
        c2.addView(Ui.center(Ui.text(this, "Кілт бар ма?", 22, Ui.NAVY, true)));

        EditText key = keyField();
        c2.addView(key, Ui.lp(MATCH, px(58), 0, px(14), 0, 0));

        TextView err = Ui.center(Ui.text(this, "", 15, Ui.RED, true));
        err.setVisibility(View.GONE);
        c2.addView(err, Ui.lp(MATCH, WRAP, 0, px(8), 0, 0));

        ImageView go = Ui.imageButton(this, R.drawable.btn_activate);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(px(270), WRAP);
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        gl.setMargins(0, px(16), 0, 0);
        c2.addView(go, gl);
        go.setOnClickListener(v -> {
            if (License.activate(this, key.getText().toString())) {
                Toast.makeText(this, "Іске қосылды! Жолың болсын!", Toast.LENGTH_LONG).show();
                show();
            } else {
                err.setText(License.clean(key.getText().toString()).length() < 8
                        ? "Кілтті толық жазыңыз: XXXX-XXXX"
                        : "Кілт қате. Асхаттан сұраңыз");
                err.setVisibility(View.VISIBLE);
                key.animate().translationX(px(10)).setInterpolator(new CycleInterpolator(3))
                        .setDuration(350).withEndAction(() -> key.setTranslationX(0)).start();
            }
        });
        body.addView(c2, Ui.lp(MATCH, WRAP, 0, px(10), 0, 0));

        body.addView(footer());
        return scroll(page);
    }

    private EditText keyField() {
        EditText e = new EditText(this);
        e.setHint("XXXX-XXXX");
        e.setHintTextColor(Color.parseColor("#9AA0B4"));
        e.setTextColor(Ui.NAVY);
        e.setTextSize(24);
        e.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        e.setLetterSpacing(0.12f);
        e.setGravity(Gravity.CENTER);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(px(12));
        bg.setStroke(px(1.5f), Ui.GOLD_LIGHT);
        e.setBackground(bg);
        e.setPadding(px(10), 0, px(10), 0);
        e.addTextChangedListener(new TextWatcher() {
            boolean busy;
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (busy) return;
                String f = License.format(s.toString());
                if (f.length() > 9) f = f.substring(0, 9);
                if (!f.equals(s.toString())) {
                    busy = true;
                    s.replace(0, s.length(), f);
                    busy = false;
                }
            }
        });
        return e;
    }

    private void askOnWhatsApp(String code) {
        String msg = "Сәлеметсіз бе! «Тапсырыс ұстағыш» қосымшасын іске қосып беріңізші.\nТелефон коды: " + code;
        String enc;
        try { enc = URLEncoder.encode(msg, "UTF-8"); } catch (Exception ex) { enc = ""; }
        open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + PHONE.substring(1) + "?text=" + enc)));
    }

    private void copy(String s) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("код", s));
            Toast.makeText(this, "Көшірілді: " + s, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {}
    }

    // =====================================================================
    //  БАСТЫ ЭКРАН
    // =====================================================================

    private View buildMain() {
        LinearLayout page = page();
        page.addView(header(R.drawable.hdr_main));

        LinearLayout body = page();
        body.setPadding(px(16), 0, px(16), px(20));
        page.addView(body);

        body.addView(Ui.center(Ui.text(this, "Тапсырыс көшбасшысы бол", 25, Ui.NAVY, true)),
                Ui.lp(MATCH, WRAP, 0, px(2), 0, 0));
        ImageView div = new ImageView(this);
        div.setImageResource(R.drawable.divider);
        div.setAdjustViewBounds(true);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(260), WRAP);
        dl.gravity = Gravity.CENTER_HORIZONTAL;
        dl.setMargins(0, px(4), 0, px(4));
        body.addView(div, dl);

        // Рұқсат (тек берілмесе)
        LinearLayout ac = Ui.card(this);
        ac.addView(Ui.center(Ui.text(this, "Рұқсат жоқ", 22, Ui.RED, true)));
        TextView ah = Ui.center(Ui.text(this, "«Арнайы мүмкіндіктер» ішінен\n«Тапсырыс ұстағыш» қосыңыз", 15, Ui.MUTED, false));
        ah.setPadding(0, px(6), 0, 0);
        ac.addView(ah);
        TextView accessBtn = Ui.bar(this, R.drawable.bar_red, "РҰҚСАТ БЕРУ", 22);
        accessBtn.setOnClickListener(v -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        ac.addView(accessBtn, Ui.lp(MATCH, px(60), 0, px(12), 0, 0));
        accessCard = ac;
        body.addView(ac, Ui.lp(MATCH, WRAP, 0, px(8), 0, px(4)));

        // Батарея (тек шектеу болса)
        LinearLayout bc = Ui.card(this);
        bc.addView(Ui.center(Ui.text(this, "Телефон қосымшаны ұйықтатпасын", 17, Ui.NAVY, true)));
        TextView bb = Ui.bar(this, R.drawable.bar_navy, "БАТАРЕЯ ШЕКТЕУІН АЛУ", 17);
        bb.setOnClickListener(v -> askBattery());
        bc.addView(bb, Ui.lp(MATCH, px(54), 0, px(10), 0, 0));
        batteryCard = bc;
        body.addView(bc, Ui.lp(MATCH, WRAP, 0, px(8), 0, px(4)));

        // ҚОСУ / ӨШІРУ
        onBtn = Ui.bar(this, R.drawable.bar_red, "ҚОСУ", 30);
        offBtn = Ui.bar(this, R.drawable.bar_green, "ӨШІРУ", 30);
        redA = Ui.barDrawable(this, R.drawable.bar_red, onBtn);
        redB = Ui.barDrawable(this, R.drawable.bar_red, offBtn);
        greenA = Ui.barDrawable(this, R.drawable.bar_green, onBtn);
        greenB = Ui.barDrawable(this, R.drawable.bar_green, offBtn);
        onBtn.setOnClickListener(v -> {
            if (!serviceOn()) {
                Toast.makeText(this, "Алдымен рұқсат беріңіз", Toast.LENGTH_LONG).show();
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            Prefs.turnOn(p);
        });
        offBtn.setOnClickListener(v -> p.edit().putBoolean(Prefs.ENABLED, false).apply());
        body.addView(onBtn, Ui.lp(MATCH, px(64), 0, px(10), 0, 0));
        body.addView(offBtn, Ui.lp(MATCH, px(64), 0, px(10), 0, 0));

        status = Ui.center(Ui.text(this, "", 16, Ui.MUTED, true));
        body.addView(status, Ui.lp(MATCH, WRAP, 0, px(10), 0, 0));

        // Сүзгі
        LinearLayout fc = Ui.card(this);
        fc.setPadding(px(14), px(10), px(14), px(14));
        sumVal = stepper(fc, "Баға", Prefs.MIN_SUM, 50, 0, 10000);
        kmPriceVal = stepper(fc, "1 км бағасы", Prefs.MIN_PER_KM, 10, 0, 1000);
        body.addView(fc, Ui.lp(MATCH, WRAP, 0, px(10), 0, 0));

        // Әзірлеуші
        LinearLayout dc = Ui.card(this);
        dc.setPadding(px(12), px(16), px(12), px(14));
        dc.addView(Ui.center(Ui.text(this, "Қосымша керек пе?", 23, Ui.NAVY, true)));
        TextView dn = Ui.center(Ui.text(this, "Әзірлеуші — Асхат", 17, Ui.MUTED, false));
        dn.setPadding(0, px(6), 0, 0);
        dc.addView(dn);
        TextView ph = Ui.center(Ui.text(this, PHONE_SHOWN, 31, Ui.NAVY_DARK, true));
        ph.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        ph.setPadding(0, px(6), 0, px(10));
        dc.addView(ph);
        LinearLayout drow = new LinearLayout(this);
        drow.setOrientation(LinearLayout.HORIZONTAL);
        drow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView call = Ui.imageButton(this, R.drawable.btn_call);
        ImageView wa = Ui.imageButton(this, R.drawable.btn_whatsapp);
        LinearLayout.LayoutParams d1 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        d1.setMargins(0, 0, px(5), 0);
        LinearLayout.LayoutParams d2 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        d2.setMargins(px(5), 0, 0, 0);
        drow.addView(call, d1);
        drow.addView(wa, d2);
        call.setOnClickListener(v -> open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + PHONE))));
        wa.setOnClickListener(v -> open(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/" + PHONE.substring(1)))));
        dc.addView(drow);
        body.addView(dc, Ui.lp(MATCH, WRAP, 0, px(12), 0, 0));

        body.addView(footer());

        // Тест режимі
        LinearLayout tr = new LinearLayout(this);
        tr.setOrientation(LinearLayout.HORIZONTAL);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        tr.setPadding(px(4), px(6), px(4), px(6));
        ImageView shield = new ImageView(this);
        shield.setImageResource(R.drawable.shield_icon);
        tr.addView(shield, new LinearLayout.LayoutParams(px(44), px(50)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(px(12), 0, 0, 0);
        tt.addView(Ui.text(this, "Тест режимі", 19, Ui.NAVY, true));
        TextView ts = Ui.text(this, "Тек тестілеу үшін", 15, Color.parseColor("#9AA0B4"), false);
        ts.setPadding(0, px(4), 0, 0);
        tt.addView(ts);
        tr.addView(tt, new LinearLayout.LayoutParams(0, WRAP, 1f));
        testSwitch = new Switch(this);
        testSwitch.setScaleX(1.3f);
        testSwitch.setScaleY(1.3f);
        int[][] st = {{android.R.attr.state_checked}, {}};
        testSwitch.setThumbTintList(new ColorStateList(st, new int[]{Ui.GOLD, Color.WHITE}));
        testSwitch.setTrackTintList(new ColorStateList(st, new int[]{Ui.GOLD_LIGHT, Color.parseColor("#C9CDD8")}));
        testSwitch.setOnCheckedChangeListener((v, on) -> {
            if (on != Prefs.test(p)) p.edit().putBoolean(Prefs.TEST, on).apply();
        });
        tr.addView(testSwitch, new LinearLayout.LayoutParams(WRAP, WRAP));
        body.addView(tr, Ui.lp(MATCH, WRAP, 0, px(6), 0, 0));

        return scroll(page);
    }

    /** «Атау» + [−] мән [+]. Мән өрісін қайтарады. */
    private TextView stepper(LinearLayout parent, String name, String key, int step, int min, int max) {
        TextView head = Ui.text(this, name, 19, Ui.NAVY, true);
        head.setPadding(px(6), px(8), 0, px(4));
        parent.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView minus = Ui.imageButton(this, R.drawable.btn_minus);
        ImageView plus = Ui.imageButton(this, R.drawable.btn_plus);
        // Ортасында: сан, астында «және одан жоғары»
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.setBackground(Ui.glow(this));
        TextView val = Ui.center(Ui.text(this, "", 34, Ui.NAVY_DARK, true));
        val.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        val.setSingleLine(true);
        val.setAutoSizeTextTypeUniformWithConfiguration(16, 34, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        mid.addView(val, new LinearLayout.LayoutParams(MATCH, px(42)));
        TextView up = Ui.center(Ui.text(this, "және одан жоғары", 13, Ui.GOLD, true));
        mid.addView(up, new LinearLayout.LayoutParams(MATCH, WRAP));
        row.addView(minus, new LinearLayout.LayoutParams(px(76), px(54)));
        row.addView(mid, new LinearLayout.LayoutParams(0, px(68), 1f));
        row.addView(plus, new LinearLayout.LayoutParams(px(76), px(54)));
        parent.addView(row);

        repeat(minus, () -> {
            int cur = p.getInt(key, defaultFor(key));
            p.edit().putInt(key, Math.max(min, cur - step)).apply();
        });
        repeat(plus, () -> {
            int cur = p.getInt(key, defaultFor(key));
            p.edit().putInt(key, Math.min(max, cur + step)).apply();
        });
        return val;
    }

    /** Басу — бір қадам. Басып тұру — жылдам өзгереді. */
    private void repeat(View v, Runnable act) {
        final Runnable[] loop = new Runnable[1];
        loop[0] = () -> { act.run(); handler.postDelayed(loop[0], 110); };
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(60).start();
                    act.run();
                    handler.postDelayed(loop[0], 450);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    handler.removeCallbacks(loop[0]);
                    return true;
            }
            return true;
        });
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
        show();
        refresh();
    }

    @Override
    protected void onPause() {
        p.unregisterOnSharedPreferenceChangeListener(this);
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (Prefs.LICENSE.equals(key)) show();
        refresh();
    }

    private void refresh() {
        if (onBtn == null) return;
        boolean on = Prefs.enabled(p);
        // Белсенді күй — жасыл, екіншісі — қызыл (суреттегідей)
        onBtn.setBackground(on ? greenA : redA);
        offBtn.setBackground(on ? redB : greenB);
        status.setText(on ? (Prefs.test(p) ? "● Тест: тапсырыс тексеріліп жатыр" : "● Тапсырыс ұсталып жатыр")
                : "Тапсырыс ұсталмайды");
        status.setTextColor(on ? Ui.GREEN : Ui.MUTED);
        sumVal.setText(Prefs.minSum(p) + " ₸");
        kmPriceVal.setText(Prefs.minPerKm(p) + " ₸");
        accessCard.setVisibility(serviceOn() ? View.GONE : View.VISIBLE);
        batteryCard.setVisibility(batteryOk() ? View.GONE : View.VISIBLE);
        if (testSwitch.isChecked() != Prefs.test(p)) testSwitch.setChecked(Prefs.test(p));
    }

    // ---------- батарея ----------

    private boolean batteryOk() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return true;
        }
    }

    private void askBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // =====================================================================
    //  ӘЗІРЛЕУШІ: кілт жасау (суретті 3 секунд басып тұру → PIN)
    // =====================================================================

    private void secretHold(View v) {
        Runnable fire = this::askPin;
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    handler.postDelayed(fire, 3000);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(fire);
                    return true;
            }
            return true;
        });
    }

    private EditText dialogField(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setTextSize(22);
        e.setGravity(Gravity.CENTER);
        e.setSingleLine(true);
        return e;
    }

    private LinearLayout wrap(View v) {
        LinearLayout l = new LinearLayout(this);
        l.setPadding(px(22), px(8), px(22), 0);
        l.addView(v, new LinearLayout.LayoutParams(MATCH, WRAP));
        return l;
    }

    private void askPin() {
        EditText pin = dialogField("PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Әзірлеуші")
                .setView(wrap(pin))
                .setPositiveButton("Кіру", (d, w) -> {
                    if (License.pinOk(pin.getText().toString())) askCode();
                    else Toast.makeText(this, "PIN қате", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Жабу", null)
                .show();
    }

    private void askCode() {
        EditText code = dialogField("Телефон коды", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        code.setText(License.deviceCode(this));
        code.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Клиенттің телефон коды")
                .setMessage("Клиент жіберген кодты жазыңыз (қазір осы телефонның коды тұр)")
                .setView(wrap(code))
                .setPositiveButton("Кілт жасау", (d, w) -> showKey(code.getText().toString()))
                .setNegativeButton("Жабу", null)
                .show();
    }

    private void showKey(String rawCode) {
        String code = License.format(rawCode);
        String key = License.keyFor(code);
        if (key.isEmpty()) {
            Toast.makeText(this, "Код 8 таңбадан тұруы керек", Toast.LENGTH_LONG).show();
            return;
        }
        boolean mine = License.clean(code).equals(License.clean(License.deviceCode(this)));
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Кілт: " + key)
                .setMessage("Телефон коды: " + code + "\nКілт: " + key)
                .setPositiveButton("Жіберу", (d, w) -> {
                    Intent s = new Intent(Intent.ACTION_SEND);
                    s.setType("text/plain");
                    s.putExtra(Intent.EXTRA_TEXT, "«Тапсырыс ұстағыш» іске қосу кілті: " + key);
                    open(Intent.createChooser(s, "Кілтті жіберу"));
                })
                .setNeutralButton("Көшіру", (d, w) -> copy(key));
        if (mine) {
            b.setNegativeButton("Осында қосу", (d, w) -> {
                License.activate(this, key);
                show();
            });
        }
        b.show();
    }

    // ---------- жалпы ----------

    private void open(Intent i) {
        try { startActivity(i); } catch (Throwable ignored) {
            Toast.makeText(this, "Ашылмады", Toast.LENGTH_SHORT).show();
        }
    }

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
