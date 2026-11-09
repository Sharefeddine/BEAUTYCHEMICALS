package com.chemmanager.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.chemmanager.dto.ApiError;
import com.chemmanager.dto.PagedResult;
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
            HttpRequest request = requestBuilder(ApiEndpoints.HEALTH, Map.of()).GET().timeout(Duration.ofSeconds(5)).build();
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

    public <T> T get(String path, TypeReference<T> type) {
        return sendTyped(requestBuilder(path, Map.of()).GET(), type, path);
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
        if (type == Void.class) {
            return null;
        }
        JsonNode data = unwrap(response.body(), path);
        if (data == null || data.isNull()) {
            return null;
        }
        return convert(data, type, path);
    }

    private <T> T sendTyped(HttpRequest.Builder builder, TypeReference<T> type, String path) {
        HttpResponse<String> response = execute(builder, path);
        JsonNode data = unwrap(response.body(), path);
        if (data == null || data.isNull()) {
            return null;
        }
        return convert(data, type, path);
    }

    private JsonNode sendTree(HttpRequest.Builder builder, String path) {
        HttpResponse<String> response = execute(builder, path);
        JsonNode data = unwrap(response.body(), path);

        return data == null ? JsonMapper.get().nullNode() : data;
    }

    /**
     * The API answers with one envelope: {@code {success:true, data:..., meta:{...}}} or
     * {@code {success:false, error:{code,message,details}}}. Only the {@code data} node is handed
     * to the callers, the {@code meta} block is read by {@link #getPage}.
     */
    private JsonNode unwrap(String body, String path) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JsonMapper.get().readTree(body);
            if (root.has("success")) {
                if (!root.path("success").asBoolean(true)) {
                    JsonNode error = root.path("error");
                    throw new ApiException(422, error.path("code").asText("error"),
                            error.path("message").asText("The API rejected the request."),
                            toStringMap(error.path("details")));
                }
                return root.get("data");
            }
            return root;
        } catch (IOException ex) {
            throw new ApiException("Malformed JSON response from " + path + ": " + ex.getMessage(), ex);
        }
    }

    private static Map<String, String> toStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText()));
        return values;
    }

    private <T> T convert(JsonNode node, Class<T> type, String path) {
        try {
            return JsonMapper.get().treeToValue(node, type);
        } catch (IOException ex) {
            throw new ApiException("Malformed payload from " + path + ": " + ex.getMessage(), ex);
        }
    }

    private <T> T convert(JsonNode node, TypeReference<T> type, String path) {
        try {
            return JsonMapper.get().readerFor(type).readValue(node);
        } catch (IOException ex) {
            throw new ApiException("Malformed payload from " + path + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Reads a paginated list: the rows are in {@code data}, the pagination in {@code meta}.
     */
    public <T> PagedResult<T> getPage(String path, Map<String, String> query, Class<T> rowType) {
        HttpResponse<String> response = execute(requestBuilder(path, query).GET(), path);
        String body = response.body();
        try {
            JsonNode root = body == null || body.isBlank()
                    ? JsonMapper.get().nullNode()
                    : JsonMapper.get().readTree(body);
            JsonNode rows = root.has("success") ? root.path("data") : root;
            JsonNode meta = root.path("meta");
            List<T> items = new ArrayList<>();
            if (rows != null && rows.isArray()) {
                for (JsonNode row : rows) {
                    items.add(convert(row, rowType, path));
                }
            }
            int page = meta.path("page").asInt(0);
            int pageSize = meta.path("pageSize").asInt(Math.max(1, items.size()));
            long total = meta.path("total").asLong(items.size());
            // the API counts pages from 1, the client from 0
            return new PagedResult<>(items, Math.max(0, page - 1), pageSize, total);
        } catch (IOException ex) {
            throw new ApiException("Malformed page from " + path + ": " + ex.getMessage(), ex);
        }
    }

    public void putVoid(String path, Object payload) {
        put(path, payload, Void.class);
    }

    public void delete(String path) {
        send(requestBuilder(path, Map.of()).DELETE(), Void.class, path);
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
