package ir.maniimani.dooralert;

final class Text {
    private Text() {
    }

    static String fa(long n) {
        String s = String.valueOf(n);
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        return b.toString();
    }
}
