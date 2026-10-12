package kz.askhat.ustagysh;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Іске қосу кілті.
 * Телефон коды (XXXX-XXXX) — осы телефонның ANDROID_ID-інен шығады.
 * Кілт = HMAC(SECRET, телефон коды). Кілт тек сол телефонда жұмыс істейді.
 * Кілтті Асхат жасайды: құлып экранындағы суретті 3 секунд басып тұру → PIN.
 */
public final class License {

    private static final String SECRET = "e07f1d72579ba57670a4ff7365d78279456d2fdbf871f005";
    private static final String PIN_HASH = "609003e2de8b2a69e0fe925a133c8f84b1aa1b35bf4419f982b45a0e36237f5c";
    /** I, O, 0, 1 жоқ — шатастырмас үшін. */
    private static final String ABC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private License() {}

    /** Осы телефонның коды, мысалы K7PM-2QXA. */
    public static String deviceCode(Context c) {
        String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null || id.isEmpty()) id = "no-id";
        return format(enc(sha256("ustagysh-dev:" + id)));
    }

    /** Телефон кодына сәйкес кілт. */
    public static String keyFor(String deviceCode) {
        String code = clean(deviceCode);
        if (code.length() != 8) return "";
        return format(enc(hmac("ustagysh-key:" + code)));
    }

    public static boolean isActive(Context c) {
        return isActive(c, Prefs.get(c));
    }

    public static boolean isActive(Context c, SharedPreferences p) {
        String k = p.getString(Prefs.LICENSE, "");
        return !k.isEmpty() && clean(k).equals(clean(keyFor(deviceCode(c))));
    }

    /** true — кілт дұрыс, сақталды. */
    public static boolean activate(Context c, String key) {
        String k = clean(key);
        if (k.length() != 8 || !k.equals(clean(keyFor(deviceCode(c))))) return false;
        Prefs.get(c).edit().putString(Prefs.LICENSE, format(k)).apply();
        return true;
    }

    public static boolean pinOk(String pin) {
        return hex(sha256("ustagysh-pin:" + (pin == null ? "" : pin.trim()))).equals(PIN_HASH);
    }

    /** Тек әріп пен сан, бас әріппен. */
    public static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : s.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) b.append(ch);
        }
        return b.toString();
    }

    public static String format(String s) {
        s = clean(s);
        return s.length() > 4 ? s.substring(0, 4) + "-" + s.substring(4) : s;
    }

    // ---------- крипто ----------

    private static String enc(byte[] h) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 8; i++) b.append(ABC.charAt((h[i] & 0xFF) % 32));
        return b.toString();
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new byte[32];
        }
    }

    private static byte[] hmac(String s) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return m.doFinal(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new byte[32];
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xFF));
        return s.toString();
    }
}
