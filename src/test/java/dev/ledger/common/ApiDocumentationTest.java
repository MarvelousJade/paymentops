package dev.ledger.common;

import dev.ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApiDocumentationTest {
    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void openApiDocumentRemainsAvailableAfterFrameworkUpgrade() throws Exception {
        var response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/json");
        assertThat(response.body()).contains("\"openapi\"", "\"/api/v1/payments\"");
    }

    @Test
    void swaggerUiServesThePatchedSanitizerBundle() throws Exception {
        var page = get("/swagger-ui/index.html");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("swagger-ui-bundle.js");

        var bundle = get("/swagger-ui/swagger-ui-bundle.js");
        assertThat(bundle.statusCode()).isEqualTo(200);
        var version = Pattern.compile("DOMPurify\\.version=\"(\\d+)\\.(\\d+)\\.(\\d+)\"").matcher(bundle.body());
        assertThat(version.find()).as("served bundle identifies its embedded sanitizer").isTrue();
        int major = Integer.parseInt(version.group(1));
        int minor = Integer.parseInt(version.group(2));
        int patch = Integer.parseInt(version.group(3));
        assertThat(major > 3 || major == 3 && (minor > 4 || minor == 4 && patch >= 12))
                .as("DOMPurify is at least 3.4.12, not the vulnerable 3.3.2 bundle")
                .isTrue();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
