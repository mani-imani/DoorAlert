package ir.maniimani.dooralert;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dashboard: live 3D car, the state of every opening, and the tools —
 * one-time setup wizard, demo popup, auto-popup switch and a raw CANBUS data view.
 */
public class MainActivity extends Activity implements Doors.Listener {

    private final Handler main = new Handler(Looper.getMainLooper());
    private Doors doors;
    private Chrome chrome;
    private FrameLayout root;
    private TextView status;
    private final TextView[] rows = new TextView[Doors.COUNT];
    private TextView autoBtn;

    // wizard
    private FrameLayout wizard;
    private TextView wizTitle, wizText;
    private LinearLayout wizButtons;
    private int wizStep;
    private Map<String, Integer> baseline;
    private Set<String> volatileBits;
    private final boolean[] learned = new boolean[Doors.COUNT];
    private boolean sampling;

    // raw data
    private FrameLayout raw;
    private TextView rawText;
    private final Map<String, String> rawPrev = new HashMap<>();
    private final Map<String, Long> rawChanged = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        doors = Doors.get(this);
        chrome = new Chrome(this);
        build();
        setContentView(root);
        DoorService.start(this);
        if (!doors.isCalibrated()) main.postDelayed(this::openWizard, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        chrome.start();
        doors.startPolling();
        doors.addListener(this);
        updateAutoBtn();
    }

