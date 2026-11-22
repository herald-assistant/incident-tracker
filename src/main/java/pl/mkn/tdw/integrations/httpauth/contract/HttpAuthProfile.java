package pl.mkn.tdw.integrations.httpauth.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;

/** Operator-maintained connection settings; never part of model-facing arguments. */
public record HttpAuthProfile(
        @NotBlank @Size(max = 100) String id,
        @NotBlank @Size(max = 100) String name,
        @NotBlank String type,
        String username, String password, String token,
        String tokenUrl, String clientId, String clientSecret,
        String scopes, String issuerUrl, String redirectUri,
        String clientAuthentication) {

    public void validate() {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,100}") || !text(name) || name.length() > 100)
            fail("Podaj nazwę uwierzytelniania i poprawne ID.");
        if (type == null || !Set.of("BASIC", "BEARER", "OAUTH2", "OIDC").contains(type))
            fail("Wybierz Basic, Bearer, OAuth2 albo OIDC.");
        for (var value : new String[]{username, password, token, tokenUrl, clientId, clientSecret, scopes, issuerUrl, redirectUri})
            if (value != null && (value.length() > 16_384 || value.contains("\r") || value.contains("\n")))
                fail("Pole Auth jest zbyt długie lub zawiera znak nowej linii.");
        switch (type) {
            case "BASIC" -> {
                if (!text(username) || username.contains(":") || !text(password)) fail("Basic wymaga loginu bez dwukropka i hasła.");
            }
            case "BEARER" -> {
                if (!text(token) || !token.matches("[A-Za-z0-9._~+/=-]+")) fail("Podaj token bez prefiksu Bearer i bez spacji.");
            }
            case "OAUTH2" -> {
                endpoint(tokenUrl);
                if (!text(clientId) || !text(clientSecret)) fail("OAuth2 wymaga client ID i client secret.");
                clientMethod(false);
            }
            case "OIDC" -> {
                endpoint(issuerUrl); endpoint(redirectUri);
                if (!text(clientId)) fail("OIDC wymaga client ID.");
                if (scopes == null || java.util.Arrays.stream(scopes.trim().split("\\s+")).noneMatch("openid"::equals)) fail("Scopes OIDC muszą zawierać openid.");
                clientMethod(!text(clientSecret));
            }
        }
    }

    private void clientMethod(boolean publicClient) {
        if (publicClient) {
            if (!"none".equals(clientAuthentication)) fail("Klient OIDC bez sekretu wymaga metody none.");
        } else if (!Set.of("client_secret_basic", "client_secret_post").contains(clientAuthentication == null ? "" : clientAuthentication))
            fail("Wybierz sposób przekazania client secret.");
    }

    public static URI endpoint(String value) {
        try {
            var uri = URI.create(value);
            if (!Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null)
                throw new IllegalArgumentException();
            return uri;
        } catch (Exception ex) { throw new HttpAuthException("Podaj pełny adres HTTP(S) bez credentials, query i fragmentu."); }
    }

    public static boolean text(String value) { return value != null && !value.isBlank(); }
    private static void fail(String message) { throw new HttpAuthException(message); }

    @Override public String toString() { return "HttpAuthProfile[id=" + id + ", type=" + type + "]"; }
}
