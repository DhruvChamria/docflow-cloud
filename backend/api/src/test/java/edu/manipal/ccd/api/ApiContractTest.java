package edu.manipal.ccd.api;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class ApiContractTest {
    static ServletWebServerApplicationContext app;
    static String base;
    static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll static void start() {
        app = (ServletWebServerApplicationContext) SpringApplication.run(Application.class, "--server.port=0");
        base = "http://127.0.0.1:" + app.getWebServer().getPort();
    }
    @AfterAll static void stop() { if (app != null) app.close(); }
    static HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void healthMetricsAndOpenApiAreAvailable() throws Exception {
        for (String path : new String[]{"/actuator/health/liveness", "/actuator/health/readiness", "/actuator/prometheus", "/v3/api-docs"})
            assertEquals(200, get(path).statusCode(), path);
    }
    @Test void catalogueDoesNotPromiseWorkingOperations() throws Exception {
        var result = get("/api/v1/operations");
        assertEquals(200, result.statusCode());
        assertTrue(result.body().contains("\"available\":false"));
        assertFalse(result.body().contains("\"available\":true"));
    }
    @Test void submissionReturnsProblemDetailsAndCorrelationId() throws Exception {
        var result = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/jobs"))
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(501, result.statusCode());
        assertTrue(result.headers().firstValue("content-type").orElse("").contains("application/problem+json"));
        assertTrue(result.headers().firstValue("X-Correlation-Id").isPresent());
    }
    @Test void localConfigurationProvidesJacksonTwoMapperForJobMessages() {
        assertNotNull(new LocalConfiguration().jobMessageMapper());
    }
}
