package io.github.marcobelligoli.sentra.instagram.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Local HTTP server answering like the Instagram web API, with responses chosen per test.
 */
class FakeInstagram implements AutoCloseable {

    record Request(String method, String pathAndQuery, Map<String, List<String>> headers, String body) {

        String header(String name) {
            return headers.entrySet()
                    .stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }

    }

    record Response(int status, String body, Map<String, List<String>> headers) {

        static Response json(int status, String body, String... setCookies) {
            return new Response(status, body, setCookies.length == 0 ? Map.of()
                    : Map.of("Set-Cookie", List.of(setCookies)));
        }

    }

    private final HttpServer server;
    private final Map<String, Function<Request, Response>> routes = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    FakeInstagram() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /**
     * @param pathAndQuery exact path and query string to answer, e.g. {@code /api/v1/users/mario/usernameinfo/}
     */
    FakeInstagram on(String pathAndQuery, Function<Request, Response> handler) {
        routes.put(pathAndQuery, handler);
        return this;
    }

    FakeInstagram on(String pathAndQuery, Response response) {
        return on(pathAndQuery, request -> response);
    }

    List<Request> requests() {
        return requests;
    }

    List<Request> requests(String pathAndQuery) {
        return requests.stream().filter(r -> r.pathAndQuery().equals(pathAndQuery)).toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String pathAndQuery = exchange.getRequestURI().getRawPath()
                + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
        Request request = new Request(exchange.getRequestMethod(), pathAndQuery, Map.copyOf(exchange.getRequestHeaders()),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        requests.add(request);
        Function<Request, Response> route = routes.get(pathAndQuery);
        Response response = route != null ? route.apply(request)
                : Response.json(404, "{\"message\":\"no route for " + pathAndQuery + "\",\"status\":\"fail\"}");
        response.headers().forEach((name, values) -> values.forEach(v -> exchange.getResponseHeaders().add(name, v)));
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

}
