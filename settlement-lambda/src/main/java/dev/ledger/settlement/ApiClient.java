package dev.ledger.settlement;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Calls the ledger API's internal settlement endpoints. The API stays the single database writer. */
public class ApiClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final String serviceToken;

    public ApiClient(String baseUrl, String serviceToken) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.serviceToken = serviceToken;
    }

    public void settle(long transferId) {
        post("/internal/transfers/" + transferId + "/settle", "");
    }

    public void fail(long transferId, String reason) {
        post("/internal/transfers/" + transferId + "/fail",
                "{\"reason\":\"" + reason.replace("\"", "'") + "\"}");
    }

    private void post(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("X-Service-Token", serviceToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("API " + path + " returned " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("API call failed: " + path, e);
        }
    }
}
