package pl.mkn.tdw.integrations.httpauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.httpauth.adapter.rest.HttpAuthProtocolClient;
import pl.mkn.tdw.integrations.httpauth.contract.*;
import pl.mkn.tdw.integrations.httpauth.service.OidcLoginService;
import pl.mkn.tdw.testsupport.integrations.LocalHttpsServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

class OidcLoginTest {
    @Test void requiresLoginWhenPublicClientAccessTokenExpiresWithoutRefreshToken() throws Exception {
        try (var provider = new Provider()) {
            provider.refreshEnabled = false;
            var profile = provider.profile(); var service = provider.service();
            var login = params(URI.create(service.begin(profile, "test-browser")).getRawQuery());
            provider.nonce = login.get("nonce"); provider.challenge = login.get("code_challenge");
            service.complete(List.of(profile), login.get("state"), "test-code", null, "test-browser", profile.redirectUri());
            assertThat(service.authorization(profile)).isEqualTo("Bearer crm-access");
            provider.clock.advance(4000);
            assertThatThrownBy(() -> service.authorization(profile)).isInstanceOf(HttpAuthException.class);
            assertThat(service.status(profile).connected()).isFalse();
            assertThat(provider.tokenCalls).hasValue(1);
        }
    }
    @Test void appliesSharedTlsToDiscoveryJwksCodeAndRefreshWithoutSkippingJwtValidation() throws Exception {
        try (var provider = new Provider(true)) {
            var profile = provider.profile();
            assertThatThrownBy(() -> provider.service(false).begin(profile, "test-browser")).isInstanceOf(HttpAuthException.class);
            assertThat(provider.discoveryCalls).hasValue(0);
            var service = provider.service(true);
            var login = params(URI.create(service.begin(profile, "test-browser")).getRawQuery());
            provider.nonce = login.get("nonce"); provider.challenge = login.get("code_challenge");
            service.complete(List.of(profile), login.get("state"), "test-code", null, "test-browser", profile.redirectUri());
            assertThat(service.authorization(profile)).isEqualTo("Bearer crm-access");
            assertThat(provider.jwksCalls.get()).isGreaterThan(0);
            provider.clock.advance(4000);
            assertThat(service.authorization(profile)).isEqualTo("Bearer crm-refreshed");
            assertThat(provider.lastForm).containsEntry("grant_type", "refresh_token");
            var invalid = provider.service(true); provider.invalid = "signature";
            var next = params(URI.create(invalid.begin(profile, "test-browser")).getRawQuery());
            provider.nonce = next.get("nonce"); provider.challenge = next.get("code_challenge");
            assertThatThrownBy(() -> invalid.complete(List.of(profile), next.get("state"), "test-code", null, "test-browser", profile.redirectUri()))
                    .hasMessageContaining("potwierdzić logowania");
            assertThat(invalid.status(profile).connected()).isFalse();
        }
    }

    @Test void logsInWithPkceUsesAccessTokenRefreshesAndDisconnectsWithoutPersisting() throws Exception {
        try (var provider = new Provider()) {
            var profile = provider.profile(); var service = provider.service();
            assertThat(service.status(profile).connected()).isFalse();
            assertThatThrownBy(() -> service.authorization(profile)).hasMessageContaining("Zaloguj");
            var login = params(URI.create(service.begin(profile, "browser-one")).getRawQuery());
            provider.nonce = login.get("nonce"); provider.challenge = login.get("code_challenge");
            assertThat(login).containsEntry("response_type", "code").containsEntry("code_challenge_method", "S256");
            assertThat(login.get("scope")).contains("openid");
            service.complete(List.of(profile), login.get("state"), "test-code", null, "browser-one", profile.redirectUri());
            assertThat(service.status(profile)).satisfies(status -> {
                assertThat(status.connected()).isTrue(); assertThat(status.username()).isEqualTo("crm.operator");
            });
            assertThat(service.authorization(profile)).isEqualTo("Bearer crm-access");
            assertThat(provider.tokenCalls).hasValue(1);
            provider.clock.advance(4000);
            assertThat(service.authorization(profile)).isEqualTo("Bearer crm-refreshed");
            assertThat(provider.tokenCalls).hasValue(2);
            assertThat(provider.lastForm).containsEntry("grant_type", "refresh_token").containsEntry("refresh_token", "crm-refresh");
            assertThatThrownBy(() -> service.complete(List.of(profile), login.get("state"), "test-code", null, "browser-one", profile.redirectUri()))
                    .hasMessageContaining("wygasło");
            assertThat(provider.service().status(profile).connected()).isFalse();
            service.disconnect(profile);
            assertThat(service.status(profile).connected()).isFalse();
            assertThatThrownBy(() -> service.authorization(profile)).hasMessageContaining("Zaloguj");
        }
    }

