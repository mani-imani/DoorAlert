package ir.maniimani.dooralert;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads every value under a prefix (e.g. "canbus.") from the head unit's vendor "car"
 * service, with its real type. The reply is parsed straight off the binder Parcel, so no
 * vendor classes are needed.
 *
 * Wire format (android.car.server.ICarService, transaction 5 = requestSync):
 *   in : token, what, int, int, String, [1, Bundle{arg_string=prefix}], int
 *   out: exception, int result, [1, Bundle{ data_list = DataItem[] , ... }]
 *   DataItem = String key, int type (CarDataManager.DataType ordinal), value
 */
final class CarData {

    static final class Item {
        final String key;
        final int type;
        final Object value;

        Item(String key, int type, Object value) {
            this.key = key;
            this.type = type;
            this.value = value;
        }
    }

    enum Status {OK, NO_SERVICE, ERROR}

    private static final String DESCRIPTOR = "android.car.server.ICarService";
    private static final int VAL_PARCELABLEARRAY = 16;

    private IBinder binder;
    volatile Status lastStatus = Status.OK;

    private synchronized boolean connect() {
        if (binder != null && binder.isBinderAlive()) return true;
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            binder = (IBinder) sm.getMethod("getService", String.class).invoke(null, "car");
        } catch (Throwable t) {
            binder = null;
        }
        return binder != null;
    }

    List<Item> byPrefix(String prefix) {
        if (!connect()) {
            lastStatus = Status.NO_SERVICE;
            return Collections.emptyList();
        }
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(DESCRIPTOR);
            d.writeString("request.dm.get_value_by_prefix");
            d.writeInt(0);
            d.writeInt(0);
            d.writeString(null);
            Bundle b = new Bundle();
            b.putString("arg_string", prefix);
            d.writeInt(1);
            b.writeToParcel(d, 0);
            d.writeInt(0);
            binder.transact(5, d, r, 0);
            r.readException();
            r.readInt(); // result count
            if (r.readInt() == 0) {
                lastStatus = Status.OK;
                return Collections.emptyList();
            }
            List<Item> out = parseBundle(r);
            lastStatus = Status.OK;
            return out;
        } catch (Throwable t) {
            lastStatus = Status.ERROR;
            return Collections.emptyList();
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    private List<Item> parseBundle(Parcel r) {
        List<Item> out = new ArrayList<>();
        int length = r.readInt();
        if (length <= 0) return out;
        int start = r.dataPosition();
        r.readInt(); // magic
        int n = r.readInt();
        for (int i = 0; i < n; i++) {
            String key = r.readString();
            int type = r.readInt();
            if ("data_list".equals(key) && type == VAL_PARCELABLEARRAY) {
                int count = r.readInt();
                for (int j = 0; j < count; j++) {
                    String cls = r.readString();
                    if (cls == null) continue;
                    Item it = readItem(r);
                    if (it == null) return out; // unknown value type: stop safely
                    out.add(it);
                }
            } else if (!skipValue(r, type)) {
                break;
            }
        }
        r.setDataPosition(start + length);
        return out;
    }

    private static Item readItem(Parcel r) {
        String key = r.readString();
        int type = r.readInt();
        Object v;
        switch (type) {
            case 0: v = r.readString(); break;                      // UNKNOWN
            case 1: v = null; break;                                // NULL
            case 2: v = r.readInt(); break;                         // INT
            case 3: v = r.readLong(); break;                        // LONG
            case 4: v = r.readFloat(); break;                       // FLOAT
            case 5: v = r.readDouble(); break;                      // DOUBLE
            case 6: v = r.readString(); break;                      // STRING
            case 7: v = r.readBundle(CarData.class.getClassLoader()); break; // BUNDLE
            case 9: v = r.createBooleanArray(); break;
            case 10: v = r.createByteArray(); break;
            case 11: v = r.createIntArray(); break;
            case 12: v = r.createLongArray(); break;
            case 13: v = r.createFloatArray(); break;
            case 14: v = r.createDoubleArray(); break;
            case 15: v = r.createStringArray(); break;
            default: return null;                                   // PARCELABLE(_ARRAY): can't decode
        }
        return new Item(key, type, v);
    }

    /** Skips one Parcel.writeValue() entry of a known type. */
    private static boolean skipValue(Parcel r, int type) {
        switch (type) {
            case -1: return true;                       // null
            case 0: r.readString(); return true;        // String
            case 1: case 5: case 9: r.readInt(); return true; // int / short / boolean
            case 6: r.readLong(); return true;
            case 7: r.readFloat(); return true;
            case 8: r.readDouble(); return true;
            case 3: r.readBundle(CarData.class.getClassLoader()); return true;
            case 13: r.createByteArray(); return true;
            case 14: r.createStringArray(); return true;
            case 18: r.createIntArray(); return true;
            case 19: r.createLongArray(); return true;
            case 23: r.createBooleanArray(); return true;
            default: return false;
        }
    }

    // ------------------------------------------------------------------ display / flatten helpers

    static String describe(Object v) {
        if (v == null) return "null";
        if (v instanceof byte[]) {
            StringBuilder b = new StringBuilder("[");
            for (byte x : (byte[]) v) b.append(String.format("%02X ", x & 0xFF));
            return b.toString().trim() + "]";
        }
        if (v instanceof int[]) return java.util.Arrays.toString((int[]) v);
        if (v instanceof long[]) return java.util.Arrays.toString((long[]) v);
        if (v instanceof boolean[]) return java.util.Arrays.toString((boolean[]) v);
        if (v instanceof float[]) return java.util.Arrays.toString((float[]) v);
        if (v instanceof double[]) return java.util.Arrays.toString((double[]) v);
        if (v instanceof String[]) return java.util.Arrays.toString((String[]) v);
        if (v instanceof Integer) return v + "  (0x" + Integer.toHexString((Integer) v) + ")";
        if (v instanceof Bundle) {
            Bundle b = (Bundle) v;
            StringBuilder s = new StringBuilder("{");
            try {
                for (String k : new java.util.TreeSet<>(b.keySet())) s.append(k).append('=').append(describe(b.get(k))).append(", ");
            } catch (Throwable t) {
                s.append("…");
            }
            return s.append('}').toString();
        }
        return String.valueOf(v);
    }

    /** Turns any value into individual bits: "key#bit" → 0/1, so any door format can be learned. */
    static void flatten(String key, Object v, java.util.Map<String, Integer> out) {
        if (v == null) return;
        if (v instanceof Integer) bits(key, (Integer) v, 32, out);
        else if (v instanceof Long) bits(key, (Long) v, 64, out);
        else if (v instanceof Boolean) out.put(key + "#0", (Boolean) v ? 1 : 0);
        else if (v instanceof Float) bits(key, Float.floatToIntBits((Float) v), 32, out);
        else if (v instanceof Double) bits(key, Double.doubleToLongBits((Double) v), 64, out);
        else if (v instanceof byte[]) {
            byte[] a = (byte[]) v;
            for (int i = 0; i < a.length && i < 64; i++) bits(key + "[" + i + "]", a[i] & 0xFF, 8, out);
        } else if (v instanceof int[]) {
            int[] a = (int[]) v;
            for (int i = 0; i < a.length && i < 32; i++) bits(key + "[" + i + "]", a[i], 32, out);
        } else if (v instanceof long[]) {
            long[] a = (long[]) v;
            for (int i = 0; i < a.length && i < 16; i++) bits(key + "[" + i + "]", a[i], 64, out);
        } else if (v instanceof boolean[]) {
            boolean[] a = (boolean[]) v;
            for (int i = 0; i < a.length && i < 64; i++) out.put(key + "[" + i + "]#0", a[i] ? 1 : 0);
        } else if (v instanceof String) {
            String s = (String) v;
            for (int i = 0; i < s.length() && i < 32; i++) bits(key + "'" + i, s.charAt(i), 16, out);
        } else if (v instanceof String[]) {
            String[] a = (String[]) v;
            for (int i = 0; i < a.length && i < 16; i++) flatten(key + "[" + i + "]", a[i], out);
        } else if (v instanceof Bundle) {
            Bundle b = (Bundle) v;
            try {
                for (String k : b.keySet()) flatten(key + "/" + k, b.get(k), out);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void bits(String key, long v, int n, java.util.Map<String, Integer> out) {
        for (int i = 0; i < n; i++) out.put(key + "#" + i, (int) ((v >>> i) & 1L));
    }
}
