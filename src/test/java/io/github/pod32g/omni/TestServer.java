package io.github.pod32g.omni;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A local HTTP server for tests, built on the JDK's own com.sun.net.httpserver (the Java cousin of
 * Go's httptest.Server). Close it with try-with-resources.
 */
final class TestServer implements AutoCloseable {
    private final HttpServer server;

    private TestServer(HttpHandler handler) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", handler);
        server.start();
    }

    /** A server whose handler does anything. */
    static TestServer handling(HttpHandler handler) {
        return new TestServer(handler);
    }

    /** Answers each exact path in {@code routes} with its JSON body, and anything else with 404. */
    static TestServer withRoutes(Map<String, String> routes) {
        return new TestServer(exchange -> {
            String body = routes.get(exchange.getRequestURI().getPath());
            if (body == null) {
                respond(exchange, 404, "404 page not found");
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            respond(exchange, 200, body);
        });
    }

    /** Answers every request with this status and body. */
    static TestServer replying(int status, String body) {
        return new TestServer(exchange -> respond(exchange, status, body));
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    OmniClient client() {
        return OmniClient.builder(url()).build();
    }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
