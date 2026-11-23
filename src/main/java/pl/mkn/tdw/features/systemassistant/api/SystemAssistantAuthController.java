package pl.mkn.tdw.features.systemassistant.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantException;
import pl.mkn.tdw.integrations.httpauth.OidcLoginPort;
import pl.mkn.tdw.integrations.httpauth.contract.*;
import pl.mkn.tdw.shared.error.UserFacingErrorType;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/system-assistant/auth")
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
public class SystemAssistantAuthController {
    private static final String COOKIE = "tdw_oidc_browser";
    private static final String CALLBACK = "/api/system-assistant/auth/callback";
    private final SystemAssistantConfigurationService configuration;
    private final OidcLoginPort oidc;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, LoginReturn> returns = new HashMap<>();

    public record LoginRequest(String conversationId, String profileId, String returnPath) { }
    private record LoginReturn(String browser, Instant expiresAt, LoginRequest destination) { }

    public SystemAssistantAuthController(SystemAssistantConfigurationService configuration, OidcLoginPort oidc) {
        this.configuration = configuration; this.oidc = oidc;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, OidcConnection>> status() {
        var statuses = new LinkedHashMap<String, OidcConnection>();
        configuration.current().authProfiles().stream().filter(profile -> profile.type().equals("OIDC"))
                .forEach(profile -> statuses.put(profile.id(), oidc.status(profile)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(statuses);
    }

    public record Login(String authorizationUrl) { }

    @PostMapping(value = "/{id}/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public synchronized ResponseEntity<Login> login(@PathVariable String id, @RequestBody LoginRequest destination, HttpServletRequest request,
                                      @CookieValue(name = COOKIE, required = false) String browser) {
        validateDestination(destination);
        returns.values().removeIf(item -> !item.expiresAt().isAfter(Instant.now()));
        if (returns.size() >= 100) throw new HttpAuthException("Zbyt wiele rozpoczętych logowań. Spróbuj ponownie później.");
        var profile = profile(id);
        var callback = HttpAuthProfile.endpoint(profile.redirectUri());
        var origin = request.getHeader("Origin");
        if (origin == null) origin = UriComponentsBuilder.fromUriString(request.getRequestURL().toString()).replacePath(null).replaceQuery(null).build().toUriString();
        var expected = UriComponentsBuilder.fromUriString(origin).path(CALLBACK).build().toUriString();
        if (!CALLBACK.equals(callback.getPath()) || !expected.equals(profile.redirectUri()))
            throw new HttpAuthException("Adres powrotu musi wskazywać " + expected + ". Zapisz konfigurację klienta i TDW.");
        if (browser == null || !browser.matches("[A-Za-z0-9_-]{43}")) {
            var bytes = new byte[32]; random.nextBytes(bytes);
            browser = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        var url = oidc.begin(profile, browser);
        var state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        if (state == null || state.isBlank()) throw new HttpAuthException("Nie udało się rozpocząć logowania OIDC.");
        returns.put(state, new LoginReturn(browser, Instant.now().plusSeconds(300), destination));
        var cookie = ResponseCookie.from(COOKIE, browser).httpOnly(true).secure(true)
                .sameSite("None").path("/api/system-assistant/auth").build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE, cookie.toString()).body(new Login(url));
    }

    @GetMapping("/callback")
    public synchronized ResponseEntity<Void> callback(@RequestParam(required = false) String state, @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String error, HttpServletRequest request,
                                         @CookieValue(name = COOKIE, required = false) String browser) {
        var result = "success";
        var loginReturn = state == null ? null : returns.remove(state);
        var destination = loginReturn != null && loginReturn.browser().equals(browser)
                && loginReturn.expiresAt().isAfter(Instant.now()) ? loginReturn.destination() : null;
        try {
            var uri = request.getRequestURL().toString();
            oidc.complete(configuration.current().authProfiles(), state, code, error, browser, uri);
        } catch (RuntimeException failure) { result = "failed"; }
        var location = UriComponentsBuilder.fromPath(destination == null ? "/system-assistant" : destination.returnPath()).queryParam("auth", result);
        if (destination != null && destination.conversationId() != null)
            location.queryParam("localRunId", destination.conversationId()).queryParam("profileId", destination.profileId());
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location.build().toUri())
                .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").build();
    }

    @PostMapping(value = "/{id}/disconnect", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> disconnect(@PathVariable String id) {
        oidc.disconnect(profile(id));
        return ResponseEntity.noContent().build();
    }

    private HttpAuthProfile profile(String id) {
        return configuration.current().authProfiles().stream().filter(profile -> profile.id().equals(id) && profile.type().equals("OIDC"))
                .findFirst().orElseThrow(() -> new SystemAssistantException("AUTH_PROFILE_NOT_FOUND", UserFacingErrorType.BAD_REQUEST, "Wybierz zapisany profil OIDC."));
    }

    private void validateDestination(LoginRequest destination) {
        if (!"/system-assistant".equals(destination.returnPath()) && !"/system-assistant/chat".equals(destination.returnPath()))
            throw new HttpAuthException("Wybierz prawidłową ścieżkę powrotu System Assistant.");
        if (destination.conversationId() == null && destination.profileId() == null && "/system-assistant".equals(destination.returnPath())) return;
        if (destination.conversationId() == null || !destination.conversationId().matches("[a-zA-Z0-9_-]{1,100}")
                || destination.profileId() == null || !destination.profileId().matches("[a-zA-Z0-9_-]{1,100}"))
            throw new HttpAuthException("Nieprawidłowa rozmowa lub profil powrotu po logowaniu.");
        configuration.snapshot().forProfile(destination.profileId());
    }
}
