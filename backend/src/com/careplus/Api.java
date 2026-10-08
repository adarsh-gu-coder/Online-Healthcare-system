package com.careplus;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** REST API. Two layers: (1) generic /api/data + /api/state used by the frontend to persist its data,
 *  (2) domain endpoints with real server-side rules: login, appointments, users, analytics. */
final class Api {
    private static final Set<String> STATUSES = Set.of("Pending", "Confirmed", "Completed", "Cancelled");
    private static final Set<String> ROLES = Set.of("admin", "doctor", "patient");

    private final Store st;
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    Api(Store st) { this.st = st; }

    static final class ApiError extends RuntimeException {
        final int code;
        ApiError(int code, String msg) { super(msg); this.code = code; }
    }

    void handle(HttpExchange x) throws IOException {
        x.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        x.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        x.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        try {
            String method = x.getRequestMethod();
            if (method.equals("OPTIONS")) { x.sendResponseHeaders(204, -1); x.close(); return; }
            String[] p = x.getRequestURI().getPath().substring(5).split("/");
            Map<String, String> q = query(x.getRequestURI().getRawQuery());
            String body = method.equals("GET") || method.equals("DELETE") ? "" :
                new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            send(x, 200, route(method, p, q, body));
        } catch (Created e) {
            send(x, 201, e.getMessage());
        } catch (ApiError e) {
            send(x, e.code, error(e.code, e.getMessage()));
        } catch (IllegalArgumentException | ClassCastException e) {
            send(x, 400, error(400, e.getMessage() == null ? "Bad request" : e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            send(x, 500, error(500, "Internal server error"));
        }
    }

    private String route(String m, String[] p, Map<String, String> q, String body) {
        String r = p[0];
        if (r.equals("health") && m.equals("GET")) return Json.write(mapOf("status", "UP", "service", "CarePlus backend", "time", LocalDateTime.now().toString()));
        if (r.equals("state") && m.equals("GET")) return st.allJson();
        if (r.equals("data") && p.length == 2) {
            if (!Store.NAMES.contains(p[1])) throw new ApiError(404, "Unknown collection: " + p[1]);
            if (m.equals("GET")) return st.json(p[1]);
            if (m.equals("PUT")) { st.put(p[1], Json.parse(body)); return Json.write(mapOf("saved", p[1])); }
        }
        if (r.equals("login") && m.equals("POST")) return login(obj(Json.parse(body)));
        if (r.equals("appointments")) return appointments(m, p, q, body);
        if (r.equals("users")) return users(m, p, q, body);
        if (r.equals("analytics") && m.equals("GET")) return analytics();
        throw new ApiError(404, "No such endpoint: " + m + " /api/" + String.join("/", p));
    }

    /* ---------- auth (simulated: any non-empty credentials work, as in the frontend prototype) ---------- */
    private String login(Map<String, Object> b) {
        String id = str(b, "userId"), pw = str(b, "password"), role = str(b, "role");
        if (id.isEmpty() || pw.isEmpty()) throw new ApiError(400, "User ID and password are required");
        if (!ROLES.contains(role)) throw new ApiError(400, "Role must be admin, doctor or patient");
        String token = UUID.randomUUID().toString();
        tokens.put(token, role + ":" + id);
        return Json.write(mapOf("token", token, "userId", id, "role", role, "name", displayName(id),
            "note", "Prototype: authentication is simulated"));
    }

    /* ---------- appointments ---------- */
    private String appointments(String m, String[] p, Map<String, String> q, String body) {
        synchronized (st) {
            List<Map<String, Object>> all = st.list("appointments");
            if (m.equals("GET") && p.length == 1) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Map<String, Object> a : all) {
                    if (q.containsKey("status") && !q.get("status").equalsIgnoreCase(str(a, "status"))) continue;
                    if (q.containsKey("doctorId") && !q.get("doctorId").equals(str(a, "doctorId"))) continue;
                    if (q.containsKey("patientId") && !q.get("patientId").equals(str(a, "patientId"))) continue;
                    out.add(a);
                }
                return Json.write(out);
            }
            if (m.equals("POST") && p.length == 1) {
                Map<String, Object> in = obj(Json.parse(body));
                for (String f : new String[]{"patientId", "patientName", "doctorId", "doctorName", "dept", "date", "time", "reason"})
                    if (str(in, f).isEmpty()) throw new ApiError(400, "Missing field: " + f);
                LocalDate date;
                try { date = LocalDate.parse(str(in, "date")); LocalTime.parse(str(in, "time")); }
                catch (Exception e) { throw new ApiError(400, "Date must be yyyy-MM-dd and time HH:mm"); }
                if (date.isBefore(LocalDate.now())) throw new ApiError(400, "Appointment date cannot be in the past");
                for (Map<String, Object> a : all)
                    if (str(a, "doctorId").equals(str(in, "doctorId")) && str(a, "date").equals(str(in, "date"))
                        && str(a, "time").equals(str(in, "time")) && !str(a, "status").equals("Cancelled"))
                        throw new ApiError(409, "That slot is already booked for this doctor");
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("id", "ap" + UUID.randomUUID().toString().substring(0, 7));
                for (String f : new String[]{"patientId", "patientName", "doctorId", "doctorName", "dept", "date", "time"}) a.put(f, str(in, f));
                a.put("type", str(in, "type").isEmpty() ? "In-person" : str(in, "type"));
                a.put("reason", str(in, "reason"));
                a.put("notes", str(in, "notes"));
                a.put("diagnosis", "");
                a.put("rx", "");
                a.put("status", "Pending");
                List<Map<String, Object>> next = new ArrayList<>(all);
                next.add(a);
                st.put("appointments", next);
                notify("doctor", "New appointment request from " + a.get("patientName") + " on " + a.get("date") + " " + a.get("time") + ".", null);
                notify("admin", a.get("patientName") + " booked " + a.get("doctorName") + " on " + a.get("date") + ".", null);
                throw new Created(Json.write(a));
            }
            if (m.equals("PUT") && p.length == 3 && p[2].equals("status")) {
                String status = str(obj(Json.parse(body)), "status");
                if (!STATUSES.contains(status)) throw new ApiError(400, "Status must be one of " + new TreeSet<>(STATUSES));
                for (Map<String, Object> a : all) {
                    if (!str(a, "id").equals(p[1])) continue;
                    String cur = str(a, "status");
                    if (cur.equals("Completed") || cur.equals("Cancelled")) throw new ApiError(409, "A " + cur + " appointment cannot be changed");
                    if (status.equals("Completed") && !cur.equals("Confirmed")) throw new ApiError(409, "Only a Confirmed appointment can be completed");
                    a.put("status", status);
                    st.put("appointments", new ArrayList<>(all));
                    notify("patient", "Your " + a.get("dept") + " appointment on " + a.get("date") + " is now " + status + ".", str(a, "patientId"));
                    return Json.write(a);
                }
                throw new ApiError(404, "Appointment not found: " + p[1]);
            }
        }
        throw new ApiError(404, "No such appointment endpoint");
    }

