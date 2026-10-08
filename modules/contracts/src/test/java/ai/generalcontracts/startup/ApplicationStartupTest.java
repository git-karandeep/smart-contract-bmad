package ai.generalcontracts.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    // Test profile keeps the application isolated from all external services.
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "management.health.mongo.enabled=false",
        "spring.mongodb.uri=mongodb://127.0.0.1:1/health-test?directConnection=true",
        "spring.mongodb.ssl.enabled=false"
    }
)
@ActiveProfiles("test")
class ApplicationStartupTest {

    @Autowired
    private Environment environment;

    @Test
    void exposesHealthyActuatorEndpoint() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpRequest request = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/actuator/health")
        ).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
    }

    @Test
    void exposesReadinessHealthEndpoint() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpRequest request = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/actuator/health/readiness")
        ).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
    }

    @Test
    void exposesMetricsToAuthenticatedRequests() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        String credentials = Base64.getEncoder().encodeToString("test-user:test-only".getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/actuator/metrics")
        ).header("Authorization", "Basic " + credentials).GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"names\""));
    }

    @Test
    void protectsMetricsFromUnauthenticatedRequests() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpRequest request = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/actuator/metrics")
        ).GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(401, response.statusCode());
    }

    @Test
    void returnsRequestCorrelationIdentifiers() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpRequest request = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/actuator/health")
        ).header("X-Request-Id", "request-123")
            .header("X-Correlation-Id", "correlation-123")
            .header("X-Transaction-Id", "transaction-456")
            .GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertEquals("request-123", response.headers().firstValue("X-Request-Id").orElseThrow());
        assertEquals("correlation-123", response.headers().firstValue("X-Correlation-Id").orElseThrow());
        assertEquals("transaction-456", response.headers().firstValue("X-Transaction-Id").orElseThrow());
    }

    @Test
    void configuresGracefulShutdownAndBasicLogging() {
        assertEquals("graceful", environment.getProperty("server.shutdown"));
        assertEquals("INFO", environment.getProperty("logging.level.root"));
        assertEquals(false, environment.getRequiredProperty("app.messaging.enabled", Boolean.class));
    }
}
