package dev.nimbuspowered.nimbus.auth.velocity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Tiny async HTTP client for the Nimbus auth API used by the Velocity
 * {@code /dashboard} command.
 *
 * <p>Intentionally standalone (no dependency on Bridge's NimbusApiClient) so
 * the auth-velocity plugin ships independently. Bearer token comes from the
 * service's {@code NIMBUS_API_TOKEN}.
 */
public class AuthApiClient {

    private final String baseUrl;
    private final String token;
    private final Logger logger;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Gson gson = new Gson();

    public AuthApiClient(String baseUrl, String token, Logger logger) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.logger = logger;
    }

    public CompletableFuture<ApiResult> get(String path) {
        return exec(build(path).GET().build());
    }

    public CompletableFuture<ApiResult> post(String path, JsonObject body) {
        HttpRequest.Builder b = build(path);
        if (body != null) {
            b.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)));
        } else {
            b.POST(HttpRequest.BodyPublishers.noBody());
        }
        return exec(b.build());
    }

    private HttpRequest.Builder build(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(8));
        if (token != null && !token.isEmpty()) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    private CompletableFuture<ApiResult> exec(HttpRequest req) {
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> new ApiResult(r.statusCode(), r.body()))
                .exceptionally(err -> {
                    logger.debug("HTTP call failed: {}", err.getMessage());
                    return new ApiResult(-1, "");
                });
    }

    public static final class ApiResult {
        private final int status;
        private final String body;

        ApiResult(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public int statusCode() { return status; }
        public String body() { return body; }
        public boolean isSuccess() { return status >= 200 && status < 300; }
        public JsonObject asJson() { return new Gson().fromJson(body, JsonObject.class); }
    }
}
