package pl.mkn.incidenttracker.analysis.adapter.gitlab.source;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
