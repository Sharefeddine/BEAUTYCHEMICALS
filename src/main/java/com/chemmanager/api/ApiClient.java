package com.chemmanager.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.chemmanager.dto.ApiError;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Thin, dependency-light HTTP client for the PHP API.
 *
 * <p>Responsibilities: build URIs, attach the bearer token, serialise/deserialise JSON,
 * translate transport errors and API error envelopes into {@link ApiException} and expose
 * a cheap {@link #ping()} used by auto-detection of backend availability.</p>
 */
public final class ApiClient {

    private final String baseUrl;
    private final Duration timeout;
    private final Supplier<String> tokenSupplier;
    private final HttpClient http;

    public ApiClient(String baseUrl, Duration timeout, Supplier<String> tokenSupplier) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        this.tokenSupplier = tokenSupplier == null ? () -> null : tokenSupplier;
        this.http = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public boolean ping() {
        try {
            HttpRequest request = requestBuilder(PING, Map.of()).GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public <T> T get(String path, Class<T> type) {
        return send(requestBuilder(path, Map.of()).GET(), type, path);
    }

    public <T> T get(String path, Map<String, String> query, Class<T> type) {
        return send(requestBuilder(path, query).GET(), type, path);
    }

    public <T> T get(String path, Map<String, String> query, TypeReference<T> type) {
        return sendTyped(requestBuilder(path, query).GET(), type, path);
    }

    public <T> T post(String path, Object payload, Class<T> type) {
        HttpRequest.BodyPublisher body = payload == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(writeJson(payload), StandardCharsets.UTF_8);
        return send(requestBuilder(path, Map.of())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(body), type, path);
    }

    public void postVoid(String path, Object payload) {
        post(path, payload, Void.class);
    }

    public <T> T put(String path, Object payload, Class<T> type) {
        HttpRequest.BodyPublisher body = payload == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(writeJson(payload), StandardCharsets.UTF_8);
        return send(requestBuilder(path, Map.of())
                .header("Content-Type", "application/json; charset=utf-8")
                .PUT(body), type, path);
    }

    public <T> T post(String path, Object payload, TypeReference<T> type) {
        HttpRequest.BodyPublisher body = payload == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(writeJson(payload), StandardCharsets.UTF_8);
        return sendTyped(requestBuilder(path, Map.of())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(body), type, path);
    }

    public JsonNode getTree(String path, Map<String, String> query) {
        return sendTree(requestBuilder(path, query).GET(), path);
    }

    private HttpRequest.Builder requestBuilder(String path, Map<String, String> query) {
        StringBuilder uri = new StringBuilder(baseUrl).append(path.startsWith("/") ? path : "/" + path);
        Map<String, String> params = query == null ? Map.of() : new LinkedHashMap<>(query);
        if (!params.isEmpty()) {
            uri.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                if (!first) {
                    uri.append('&');
                }
                uri.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
                first = false;
            }
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri.toString()))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "ChemManager-Desktop/1.0");
        String token = tokenSupplier.get();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private <T> T send(HttpRequest.Builder builder, Class<T> type, String path) {
        HttpResponse<String> response = execute(builder, path);
        String body = response.body();
        if (type == Void.class) {
            return null;
        }
        try {
            if (body == null || body.isBlank()) {
                return null;
            }
            return JsonMapper.get().readValue(body, type);
        } catch (IOException ex) {
            throw new ApiException("Malformed JSON response from " + path + ": " + ex.getMessage(), ex);
        }
    }

    private <T> T sendTyped(HttpRequest.Builder builder, TypeReference<T> type, String path) {
        HttpResponse<String> response = execute(builder, path);
        try {
            return JsonMapper.get().readValue(response.body(), type);
        } catch (IOException ex) {
            throw new ApiException("Malformed JSON response from " + path + ": " + ex.getMessage(), ex);
        }
    }

    private JsonNode sendTree(HttpRequest.Builder builder, String path) {
        HttpResponse<String> response = execute(builder, path);
        try {
            return JsonMapper.get().readTree(response.body());
        } catch (IOException ex) {
            throw new ApiException("Malformed JSON response from " + path + ": " + ex.getMessage(), ex);
        }
    }

    private HttpResponse<String> execute(HttpRequest.Builder builder, String path) {
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response;
            }
            throw toApiException(response, path);
        } catch (IOException ex) {
            throw new ApiException("Cannot reach the API at " + baseUrl + " (" + ex.getMessage() + ")", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request to " + path + " was interrupted", ex);
        }
    }

    private ApiException toApiException(HttpResponse<String> response, String path) {
        int status = response.statusCode();
        String body = response.body();
        String code = "http_" + status;
        String message = "API error " + status + " on " + path;
        Map<String, String> details = Map.of();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = JsonMapper.get().readTree(body);
                JsonNode errorNode = node.has("error") ? node.get("error") : node;
                ApiError error = JsonMapper.get().treeToValue(errorNode, ApiError.class);
                code = error.code();
                message = error.message();
                details = error.details();
            } catch (IOException ignored) {
                message = body.length() > 300 ? body.substring(0, 300) : body;
            }
        }
        return new ApiException(status, code, message, details);
    }

    private String writeJson(Object payload) {
        try {
            return JsonMapper.get().writeValueAsString(payload);
        } catch (IOException ex) {
            throw new ApiException("Unable to serialise request payload: " + ex.getMessage(), ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
