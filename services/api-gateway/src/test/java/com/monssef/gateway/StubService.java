package com.monssef.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * A fake downstream service on a free port, with the HTTP server of the JDK (no extra dependency).
 * It answers every request with what it received, so a test sees where the gateway sent it:
 * {"service": "auth", "method": "POST", "path": "/api/auth/login", "authorization": "...", "body": "..."}.
 * A "status" query parameter sets the response status, e.g. /api/cart?status=401,
 * a "delay" one makes it wait before answering, in ms, e.g. /api/products?delay=2000.
 */
class StubService implements AutoCloseable {

    private final String name;
    private final HttpServer server;

    StubService(String name) {
        this.name = name;
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the " + name + " stub", e);
        }
        server.createContext("/", this::echo);
        // One thread per request: a delayed answer must not hold the requests behind it
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void echo(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> params = paramsOf(exchange.getRequestURI().getQuery());
        int status = Integer.parseInt(params.getOrDefault("status", "200"));
        pause(Long.parseLong(params.getOrDefault("delay", "0")));
        String json = """
                {"service": "%s", "method": "%s", "path": "%s", "authorization": "%s", "body": "%s"}"""
                .formatted(name, exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        valueOrEmpty(exchange.getRequestHeaders().getFirst("Authorization")),
                        body.replace("\"", "\\\""));
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // "status=401&delay=2000" -> {status=401, delay=2000}
    private static Map<String, String> paramsOf(String query) {
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        return Arrays.stream(query.split("&"))
                .map(param -> param.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair.length > 1 ? pair[1] : ""));
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
