package ir.maniimani.dooralert;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Door state engine: polls the head unit's CANBUS data, maps learned bits to the six
 * openings and tells listeners when anything changes. One instance per process.
 */
final class Doors {

    static final int FL = 0, FR = 1, RL = 2, RR = 3, TRUNK = 4, HOOD = 5, COUNT = 6;
    static final String[] NAMES = {"در جلو چپ", "در جلو راست", "در عقب چپ", "در عقب راست", "صندوق عقب", "کاپوت"};

    interface Listener {
        void onDoorsChanged(boolean[] open, boolean calibrated);
    }

    /** One learned signal: a single bit of a CANBUS value. */
    static final class Mapping {
        final String bitKey;
        final int openValue;

        Mapping(String bitKey, int openValue) {
            this.bitKey = bitKey;
            this.openValue = openValue;
        }
    }

    private static Doors sInstance;

    static synchronized Doors get(Context c) {
        if (sInstance == null) sInstance = new Doors(c.getApplicationContext());
        return sInstance;
    }

    private final Context ctx;
    private final CarData car = new CarData();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Mapping[] map = new Mapping[COUNT];
    private HandlerThread thread;
    private Handler worker;
    private int pollers;

    private volatile boolean[] open = new boolean[COUNT];
    private volatile List<CarData.Item> lastItems = new ArrayList<>();
    private volatile Map<String, Integer> lastBits = new HashMap<>();
    private boolean[] demo;          // non-null while a demo is running
    private volatile int fullScanUsers; // setup / raw view need every key, not just the learned ones

    private static final String[] FALLBACK_PREFIXES = {"canbus.", "state.", "mcu.", "car.", "data.", "info.", "vehicle."};

    private Doors(Context c) {
        ctx = c;
        load();
    }

    // ================================================================== polling

    synchronized void startPolling() {
        if (pollers++ > 0) return;
        thread = new HandlerThread("doors");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(poll);
    }

