package kz.askhat.ustagysh;

import android.content.Context;
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
 * Плавающие элементы поверх inDrive:
 *  - круглая кнопка с батыром (тап — показать/спрятать панель; на ✕ — подтверждение и выход);
 *  - панель ҚОСУ / ӨШІРУ (на ✕ — просто прячется).
 */
final class Overlays {

    static final int GREEN = Color.parseColor("#2E9E44");
    static final int RED = Color.parseColor("#D32F2F");
    static final int BLUE = Color.parseColor("#005F80");
    static final int GOLD = Color.parseColor("#FEC50C");

    private final Context ctx;
    private final SharedPreferences p;
    private final WindowManager wm;
    private final float dp;

    private ImageView bubble;
    private WindowManager.LayoutParams bubbleLp;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;
    private TextView onBtn, offBtn;
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

    private static final int FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

    /** Привести экран в соответствие с настройками. */
    void refresh() {
        try {
            if (Prefs.hidden(p)) {
                removeBubble();
                removePanel();
                return;
            }
            if (bubble == null) addBubble();
            paintBubble();
            if (Prefs.panel(p)) {
                if (panel == null) addPanel();
                paintPanel();
            } else {
                removePanel();
            }
        } catch (Throwable ignored) {}
    }

    void destroy() {
        removeBubble();
        removePanel();
        hideTrash();
        closeDialog();
    }

    // ---------- круглая кнопка ----------

    private void addBubble() {
        bubble = new ImageView(ctx);
        bubble.setImageResource(R.drawable.batyr_icon);
        bubble.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bubbleLp = lp(px(60), px(60), FLAGS);
        bubbleLp.x = px(4);
        bubbleLp.y = px(200);
        bubble.setOnTouchListener(drag(bubble, bubbleLp,
                () -> p.edit().putBoolean(Prefs.PANEL, !Prefs.panel(p)).apply(),
                this::showConfirm));
        wm.addView(bubble, bubbleLp);
    }

    private void paintBubble() {
        if (bubble == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(BLUE);
        bg.setStroke(px(3), Prefs.enabled(p) ? GREEN : RED);
        bubble.setBackground(bg);
    }

    private void removeBubble() {
        if (bubble != null) { try { wm.removeView(bubble); } catch (Throwable ignored) {} }
        bubble = null;
    }

    // ---------- панель ҚОСУ / ӨШІРУ ----------

    private void addPanel() {
        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setPadding(px(4), px(4), px(4), px(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E6002B3D"));
        bg.setCornerRadius(px(14));
        bg.setStroke(px(1), GOLD);
        panel.setBackground(bg);

        onBtn = button("ҚОСУ");
        offBtn = button("ӨШІРУ");
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.setMargins(0, 0, px(4), 0);
        panel.addView(onBtn, gap);
        panel.addView(offBtn);

        panelLp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, FLAGS);
        panelLp.x = bubbleLp != null ? bubbleLp.x : px(4);
        panelLp.y = (bubbleLp != null ? bubbleLp.y : px(200)) + px(66);

        Runnable hide = () -> p.edit().putBoolean(Prefs.PANEL, false).apply();
        onBtn.setOnTouchListener(drag(panel, panelLp, () -> Prefs.turnOn(p), hide));
        offBtn.setOnTouchListener(drag(panel, panelLp,
                () -> p.edit().putBoolean(Prefs.ENABLED, false).apply(), hide));
        wm.addView(panel, panelLp);
    }

    private TextView button(String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(16), px(10), px(16), px(10));
        return t;
    }

    private void paintPanel() {
        if (panel == null) return;
        boolean on = Prefs.enabled(p);
        onBtn.setBackground(round(on ? GREEN : RED));
        offBtn.setBackground(round(on ? RED : GREEN));
    }

    private GradientDrawable round(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(10));
        return d;
    }

    private void removePanel() {
        if (panel != null) { try { wm.removeView(panel); } catch (Throwable ignored) {} }
        panel = null;
    }

    // ---------- перетаскивание и ✕ ----------

    private View.OnTouchListener drag(View win, WindowManager.LayoutParams l,
                                      Runnable onTap, Runnable onTrash) {
        return new View.OnTouchListener() {
            float rx, ry; int x0, y0; boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        rx = e.getRawX(); ry = e.getRawY();
                        x0 = l.x; y0 = l.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - rx, dy = e.getRawY() - ry;
                        if (!moved && (Math.abs(dx) > px(10) || Math.abs(dy) > px(10))) {
                            moved = true;
                            showTrash();
                        }
                        if (moved) {
                            l.x = x0 + (int) dx;
                            l.y = y0 + (int) dy;
                            try { wm.updateViewLayout(win, l); } catch (Throwable ignored) {}
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
            WindowManager.LayoutParams l = lp(px(68), px(68), FLAGS
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
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

    private void showConfirm() {
        if (dialog != null) return;
        try {
            FrameLayout dim = new FrameLayout(ctx);
            dim.setBackgroundColor(Color.parseColor("#99000000"));

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(px(22), px(20), px(22), px(16));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(px(18));
            bg.setStroke(px(2), GOLD);
            card.setBackground(bg);

            TextView title = new TextView(ctx);
            title.setText("Шығасыз ба?");
            title.setTextSize(22);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(Color.BLACK);
            card.addView(title);

            TextView msg = new TextView(ctx);
            msg.setText("Тапсырыс ұстағыш өшеді, батырмалар жабылады.\nҚайта қосу үшін қосымшаны ашыңыз.");
            msg.setTextSize(15);
            msg.setTextColor(Color.DKGRAY);
            msg.setPadding(0, px(8), 0, px(16));
            card.addView(msg);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.END);
            TextView no = button("Жоқ");
            no.setBackground(round(Color.GRAY));
            TextView yes = button("Иә, шығу");
            yes.setBackground(round(RED));
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gap.setMargins(0, 0, px(10), 0);
            row.addView(no, gap);
            row.addView(yes);
            card.addView(row);

            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            cl.setMargins(px(24), 0, px(24), 0);
            dim.addView(card, cl);

            no.setOnClickListener(v -> closeDialog());
            dim.setOnClickListener(v -> closeDialog());
            card.setOnClickListener(v -> { });
            yes.setOnClickListener(v -> {
                closeDialog();
                p.edit().putBoolean(Prefs.ENABLED, false).putBoolean(Prefs.HIDDEN, true).apply();
            });

            WindowManager.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT, FLAGS);
            dialog = dim;
            wm.addView(dim, l);
        } catch (Throwable ignored) { dialog = null; }
    }

    private void closeDialog() {
        if (dialog != null) { try { wm.removeView(dialog); } catch (Throwable ignored) {} }
        dialog = null;
    }
}
