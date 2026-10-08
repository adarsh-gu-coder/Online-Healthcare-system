package com.careplus;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.Executors;

/**
 * CarePlus backend prototype - pure JDK (com.sun.net.httpserver), no Maven/Gradle/Spring needed.
 * Usage: java -cp out com.careplus.Main [port] [frontendDir]
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path web = Paths.get(args.length > 1 ? args[1] : "../frontend").toAbsolutePath().normalize();
        Store store = new Store(Paths.get("data"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", new Api(store)::handle);
        server.createContext("/", new StaticFiles(web)::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("CarePlus backend running -> http://localhost:" + port);
        System.out.println("Serving frontend from   -> " + web);
        System.out.println("Data stored in          -> " + Paths.get("data").toAbsolutePath());
    }
}