    /* ---------- users ---------- */
    private String users(String m, String[] p, Map<String, String> q, String body) {
        synchronized (st) {
            List<Map<String, Object>> all = st.list("users");
            if (m.equals("GET")) {
                List<Map<String, Object>> out = new ArrayList<>();
                String role = q.getOrDefault("role", ""), s = q.getOrDefault("q", "").toLowerCase();
                for (Map<String, Object> u : all) {
                    if (!role.isEmpty() && !role.equals(str(u, "role"))) continue;
                    if (!s.isEmpty() && !(str(u, "name") + str(u, "email")).toLowerCase().contains(s)) continue;
                    out.add(u);
                }
                return Json.write(out);
            }
            if (m.equals("POST")) {
                Map<String, Object> in = obj(Json.parse(body));
                if (str(in, "name").isEmpty()) throw new ApiError(400, "Name is required");
                if (!str(in, "email").matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new ApiError(400, "A valid email is required");
                if (!ROLES.contains(str(in, "role"))) throw new ApiError(400, "Role must be admin, doctor or patient");
                for (Map<String, Object> u : all)
                    if (str(u, "email").equalsIgnoreCase(str(in, "email"))) throw new ApiError(409, "Email already registered");
                Map<String, Object> u = new LinkedHashMap<>(in);
                u.remove("password");
                u.put("id", "u_" + UUID.randomUUID().toString().substring(0, 7));
                u.put("status", str(in, "status").isEmpty() ? "Active" : str(in, "status"));
                u.put("reg", LocalDate.now().toString());
                List<Map<String, Object>> next = new ArrayList<>(all);
                next.add(u);
                st.put("users", next);
                notify("admin", "New user added: " + u.get("name"), null);
                throw new Created(Json.write(u));
            }
            if (m.equals("DELETE") && p.length == 2) {
                List<Map<String, Object>> next = new ArrayList<>(all);
                if (!next.removeIf(u -> str(u, "id").equals(p[1]))) throw new ApiError(404, "User not found: " + p[1]);
                st.put("users", next);
                return Json.write(mapOf("deleted", p[1]));
            }
        }
        throw new ApiError(404, "No such user endpoint");
    }

    /* ---------- analytics (computed on the server from stored data) ---------- */
    private String analytics() {
        synchronized (st) {
            List<Map<String, Object>> ap = st.list("appointments"), us = st.list("users"), fb = st.list("feedback");
            Map<String, Object> out = new LinkedHashMap<>();
            Map<String, Integer> byStatus = count(ap, "status");
            out.put("totalAppointments", ap.size());
            out.put("byStatus", byStatus);
            out.put("byDepartment", count(ap, "dept"));
            out.put("byDoctor", count(ap, "doctorName"));
            out.put("usersByRole", count(us, "role"));
            int n = Math.max(1, ap.size());
            out.put("completionRatePct", Math.round(100.0 * byStatus.getOrDefault("Completed", 0) / n));
            out.put("cancellationRatePct", Math.round(100.0 * byStatus.getOrDefault("Cancelled", 0) / n));
            double sum = 0;
            for (Map<String, Object> f : fb) sum += ((Number) f.get("rating")).doubleValue();
            out.put("averageRating", fb.isEmpty() ? 0.0 : Math.round(sum / fb.size() * 10) / 10.0);
            return Json.write(out);
        }
    }

    /* ---------- helpers ---------- */
    private static final class Created extends RuntimeException {
        Created(String json) { super(json, null, false, false); }
    }

    private void notify(String role, String text, String to) {
        List<Map<String, Object>> next = new ArrayList<>(st.list("notifications"));
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", UUID.randomUUID().toString().substring(0, 7));
        n.put("role", role);
        n.put("text", text);
        if (to != null) n.put("to", to);
        n.put("time", LocalDateTime.now().toString());
        n.put("read", Boolean.FALSE);
        next.add(0, n);
        st.put("notifications", next);
    }

    private static Map<String, Integer> count(List<Map<String, Object>> l, String key) {
        Map<String, Integer> m = new TreeMap<>();
        for (Map<String, Object> o : l) m.merge(str(o, key), 1, Integer::sum);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) {
        if (!(o instanceof Map)) throw new IllegalArgumentException("JSON object expected");
        return (Map<String, Object>) o;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            String[] a = kv.split("=", 2);
            m.put(URLDecoder.decode(a[0], StandardCharsets.UTF_8), a.length > 1 ? URLDecoder.decode(a[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }

    static String displayName(String id) {
        String base = id.contains("@") ? id.substring(0, id.indexOf('@')) : id;
        String[] parts = base.replaceAll("[._-]+", " ").trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String w : parts) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.length() == 0 ? "User" : b.toString().trim();
    }

    private static String error(int code, String msg) { return Json.write(mapOf("error", msg, "status", code)); }

    private void send(HttpExchange x, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.sendResponseHeaders(code, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }
}
