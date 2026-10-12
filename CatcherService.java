package kz.askhat.ustagysh;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ловит всплывающую карточку заказа inDrive.
 * Нажимает только «Принять за … ₸» и минимальное время прибытия. Больше ничего.
 */
public class CatcherService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long SCAN_GAP_MS = 40;       // не чаще, чтобы не лагало
    private static final long SAME_CARD_MS = 20000;   // одну карточку дважды не трогаем
    private static final long ETA_WINDOW_MS = 15000;  // ждём окно «Как скоро вы прибудете?»
    private static final int MAX_NODES = 800;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private ToneGenerator tone;
    private Vibrator vibrator;

    private boolean scanPending = false;
    private long lastScanAt = 0;
    private String lastKey = "";
    private long lastKeyAt = 0;
    private long acceptedAt = 0;

    private WindowManager wm;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleLp;

    private final Runnable scanTask = () -> {
        scanPending = false;
        lastScanAt = SystemClock.uptimeMillis();
        try {
            scan();
        } catch (Throwable ignored) {
            // при любой ошибке просто ничего не нажимаем
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = Prefs.get(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        try { tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100); } catch (Throwable ignored) {}
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        showBubble();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (prefs == null || !Prefs.enabled(prefs) || e == null) return;
        if (!isInDrive(e.getPackageName())) return;
        if (scanPending) return;
        scanPending = true;
        long wait = SCAN_GAP_MS - (SystemClock.uptimeMillis() - lastScanAt);
        handler.postDelayed(scanTask, Math.max(0, wait));
    }

    private static boolean isInDrive(CharSequence pkg) {
        return pkg != null && pkg.toString().toLowerCase(Locale.ROOT).contains("indriver");
    }

    // ---------------- основная логика ----------------

    private void scan() {
        if (!Prefs.enabled(prefs)) return;
        for (AccessibilityNodeInfo root : inDriveRoots()) {
            long now = SystemClock.uptimeMillis();

            // Шаг 2: после принятия выбираем минимальное время
            if (now - acceptedAt < ETA_WINDOW_MS && pickMinEta(root)) {
                acceptedAt = 0;
                return;
            }

            // Шаг 1: всплывающая карточка
            AccessibilityNodeInfo btn = findAccept(root);
            if (btn == null) continue;

            List<String> texts = new ArrayList<>();
            collect(root, texts, new int[]{0});
            if (!hasSkip(texts)) continue; // не карточка заказа — не трогаем

            OrderParser.Order o = OrderParser.parse(texts, String.valueOf(btn.getText()));
            if (o.key.equals(lastKey) && now - lastKeyAt < SAME_CARD_MS) return;
            lastKey = o.key;
            lastKeyAt = now;

            String reason = OrderParser.check(o,
                    Prefs.minSum(prefs), Prefs.minPerKm(prefs), Prefs.maxKm(prefs));

            if (Prefs.test(prefs)) {
                if (reason == null) beep(true);
                toast("СЫНАҚ · " + o + "\n" + (reason == null ? "СӘЙКЕС — басар еді" : "Өткізілді: " + reason));
                return;
            }
            if (reason != null) return;

            if (click(btn)) {
                acceptedAt = SystemClock.uptimeMillis();
                beep(true);
                vibrate();
            }
            return;
        }
    }

    private List<AccessibilityNodeInfo> inDriveRoots() {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        try {
            List<AccessibilityWindowInfo> ws = getWindows();
            if (ws != null) {
                // сверху вниз: всплывающее окно обычно последнее
                for (int i = ws.size() - 1; i >= 0; i--) {
                    AccessibilityNodeInfo r = ws.get(i).getRoot();
                    if (r != null && isInDrive(r.getPackageName())) out.add(r);
                }
            }
        } catch (Throwable ignored) {}
        if (out.isEmpty()) {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null && isInDrive(r.getPackageName())) out.add(r);
        }
        return out;
    }

    private AccessibilityNodeInfo findAccept(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> stack = new ArrayList<>();
        stack.add(root);
        int seen = 0;
        while (!stack.isEmpty() && seen++ < MAX_NODES) {
            AccessibilityNodeInfo n = stack.remove(stack.size() - 1);
            if (n == null) continue;
            if (OrderParser.isAcceptText(str(n.getText()))
                    || OrderParser.isAcceptText(str(n.getContentDescription()))) {
                if (n.isVisibleToUser() && n.isEnabled()) return n;
            }
            for (int i = n.getChildCount() - 1; i >= 0; i--) stack.add(n.getChild(i));
        }
        return null;
    }

    private void collect(AccessibilityNodeInfo n, List<String> out, int[] count) {
        if (n == null || count[0]++ > MAX_NODES) return;
        if (n.isVisibleToUser()) {
            String t = str(n.getText());
            if (!t.isEmpty()) out.add(t);
            String d = str(n.getContentDescription());
            if (!d.isEmpty() && !d.equals(t)) out.add(d);
        }
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out, count);
    }

    private static boolean hasSkip(List<String> texts) {
        for (String t : texts) {
            String l = t.toLowerCase(Locale.ROOT);
            if (l.contains("пропустить") || l.equals("skip")) return true;
        }
        return false;
    }

    /** Окно «Как скоро вы прибудете?» — нажать самое маленькое время. */
    private boolean pickMinEta(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        List<AccessibilityNodeInfo> stack = new ArrayList<>();
        stack.add(root);
        boolean isEtaScreen = false;
        int seen = 0;
        while (!stack.isEmpty() && seen++ < MAX_NODES) {
            AccessibilityNodeInfo n = stack.remove(stack.size() - 1);
            if (n == null) continue;
            String t = str(n.getText()).toLowerCase(Locale.ROOT);
            if (t.contains("прибуд") || t.contains("как скоро") || t.contains("how soon")) isEtaScreen = true;
            if (OrderParser.etaMinutes(str(n.getText())) > 0 && n.isVisibleToUser()) all.add(n);
            for (int i = 0; i < n.getChildCount(); i++) stack.add(n.getChild(i));
        }
        if (!isEtaScreen || all.isEmpty()) return false;
        AccessibilityNodeInfo best = null;
        int bestMin = Integer.MAX_VALUE;
        for (AccessibilityNodeInfo n : all) {
            int m = OrderParser.etaMinutes(str(n.getText()));
            if (m < bestMin) { bestMin = m; best = n; }
        }
        return best != null && click(best);
    }

    // ---------------- нажатие ----------------

    private boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        for (int i = 0; i < 6 && n != null; i++) {
            if (n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            n = n.getParent();
        }
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.isEmpty()) return false;
        Path p = new Path();
        p.moveTo(r.exactCenterX(), r.exactCenterY());
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60))
                .build();
        return dispatchGesture(g, null, null);
    }

    // ---------------- сигнал ----------------

    private void beep(boolean ok) {
        try {
            if (tone != null) tone.startTone(ok ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_NACK, 200);
        } catch (Throwable ignored) {}
    }

    private void vibrate() {
        try {
            if (vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Throwable ignored) {}
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private static String str(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    // ---------------- плавающая кнопка ----------------

    private void showBubble() {
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            bubble = new TextView(this);
            bubble.setTextColor(Color.WHITE);
            bubble.setTextSize(13);
            bubble.setGravity(Gravity.CENTER);
            int size = (int) (52 * getResources().getDisplayMetrics().density);
            bubbleLp = new WindowManager.LayoutParams(size, size,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            bubbleLp.gravity = Gravity.TOP | Gravity.START;
            bubbleLp.x = 0;
            bubbleLp.y = (int) (220 * getResources().getDisplayMetrics().density);
            bubble.setOnTouchListener(new View.OnTouchListener() {
                float downX, downY; int startX, startY; boolean moved;
                @Override
                public boolean onTouch(View v, MotionEvent ev) {
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = ev.getRawX(); downY = ev.getRawY();
                            startX = bubbleLp.x; startY = bubbleLp.y; moved = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
                            if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true;
                            if (moved) {
                                bubbleLp.x = startX + (int) dx;
                                bubbleLp.y = startY + (int) dy;
                                wm.updateViewLayout(bubble, bubbleLp);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (!moved) {
                                prefs.edit().putBoolean(Prefs.ENABLED, !Prefs.enabled(prefs)).apply();
                            }
                            return true;
                    }
                    return false;
                }
            });
            wm.addView(bubble, bubbleLp);
            paintBubble();
        } catch (Throwable ignored) {
            bubble = null;
        }
    }

    private void paintBubble() {
        if (bubble == null) return;
        boolean on = Prefs.enabled(prefs);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(on ? Color.parseColor("#2E9E44") : Color.parseColor("#8A8A8A"));
        bg.setStroke(3, Color.WHITE);
        bubble.setBackground(bg);
        bubble.setText(on ? "ҚОС" : "ӨШ");
        bubble.setAlpha(0.85f);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Prefs.ENABLED.equals(key)) {
            handler.post(this::paintBubble);
            if (!Prefs.enabled(p)) { acceptedAt = 0; handler.removeCallbacks(scanTask); scanPending = false; }
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        try { if (bubble != null) wm.removeView(bubble); } catch (Throwable ignored) {}
        if (tone != null) tone.release();
        super.onDestroy();
    }
}
