package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import pl.mkn.tdw.integrations.gitlab.adapter.rest.GitLabRestRepositoryAdapter;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.testsupport.integrations.GitLabIntegrationTestCreator;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitLabReadRetryTest {
    private static final String SHA = "a".repeat(40);
    private static final String FILE = "db/customer.yml";
    private static final String URL = "https://gitlab.example.com/api/v4/projects/CRM%2Fcustomer/repository/files/db%2Fcustomer.yml";
    private static final String RAW_URL = URL + "/raw?ref=" + SHA;

    @Test void retriesWholeResponseAfter503AndBrokenBodyOnTheSameRevision() {
        var fixture = fixture();
        var closed = new AtomicBoolean();
        fixture.server.expect(requestTo(RAW_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        fixture.server.expect(requestTo(RAW_URL)).andRespond(request -> new MockClientHttpResponse(new InputStream() {
            @Override public int read() throws IOException {throw new IOException("CRM interrupted response");}
            @Override public void close() {closed.set(true);}
        }, HttpStatus.OK));
        fixture.server.expect(requestTo(RAW_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("databaseChangeLog: []\n", MediaType.TEXT_PLAIN));

        var file = fixture.adapter.readFileComplete("CRM", "customer", SHA, FILE);

        assertEquals("databaseChangeLog: []\n", file.content());
        assertEquals(SHA, file.branch());
        assertFalse(file.truncated());
        assertTrue(closed.get(), "The failed response must be closed before a new read");
        fixture.server.verify();
    }

    @Test void retriesMetadataTimeoutAndKeepsPinnedScope() {
        var fixture = fixture();
        fixture.server.expect(requestTo(URL + "?ref=" + SHA)).andExpect(method(HttpMethod.HEAD))
                .andRespond(withException(new SocketTimeoutException("CRM timeout")));
        fixture.server.expect(requestTo(URL + "?ref=" + SHA)).andExpect(method(HttpMethod.HEAD))
                .andRespond(withSuccess().header("X-Gitlab-Commit-Id", SHA)
                        .header("X-Gitlab-File-Path", FILE).header("X-Gitlab-Size", "12"));

        var metadata = fixture.adapter.readFileMetadata("CRM", "customer", SHA, FILE);

        assertEquals(SHA, metadata.commitId());
        assertEquals(FILE, metadata.filePath());
        assertEquals(12L, metadata.sizeBytes());
        fixture.server.verify();
    }

    @Test void ordinaryRawReadAlsoRecoversFromTransientHttpFailure() {
        var fixture = fixture();
        fixture.server.expect(requestTo(RAW_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        fixture.server.expect(requestTo(RAW_URL)).andRespond(withSuccess("CRM source", MediaType.TEXT_PLAIN));
        assertEquals("CRM source", fixture.adapter.readFile("CRM", "customer", SHA, FILE, 100).content());
        fixture.server.verify();
    }

    @Test void permanentHttpErrorsDoNotRetry() {
        for (var status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.UNPROCESSABLE_ENTITY)) {
            var fixture = fixture();
            fixture.server.expect(requestTo(RAW_URL)).andRespond(withStatus(status));
            var failure = assertThrows(IllegalStateException.class,
                    () -> fixture.adapter.readFileComplete("CRM", "customer", SHA, FILE));
            assertEquals(status.value(), ((RestClientResponseException) failure.getCause()).getStatusCode().value());
            fixture.server.verify();
        }
    }

    @Test void stopsAfterThreeAttemptsAndPreservesTheLastHttpStatus() {
        var fixture = fixture();
        fixture.server.expect(ExpectedCount.times(3), requestTo(RAW_URL))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        var failure = assertThrows(IllegalStateException.class,
                () -> fixture.adapter.readFileComplete("CRM", "customer", SHA, FILE));
        assertEquals(504, ((RestClientResponseException) failure.getCause()).getStatusCode().value());
        fixture.server.verify();
    }

    @Test void doesNotRetryEarlierThanAnExcessiveRetryAfter() {
        for (var value : List.of("121", "99999999999999999999999999",
                ZonedDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(5).format(DateTimeFormatter.RFC_1123_DATE_TIME))) {
            var fixture = fixture();
            fixture.server.expect(requestTo(RAW_URL))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", value));
            assertThrows(IllegalStateException.class,
                    () -> fixture.adapter.readFileComplete("CRM", "customer", SHA, FILE));
            fixture.server.verify();
        }
    }

    @Test void retriesRateLimitWhenRetryAfterAllowsIt() {
        var fixture = fixture();
        fixture.server.expect(requestTo(RAW_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        fixture.server.expect(requestTo(RAW_URL)).andRespond(withSuccess("CRM source", MediaType.TEXT_PLAIN));
        assertEquals("CRM source", fixture.adapter.readFileComplete("CRM", "customer", SHA, FILE).content());
        fixture.server.verify();
    }

    @Test void invalidUtf8AndTlsFailuresAreNotRetried() {
        var fixture = fixture();
        fixture.server.expect(requestTo(RAW_URL))
                .andRespond(request -> new MockClientHttpResponse(new byte[]{(byte) 0xc3}, HttpStatus.OK));
        assertThrows(IllegalStateException.class,
                () -> fixture.adapter.readFileComplete("CRM", "customer", SHA, FILE));
        fixture.server.verify();
        fixture.server.reset();
        fixture.server.expect(requestTo(RAW_URL))
                .andRespond(withException(new javax.net.ssl.SSLHandshakeException("CRM certificate failure")));
        assertThrows(IllegalStateException.class,
                () -> fixture.adapter.readFileComplete("CRM", "customer", SHA, FILE));
        fixture.server.verify();
    }

    @Test void interruptionStopsRetryAndPreservesTheThreadFlag() throws Exception {
        var fixture = fixture();
        var firstRequest = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        var failure = new AtomicReference<Throwable>();
        fixture.server.expect(requestTo(RAW_URL)).andRespond(request -> {
            firstRequest.countDown();
            var response = new MockClientHttpResponse(new byte[0], HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().set("Retry-After", "60");
            return response;
        });
        var worker = new Thread(() -> {
            try {fixture.adapter.readFileComplete("CRM", "customer", SHA, FILE);}
            catch (Throwable exception) {failure.set(exception);}
            finally {interrupted.set(Thread.currentThread().isInterrupted());}
        });
        try {
            worker.start();
            assertTrue(firstRequest.await(5, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(5_000);
            assertFalse(worker.isAlive());
            assertInstanceOf(IllegalStateException.class, failure.get());
            assertTrue(interrupted.get());
            fixture.server.verify();
        } finally {
            worker.interrupt();
            worker.join(5_000);
        }
    }

    private record Fixture(GitLabRestRepositoryAdapter adapter, MockRestServiceServer server) {}
    private Fixture fixture() {
        var properties = new GitLabProperties();
        properties.setBaseUrl("https://gitlab.example.com");
        properties.setGroup("CRM");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = GitLabIntegrationTestCreator.repositoryAdapter(properties,
                new GitLabRestClientFactory(properties, IntegrationRestClientBuilderFactoryTestCreator.create(builder)));
        return new Fixture(adapter, server);
    }
}
