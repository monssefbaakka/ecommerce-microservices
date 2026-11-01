package com.monssef.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A fake downstream service on a free port, with the HTTP server of the JDK (no extra dependency).
 * It answers every request with what it received, so a test sees where the gateway sent it:
 * {"service": "auth", "method": "POST", "path": "/api/auth/login", "authorization": "...", "body": "..."}.
 * A "status" query parameter sets the response status, e.g. /api/cart?status=401.
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
        String query = exchange.getRequestURI().getQuery();
        int status = query != null && query.startsWith("status=") ? Integer.parseInt(query.substring(7)) : 200;
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

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
