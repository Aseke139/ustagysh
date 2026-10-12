package kz.askhat.ustagysh;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор текстов карточки заказа inDrive и проверка фильтра.
 * Без зависимостей от Android, чтобы проверять отдельно.
 */
public final class OrderParser {

    private OrderParser() {}

    public static final class Order {
        public int price = -1;          // цена клиента из кнопки «Принять за … ₸»
        public double perKm = -1;       // ₸/км из карточки
        public double pickupKm = -1;    // расстояние до клиента (~750 м)
        public double tripKm = -1;      // длина поездки (метка на карте)
        public String key = "";

        @Override
        public String toString() {
            return price + " ₸ · " + fmt(perKm) + " ₸/км · жол " + fmt(tripKm) + " км";
        }
    }

    private static final Pattern ACCEPT =
            Pattern.compile("^\\s*(Принять за|Accept for)\\s*(.+)$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PER_KM =
            Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*₸\\s*/\\s*км");
    private static final Pattern PICKUP =
            Pattern.compile("~\\s*([0-9]+(?:[.,][0-9]+)?)\\s*(км|м)(?![а-яa-z])");
    private static final Pattern KM =
            Pattern.compile("(?<![~0-9.,])(?<!~\\s)([0-9]+(?:[.,][0-9]+)?)\\s*км(?![а-яa-z/])");
    private static final Pattern ETA =
            Pattern.compile("^\\s*([0-9]{1,3})\\s*мин(\\.|ут[аы]?)?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Неразрывные и узкие пробелы, переносы строк -> обычный пробел. */
    public static String norm(String t) {
        if (t == null) return null;
        StringBuilder b = new StringBuilder(t.length());
        for (char c : t.toCharArray()) {
            if (c == '\u00A0' || c == '\u202F' || c == '\u2007' || c == '\u2009' || c == '\u200A') b.append(' ');
            else if (c == '\u200B' || c == '\u200E' || c == '\u200F') continue;
            else b.append(c);
        }
        return b.toString();
    }

    /** Текст кнопки принятия по цене клиента? Встречные предложения не подходят. */
    public static boolean isAcceptText(String t) {
        t = norm(t);
        if (t == null) return false;
        String low = t.toLowerCase(Locale.ROOT);
        if (low.contains("предлож") || low.contains("offer")) return false;
        return ACCEPT.matcher(t).matches();
    }

    /** Число минут на кнопке выбора времени, иначе -1. */
    public static int etaMinutes(String t) {
        t = norm(t);
        if (t == null) return -1;
        Matcher m = ETA.matcher(t);
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }

    public static int digits(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') b.append(c);
            else if (c == ',' || c == '.') break; // копейки не нужны
        }
        if (b.length() == 0 || b.length() > 7) return -1;
        return Integer.parseInt(b.toString());
    }

    public static Order parse(List<String> texts, String acceptText) {
        Order o = new Order();
        List<String> nt = new ArrayList<>();
        for (String t : texts) if (t != null) nt.add(norm(t));
        texts = nt;
        acceptText = norm(acceptText);
        Matcher am = ACCEPT.matcher(acceptText == null ? "" : acceptText);
        if (am.matches()) o.price = digits(am.group(2));

        for (String t : texts) {
            if (t == null) continue;
            Matcher pk = PER_KM.matcher(t);
            if (o.perKm < 0 && pk.find()) {
                o.perKm = num(pk.group(1));
                Matcher pu = PICKUP.matcher(t.substring(pk.end()));
                if (pu.find()) o.pickupKm = toKm(pu.group(1), pu.group(2));
            }
        }
        if (o.pickupKm < 0) {
            for (String t : texts) {
                if (t == null) continue;
                Matcher pu = PICKUP.matcher(t);
                if (pu.find()) { o.pickupKm = toKm(pu.group(1), pu.group(2)); break; }
            }
        }

        // Длина поездки: метки «4,3 км» без «~», не равные подаче
        List<Double> cand = new ArrayList<>();
        for (String t : texts) {
            if (t == null || t.contains("₸")) continue;
            Matcher km = KM.matcher(t);
            while (km.find()) {
                double v = num(km.group(1));
                if (v <= 0) continue;
                if (o.pickupKm > 0 && Math.abs(v - o.pickupKm) < 0.051) continue;
                boolean dup = false;
                for (double c : cand) if (Math.abs(c - v) < 0.001) dup = true;
                if (!dup) cand.add(v);
            }
        }
        if (cand.size() == 1) o.tripKm = cand.get(0);

        if (o.perKm < 0 && o.price > 0 && o.tripKm > 0) {
            o.perKm = Math.round(o.price / o.tripKm * 10.0) / 10.0;
        }
        o.key = o.price + "|" + o.perKm + "|" + o.pickupKm + "|" + String.join("¦", texts).hashCode();
        return o;
    }

    /**
     * null — заказ подходит. Иначе причина отказа (на казахском).
     * maxKm <= 0 — без ограничения по длине поездки.
     */
    public static String check(Order o, int minSum, int minPerKm, int maxKm) {
        if (o.price <= 0) return "баға оқылмады";
        if (o.perKm <= 0) return "1 км бағасы оқылмады";
        if (o.price < minSum) return "сома аз: " + o.price + " ₸";
        if (o.perKm < minPerKm) return "1 км арзан: " + fmt(o.perKm) + " ₸";
        if (maxKm > 0) {
            if (o.tripKm <= 0) return "қашықтық оқылмады";
            if (o.tripKm > maxKm) return "жол ұзақ: " + fmt(o.tripKm) + " км";
        }
        return null;
    }

    private static double toKm(String n, String unit) {
        double v = num(n);
        return "м".equals(unit) ? v / 1000.0 : v;
    }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(',', '.')); }
        catch (NumberFormatException e) { return -1; }
    }

    static String fmt(double v) {
        if (v < 0) return "?";
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
