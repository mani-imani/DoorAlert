package ir.maniimani.dooralert;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The alert: a slim banner that slides down from the top of the screen over whatever app
 * is open. It never blocks the app underneath (touches outside it go straight through).
 *   ● در عقب راست بازه
 *     لطفاً ببندیدش
 * When everything is closed it turns green, says so, and slides away. Tap it to dismiss.
 */
public class PopupActivity extends Activity implements Doors.Listener {

    static volatile boolean sVisible;
    static final String EXTRA_DEMO = "demo";

    private final Handler main = new Handler(Looper.getMainLooper());
    private Doors doors;
    private AlertCard card;
    private AlertCard.Badge badge;
    private TextView title, sub;
    private boolean closing, sawOpen, demo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        doors = Doors.get(this);
        demo = getIntent().getBooleanExtra(EXTRA_DEMO, false);

        // a small window at the top: the rest of the screen stays usable
        Window w = getWindow();
        w.setGravity(Gravity.CENTER);
        // An explicit width: a WRAP_CONTENT window is first measured at the small "dialog"
        // width, which made the big title wrap and get cut ("همه‌چیز" without "بسته‌ست").
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int winW = Math.min((int) (dm.widthPixels * 0.82f), Ui.dp(this, 760));
        w.setLayout(winW, WindowManager.LayoutParams.WRAP_CONTENT);
        w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        card = new AlertCard(this);
        int gp = (int) card.glowPad();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(gp + Ui.dp(this, 22), gp + Ui.dp(this, 20), gp + Ui.dp(this, 34), gp + Ui.dp(this, 20));
        card.setMinimumWidth(winW);
        card.setOnClickListener(v -> dismissByUser());

        badge = new AlertCard.Badge(this);
        card.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        title = Ui.text(this, Ui.black(), 34, Color.WHITE);
        title.setShadowLayer(Ui.dp(this, 6), 0, Ui.dp(this, 1), Color.argb(90, 0, 0, 0));
        sub = Ui.text(this, Ui.bold(), 18, Color.argb(235, 255, 255, 255));
        title.setMaxLines(2);
        sub.setMaxLines(3);
        texts.addView(title, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = Ui.dp(this, 2);
        texts.addView(sub, sl);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginStart(Ui.dp(this, 18));
        card.addView(texts, tl);
        setContentView(card);

        // entrance: drop in from above
        card.setAlpha(0f);
        card.setScaleX(0.7f);
        card.setScaleY(0.7f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();

        if (demo) runDemo();
    }

    @Override
    protected void onStart() {
        super.onStart();
        sVisible = true;
        doors.addListener(this);
    }

    @Override
    protected void onStop() {
        doors.removeListener(this);
        sVisible = false;
        if (demo) doors.setDemo(null);
        super.onStop();
        if (!isFinishing()) finish();
    }

    @Override
    public void onDoorsChanged(boolean[] open, boolean calibrated) {
        if (closing) return;
        List names = new List();
        for (int i = 0; i < Doors.COUNT; i++) if (open[i]) names.add(Doors.NAMES[i]);
        if (names.count > 0) {
            sawOpen = true;
            okShown = false;
            main.removeCallbacks(closeAfterOk);
            card.setOk(false);
            if (names.count == 1) {
                setText(title, names.first + " بازه");
                setText(sub, "لطفاً ببندیدش");
            } else {
                setText(title, Text.fa(names.count) + " جا بازه");
                setText(sub, names.joined + " · لطفاً ببندید");
            }
        } else if (sawOpen) {
            // the doors are re-read every 250ms: schedule the close only once, on the switch to green
            if (okShown) return;
            okShown = true;
            card.setOk(true);
            setText(title, "همه‌چیز بسته‌ست");
            setText(sub, "با خیال راحت حرکت کنید");
            main.removeCallbacks(closeAfterOk);
            main.postDelayed(closeAfterOk, 1800);
        } else if (!demo) {
            finish();
        }
    }

    /** Tiny helper to collect the open names. */
    private static final class List {
        int count;
        String first, joined = "";

        void add(String n) {
            if (count == 0) first = n;
            joined = count == 0 ? n : joined + "، " + n;
            count++;
        }
    }

    private void setText(TextView tv, String s) {
        if (s.contentEquals(tv.getText())) return;
        boolean first = tv.getText().length() == 0;
        tv.setText(s);
        if (first) return;
        tv.animate().cancel();
        tv.setAlpha(0.25f);
        tv.animate().alpha(1f).setDuration(260).start();
    }

    private boolean okShown;
    private final Runnable closeAfterOk = this::animateOut;

    private void dismissByUser() {
        if (!demo) DoorService.sDismissedWith = doors.open();
        animateOut();
    }

    private void animateOut() {
        if (closing) return;
        closing = true;
        card.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(380)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
        // safety net: close even if the animation never reports its end
        main.postDelayed(() -> {
            if (!isFinishing()) finish();
        }, 700);
    }

    private void runDemo() {
        boolean[][] steps = {
                {false, false, false, true, false, false},
                {true, false, false, true, true, false},
                {false, false, false, false, true, false},
                {false, false, false, false, false, false},
        };
        long t = 400;
        for (boolean[] s : steps) {
            final boolean[] st = s;
            main.postDelayed(() -> {
                if (!closing) doors.setDemo(st);
            }, t);
            t += 2400;
        }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
