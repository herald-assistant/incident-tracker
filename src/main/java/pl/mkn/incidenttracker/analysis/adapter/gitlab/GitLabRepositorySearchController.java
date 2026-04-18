package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gitlab/repository")
@RequiredArgsConstructor
public class GitLabRepositorySearchController {

    private final GitLabRepositorySearchService gitLabRepositorySearchService;

    @PostMapping("/search")
    public GitLabRepositorySearchResponse search(@Valid @RequestBody GitLabRepositorySearchRequest request) {
        return gitLabRepositorySearchService.search(request);
    }

}
