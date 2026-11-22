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
import java.net.URI;
import java.security.SecureRandom;
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
    public ResponseEntity<Login> login(@PathVariable String id, HttpServletRequest request,
                                      @CookieValue(name = COOKIE, required = false) String browser) {
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
        var cookie = ResponseCookie.from(COOKIE, browser).httpOnly(true).secure(callback.getScheme().equals("https"))
                .sameSite("Lax").path("/api/system-assistant/auth").build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE, cookie.toString()).body(new Login(url));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String state, @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String error, HttpServletRequest request,
                                         @CookieValue(name = COOKIE, required = false) String browser) {
        var result = "success";
        try {
            var uri = request.getRequestURL().toString();
            oidc.complete(configuration.current().authProfiles(), state, code, error, browser, uri);
        } catch (RuntimeException failure) { result = "failed"; }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/system-assistant?auth=" + result))
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
}
