package com.careplus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;

/** Thread-safe in-memory store that persists every collection as data/<name>.json (a tiny file "database"). */
final class Store {
    static final Set<String> NAMES = Set.of("users", "appointments", "records", "feedback", "notifications", "settings", "schedules");

    private final Path dir;
    private final Map<String, Object> data = new LinkedHashMap<>();

    Store(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
        for (String n : NAMES) {
            Path p = dir.resolve(n + ".json");
            if (!Files.exists(p)) continue;
            try { data.put(n, Json.parse(Files.readString(p))); }
            catch (Exception e) { System.err.println("Skipping unreadable file " + p + ": " + e.getMessage()); }
        }
    }

    synchronized String allJson() { return Json.write(data); }
    synchronized String json(String name) { return Json.write(data.get(name)); }

    synchronized void put(String name, Object value) {
        data.put(name, value);
        try { Files.writeString(dir.resolve(name + ".json"), Json.write(value)); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @SuppressWarnings("unchecked")
    synchronized List<Map<String, Object>> list(String name) {
        Object o = data.get(name);
        return o instanceof List ? (List<Map<String, Object>>) o : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    synchronized Map<String, Object> map(String name) {
        Object o = data.get(name);
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }
}
