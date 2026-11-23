package pl.mkn.tdw.integrations.support.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.httpauth.adapter.rest.HttpAuthProtocolClient;
import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;
import pl.mkn.tdw.integrations.httpauth.service.*;
import pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;
import pl.mkn.tdw.testsupport.integrations.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;

class IntegrationApiAuthTlsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpAuthProfile profile(String tokenUrl) {
        return new HttpAuthProfile("test", "Fikcyjny OAuth2", "OAUTH2", null, null, null, tokenUrl,
                "test-client", "test-secret", null, null, null, "client_secret_post");
    }
    private OpenApiRestExecutor executor(IntegrationRestClientBuilderFactory factory) {
        var protocol = new HttpAuthProtocolClient(factory);
        return new OpenApiRestExecutor(factory, mapper,
                new HttpAuthenticationService(protocol, new OidcLoginService(protocol, Clock.systemUTC())));
    }

    @Test void appliesTheFlagToBothOAuth2AndApiPatch() throws Exception {
        try (var https = new LocalHttpsServer()) {
            var strict = IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder());
            var ignore = IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder(), true);
            var tokens = new AtomicInteger(); var requests = new AtomicInteger();
            var method = new AtomicReference<String>(); var authorization = new AtomicReference<String>();
            https.server.createContext("/token", exchange -> {
                tokens.incrementAndGet(); exchange.getRequestBody().readAllBytes();
                var bytes = "{\"access_token\":\"test-access\",\"token_type\":\"Bearer\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            https.server.createContext("/items/7", exchange -> {
                requests.incrementAndGet(); method.set(exchange.getRequestMethod());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization")); exchange.getRequestBody().readAllBytes();
                var bytes = "{\"updated\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            https.server.start();
            var source = Files.readString(Path.of("src/test/resources/openapi/patch-api.yaml"))
                    .replace("http://127.0.0.1:8099", https.wrongHostUrl());
            var operation = new OpenApiImporter(mapper).parse("test", source).stream().filter(op -> op.name().endsWith("_patchItem")).findFirst().orElseThrow();
            var arguments = new OpenApiArguments(mapper.readTree("{\"path\":{\"id\":7},\"body\":{\"note\":\"TLS test\"}}"));
            var auth = profile(https.wrongHostUrl() + "/token");
            var verified = executor(strict);
            assertThat(verified.execute(new OpenApiExecution(operation, null), arguments).outcome()).isEqualTo("OUTCOME_UNKNOWN");
            assertThat(verified.execute(new OpenApiExecution(operation, auth), arguments).outcome()).isEqualTo("AUTH_REQUIRED");
            assertThat(tokens).hasValue(0); assertThat(requests).hasValue(0);
            assertThat(executor(ignore).execute(new OpenApiExecution(operation, auth), arguments).statusCode()).isEqualTo(200);
            assertThat(tokens).hasValue(1); assertThat(requests).hasValue(1);
            assertThat(method).hasValue("PATCH"); assertThat(authorization).hasValue("Bearer test-access");
        }
    }

    @Test void doesNotFollowRedirectsForTokenDiscoveryOrJwks() throws Exception {
        try (var https = new LocalHttpsServer()) {
            var factory = IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder(), true);
            var target = new AtomicInteger();
            https.server.createContext("/redirect", exchange -> {
                exchange.getRequestBody().readAllBytes(); exchange.getResponseHeaders().set("Location", https.url() + "/target");
                exchange.sendResponseHeaders(307, -1); exchange.close();
            });
            https.server.createContext("/target", exchange -> { target.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
            https.server.start();
            var protocol = new HttpAuthProtocolClient(factory);
            var url = https.wrongHostUrl() + "/redirect";
            assertThatThrownBy(() -> protocol.credentials(profile(url))).isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthorizationException.class);
            assertThat(protocol.json().get().uri(url).retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(307);
            assertThat(protocol.jwks().getForEntity(url, String.class).getStatusCode().value()).isEqualTo(307);
            assertThat(target).hasValue(0);
        }
    }
}
