package pl.mkn.tdw.integrations.httpauth.service;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import pl.mkn.tdw.integrations.httpauth.HttpAuthenticationPort;
import pl.mkn.tdw.integrations.httpauth.adapter.rest.HttpAuthProtocolClient;
import pl.mkn.tdw.integrations.httpauth.contract.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HttpAuthenticationService implements HttpAuthenticationPort {
    private final HttpAuthProtocolClient protocol;
    private final pl.mkn.tdw.integrations.httpauth.OidcLoginPort oidc;
    private final Map<HttpAuthProfile, OAuth2AccessTokenResponse> tokens = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<HttpAuthProfile, OAuth2AccessTokenResponse> entry) { return size() > 128; }
    };

    public HttpAuthenticationService(HttpAuthProtocolClient protocol, pl.mkn.tdw.integrations.httpauth.OidcLoginPort oidc) {
        this.protocol = protocol; this.oidc = oidc;
    }

    @Override public synchronized String authorization(HttpAuthProfile profile) {
        if (profile == null) return null;
        profile.validate();
        if (profile.type().equals("BASIC")) return "Basic " + HttpHeaders.encodeBasicAuth(profile.username(), profile.password(), StandardCharsets.UTF_8);
        if (profile.type().equals("BEARER")) return "Bearer " + profile.token();
        if (profile.type().equals("OIDC")) return oidc.authorization(profile);
        try {
            var response = tokens.get(profile);
            if (!valid(response)) {
                response = protocol.credentials(profile);
                tokens.put(profile, response);
            }
            return "Bearer " + response.getAccessToken().getTokenValue();
        } catch (RuntimeException failure) {
            tokens.remove(profile);
            throw new HttpAuthException("Nie udało się uzyskać tokenu OAuth2 dla „" + profile.name() + "”. Sprawdź ustawienia i uprawnienia klienta.");
        }
    }

    private boolean valid(OAuth2AccessTokenResponse response) {
        return response != null && response.getAccessToken().getExpiresAt() != null
                && response.getAccessToken().getExpiresAt().isAfter(Instant.now().plusSeconds(15));
    }
}
