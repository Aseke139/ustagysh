package kz.askhat.ustagysh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Полоса казахского орнамента «қошқар мүйіз» (золото на тёмно-синем). */
public class OrnamentView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int bg;

    public OrnamentView(Context c, int background) {
        super(c);
        bg = background;
        line.setColor(Overlays.GOLD);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        c.drawColor(bg);
        if (h <= 0) return;
        line.setStrokeWidth(h * 0.09f);
        float unit = h * 1.6f;
        int n = Math.max(1, (int) (w / unit));
        float step = w / n;
        for (int i = 0; i < n; i++) horn(c, step * i + step / 2f, h, step);
        line.setStrokeWidth(h * 0.05f);
        c.drawLine(0, h * 0.06f, w, h * 0.06f, line);
        c.drawLine(0, h * 0.94f, w, h * 0.94f, line);
    }

    /** Один элемент: ножка и два рога, закрученные наружу. */
    private void horn(Canvas c, float cx, float h, float step) {
        float s = Math.min(step, h * 1.6f) / 1.6f; // масштаб по высоте
        Path p = new Path();
        float base = h * 0.82f, mid = h * 0.50f;
        p.moveTo(cx, base);
        p.lineTo(cx, mid);
        // правый рог
        p.cubicTo(cx, h * 0.18f, cx + s * 0.55f, h * 0.12f, cx + s * 0.58f, h * 0.42f);
        p.cubicTo(cx + s * 0.60f, h * 0.64f, cx + s * 0.32f, h * 0.62f, cx + s * 0.34f, h * 0.44f);
        // левый рог
        p.moveTo(cx, mid);
        p.cubicTo(cx, h * 0.18f, cx - s * 0.55f, h * 0.12f, cx - s * 0.58f, h * 0.42f);
        p.cubicTo(cx - s * 0.60f, h * 0.64f, cx - s * 0.32f, h * 0.62f, cx - s * 0.34f, h * 0.44f);
        c.drawPath(p, line);
    }
}
