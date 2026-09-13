package pl.mkn.tdw.features.operationalcontextassistance.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceCollector;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryBranchService;

import java.util.List;

@RestController
@RequestMapping("/api/operational-context/assistance/source-options/branches")
@RequiredArgsConstructor
public class OperationalContextAssistanceBranchOptionsController {

    private static final int MAX_SEARCH_LENGTH = 160;

    private final OperationalContextGitLabSourceCollector sourceCollector;
    private final GitLabRepositoryBranchService branchService;

    @GetMapping
    public BranchesResponse branches(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String projectUrl,
            @RequestParam(required = false) String search
    ) {
        var normalizedSearch = search != null ? search.trim() : "";
        if (normalizedSearch.length() > MAX_SEARCH_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch filter is too long.");
        }
        var projectPath = sourceCollector.projectPathForBranchOptions(project, projectUrl);
        try {
            var page = branchService.listBranches(projectPath, normalizedSearch);
            return new BranchesResponse(
                    page.branches().stream().map(branch -> new BranchOption(branch.name(), branch.isDefault())).toList(),
                    page.truncated(), List.of()
            );
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Nie udało się pobrać gałęzi wybranego projektu GitLab.", exception);
        }
    }

    public record BranchOption(String name, boolean isDefault) {
    }

    public record BranchesResponse(List<BranchOption> branches, boolean truncated, List<String> warnings) {
    }
}
