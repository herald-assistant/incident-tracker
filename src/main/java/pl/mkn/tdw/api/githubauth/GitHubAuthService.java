package pl.mkn.tdw.api.githubauth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotFineGrainedPat;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotPatSource;

@Service
@RequiredArgsConstructor
public class GitHubAuthService {

    private final CopilotPatSource patSource;

    public GitHubAuthStatusResponse status() {
        return new GitHubAuthStatusResponse(
                CopilotFineGrainedPat.valid(patSource.currentPat()),
                "/workspace-settings"
        );
    }
}
