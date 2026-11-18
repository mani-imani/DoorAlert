package ir.maniimani.dooralert;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;

/**
 * The bright alert card: a vivid red gradient with a breathing glow around it and a white
 * warning badge. Morphs to green with a check mark once everything is closed.
 */
final class AlertCard extends LinearLayout {

    private static final int RED_A = Color.rgb(255, 64, 80), RED_B = Color.rgb(200, 22, 52);
    private static final int GREEN_A = Color.rgb(52, 211, 120), GREEN_B = Color.rgb(20, 150, 85);

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d, glowPad;
    private float ok;                 // 0 red .. 1 green
    private ValueAnimator okAnim;

    AlertCard(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        glowPad = 26 * d;
        setWillNotDraw(false);
        setClipToPadding(false);
        glow.setStyle(Paint.Style.STROKE);
    }

    float glowPad() {
        return glowPad;
    }

    void setOk(boolean on) {
        float target = on ? 1f : 0f;
        if (okAnim != null) okAnim.cancel();
        okAnim = ValueAnimator.ofFloat(ok, target);
        okAnim.setDuration(450);
        okAnim.addUpdateListener(a -> {
            ok = (float) a.getAnimatedValue();
            invalidate();
        });
        okAnim.start();
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch instanceof Badge) ((Badge) ch).setOk(on);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        r.set(glowPad, glowPad, w - glowPad, h - glowPad);
        float rad = r.height() / 2.6f;
        int a = mix(RED_A, GREEN_A, ok), b = mix(RED_B, GREEN_B, ok);

        // breathing glow (stronger while something is open)
        float t = (float) (0.5 + 0.5 * Math.sin(SystemClock.uptimeMillis() / 320.0));
        float strength = (0.55f + 0.45f * t) * (1f - 0.5f * ok);
        for (int i = 6; i >= 1; i--) {
            float grow = i * 4f * d;
            glow.setColor(a);
            glow.setAlpha((int) (34 * strength * (7 - i) / 6f + 6));
            glow.setStrokeWidth(grow);
            c.drawRoundRect(r, rad, rad, glow);
        }
        fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, a, b, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, fill);
        // soft top sheen for depth
        sheen.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.6f,
                Color.argb(60, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, sheen);
        postInvalidateOnAnimation();
    }

    private static int mix(int x, int y, float t) {
        return Color.rgb((int) (Color.red(x) + (Color.red(y) - Color.red(x)) * t),
                (int) (Color.green(x) + (Color.green(y) - Color.green(x)) * t),
                (int) (Color.blue(x) + (Color.blue(y) - Color.blue(x)) * t));
    }

    /** White round badge: "!" with a pulsing ring, or a check mark when OK. */
    static final class Badge extends View {
        private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private float ok;
        private boolean target;

        Badge(Context c) {
            super(c);
            white.setColor(Color.WHITE);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(Color.WHITE);
            mark.setStrokeCap(Paint.Cap.ROUND);
            mark.setStrokeJoin(Paint.Join.ROUND);
        }

        void setOk(boolean on) {
            target = on;
        }

        @Override
        protected void onDraw(Canvas c) {
            ok += ((target ? 1f : 0f) - ok) * 0.18f;
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
            float R = Math.min(w, h) / 2f, r = R * 0.62f;
            float t = (SystemClock.uptimeMillis() % 1400) / 1400f;
            ring.setStrokeWidth(R * 0.08f);
            ring.setAlpha((int) (200 * (1f - t) * (1f - ok)));
            c.drawCircle(cx, cy, r + (R - r) * t, ring);
            c.drawCircle(cx, cy, r, white);

            int col = mix(RED_B, GREEN_B, ok);
            mark.setColor(col);
            if (ok < 0.5f) {
                float a = 1f - ok * 2f;
                mark.setAlpha((int) (255 * a));
                mark.setStyle(Paint.Style.STROKE);
                mark.setStrokeWidth(r * 0.24f);
                c.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.12f, mark);
                mark.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy + r * 0.45f, r * 0.13f, mark);
            } else {
                float a = (ok - 0.5f) * 2f;
                mark.setAlpha((int) (255 * a));
                mark.setStyle(Paint.Style.STROKE);
                mark.setStrokeWidth(r * 0.22f);
                path.reset();
                path.moveTo(cx - r * 0.45f, cy + r * 0.02f);
                path.lineTo(cx - r * 0.12f, cy + r * 0.34f);
                path.lineTo(cx + r * 0.48f, cy - r * 0.32f);
                c.drawPath(path, mark);
            }
            postInvalidateOnAnimation();
        }
    }
}
