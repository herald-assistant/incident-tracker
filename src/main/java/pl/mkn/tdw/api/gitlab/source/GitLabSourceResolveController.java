package pl.mkn.tdw.api.gitlab.source;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.gitlab.contract.source.GitLabSourceResolveRequest;
import pl.mkn.tdw.integrations.gitlab.contract.source.GitLabSourceResolveResponse;
import pl.mkn.tdw.integrations.gitlab.GitLabSourceResolvePort;

@RestController
@RequestMapping("/api/gitlab/source")
@RequiredArgsConstructor
public class GitLabSourceResolveController {

    private final GitLabSourceResolvePort gitLabSourceResolveService;

    @PostMapping("/resolve")
    public GitLabSourceResolveResponse resolve(@Valid @RequestBody GitLabSourceResolveRequest request) {
        return gitLabSourceResolveService.resolve(request);
    }

    @PostMapping("/resolve/preview")
    public GitLabSourceResolveResponse resolvePreview(@Valid @RequestBody GitLabSourceResolveRequest request) {
        return gitLabSourceResolveService.resolvePreview(request);
    }
}
