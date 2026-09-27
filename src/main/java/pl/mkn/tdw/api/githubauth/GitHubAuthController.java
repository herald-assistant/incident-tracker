package pl.mkn.tdw.api.githubauth;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/github")
@RequiredArgsConstructor
public class GitHubAuthController {

    private final GitHubAuthService githubAuthService;

    @GetMapping("/status")
    public GitHubAuthStatusResponse status() {
        return githubAuthService.status();
    }
}
