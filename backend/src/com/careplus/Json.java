package com.careplus;

import java.util.*;

/** Minimal JSON parser/writer (no external libraries). Objects -> Map, arrays -> List, numbers -> Long/Double. */
final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        Object v = j.val();
        j.ws();
        if (j.i < text.length()) throw j.err();
        return v;
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
    private boolean take(char c) { if (peek(c)) { i++; return true; } return false; }
    private void expect(char c) { if (!take(c)) throw err(); }
    private IllegalArgumentException err() { return new IllegalArgumentException("Invalid JSON near position " + i); }

    private Object val() {
        if (i >= s.length()) throw err();
        char c = s.charAt(i);
        if (c == '{') {
            i++;
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (take('}')) return m;
            do { ws(); String k = str(); ws(); expect(':'); ws(); m.put(k, val()); ws(); } while (take(','));
            expect('}');
            return m;
        }
        if (c == '[') {
            i++;
            List<Object> l = new ArrayList<>();
            ws();
            if (take(']')) return l;
            do { ws(); l.add(val()); ws(); } while (take(','));
            expect(']');
            return l;
        }
        if (c == '"') return str();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return num();
    }

    private String str() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw err();
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c != '\\') { b.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                default: b.append(e);
            }
        }
        return b.toString();
    }

    private Object num() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw err();
        String t = s.substring(st, i);
        return t.matches("-?\\d+") ? (Object) Long.valueOf(t) : (Object) Double.valueOf(t);
    }

    static String write(Object o) { StringBuilder b = new StringBuilder(); w(o, b); return b.toString(); }

    private static void w(Object o, StringBuilder b) {
        if (o == null) b.append("null");
        else if (o instanceof String) q((String) o, b);
        else if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                q(String.valueOf(e.getKey()), b);
                b.append(':');
                w(e.getValue(), b);
            }
            b.append('}');
        } else if (o instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object e : (List<?>) o) { if (!first) b.append(','); first = false; w(e, b); }
            b.append(']');
        } else if (o instanceof Double) {
            double d = (Double) o;
            if (d == Math.rint(d) && !Double.isInfinite(d)) b.append((long) d); else b.append(d);
        } else b.append(o);
    }

    private static void q(String s, StringBuilder b) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        b.append('"');
    }
}
