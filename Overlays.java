package kz.askhat.ustagysh;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Один блок поверх inDrive:
 *  [кнопка с машиной]  Баға 750 ₸+
 *                      1 км 150 ₸+
 * Кнопка: тап — вкл/выкл (зелёная/красная). Надпись: тап — открыть приложение.
 * Тянуть — двигается весь блок. Бросить на ✕ — спросить и выйти.
 */
final class Overlays {

    static final int GREEN = Color.parseColor("#2E9E44");
    static final int RED = Color.parseColor("#D32F2F");
    static final int GOLD = Color.parseColor("#FEC50C");

    private static final int FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

    private final Context ctx;
    private final SharedPreferences p;
    private final WindowManager wm;
    private final float dp;

    private LinearLayout block;
    private WindowManager.LayoutParams blockLp;
    private LinearLayout button;
    private TextView word, label;
    private TextView trash;
    private View dialog;

    Overlays(Context ctx, SharedPreferences p) {
        this.ctx = ctx;
        this.p = p;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.dp = ctx.getResources().getDisplayMetrics().density;
    }

    private int px(float v) { return (int) (v * dp); }

    private WindowManager.LayoutParams lp(int w, int h, int flags) {
        WindowManager.LayoutParams l = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        l.gravity = Gravity.TOP | Gravity.START;
        return l;
    }

    void refresh() {
        try {
            if (Prefs.hidden(p)) { removeBlock(); return; }
            if (block == null) addBlock();
            paint();
        } catch (Throwable ignored) {}
    }

    void destroy() {
        removeBlock();
        hideTrash();
        closeDialog();
    }

    // ---------- блок ----------

    private void addBlock() {
        block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.HORIZONTAL);
        block.setGravity(Gravity.CENTER_VERTICAL);

        button = new LinearLayout(ctx);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(px(6), px(6), px(6), px(5));
        ImageView car = new ImageView(ctx);
        car.setImageResource(R.drawable.car_icon);
        button.addView(car, new LinearLayout.LayoutParams(px(50), px(21)));
        word = new TextView(ctx);
        word.setTextColor(Color.WHITE);
        word.setTextSize(11);
        word.setTypeface(Typeface.DEFAULT_BOLD);
        word.setGravity(Gravity.CENTER);
        button.addView(word);
        block.addView(button, new LinearLayout.LayoutParams(px(72), px(56)));

        label = new TextView(ctx);
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setShadowLayer(px(3), 0, 0, Color.BLACK);
        label.setPadding(px(8), px(2), px(8), px(2));
        block.addView(label);

        blockLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, FLAGS);
        blockLp.x = px(4);
        blockLp.y = px(220);

        Runnable exit = this::showConfirm;
        button.setOnTouchListener(drag(() -> {
            if (Prefs.enabled(p)) p.edit().putBoolean(Prefs.ENABLED, false).apply();
            else Prefs.turnOn(p);
        }, exit));
        label.setOnTouchListener(drag(this::openApp, exit));
        wm.addView(block, blockLp);
    }

    private void paint() {
        if (block == null) return;
        boolean on = Prefs.enabled(p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(on ? GREEN : RED);
        bg.setCornerRadius(px(14));
        bg.setStroke(px(2), Color.WHITE);
        button.setBackground(bg);
        word.setText(on ? "ҚОСУЛЫ" : "ӨШІРУЛІ");
        label.setText(Prefs.label(p));
    }

    private void removeBlock() {
        if (block != null) { try { wm.removeView(block); } catch (Throwable ignored) {} }
        block = null;
    }

    private void openApp() {
        try {
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ctx.startActivity(i);
        } catch (Throwable ignored) {}
    }

    // ---------- перетаскивание и ✕ ----------

    private View.OnTouchListener drag(Runnable onTap, Runnable onTrash) {
        return new View.OnTouchListener() {
            float rx, ry; int x0, y0; boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        rx = e.getRawX(); ry = e.getRawY();
                        x0 = blockLp.x; y0 = blockLp.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - rx, dy = e.getRawY() - ry;
                        if (!moved && (Math.abs(dx) > px(10) || Math.abs(dy) > px(10))) {
                            moved = true;
                            showTrash();
                        }
                        if (moved) {
                            blockLp.x = x0 + (int) dx;
                            blockLp.y = y0 + (int) dy;
                            try { wm.updateViewLayout(block, blockLp); } catch (Throwable ignored) {}
                            paintTrash(overTrash(e));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            boolean over = overTrash(e);
                            hideTrash();
                            if (over) onTrash.run();
                        } else {
                            onTap.run();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        hideTrash();
                        return true;
                }
                return false;
            }
        };
    }

    private void showTrash() {
        if (trash != null) return;
        try {
            trash = new TextView(ctx);
            trash.setText("✕");
            trash.setTextColor(Color.WHITE);
            trash.setTextSize(28);
            trash.setGravity(Gravity.CENTER);
            paintTrash(false);
            WindowManager.LayoutParams l = lp(px(68), px(68),
                    FLAGS | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            l.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            l.y = px(70);
            wm.addView(trash, l);
        } catch (Throwable ignored) { trash = null; }
    }

    private void paintTrash(boolean hot) {
        if (trash == null) return;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(hot ? RED : Color.parseColor("#B3000000"));
        d.setStroke(px(2), Color.WHITE);
        trash.setBackground(d);
    }

    private boolean overTrash(MotionEvent e) {
        if (trash == null) return false;
        int[] loc = new int[2];
        trash.getLocationOnScreen(loc);
        float cx = loc[0] + trash.getWidth() / 2f, cy = loc[1] + trash.getHeight() / 2f;
        float dx = e.getRawX() - cx, dy = e.getRawY() - cy;
        float r = px(70);
        return dx * dx + dy * dy < r * r;
    }

    private void hideTrash() {
        if (trash != null) { try { wm.removeView(trash); } catch (Throwable ignored) {} }
        trash = null;
    }

    // ---------- подтверждение выхода ----------

    private TextView pill(String text, int color) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(20), px(12), px(20), px(12));
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(12));
        t.setBackground(d);
        return t;
    }

    private void showConfirm() {
        if (dialog != null) return;
        try {
            FrameLayout dim = new FrameLayout(ctx);
            dim.setBackgroundColor(Color.parseColor("#99000000"));

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(px(22), px(20), px(22), px(18));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(px(18));
            bg.setStroke(px(2), GOLD);
            card.setBackground(bg);

            TextView title = new TextView(ctx);
            title.setText("Шығасыз ба?");
            title.setTextSize(26);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(Color.BLACK);
            title.setPadding(0, 0, 0, px(16));
            card.addView(title);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView no = pill("ЖОҚ", Color.GRAY);
            TextView yes = pill("ИӘ", RED);
            LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            l1.setMargins(0, 0, px(8), 0);
            LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            l2.setMargins(px(8), 0, 0, 0);
            row.addView(no, l1);
            row.addView(yes, l2);
            card.addView(row);

            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            cl.setMargins(px(28), 0, px(28), 0);
            dim.addView(card, cl);

            no.setOnClickListener(v -> closeDialog());
            dim.setOnClickListener(v -> closeDialog());
            card.setOnClickListener(v -> { });
            yes.setOnClickListener(v -> {
                closeDialog();
                p.edit().putBoolean(Prefs.ENABLED, false).putBoolean(Prefs.HIDDEN, true).apply();
            });

            dialog = dim;
            wm.addView(dim, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, FLAGS));
        } catch (Throwable ignored) { dialog = null; }
    }

    private void closeDialog() {
        if (dialog != null) { try { wm.removeView(dialog); } catch (Throwable ignored) {} }
        dialog = null;
    }
}
