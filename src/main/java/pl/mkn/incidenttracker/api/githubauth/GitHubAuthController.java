package pl.mkn.incidenttracker.api.githubauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/github")
@RequiredArgsConstructor
public class GitHubAuthController {

    private final GitHubAuthService githubAuthService;

    @GetMapping("/status")
    public GitHubAuthStatusResponse status(HttpServletRequest request, HttpServletResponse response) {
        return githubAuthService.status(request, response);
    }

    @GetMapping("/start")
    public ResponseEntity<Void> start(
            @RequestParam(required = false, defaultValue = "/") String returnUrl,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var redirectUri = githubAuthService.start(returnUrl, request, response);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUri.toString())
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request
    ) {
        var redirectUri = githubAuthService.callback(code, state, error, request);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUri.toString())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        githubAuthService.logout();
        return ResponseEntity.noContent().build();
    }
}
