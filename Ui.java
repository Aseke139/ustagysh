package kz.askhat.ustagysh;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Дизайнның жалпы бөлшектері: түстер, алтын батырмалар, карточкалар. */
final class Ui {

    static final int NAVY = Color.parseColor("#0C2B5A");
    static final int NAVY_DARK = Color.parseColor("#011635");
    static final int GOLD = Color.parseColor("#B8862B");
    static final int GOLD_LIGHT = Color.parseColor("#E9C46A");
    static final int MUTED = Color.parseColor("#6C7498");
    static final int BG = Color.WHITE;
    static final int SKY = Color.parseColor("#E4F0FE");
    static final int RED = Color.parseColor("#C62828");
    static final int GREEN = Color.parseColor("#2E8B3E");

    private Ui() {}

    static int px(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView center(TextView t) {
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** Ақ карточка, жұмсақ көлеңке. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(px(c, 18));
        bg.setStroke(Math.max(1, px(c, 0.6f)), Color.parseColor("#EEF0F5"));
        l.setBackground(bg);
        l.setElevation(px(c, 4));
        l.setPadding(px(c, 16), px(c, 14), px(c, 16), px(c, 16));
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    /** Мәннің артындағы алтын жарқыл. */
    static Drawable glow(Context c) {
        return new GlowDrawable();
    }

    static Bitmap bmp(Context c, int res) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        return BitmapFactory.decodeResource(c.getResources(), res, o);
    }

    /** Сурет-батырма: бейне пропорциясы сақталады, басқанда сәл кішірейеді. */
    static ImageView imageButton(Context c, int res) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setAdjustViewBounds(true);
        press(v);
        return v;
    }

    /** Алтын жиекті батырма (қызыл / жасыл / көк) + өрнек + жанды мәтін. */
    static TextView bar(Context c, int res, String label, float sp) {
        TextView t = text(c, label, sp, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(px(c, 3), 0, px(c, 1.5f), Color.parseColor("#66000000"));
        t.setBackground(barDrawable(c, res, t));
        t.setLetterSpacing(0.03f);
        press(t);
        return t;
    }

    private static Bitmap ornament;

    static BarDrawable barDrawable(Context c, int res, TextView owner) {
        if (ornament == null) ornament = bmp(c, R.drawable.ornament);
        return new BarDrawable(bmp(c, res), ornament, owner);
    }

    /** Басқанда кішкене батып шығады. */
    static void press(View v) {
        v.setClickable(true);
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.97f).scaleY(0.95f).setDuration(70).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                    break;
            }
            return false; // басу өңдеуі әрі қарай
        });
    }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    /**
     * Батырма фоны: шеттері (дөңгелек алтын жиек) биіктікке қарай масштабталады,
     * ортасы созылады. Екі жағына өрнек салынады — мәтінге тимейтіндей.
     */
    static final class BarDrawable extends Drawable {
        private static final int CAP = 40; // суреттегі шет ені, пиксель
        private final Bitmap b, orn;
        private final TextView owner;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Rect src = new Rect();
        private final RectF dst = new RectF();

        BarDrawable(Bitmap b, Bitmap orn, TextView owner) {
            this.b = b;
            this.orn = orn;
            this.owner = owner;
        }

        @Override
        public void draw(Canvas c) {
            Rect bo = getBounds();
            float w = bo.width(), h = bo.height();
            if (b == null || w <= 0 || h <= 0) return;
            int bw = b.getWidth(), bh = b.getHeight();
            float cap = Math.min(CAP * h / bh, w / 3f);
            src.set(0, 0, CAP, bh);
            dst.set(bo.left, bo.top, bo.left + cap, bo.bottom);
            c.drawBitmap(b, src, dst, paint);
            src.set(CAP, 0, bw - CAP, bh);
            dst.set(bo.left + cap, bo.top, bo.right - cap, bo.bottom);
            c.drawBitmap(b, src, dst, paint);
            src.set(bw - CAP, 0, bw, bh);
            dst.set(bo.right - cap, bo.top, bo.right, bo.bottom);
            c.drawBitmap(b, src, dst, paint);

            if (orn == null) return;
            float oh = h * 0.62f;
            float ow = oh * orn.getWidth() / orn.getHeight();
            float gap = h * 0.42f;
            float textW = owner == null ? h * 2f
                    : owner.getPaint().measureText(String.valueOf(owner.getText())) + h * 0.35f;
            if (w - (ow + gap) * 2 < textW) {          // орын аз: өрнекті кішірейту
                float k = Math.max(0f, (w - textW) / 2f) / (ow + gap);
                if (k < 0.6f) return;                  // тым тар — өрнексіз
                oh *= k; ow *= k; gap *= k;
            }
            float top = bo.top + (h - oh) / 2f;
            dst.set(bo.left + gap, top, bo.left + gap + ow, top + oh);
            c.drawBitmap(orn, null, dst, paint);
            dst.set(bo.right - gap - ow, top, bo.right - gap, top + oh);
            c.drawBitmap(orn, null, dst, paint);
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Жұмсақ сопақ алтын жарқыл (шеттері мөлдір, кесілмейді). */
    static final class GlowDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int lastW = -1;

        @Override
        public void draw(Canvas c) {
            Rect bo = getBounds();
            float w = bo.width(), h = bo.height();
            if (w <= 0 || h <= 0) return;
            float r = Math.min(w * 0.42f, h * 1.6f);
            if (lastW != bo.width()) {
                lastW = bo.width();
                paint.setShader(new android.graphics.RadialGradient(0, 0, r,
                        new int[]{Color.parseColor("#FFE9B8"), Color.parseColor("#CCFFF3D9"), Color.parseColor("#00FFFFFF")},
                        new float[]{0f, 0.55f, 1f}, android.graphics.Shader.TileMode.CLAMP));
            }
            c.save();
            c.translate(bo.exactCenterX(), bo.exactCenterY());
            c.scale(1f, (h * 0.5f) / r);
            c.drawCircle(0, 0, r, paint);
            c.restore();
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
