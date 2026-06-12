package pl.mkn.incidenttracker.api.gitlab;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointListResult;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchRequest;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchResponse;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchService;

@RestController
@RequestMapping("/api/gitlab/repository")
@RequiredArgsConstructor
public class GitLabRepositorySearchController {

    private final GitLabRepositorySearchService gitLabRepositorySearchService;
    private final GitLabRepositoryEndpointService gitLabRepositoryEndpointService;

    @PostMapping("/search")
    public GitLabRepositorySearchResponse search(@Valid @RequestBody GitLabRepositorySearchRequest request) {
        return gitLabRepositorySearchService.search(request);
    }

    @PostMapping("/endpoints")
    public GitLabRepositoryEndpointListResult listEndpoints(
            @Valid @RequestBody GitLabRepositoryEndpointListRequest request
    ) {
        return gitLabRepositoryEndpointService.listEndpoints(request);
    }
}
