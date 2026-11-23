package pl.mkn.tdw.integrations.support.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;
import pl.mkn.tdw.testsupport.integrations.LocalHttpsServer;
import javax.net.ssl.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class IntegrationRestClientHttpsTest {
    @Test void appliesCertificateAndHostnamePolicyLocallyAndSupportsPatch() throws Exception {
        var defaultContext = SSLContext.getDefault();
        var defaultSockets = HttpsURLConnection.getDefaultSSLSocketFactory();
        var defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        var hostnameProperty = System.getProperty("jdk.internal.httpclient.disableHostnameVerification");
        try (var https = new LocalHttpsServer()) {
            var strict = IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder());
            var ignore = IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder(), true);
            var calls = new AtomicInteger(); var method = new AtomicReference<String>();
            https.server.createContext("/", exchange -> {
                calls.incrementAndGet(); method.set(exchange.getRequestMethod()); exchange.getRequestBody().readAllBytes();
                var bytes = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            https.server.start();
            var timeout = Duration.ofSeconds(2);
            var untrusted = RestClient.builder().requestFactory(strict.newRequestFactory(timeout, timeout)).build();
            assertThatThrownBy(() -> untrusted.get().uri(https.url()).retrieve().toBodilessEntity()).isInstanceOf(ResourceAccessException.class);
            assertThatThrownBy(() -> strict.newBuilder().build().get().uri(https.url()).retrieve().toBodilessEntity())
                    .isInstanceOf(ResourceAccessException.class);
            var trusted = RestClient.builder().requestFactory(strict.newRequestFactory(timeout, timeout, https.trustedContext())).build();
            assertThat(trusted.get().uri(https.url()).retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(200);
            assertThatThrownBy(() -> trusted.get().uri(https.wrongHostUrl()).retrieve().toBodilessEntity()).isInstanceOf(ResourceAccessException.class);
            var relaxed = RestClient.builder().requestFactory(ignore.newRequestFactory(timeout, timeout)).build();
            assertThat(relaxed.patch().uri(https.wrongHostUrl()).body("{}").retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(200);
            assertThat(ignore.newBuilder().build().patch().uri(https.wrongHostUrl()).body("{}").retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(200);
            assertThat(method).hasValue("PATCH"); assertThat(calls).hasValue(3);
        }
        assertThat(SSLContext.getDefault()).isSameAs(defaultContext);
        assertThat(HttpsURLConnection.getDefaultSSLSocketFactory()).isSameAs(defaultSockets);
        assertThat(HttpsURLConnection.getDefaultHostnameVerifier()).isSameAs(defaultVerifier);
        assertThat(System.getProperty("jdk.internal.httpclient.disableHostnameVerification")).isEqualTo(hostnameProperty);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void doesNotRedirectOrRetryWritesAndRetainsReadTimeout(boolean ignoreTls) throws Exception {
        try (var https = new LocalHttpsServer()) {
            var factory = IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder(), ignoreTls);
            var target = new AtomicInteger(); var dropped = new AtomicInteger();
            https.server.createContext("/redirect", exchange -> {
                exchange.getResponseHeaders().set("Location", https.url() + "/target"); exchange.sendResponseHeaders(307, -1); exchange.close();
            });
            https.server.createContext("/target", exchange -> { target.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
            https.server.createContext("/drop", exchange -> { dropped.incrementAndGet(); exchange.close(); });
            https.server.createContext("/slow", exchange -> {
                try { Thread.sleep(1500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                exchange.close();
            });
            https.server.start();
            var client = RestClient.builder().requestFactory(factory.newRequestFactory(Duration.ofSeconds(2), Duration.ofMillis(500), https.trustedContext())).build();
            assertThat(client.get().uri(https.url() + "/redirect").retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(307);
            assertThat(target).hasValue(0);
            for (var method : java.util.List.of(org.springframework.http.HttpMethod.PATCH, org.springframework.http.HttpMethod.POST)) {
                assertThatThrownBy(() -> client.method(method).uri(https.url() + "/drop").body("{}").retrieve().toBodilessEntity())
                        .isInstanceOf(ResourceAccessException.class);
            }
            assertThat(dropped).hasValue(2);
            assertThatThrownBy(() -> client.get().uri(https.url() + "/slow").retrieve().toBodilessEntity())
                    .isInstanceOf(ResourceAccessException.class).hasRootCauseInstanceOf(java.net.http.HttpTimeoutException.class);
        }
    }
}
