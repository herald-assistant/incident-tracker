package pl.mkn.incidenttracker.api.gitlab.source;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.incidenttracker.integrations.gitlab.source.GitLabSourceResolveRequest;
import pl.mkn.incidenttracker.integrations.gitlab.source.GitLabSourceResolveResponse;
import pl.mkn.incidenttracker.integrations.gitlab.source.GitLabSourceResolveService;

@RestController
@RequestMapping("/api/gitlab/source")
@RequiredArgsConstructor
public class GitLabSourceResolveController {

    private final GitLabSourceResolveService gitLabSourceResolveService;

    @PostMapping("/resolve")
    public GitLabSourceResolveResponse resolve(@Valid @RequestBody GitLabSourceResolveRequest request) {
        return gitLabSourceResolveService.resolve(request);
    }

    @PostMapping("/resolve/preview")
    public GitLabSourceResolveResponse resolvePreview(@Valid @RequestBody GitLabSourceResolveRequest request) {
        return gitLabSourceResolveService.resolvePreview(request);
    }
}
