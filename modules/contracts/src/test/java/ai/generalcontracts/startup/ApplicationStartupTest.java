package ai.generalcontracts.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "management.health.mongo.enabled=false",
        "spring.mongodb.uri=mongodb://127.0.0.1:1/health-test?directConnection=true",
        "spring.mongodb.ssl.enabled=false"
    }
)
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
    void configuresGracefulShutdownAndBasicLogging() {
        assertEquals("graceful", environment.getProperty("server.shutdown"));
        assertEquals("INFO", environment.getProperty("logging.level.root"));
    }
}