    @Override
    protected void onPause() {
        doors.removeListener(this);
        doors.stopPolling();
        chrome.stop();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) chrome.hide();
    }

    @Override
    public void onBackPressed() {
        if (raw.getVisibility() == View.VISIBLE) {
            raw.setVisibility(View.GONE);
            return;
        }
        if (wizard.getVisibility() == View.VISIBLE) {
            closeWizard();
            return;
        }
        super.onBackPressed();
    }

    // ================================================================== live state

    @Override
    public void onDoorsChanged(boolean[] open, boolean calibrated) {
        CarData.Status st = doors.status();
        int items = doors.items().size();
        if (st == CarData.Status.NO_SERVICE) {
            status.setText("سرویس car مانیتور در دسترس نیست");
            status.setTextColor(Ui.RED);
        } else if (items == 0) {
            status.setText("هنوز هیچ داده‌ای از CANBUS نرسیده");
            status.setTextColor(Ui.TEXT_3);
        } else if (!calibrated) {
            status.setText("تنظیم نشده — «تنظیم درها» رو بزن");
            status.setTextColor(Ui.BLUE);
        } else if (Doors.any(open)) {
            status.setText("یه چیزی بازه");
            status.setTextColor(Ui.RED);
        } else {
            status.setText("همه‌چیز بسته‌ست");
            status.setTextColor(Ui.GREEN);
        }
        for (int i = 0; i < Doors.COUNT; i++) {
            String state;
            int color;
            if (!doors.isMapped(i)) {
                state = "تنظیم‌نشده";
                color = Ui.TEXT_3;
            } else if (open[i]) {
                state = "باز";
                color = Ui.RED;
            } else {
                state = "بسته";
                color = Ui.TEXT_2;
            }
            rows[i].setText("●  " + Doors.NAMES[i] + "  ·  " + state);
            rows[i].setTextColor(color);
        }
        if (raw.getVisibility() == View.VISIBLE) renderRaw();
    }

    // ================================================================== UI

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        FrameLayout centre = new FrameLayout(this);
        sv.addView(centre, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        col.setPadding(dp(28), dp(24), dp(28), dp(24));
        centre.addView(col, new FrameLayout.LayoutParams(Math.min(dp(560), getResources().getDisplayMetrics().widthPixels), -2, Gravity.CENTER));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        TextView name = Ui.text(this, Ui.black(), 26, Ui.TEXT);
        name.setText("DoorAlert");
        col.addView(name);
        status = Ui.text(this, Ui.medium(), 14, Ui.TEXT_2);
        col.addView(status, lp(-1, -2, 2));

        // 2 × 3 grid of states
        for (int r = 0; r < 3; r++) {
            LinearLayout line = new LinearLayout(this);
            line.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            for (int k = 0; k < 2; k++) {
                int i = r * 2 + k;
                rows[i] = Ui.text(this, Ui.medium(), 14, Ui.TEXT_2);
                rows[i].setPadding(dp(14), dp(10), dp(14), dp(10));
                rows[i].setBackground(Ui.round(Ui.SURFACE, Ui.STROKE, dp(14), Math.max(1, dp(1))));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
                if (k == 1) p.setMarginStart(dp(8));
                line.addView(rows[i], p);
            }
            col.addView(line, lp(-1, -2, r == 0 ? 18 : 8));
        }

        LinearLayout btns1 = new LinearLayout(this);
        btns1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        btns1.addView(Ui.button(this, "تنظیم درها", true, v -> openWizard()));
        TextView demo = Ui.button(this, "نمایش آزمایشی", false, v ->
                startActivity(new Intent(this, PopupActivity.class).putExtra(PopupActivity.EXTRA_DEMO, true)));
        btns1.addView(demo, lpStart(10));
        col.addView(btns1, lp(-2, -2, 20));

        LinearLayout btns2 = new LinearLayout(this);
        btns2.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        autoBtn = Ui.button(this, "", false, v -> toggleAuto());
        btns2.addView(autoBtn);
        btns2.addView(Ui.button(this, "داده‌های خام", false, v -> openRaw()), lpStart(10));
        col.addView(btns2, lp(-2, -2, 10));

        buildWizard();
        buildRaw();
        chrome.addTopStrip(root, dp(12));
    }

    private void toggleAuto() {
        boolean on = !doors.prefs().getBoolean("enabled", true);
        doors.prefs().edit().putBoolean("enabled", on).apply();
        if (on) DoorService.start(this);
        else DoorService.stop(this);
        updateAutoBtn();
    }

    private void updateAutoBtn() {
        boolean on = doors.prefs().getBoolean("enabled", true);
        autoBtn.setText(on ? "پاپ‌آپ خودکار: روشن" : "پاپ‌آپ خودکار: خاموش");
    }

    // ================================================================== wizard

    private void buildWizard() {
        wizard = new FrameLayout(this);
        wizard.setBackgroundColor(Color.BLACK);
        wizard.setClickable(true);
        wizard.setVisibility(View.GONE);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        wizTitle = Ui.text(this, Ui.black(), 22, Ui.TEXT);
        col.addView(wizTitle);
        wizText = Ui.text(this, Ui.medium(), 15, Ui.TEXT_2);
        wizText.setLineSpacing(0, 1.3f);
        col.addView(wizText, lp(-1, -2, 10));
        wizButtons = new LinearLayout(this);
        wizButtons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        col.addView(wizButtons, lp(-2, -2, 22));
        col.setPadding(dp(28), dp(24), dp(28), dp(24));
        wizard.addView(col, new FrameLayout.LayoutParams(Math.min(dp(560), getResources().getDisplayMetrics().widthPixels), -1, Gravity.CENTER));
        root.addView(wizard, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openWizard() {
        wizard.setVisibility(View.VISIBLE);
        wizard.setAlpha(0f);
        wizard.animate().alpha(1f).setDuration(250).start();
        for (int i = 0; i < Doors.COUNT; i++) learned[i] = false;
        showWizStep(0);
    }

    private void closeWizard() {
        sampling = false;
        wizard.animate().alpha(0f).setDuration(200).withEndAction(() -> wizard.setVisibility(View.GONE)).start();
    }

    private void showWizStep(int step) {
        wizStep = step;
        wizButtons.removeAllViews();
        if (step == 0) {
            wizTitle.setText("تنظیم درها");
            wizText.setText("یه بار برای همیشه: اپ یاد می‌گیره سیگنال هر در کدومه.\n\n"
                    + "ماشین رو روشن کن (سوییچ باز)، همه‌ی درها، صندوق و کاپوت رو ببند و «شروع» رو بزن.");
            wizButtons.addView(Ui.button(this, "شروع", true, v -> captureBaseline()));
            wizButtons.addView(Ui.button(this, "انصراف", false, v -> closeWizard()), lpStart(10));
        } else if (step <= Doors.COUNT) {
            int part = step - 1;
            wizTitle.setText(Text.fa(step) + " از " + Text.fa(Doors.COUNT) + " · " + Doors.NAMES[part]);
            wizText.setText("فقط «" + Doors.NAMES[part] + "» رو باز کن (بقیه بسته بمونن)، بعد «ثبت» رو بزن.");
            wizButtons.addView(Ui.button(this, "ثبت", true, v -> capturePart(part)));
            wizButtons.addView(Ui.button(this, "رد کن", false, v -> showWizStep(step + 1)), lpStart(10));
        } else {
            int n = 0;
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < Doors.COUNT; i++) {
                if (learned[i]) n++;
                s.append(learned[i] ? "✓  " : "—  ").append(Doors.NAMES[i]).append('\n');
            }
            wizTitle.setText(n > 0 ? "تموم شد" : "چیزی یاد گرفته نشد");
            wizText.setText(s.toString().trim() + (n > 0 ? "\n\nحالا هر بار چیزی باز بمونه، پاپ‌آپ نمایش داده می‌شه."
                    : "\n\n«داده‌های خام» رو باز کن و ببین با باز کردن در چیزی تغییر می‌کنه یا نه."));
            wizButtons.addView(Ui.button(this, "بستن", true, v -> closeWizard()));
            if (n > 0) wizButtons.addView(Ui.button(this, "امتحان کن", false, v -> {
                closeWizard();
                startActivity(new Intent(this, PopupActivity.class).putExtra(PopupActivity.EXTRA_DEMO, true));
            }), lpStart(10));
        }
    }

    private void captureBaseline() {
        if (doors.items().isEmpty()) {
            wizText.setText(doors.status() == CarData.Status.NO_SERVICE
                    ? "سرویس car مانیتور در دسترس نیست، پس این مانیتور با DoorAlert سازگار نیست."
                    : "هنوز هیچ داده‌ای از CANBUS نمی‌رسه. سوییچ ماشین باز باشه و چند ثانیه بعد دوباره «شروع» رو بزن.");
            return;
        }
        collect(10, samples -> {
            baseline = samples.get(samples.size() - 1);
            volatileBits = new HashSet<>();
            Map<String, Integer> first = samples.get(0);
            for (Map<String, Integer> s : samples)
                for (Map.Entry<String, Integer> e : s.entrySet())
                    if (!e.getValue().equals(first.get(e.getKey()))) volatileBits.add(e.getKey());
            doors.clearMappings();
            showWizStep(1);
        }, "در حال خوندن حالت «همه بسته»…");
    }

    private void capturePart(int part) {
        collect(5, samples -> {
            Doors.Mapping m = doors.learn(baseline, volatileBits, samples);
            if (m == null) {
                wizText.setText("تغییری دیده نشد. مطمئنی «" + Doors.NAMES[part] + "» کامل بازه؟\n"
                        + "دوباره «ثبت» رو بزن، یا اگه ماشین این سنسور رو نداره «رد کن».");
                return;
            }
            doors.setMapping(part, m);
            learned[part] = true;
            wizTitle.setText("✓ " + Doors.NAMES[part]);
            wizText.setText("یاد گرفتم. حالا ببندش…");
            wizButtons.removeAllViews();
            main.postDelayed(() -> showWizStep(part + 2), 1500);
        }, "در حال خوندن…");
    }

    private interface Samples {
        void done(List<Map<String, Integer>> s);
    }

    /** Takes {@code n} fresh snapshots of the CANBUS bits, ~270 ms apart. */
    private void collect(int n, Samples cb, String msg) {
        if (sampling) return;
        sampling = true;
        wizText.setText(msg);
        wizButtons.setAlpha(0.3f);
        List<Map<String, Integer>> out = new ArrayList<>();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (!sampling) return;
                out.add(new HashMap<>(doors.bits()));
                if (out.size() < n) {
                    main.postDelayed(this, 270);
                } else {
                    sampling = false;
                    wizButtons.setAlpha(1f);
                    cb.done(out);
                }
            }
        };
        main.postDelayed(r, 300);
    }

    // ================================================================== raw data

    private void buildRaw() {
        raw = new FrameLayout(this);
        raw.setBackgroundColor(Color.BLACK);
        raw.setClickable(true);
        raw.setVisibility(View.GONE);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(16), dp(20), dp(12));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView t = Ui.text(this, Ui.bold(), 18, Ui.TEXT);
        t.setText("داده‌های خام CANBUS");
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(Ui.button(this, "بستن", false, v -> raw.setVisibility(View.GONE)));
        col.addView(head);
        TextView note = Ui.text(this, Ui.regular(), 12, Ui.TEXT_3);
        note.setText("موارد آبی همین الان تغییر کردن. یه در رو باز و بسته کن و ببین کدوم خط عوض می‌شه.");
        col.addView(note, lp(-1, -2, 4));
        ScrollView sv = new ScrollView(this);
        rawText = Ui.text(this, android.graphics.Typeface.MONOSPACE, 12, Ui.TEXT_2);
        rawText.setTextDirection(View.TEXT_DIRECTION_LTR);
        rawText.setGravity(Gravity.LEFT);
        rawText.setTextIsSelectable(true);
        sv.addView(rawText);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        raw.addView(col, new FrameLayout.LayoutParams(-1, -1));
        root.addView(raw, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openRaw() {
        raw.setVisibility(View.VISIBLE);
        renderRaw();
    }

    private void renderRaw() {
        long now = System.currentTimeMillis();
        TreeMap<String, String> sorted = new TreeMap<>();
        for (CarData.Item it : doors.items()) sorted.put(it.key, CarData.describe(it.value));
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        if (sorted.isEmpty()) {
            sb.append(doors.status() == CarData.Status.NO_SERVICE ? "car service not available" : "(no canbus.* data yet)");
        }
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            String prev = rawPrev.get(e.getKey());
            if (prev != null && !prev.equals(e.getValue())) rawChanged.put(e.getKey(), now);
            rawPrev.put(e.getKey(), e.getValue());
            Long ch = rawChanged.get(e.getKey());
            boolean hot = ch != null && now - ch < 4000;
            int start = sb.length();
            sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
            if (hot) sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.BLUE), start, sb.length(), 0);
        }
        rawText.setText(sb);
    }

    // ================================================================== helpers

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private LinearLayout.LayoutParams lp(int w, int h, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topDp);
        return p;
    }

    private LinearLayout.LayoutParams lpStart(int dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMarginStart(dp(dp));
        return p;
    }
}