    synchronized void stopPolling() {
        if (--pollers > 0) return;
        pollers = 0;
        if (thread != null) thread.quitSafely();
        thread = null;
        worker = null;
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            List<CarData.Item> items = read();
            Map<String, Integer> bits = new HashMap<>();
            for (CarData.Item it : items) CarData.flatten(it.key, it.value, bits);
            lastItems = items;
            lastBits = bits;
            boolean[] now = detect(bits);
            main.post(() -> publish(now));
            Handler w = worker;
            if (w != null) w.postDelayed(this, fullScanUsers > 0 || !isCalibrated() ? 400 : 250);
        }
    };

    /** Everything while setting up; afterwards only the handful of keys that matter. */
    private List<CarData.Item> read() {
        if (fullScanUsers == 0 && isCalibrated()) {
            Set<String> keys = new HashSet<>();
            for (Mapping m : map) if (m != null) keys.add(baseKey(m.bitKey));
            return car.byFilter(keys.toArray(new String[0]));
        }
        List<CarData.Item> all = car.byPrefix("");
        if (car.lastStatus == CarData.Status.ERROR || all.isEmpty()) {
            // reply too large or empty: ask in smaller pieces
            List<CarData.Item> merged = new ArrayList<>();
            for (String p : FALLBACK_PREFIXES) merged.addAll(car.byPrefix(p));
            return merged;
        }
        return all;
    }

    /** "canbus.state.door_data[2]#5" → "canbus.state.door_data" */
    static String baseKey(String bitKey) {
        int cut = bitKey.length();
        for (char ch : new char[]{'#', '[', '/', '\''}) {
            int i = bitKey.indexOf(ch);
            if (i >= 0 && i < cut) cut = i;
        }
        return bitKey.substring(0, cut);
    }

    void beginFullScan() {
        fullScanUsers++;
    }

    void endFullScan() {
        if (fullScanUsers > 0) fullScanUsers--;
    }

    private boolean[] detect(Map<String, Integer> bits) {
        boolean[] out = new boolean[COUNT];
        for (int i = 0; i < COUNT; i++) {
            Mapping m = map[i];
            if (m == null) continue;
            Integer v = bits.get(m.bitKey);
            out[i] = v != null && v == m.openValue;
        }
        return out;
    }

    private void publish(boolean[] now) {
        if (demo != null) return;
        if (java.util.Arrays.equals(now, open)) {
            for (Listener l : listeners) l.onDoorsChanged(open, isCalibrated());
            return;
        }
        open = now;
        for (Listener l : listeners) l.onDoorsChanged(open, isCalibrated());
    }

    // ================================================================== public state

    void addListener(Listener l) {
        listeners.add(l);
        l.onDoorsChanged(demo != null ? demo : open, isCalibrated());
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    boolean[] open() {
        return (demo != null ? demo : open).clone();
    }

    static boolean any(boolean[] o) {
        for (boolean b : o) if (b) return true;
        return false;
    }

    CarData.Status status() {
        return car.lastStatus;
    }

    List<CarData.Item> items() {
        return lastItems;
    }

    Map<String, Integer> bits() {
        return lastBits;
    }

    boolean isCalibrated() {
        for (Mapping m : map) if (m != null) return true;
        return false;
    }

    boolean isMapped(int part) {
        return map[part] != null;
    }

    /** Demo: drive the UI with a fake state (null to stop). */
    void setDemo(boolean[] fake) {
        demo = fake == null ? null : fake.clone();
        boolean[] s = demo != null ? demo : open;
        for (Listener l : listeners) l.onDoorsChanged(s, isCalibrated() || demo != null);
    }

    // ================================================================== calibration

    void setMapping(int part, Mapping m) {
        map[part] = m;
        save();
    }

    void clearMappings() {
        for (int i = 0; i < COUNT; i++) map[i] = null;
        save();
    }

    /**
     * Learns which bit belongs to a part.
     * @param baseline bits with everything closed
     * @param volatileBits bits that changed on their own while everything was closed (noise)
     * @param samples   several snapshots taken with only this part open
     * @return the mapping, or null if nothing clearly changed
     */
    Mapping learn(Map<String, Integer> baseline, Set<String> volatileBits, List<Map<String, Integer>> samples) {
        if (samples.isEmpty()) return null;
        Set<String> used = new HashSet<>();
        for (Mapping m : map) if (m != null) used.add(m.bitKey);
        List<String> candidates = new ArrayList<>();
        Map<String, Integer> first = samples.get(0);
        for (Map.Entry<String, Integer> e : first.entrySet()) {
            String k = e.getKey();
            Integer base = baseline.get(k);
            if (base == null || base.equals(e.getValue())) continue;
            if (volatileBits.contains(k) || used.contains(k)) continue;
            boolean stable = true;
            for (Map<String, Integer> s : samples) {
                if (!e.getValue().equals(s.get(k))) {
                    stable = false;
                    break;
                }
            }
            if (stable) candidates.add(k);
        }
        if (candidates.isEmpty()) return null;
        // prefer anything that's literally called "door", then the shortest/lowest key
        java.util.Collections.sort(candidates, (a, b) -> {
            boolean da = a.toLowerCase().contains("door"), db = b.toLowerCase().contains("door");
            if (da != db) return da ? -1 : 1;
            return a.compareTo(b);
        });
        String k = candidates.get(0);
        return new Mapping(k, first.get(k));
    }

    private void save() {
        try {
            JSONObject o = new JSONObject();
            for (int i = 0; i < COUNT; i++) {
                if (map[i] == null) continue;
                JSONObject m = new JSONObject();
                m.put("k", map[i].bitKey);
                m.put("v", map[i].openValue);
                o.put(String.valueOf(i), m);
            }
            prefs().edit().putString("map", o.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    private void load() {
        try {
            JSONObject o = new JSONObject(prefs().getString("map", "{}"));
            for (int i = 0; i < COUNT; i++) {
                JSONObject m = o.optJSONObject(String.valueOf(i));
                if (m != null) map[i] = new Mapping(m.getString("k"), m.getInt("v"));
            }
        } catch (Throwable ignored) {
        }
    }

    SharedPreferences prefs() {
        return ctx.getSharedPreferences("doors", Context.MODE_PRIVATE);
    }
}
