package pl.mkn.tdw.integrations.httpauth.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.*;
import org.springframework.security.oauth2.jwt.*;
import pl.mkn.tdw.integrations.httpauth.OidcLoginPort;
import pl.mkn.tdw.integrations.httpauth.adapter.rest.HttpAuthProtocolClient;
import pl.mkn.tdw.integrations.httpauth.contract.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class OidcLoginService implements OidcLoginPort {
    private final HttpAuthProtocolClient protocol;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new HashMap<>();
    private final Map<HttpAuthProfile, Connection> connections = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<HttpAuthProfile, Connection> entry) { return size() > 128; }
    };

    private record Pending(HttpAuthProfile profile, String browser, Instant expires, String nonce,
                           ClientRegistration registration, OAuth2AuthorizationRequest request, NimbusJwtDecoder decoder) { }
    private record Connection(ClientRegistration registration, NimbusJwtDecoder decoder, String nonce,
                              String subject, String username, OAuth2AccessTokenResponse tokens) { }

    public OidcLoginService(HttpAuthProtocolClient protocol, @Qualifier("httpAuthClock") Clock clock) {
        this.protocol = protocol; this.clock = clock;
    }

    @Override public synchronized String begin(HttpAuthProfile profile, String browserBinding) {
        profile.validate();
        if (!"OIDC".equals(profile.type()) || !HttpAuthProfile.text(browserBinding)) fail("Nieprawidłowa konfiguracja logowania OIDC.");
        pending.values().removeIf(item -> !item.expires().isAfter(clock.instant()));
        if (pending.size() >= 100) fail("Zbyt wiele rozpoczętych logowań. Spróbuj ponownie za kilka minut.");
        try {
            var issuer = profile.issuerUrl().replaceAll("/+$", "");
            var discovery = protocol.json().get().uri(issuer + "/.well-known/openid-configuration").retrieve().body(JsonNode.class);
            if (discovery == null || !profile.issuerUrl().equals(discovery.path("issuer").asText())) fail("Dostawca zwrócił inny issuer.");
            for (var field : List.of("authorization_endpoint", "token_endpoint", "jwks_uri"))
                HttpAuthProfile.endpoint(discovery.path(field).asText());
            var registration = protocol.registration(profile, discovery.path("token_endpoint").asText(),
                    discovery.path("authorization_endpoint").asText(), discovery.path("jwks_uri").asText());
            var decoder = NimbusJwtDecoder.withJwkSetUri(discovery.path("jwks_uri").asText()).restOperations(protocol.jwks()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(profile.issuerUrl()),
                    new OidcIdTokenValidator(registration)));
            var state = random(); var nonce = random(); var verifier = random();
            var request = OAuth2AuthorizationRequest.authorizationCode().authorizationUri(discovery.path("authorization_endpoint").asText())
                    .clientId(profile.clientId()).redirectUri(profile.redirectUri()).scopes(registration.getScopes()).state(state)
                    .attributes(attributes -> attributes.put(PkceParameterNames.CODE_VERIFIER, verifier))
                    .additionalParameters(Map.of("nonce", nonce, "code_challenge", challenge(verifier), "code_challenge_method", "S256")).build();
            pending.put(state, new Pending(profile, browserBinding, clock.instant().plusSeconds(300), nonce, registration, request, decoder));
            return request.getAuthorizationRequestUri();
        } catch (HttpAuthException failure) { throw failure; }
        catch (RuntimeException failure) { throw new HttpAuthException("Nie udało się rozpocząć logowania OIDC. Sprawdź issuer URL i konfigurację klienta."); }
    }

    @Override public synchronized void complete(List<HttpAuthProfile> currentProfiles, String state, String code, String error,
                                                String browserBinding, String callbackUri) {
        var login = state == null ? null : pending.remove(state);
        if (login == null || !login.expires().isAfter(clock.instant()) || !login.browser().equals(browserBinding)
                || !login.profile().redirectUri().equals(callbackUri) || !currentProfiles.contains(login.profile()))
            fail("Logowanie wygasło lub nie pasuje do przeglądarki i bieżącej konfiguracji. Rozpocznij je ponownie.");
        if (HttpAuthProfile.text(error) || !HttpAuthProfile.text(code)) fail("Logowanie zostało anulowane lub odrzucone przez dostawcę.");
        try {
            var response = OAuth2AuthorizationResponse.success(code).redirectUri(login.profile().redirectUri()).state(state).build();
            var tokens = protocol.code(new OAuth2AuthorizationCodeGrantRequest(login.registration(), new OAuth2AuthorizationExchange(login.request(), response)));
            var jwt = validateIdToken(login.decoder(), tokens, login.nonce(), true);
            var username = jwt.getClaimAsString("preferred_username");
            if (!HttpAuthProfile.text(username)) username = jwt.getSubject();
            connections.put(login.profile(), new Connection(login.registration(), login.decoder(), login.nonce(), jwt.getSubject(), username, tokens));
        } catch (RuntimeException failure) { throw new HttpAuthException("Nie udało się potwierdzić logowania OIDC. Sprawdź konfigurację klienta i spróbuj ponownie."); }
    }

    @Override public synchronized OidcConnection status(HttpAuthProfile profile) {
        var connection = connections.get(profile);
        if (connection == null) return new OidcConnection(false, null, null, "Zaloguj, aby wykonywać wywołania API.");
        var usable = valid(connection.tokens()) || connection.tokens().getRefreshToken() != null;
        return new OidcConnection(usable, connection.username(), connection.tokens().getAccessToken().getExpiresAt(),
                usable ? "Połączono" : "Sesja wygasła. Zaloguj ponownie.");
    }

    @Override public synchronized void disconnect(HttpAuthProfile profile) {
        connections.keySet().removeIf(item -> item.id().equals(profile.id()));
        pending.values().removeIf(item -> item.profile().id().equals(profile.id()));
    }

    @Override public synchronized String authorization(HttpAuthProfile profile) {
        var connection = connections.get(profile);
        if (connection == null) return required(profile);
        if (!valid(connection.tokens())) {
            if (connection.tokens().getRefreshToken() == null) { connections.remove(profile); return required(profile); }
            try {
                var tokens = protocol.refresh(new OAuth2RefreshTokenGrantRequest(connection.registration(),
                        connection.tokens().getAccessToken(), connection.tokens().getRefreshToken()));
                if (tokens.getAdditionalParameters().containsKey("id_token")) {
                    var jwt = validateIdToken(connection.decoder(), tokens, connection.nonce(), false);
                    if (!connection.subject().equals(jwt.getSubject())) fail("Odnowienie zmieniło użytkownika.");
                }
                if (tokens.getRefreshToken() == null) tokens = OAuth2AccessTokenResponse.withResponse(tokens)
                        .refreshToken(connection.tokens().getRefreshToken().getTokenValue()).build();
                connection = new Connection(connection.registration(), connection.decoder(), connection.nonce(), connection.subject(), connection.username(), tokens);
                connections.put(profile, connection);
            } catch (RuntimeException failure) { connections.remove(profile); return required(profile); }
        }
        return "Bearer " + connection.tokens().getAccessToken().getTokenValue();
    }

    private Jwt validateIdToken(NimbusJwtDecoder decoder, OAuth2AccessTokenResponse response, String nonce, boolean initial) {
        var value = response.getAdditionalParameters().get("id_token");
        if (!(value instanceof String token)) throw new HttpAuthException("Brak ID tokenu OIDC.");
        var jwt = decoder.decode(token);
        if (!HttpAuthProfile.text(jwt.getSubject()) || ((initial || jwt.hasClaim("nonce")) && !nonce.equals(jwt.getClaimAsString("nonce"))))
            throw new HttpAuthException("Nieprawidłowy nonce lub subject OIDC.");
        return jwt;
    }

    private boolean valid(OAuth2AccessTokenResponse response) {
        return response.getAccessToken().getExpiresAt() != null && response.getAccessToken().getExpiresAt().isAfter(clock.instant().plusSeconds(15));
    }
    private String required(HttpAuthProfile profile) { throw new HttpAuthException("Zaloguj ponownie profil „" + profile.name() + "”."); }
    private String random() { var bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String challenge(String verifier) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private void fail(String message) { throw new HttpAuthException(message); }
}
