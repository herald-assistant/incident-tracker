package pl.mkn.tdw.integrations.httpauth.adapter.rest;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;

/** Strict TLS and no redirects, including token requests with client credentials. */
@Component
public class HttpAuthProtocolClient {
    private final RestClientClientCredentialsTokenResponseClient credentials = new RestClientClientCredentialsTokenResponseClient();
    private final RestClientAuthorizationCodeTokenResponseClient codes = new RestClientAuthorizationCodeTokenResponseClient();
    private final RestClientRefreshTokenTokenResponseClient refresh = new RestClientRefreshTokenTokenResponseClient();
    private final RestClient json;
    private final org.springframework.web.client.RestOperations jwks;

    public HttpAuthProtocolClient(RestClient.Builder builder) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        json = builder.clone().requestFactory(factory).build();
        jwks = new org.springframework.web.client.RestTemplate(factory);
        var tokens = builder.clone().requestFactory(factory).messageConverters(converters -> {
            converters.clear();
            converters.add(new FormHttpMessageConverter());
            converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
        }).defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build();
        credentials.setRestClient(tokens); codes.setRestClient(tokens); refresh.setRestClient(tokens);
    }

    public OAuth2AccessTokenResponse credentials(HttpAuthProfile profile) {
        return credentials.getTokenResponse(new OAuth2ClientCredentialsGrantRequest(registration(profile, null, null, null)));
    }

    public ClientRegistration registration(HttpAuthProfile profile, String tokenUri, String authorizationUri, String jwksUri) {
        var builder = ClientRegistration.withRegistrationId(profile.id()).clientId(profile.clientId())
                .clientSecret(profile.clientSecret() == null ? "" : profile.clientSecret())
                .clientAuthenticationMethod(new ClientAuthenticationMethod(profile.clientAuthentication()))
                .authorizationGrantType("OIDC".equals(profile.type()) ? AuthorizationGrantType.AUTHORIZATION_CODE : AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(tokenUri == null ? profile.tokenUrl() : tokenUri);
        if (HttpAuthProfile.text(profile.scopes())) builder.scope(Arrays.asList(profile.scopes().trim().split("\\s+")));
        if ("OIDC".equals(profile.type())) builder.authorizationUri(authorizationUri).jwkSetUri(jwksUri)
                .issuerUri(profile.issuerUrl()).redirectUri(profile.redirectUri());
        return builder.build();
    }

    public RestClient json() { return json; }
    public org.springframework.web.client.RestOperations jwks() { return jwks; }
    public OAuth2AccessTokenResponse code(OAuth2AuthorizationCodeGrantRequest request) { return codes.getTokenResponse(request); }
    public OAuth2AccessTokenResponse refresh(OAuth2RefreshTokenGrantRequest request) { return refresh.getTokenResponse(request); }
}