    @Test void rejectsBrowserMismatchExpiredStateChangedConfigurationAndDeniedLoginBeforeTokenRequest() throws Exception {
        try (var provider = new Provider()) {
            var profile = provider.profile(); var service = provider.service();
            var first = params(URI.create(service.begin(profile, "browser-one")).getRawQuery());
            assertThatThrownBy(() -> service.complete(List.of(profile), first.get("state"), "test-code", null, "browser-two", profile.redirectUri()))
                    .hasMessageContaining("przeglądarki");
            var expired = params(URI.create(service.begin(profile, "browser-one")).getRawQuery());
            provider.clock.advance(301);
            assertThatThrownBy(() -> service.complete(List.of(profile), expired.get("state"), "test-code", null, "browser-one", profile.redirectUri()))
                    .hasMessageContaining("wygasło");
            var changed = params(URI.create(service.begin(profile, "browser-one")).getRawQuery());
            assertThatThrownBy(() -> service.complete(List.of(), changed.get("state"), "test-code", null, "browser-one", profile.redirectUri()))
                    .hasMessageContaining("konfiguracji");
            var denied = params(URI.create(service.begin(profile, "browser-one")).getRawQuery());
            assertThatThrownBy(() -> service.complete(List.of(profile), denied.get("state"), null, "access_denied", "browser-one", profile.redirectUri()))
                    .hasMessageContaining("odrzucone");
            var callback = params(URI.create(service.begin(profile, "browser-one")).getRawQuery());
            assertThatThrownBy(() -> service.complete(List.of(profile), callback.get("state"), "test-code", null, "browser-one", "http://other.example.com/callback"))
                    .hasMessageContaining("konfiguracji");
            assertThat(provider.tokenCalls).hasValue(0);
        }
    }

    @Test void validatesSignatureIssuerAudienceNonceAndExpiryAndClearsFailedRefresh() throws Exception {
        for (var invalid : List.of("signature", "issuer", "audience", "nonce", "expiry", "missing")) {
            try (var provider = new Provider()) {
                var profile = provider.profile(); var service = provider.service(); provider.invalid = invalid;
                var login = params(URI.create(service.begin(profile, "browser-one")).getRawQuery());
                provider.nonce = login.get("nonce"); provider.challenge = login.get("code_challenge");
                assertThatThrownBy(() -> service.complete(List.of(profile), login.get("state"), "test-code", null, "browser-one", profile.redirectUri()))
                        .as(invalid).hasMessageContaining("potwierdzić logowania");
                assertThat(service.status(profile).connected()).isFalse();
            }
        }
        try (var provider = new Provider()) {
            var profile = provider.profile(); var service = provider.service();
            var login = params(URI.create(service.begin(profile, "browser-one")).getRawQuery());
            provider.nonce = login.get("nonce"); provider.challenge = login.get("code_challenge");
            service.complete(List.of(profile), login.get("state"), "test-code", null, "browser-one", profile.redirectUri());
            provider.clock.advance(4000); provider.refreshFails = true;
            assertThatThrownBy(() -> service.authorization(profile)).hasMessageContaining("Zaloguj");
            assertThat(service.status(profile).connected()).isFalse();
        }
    }

