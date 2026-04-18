package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GitLabRepositorySearchService {

    private final GitLabProperties gitLabProperties;
    private final GitLabRepositoryPort gitLabRepositoryPort;

    public GitLabRepositorySearchResponse search(GitLabRepositorySearchRequest request) {
        var group = configuredGroup();
        var projectHints = normalizedValues(request.projectHints());
        var operationNames = normalizedValues(request.operationNames());
        var keywords = normalizedValues(request.keywords());
        var branch = request.effectiveBranch();

        try {
            var projectCandidates = gitLabRepositoryPort.searchProjects(group, projectHints);

            if (projectCandidates.isEmpty()) {
                throw failure(
                        HttpStatus.NOT_FOUND,
                        new GitLabRepositorySearchResponse(
                                group,
                                branch,
                                projectHints,
                                List.of(),
                                List.of(),
                                "No GitLab project candidates found for hints: " + projectHints
                        )
                );
            }

            if (!hasSearchTerms(operationNames, keywords)) {
                return new GitLabRepositorySearchResponse(
                        group,
                        branch,
                        projectHints,
                        projectCandidates,
                        List.of(),
                        "Project candidates resolved. File search skipped because no operationNames or keywords were provided."
                );
            }

            var fileCandidates = gitLabRepositoryPort.searchCandidateFiles(new GitLabRepositorySearchQuery(
                    request.correlationId(),
                    group,
                    branch,
                    projectHints,
                    operationNames,
                    keywords
            ));

            if (fileCandidates.isEmpty()) {
                throw failure(
                        HttpStatus.NOT_FOUND,
                        new GitLabRepositorySearchResponse(
                                group,
                                branch,
                                projectHints,
                                projectCandidates,
                                List.of(),
                                "Project candidates resolved, but no repository file candidates matched the provided search terms."
                        )
                );
            }

            return new GitLabRepositorySearchResponse(
                    group,
                    branch,
                    projectHints,
                    projectCandidates,
                    fileCandidates,
                    "OK"
            );
        } catch (GitLabRepositorySearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            var status = exception.getMessage() != null && exception.getMessage().contains("must be configured")
                    ? HttpStatus.INTERNAL_SERVER_ERROR
                    : HttpStatus.BAD_GATEWAY;

            throw failure(
                    status,
                    new GitLabRepositorySearchResponse(
                            group,
                            branch,
                            projectHints,
                            List.of(),
                            List.of(),
                            StringUtils.hasText(exception.getMessage())
                                    ? exception.getMessage()
                                    : "GitLab repository search failed."
                    )
            );
        }
    }

    private String configuredGroup() {
        if (!StringUtils.hasText(gitLabProperties.getGroup())) {
            throw new IllegalStateException("analysis.gitlab.group must be configured.");
        }

        return gitLabProperties.getGroup().trim();
    }

    private List<String> normalizedValues(List<String> values) {
        var normalized = new LinkedHashSet<String>();

        for (var value : values != null ? values : List.<String>of()) {
            if (StringUtils.hasText(value)) {
                normalized.add(value.trim());
            }
        }

        return List.copyOf(normalized);
    }

    private boolean hasSearchTerms(List<String> operationNames, List<String> keywords) {
        return !operationNames.isEmpty() || !keywords.isEmpty();
    }

    private GitLabRepositorySearchException failure(HttpStatus status, GitLabRepositorySearchResponse response) {
        return new GitLabRepositorySearchException(status, response);
    }

}
