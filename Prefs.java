package kz.askhat.ustagysh;

import android.content.Context;
import android.content.SharedPreferences;

/** Настройки фильтра. */
public final class Prefs {
    public static final String FILE = "ustagysh";
    public static final String ENABLED = "enabled";
    public static final String TEST = "test";
    public static final String MIN_SUM = "min_sum";
    public static final String MIN_PER_KM = "min_per_km";
    public static final String MAX_KM = "max_km"; // 0 = шектеусіз
    public static final String HIDDEN = "hidden"; // плавающие кнопки закрыты через ✕
    public static final String PANEL = "panel";   // панель ҚОСУ/ӨШІРУ показана
    public static final String LICENSE = "license"; // іске қосу кілті
    public static final String BATTERY_ASKED = "battery_asked";

    public static final int MAX_KM_LIMIT = 30;

    private Prefs() {}

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean enabled(SharedPreferences p) { return p.getBoolean(ENABLED, false); }
    public static boolean test(SharedPreferences p) { return p.getBoolean(TEST, true); }
    public static int minSum(SharedPreferences p) { return p.getInt(MIN_SUM, 700); }
    public static int minPerKm(SharedPreferences p) { return p.getInt(MIN_PER_KM, 150); }
    public static int maxKm(SharedPreferences p) { return p.getInt(MAX_KM, 0); }
    public static boolean hidden(SharedPreferences p) { return p.getBoolean(HIDDEN, false); }
    public static boolean panel(SharedPreferences p) { return p.getBoolean(PANEL, true); }

    /** ҚОСУ из приложения или панели: включить и вернуть плавающие кнопки. */
    public static void turnOn(SharedPreferences p) {
        p.edit().putBoolean(ENABLED, true).putBoolean(HIDDEN, false).apply();
    }

    /** Строка фильтра для плавающей кнопки. */
    public static String label(SharedPreferences p) {
        String s = "Баға " + minSum(p) + " ₸+\n1 км " + minPerKm(p) + " ₸+";
        if (maxKm(p) > 0) s += "\nЖол " + maxKm(p) + " км-ге дейін";
        return s;
    }

    public static String summary(SharedPreferences p) {
        String km = maxKm(p) > 0 ? " · " + maxKm(p) + " км-ге дейін" : "";
        return minSum(p) + " ₸-дан, 1 км " + minPerKm(p) + " ₸-дан қымбат тапсырыстар алынады" + km;
    }
}
