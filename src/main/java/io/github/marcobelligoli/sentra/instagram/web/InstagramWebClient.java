package io.github.marcobelligoli.sentra.instagram.web;

import io.github.marcobelligoli.sentra.instagram.web.InstagramApiException.Reason;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Minimal client of the private web API used by instagram.com: login and authenticated GET requests.
 */
@Component
class InstagramWebClient {

    static final URI INSTAGRAM = URI.create("https://www.instagram.com");

    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

    // The API accepts authenticated GET requests from the app user agent without further browser headers
    private static final String APP_USER_AGENT = "Instagram 347.3.0.41.106";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final JsonMapper json;
    private final URI baseUri;

    @Autowired
    InstagramWebClient(JsonMapper json) {
        this(json, INSTAGRAM);
    }

    InstagramWebClient(JsonMapper json, URI baseUri) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.json = json;
        this.baseUri = baseUri;
    }

    InstagramWebSession login(String username, String password) {
        HttpResponse<String> loginPage = send(request("/api/v1/web/login_page/", BROWSER_USER_AGENT).GET().build());
        String csrfToken = cookie(loginPage, "csrftoken").orElseThrow(
                () -> new InstagramApiException(Reason.UNEXPECTED_RESPONSE, "The login page returned no CSRF token"));

        Map<String, String> form = new LinkedHashMap<>();
        // Version 0 of the browser password format carries the password as is, protected only by HTTPS
        form.put("enc_password", "#PWD_INSTAGRAM_BROWSER:0:" + Instant.now().getEpochSecond() + ":" + password);
        form.put("username", username);
        form.put("optIntoOneTap", "false");
        form.put("queryParams", "{}");
        form.put("trustedDeviceRecords", "{}");
        HttpResponse<String> response = send(request("/api/v1/web/accounts/login/ajax/", BROWSER_USER_AGENT)
                .header("content-type", "application/x-www-form-urlencoded")
                .header("x-csrftoken", csrfToken)
                .header("cookie", "csrftoken=" + csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build());

        JsonNode body = parse(response);
        if (!successful(response, body)) {
            throw error(response, body);
        }
        if (!body.path("authenticated").asBoolean(false)) {
            throw new InstagramApiException(Reason.BAD_CREDENTIALS, "Instagram rejected the credentials");
        }
        String sessionId = cookie(response, "sessionid").orElseThrow(
                () -> new InstagramApiException(Reason.UNEXPECTED_RESPONSE, "The login returned no session cookie"));
        return new InstagramWebSession(sessionId, cookie(response, "csrftoken").orElse(csrfToken));
    }

    /**
     * @param path path and query string, already URL-encoded
     */
    JsonNode get(InstagramWebSession session, String path) {
        HttpResponse<String> response = send(request(path, APP_USER_AGENT)
                .header("cookie", session.cookieHeader())
                .GET()
                .build());
        if (response.statusCode() / 100 == 3) {
            // Expired sessions are redirected to the login page
            throw new InstagramApiException(Reason.LOGIN_REQUIRED, "Redirected to " + location(response));
        }
        JsonNode body = parse(response);
        if (!successful(response, body)) {
            throw error(response, body);
        }
        if (body.isMissingNode()) {
            throw new InstagramApiException(Reason.UNEXPECTED_RESPONSE,
                    "GET " + path + " returned no JSON (HTTP " + response.statusCode() + ")");
        }
        return body;
    }

    private HttpRequest.Builder request(String path, String userAgent) {
        return HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("user-agent", userAgent)
                .header("accept", "application/json")
                .header("origin", "https://www.instagram.com")
                .header("referer", "https://www.instagram.com/")
                .header("sec-fetch-site", "same-origin");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new InstagramApiException(Reason.IO, "Instagram request failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InstagramApiException(Reason.IO, "Interrupted while calling Instagram", ex);
        }
    }

    /**
     * @return the JSON body, or a missing node if the body is not JSON (Instagram answers some errors with HTML)
     */
    private JsonNode parse(HttpResponse<String> response) {
        try {
            JsonNode node = json.readTree(response.body());
            return node == null ? json.missingNode() : node;
        } catch (JacksonException ex) {
            return json.missingNode();
        }
    }

    private static boolean successful(HttpResponse<String> response, JsonNode body) {
        return response.statusCode() / 100 == 2 && !"fail".equals(body.path("status").asString(""));
    }

    private static InstagramApiException error(HttpResponse<String> response, JsonNode body) {
        int status = response.statusCode();
        String message = body.path("message").asString("");
        String description = "HTTP " + status + (message.isEmpty() ? "" : ": " + message);
        if (body.path("two_factor_required").asBoolean(false)) {
            return new InstagramApiException(Reason.TWO_FACTOR_REQUIRED, description);
        }
        if (status == 429 || message.contains("Please wait a few minutes")) {
            return new InstagramApiException(Reason.RATE_LIMITED, description);
        }
        if (message.equals("challenge_required") || message.equals("checkpoint_required")
                || body.has("checkpoint_url") || body.has("challenge")) {
            return new InstagramApiException(Reason.CHALLENGE_REQUIRED, description);
        }
        if (message.equals("login_required") || body.path("require_login").asBoolean(false) || status == 401) {
            return new InstagramApiException(Reason.LOGIN_REQUIRED, description);
        }
        if (message.contains("password you entered is incorrect")
                || message.contains("username you entered doesn't appear to belong to an account")) {
            return new InstagramApiException(Reason.BAD_CREDENTIALS, description);
        }
        return new InstagramApiException(Reason.UNEXPECTED_RESPONSE, description);
    }

    private static Optional<String> cookie(HttpResponse<String> response, String name) {
        return response.headers()
                .allValues("set-cookie")
                .stream()
                .map(header -> header.split(";", 2)[0].split("=", 2))
                .filter(pair -> pair.length == 2 && pair[0].trim().equals(name) && !pair[1].isBlank()
                        && !pair[1].equals("\"\""))
                .map(pair -> pair[1].trim())
                .reduce((first, last) -> last);
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("location").orElse("unknown location");
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet()
                .stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

}
