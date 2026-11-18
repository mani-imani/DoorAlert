package ir.maniimani.dooralert;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared look: colours, fonts and a few small widgets. */
final class Ui {
    static final int TEXT = Color.rgb(236, 236, 238);
    static final int TEXT_2 = Color.rgb(142, 142, 150);
    static final int TEXT_3 = Color.rgb(84, 84, 92);
    static final int SURFACE = Color.rgb(14, 14, 16);
    static final int STROKE = Color.rgb(30, 30, 34);
    static final int RIPPLE = Color.argb(46, 255, 255, 255);
    static final int RED = Color.rgb(255, 90, 105);
    static final int GREEN = Color.rgb(80, 220, 140);
    static final int BLUE = Color.rgb(120, 160, 255);

    private static Typeface regular, medium, bold, black;

    static void init(Context c) {
        if (regular != null) return;
        regular = font(c, "vazirmatn_Regular");
        medium = font(c, "vazirmatn_Medium");
        bold = font(c, "vazirmatn_Bold");
        black = font(c, "vazirmatn_Black");
    }

    static Typeface regular() { return regular; }
    static Typeface medium() { return medium; }
    static Typeface bold() { return bold; }
    static Typeface black() { return black; }

    private static Typeface font(Context c, String n) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "fonts/" + n + ".ttf");
        } catch (Throwable t) {
            return Typeface.DEFAULT;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, Typeface face, float sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        return t;
    }

    static GradientDrawable round(int fill, int stroke, float radiusPx, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (stroke != 0) g.setStroke(strokePx, stroke);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** Pill button. */
    static TextView button(Context c, String label, boolean primary, View.OnClickListener l) {
        TextView b = text(c, bold(), 15, primary ? Color.BLACK : TEXT);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 22), dp(c, 11), dp(c, 22), dp(c, 11));
        GradientDrawable bg = round(primary ? TEXT : SURFACE, primary ? 0 : STROKE, dp(c, 100), Math.max(1, dp(c, 1)));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? Color.argb(60, 0, 0, 0) : RIPPLE), bg, null));
        b.setOnClickListener(l);
        return b;
    }

    /** Small status pill with a coloured dot. */
    static LinearLayout chip(Context c, String label, int dotColor) {
        LinearLayout p = new LinearLayout(c);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        p.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        p.setBackground(round(SURFACE, STROKE, dp(c, 100), Math.max(1, dp(c, 1))));
        View dot = new View(c);
        dot.setBackground(round(dotColor, 0, dp(c, 4), 0));
        p.addView(dot, new LinearLayout.LayoutParams(dp(c, 8), dp(c, 8)));
        TextView t = text(c, medium(), 13, TEXT);
        t.setText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(dp(c, 8));
        p.addView(t, lp);
        return p;
    }
}