    private static Map<String, String> params(String query) {
        var values = new HashMap<String, String>();
        for (var item : query.split("&")) {
            var parts = item.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return values;
    }

    private static class MutableClock extends Clock {
        private Instant instant = Instant.now();
        void advance(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private static class Provider implements AutoCloseable {
        final HttpServer server;
        final LocalHttpsServer https;
        final RSAKey key = new RSAKeyGenerator(2048).keyID("crm-key").generate();
        final MutableClock clock = new MutableClock();
        final AtomicInteger tokenCalls = new AtomicInteger();
        final AtomicInteger discoveryCalls = new AtomicInteger();
        final AtomicInteger jwksCalls = new AtomicInteger();
        final ObjectMapper mapper = new ObjectMapper();
        final String issuer;
        String nonce; String challenge; String invalid = ""; boolean refreshFails; boolean refreshEnabled = true;
        Map<String, String> lastForm;

        Provider() throws Exception {
            this(false);
        }
        Provider(boolean tls) throws Exception {
            https = tls ? new LocalHttpsServer() : null;
            server = tls ? https.server : HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            issuer = (tls ? https.wrongHostUrl() : "http://127.0.0.1:" + server.getAddress().getPort()) + "/issuer";
            server.createContext("/issuer/.well-known/openid-configuration", exchange -> {
                discoveryCalls.incrementAndGet();
                respond(exchange, 200, Map.of("issuer", issuer, "authorization_endpoint", issuer + "/authorize", "token_endpoint", issuer + "/token", "jwks_uri", issuer + "/jwks"));
            });
            server.createContext("/issuer/jwks", exchange -> { jwksCalls.incrementAndGet(); respond(exchange, 200, new JWKSet(key.toPublicJWK()).toJSONObject()); });
            server.createContext("/issuer/token", exchange -> {
                try {
                    tokenCalls.incrementAndGet();
                    lastForm = params(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    if ("refresh_token".equals(lastForm.get("grant_type"))) {
                        respond(exchange, refreshFails ? 400 : 200, refreshFails ? Map.of("error", "invalid_grant")
                                : Map.of("access_token", "crm-refreshed", "token_type", "Bearer", "expires_in", 3600));
                    } else {
                        var actualChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                                .digest(lastForm.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));
                        if (!actualChallenge.equals(challenge)) { respond(exchange, 400, Map.of("error", "invalid_grant")); return; }
                        var claims = new JWTClaimsSet.Builder().issuer(invalid.equals("issuer") ? issuer + "/wrong" : issuer)
                                .audience(invalid.equals("audience") ? "other-client" : "crm-client").subject("crm-user")
                                .issueTime(Date.from(Instant.now().minusSeconds(1)))
                                .expirationTime(Date.from(Instant.now().plusSeconds(invalid.equals("expiry") ? -120 : 3600)))
                                .claim("nonce", invalid.equals("nonce") ? "other-nonce" : nonce).claim("preferred_username", "crm.operator").build();
                        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("crm-key").build(), claims);
                        jwt.sign(new RSASSASigner(invalid.equals("signature") ? new RSAKeyGenerator(2048).generate() : key));
                        var response = new HashMap<String, Object>(Map.of("access_token", "crm-access", "token_type", "Bearer", "expires_in", 3600,
                                "refresh_token", "crm-refresh"));
                        if (!refreshEnabled) response.remove("refresh_token");
                        if (!invalid.equals("missing")) response.put("id_token", jwt.serialize());
                        respond(exchange, 200, response);
                    }
                } catch (Exception failure) { respond(exchange, 500, Map.of("error", "server_error")); }
            });
            server.start();
        }

        HttpAuthProfile profile() { return new HttpAuthProfile("crm_oidc", "CRM OIDC", "OIDC", null, null, null, null,
                "crm-client", null, "openid profile", issuer, "http://tdw.example.com/api/system-assistant/auth/callback", "none"); }
        OidcLoginService service() { return service(false); }
        OidcLoginService service(boolean ignore) {
            var factory = IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder(), ignore);
            return new OidcLoginService(new HttpAuthProtocolClient(factory), clock);
        }
        void respond(com.sun.net.httpserver.HttpExchange exchange, int status, Object value) throws java.io.IOException {
            var bytes = mapper.writeValueAsBytes(value);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }
        @Override public void close() { if (https != null) https.close(); else server.stop(0); }
    }
}
