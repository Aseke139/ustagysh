package kz.askhat.ustagysh;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
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
 * Один блок поверх inDrive (дизайн по картинкам):
 *  [ҚОСУЛЫ / ӨШІРУЛІ]  Баға 750 ₸+
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
    private ImageView button;
    private TextView label;
    private ImageView trash;
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
            if (Prefs.hidden(p) || !License.isActive(ctx, p)) { removeBlock(); return; }
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

        button = new ImageView(ctx);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        block.addView(button, new LinearLayout.LayoutParams(px(84), px(64)));

        label = new TextView(ctx);
        label.setTextColor(Color.WHITE);
        label.setTextSize(17);
        label.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        label.setShadowLayer(px(4), px(1), px(2), Color.parseColor("#E6000000"));
        label.setLineSpacing(px(1), 1f);
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
        button.setImageResource(on ? R.drawable.ov_on : R.drawable.ov_off);
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
            trash = new ImageView(ctx);
            trash.setImageResource(R.drawable.ov_trash);
            trash.setScaleType(ImageView.ScaleType.FIT_CENTER);
            paintTrash(false);
            WindowManager.LayoutParams l = lp(px(76), px(76),
                    FLAGS | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            l.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            l.y = px(70);
            wm.addView(trash, l);
        } catch (Throwable ignored) { trash = null; }
    }

    /** Үстіне әкелгенде ✕ үлкейеді. */
    private void paintTrash(boolean hot) {
        if (trash == null) return;
        float s = hot ? 1.25f : 1f;
        if (trash.getScaleX() != s) trash.animate().scaleX(s).scaleY(s).setDuration(90).start();
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

    /**
     * «Шығасыз ба?» — дайын сурет-карточка. Батырмалар суреттің ішінде:
     * ЖОҚ — сол жақ, ИӘ — оң жақ (үлесімен есептеледі).
     */
    private void showConfirm() {
        if (dialog != null) return;
        try {
            FrameLayout dim = new FrameLayout(ctx);
            dim.setBackgroundColor(Color.parseColor("#B3000000"));

            ImageView card = new ImageView(ctx);
            card.setImageResource(R.drawable.dlg_exit);
            card.setAdjustViewBounds(true);
            card.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.setElevation(px(8));

            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(310),
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            dim.addView(card, cl);

            dim.setOnClickListener(v -> closeDialog());
            card.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
                float fx = e.getX() / Math.max(1, v.getWidth());
                float fy = e.getY() / Math.max(1, v.getHeight());
                if (fy > 0.58f && fy < 0.87f) {
                    if (fx > 0.04f && fx < 0.50f) {          // ЖОҚ
                        closeDialog();
                    } else if (fx > 0.50f && fx < 0.96f) {   // ИӘ
                        closeDialog();
                        p.edit().putBoolean(Prefs.ENABLED, false).putBoolean(Prefs.HIDDEN, true).apply();
                    }
                }
                return true;
            });

            dialog = dim;
            wm.addView(dim, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, FLAGS));
            card.setScaleX(0.85f);
            card.setScaleY(0.85f);
            card.setAlpha(0f);
            card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start();
        } catch (Throwable ignored) { dialog = null; }
    }

    private void closeDialog() {
        if (dialog != null) { try { wm.removeView(dialog); } catch (Throwable ignored) {} }
        dialog = null;
    }
}
