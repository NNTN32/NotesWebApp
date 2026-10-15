package com.example.notesWeb.security;

import com.example.notesWeb.exception.redis.authRedis.AuthRedisProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class ApiRateLimitHttpIntegrationTest {
    @LocalServerPort int port;
    @MockitoBean ApiRateLimiter limiter;
    @MockitoBean AuthRedisProducer queue;

    @Test void embeddedServerPreservesAuth429InsteadOfRedispatchingToAnonymous401() throws Exception {
        var error = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
        error.getHeaders().set("Retry-After", "30");
        doThrow(error).when(limiter).login(any(), eq("rate-http-fixture"));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/auth/login"))
                .header("Content-Type", "application/json").header("Origin", "https://nhannotes.id.vn")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"rate-http-fixture\",\"password\":\"unused\"}")).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.headers().firstValue("Retry-After")).contains("30");
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("https://nhannotes.id.vn");
        assertThat(response.headers().firstValue("Access-Control-Expose-Headers")).contains("Retry-After");
        assertThat(response.body()).contains("rate_limited");
        verifyNoInteractions(queue);
    }
}
