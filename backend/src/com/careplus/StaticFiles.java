package com.careplus;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/** Serves the frontend folder so the whole app is available at http://localhost:8080 */
final class StaticFiles {
    private static final Map<String, String> TYPES = Map.of(
        "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8", "js", "application/javascript; charset=utf-8",
        "json", "application/json", "png", "image/png", "jpg", "image/jpeg", "svg", "image/svg+xml", "ico", "image/x-icon");
    private final Path root;

    StaticFiles(Path root) { this.root = root; }

    void handle(HttpExchange x) throws IOException {
        String path = x.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = root.resolve(path.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            byte[] msg = "404 Not Found".getBytes();
            x.sendResponseHeaders(404, msg.length);
            x.getResponseBody().write(msg);
            x.close();
            return;
        }
        String name = file.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        byte[] bytes = Files.readAllBytes(file);
        x.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
        x.getResponseHeaders().set("Cache-Control", "no-cache");
        x.sendResponseHeaders(200, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }
}
