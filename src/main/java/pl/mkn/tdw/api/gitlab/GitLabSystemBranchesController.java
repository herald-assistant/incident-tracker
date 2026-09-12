package pl.mkn.tdw.api.gitlab;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.mkn.tdw.common.GitLabPathUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryBranchService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/gitlab/systems")
@RequiredArgsConstructor
public class GitLabSystemBranchesController {

    private static final int MAX_SYSTEM_ID_LENGTH = 160;
    private static final int MAX_SEARCH_LENGTH = 160;
    private static final int MAX_PRIMARY_REPOSITORIES = 8;

    private final OperationalContextPort operationalContextPort;
    private final GitLabProperties gitLabProperties;
    private final GitLabRepositoryBranchService branchService;

    @GetMapping("/{systemId}/branches")
    public BranchesResponse branches(
            @PathVariable String systemId,
            @RequestParam(required = false) String search
    ) {
        var normalizedSystemId = required(systemId, MAX_SYSTEM_ID_LENGTH, "systemId");
        var normalizedSearch = search != null ? search.trim() : "";
        if (normalizedSearch.length() > MAX_SEARCH_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch filter is too long.");
        }
        var configuredGroup = gitLabProperties.getGroup();
        if (!StringUtils.hasText(configuredGroup)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GitLab group is not configured.");
        }

        var catalog = operationalContextPort.loadContext(new OperationalContextQuery(
                Set.of(OperationalContextEntryType.SYSTEM,
                        OperationalContextEntryType.REPOSITORY,
                        OperationalContextEntryType.CODE_SEARCH_SCOPE),
                List.of()));
        if (catalog.systems().stream().noneMatch(system -> normalizedSystemId.equals(system.id()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System is not registered.");
        }
        var repositoriesById = new LinkedHashMap<String, OperationalContextRepository>();
        catalog.repositories().forEach(repository -> repositoriesById.putIfAbsent(repository.id(), repository));
        var projectPaths = new LinkedHashMap<String, String>();
        for (var scope : catalog.codeSearchScopes()) {
            if (!"system".equals(normalize(scope.target().type()))
                    || !normalizedSystemId.equals(scope.target().id())) {
                continue;
            }
            for (var repositoryRef : scope.repositories()) {
                if (!"primary".equals(normalize(repositoryRef.role()))
                        && !Integer.valueOf(1).equals(repositoryRef.priority())) {
                    continue;
                }
                var repository = repositoriesById.get(repositoryRef.repoId());
                if (repository == null || !gitLabRepository(repository)) {
                    continue;
                }
                var path = projectPath(repository, configuredGroup);
                if (StringUtils.hasText(path)
                        && GitLabPathUtils.isSameOrNestedPath(configuredGroup, path)) {
                    projectPaths.putIfAbsent(path.toLowerCase(Locale.ROOT), path);
                }
            }
        }
        if (projectPaths.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "System has no primary GitLab repository in the configured group.");
        }

        var branchesByName = new LinkedHashMap<String, BranchOption>();
        var warnings = new ArrayList<String>();
        var truncated = projectPaths.size() > MAX_PRIMARY_REPOSITORIES;
        var successfulRepositories = 0;
        for (var projectPath : projectPaths.values().stream().limit(MAX_PRIMARY_REPOSITORIES).toList()) {
            try {
                var page = branchService.listBranches(projectPath, normalizedSearch);
                successfulRepositories++;
                truncated |= page.truncated();
                for (var branch : page.branches()) {
                    branchesByName.merge(branch.name(), new BranchOption(branch.name(), branch.isDefault()),
                            (left, right) -> new BranchOption(left.name(), left.isDefault() || right.isDefault()));
                }
            } catch (RuntimeException exception) {
                warnings.add("Nie udało się pobrać gałęzi z jednego repozytorium GitLab.");
            }
        }
        if (successfulRepositories == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie udało się pobrać gałęzi z GitLaba.");
        }
        if (projectPaths.size() > MAX_PRIMARY_REPOSITORIES) {
            warnings.add("Lista obejmuje tylko pierwsze osiem primary repozytoriów.");
        }
        return new BranchesResponse(
                normalizedSystemId,
                branchesByName.values().stream().sorted(Comparator.comparing(BranchOption::name)).toList(),
                truncated,
                List.copyOf(warnings)
        );
    }

    private String required(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value) || value.trim().length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required and must be short.");
        }
        return value.trim();
    }

    private boolean gitLabRepository(OperationalContextRepository repository) {
        var provider = normalize(repository.git().provider());
        return provider.isEmpty() || "gitlab".equals(provider);
    }

    private String projectPath(OperationalContextRepository repository, String configuredGroup) {
        if (StringUtils.hasText(repository.git().projectPath())) {
            return GitLabPathUtils.trimSlashes(repository.git().projectPath().trim());
        }
        if (StringUtils.hasText(repository.git().project())) {
            var group = StringUtils.hasText(repository.git().group())
                    ? repository.git().group().trim() : configuredGroup.trim();
            return GitLabPathUtils.trimSlashes(group) + "/"
                    + GitLabPathUtils.trimSlashes(repository.git().project().trim());
        }
        return null;
    }

    private String normalize(String value) {
        return value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    public record BranchOption(String name, boolean isDefault) {
    }

    public record BranchesResponse(
            String systemId,
            List<BranchOption> branches,
            boolean truncated,
            List<String> warnings
    ) {
    }
}
