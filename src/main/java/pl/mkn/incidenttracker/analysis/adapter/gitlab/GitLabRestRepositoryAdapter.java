package pl.mkn.incidenttracker.analysis.adapter.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class GitLabRestRepositoryAdapter implements GitLabRepositoryPort {

    private static final int PROJECT_SEARCH_PAGE_SIZE = 100;

    private final GitLabProperties properties;
    private final GitLabRestClientFactory gitLabRestClientFactory;

    @Override
    public List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints) {
        var searchTokens = distinctProjectSearchTokens(group, projectHints);
        if (!StringUtils.hasText(group) || searchTokens.isEmpty()) {
            return List.of();
        }

        var candidateScores = new LinkedHashMap<String, ProjectCandidateAccumulator>();

        for (var searchToken : searchTokens) {
            for (var project : searchGroupProjects(group, searchToken)) {
                var projectPath = toRelativeProjectPath(group, project.pathWithNamespace());
                if (!StringUtils.hasText(projectPath)) {
                    continue;
                }

                var matchScore = projectMatchScore(projectPath, project.name(), searchToken);
                if (matchScore <= 0) {
                    continue;
                }

                candidateScores.computeIfAbsent(
                                projectPath,
                                ignored -> new ProjectCandidateAccumulator(group, projectPath))
                        .registerMatch(searchToken, matchScore);
            }
        }

        return candidateScores.values().stream()
                .sorted((left, right) -> Integer.compare(right.matchScore(), left.matchScore()))
                .limit(properties.getMaxCandidateCount())
                .map(ProjectCandidateAccumulator::toCandidate)
                .toList();
    }

    @Override
    public List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query) {
        var projectNames = resolveProjectSearchTargets(query.group(), query.projectNames());
        var searchTerms = distinctSearchTerms(query.keywords(), query.operationNames());

        if (projectNames.isEmpty() || searchTerms.isEmpty()) {
            return List.of();
        }

        var candidateScores = new LinkedHashMap<String, CandidateAccumulator>();

        for (var projectName : projectNames) {
            for (var searchTerm : searchTerms) {
                for (var blobMatch : searchProjectBlobs(query.group(), projectName, query.branch(), searchTerm)) {
                    var key = projectName + "::" + blobMatch.path();
                    candidateScores.computeIfAbsent(
                                    key,
                                    ignored -> new CandidateAccumulator(query.group(), projectName, query.branch(), blobMatch.path()))
                            .registerMatch(searchTerm);
                }
            }
        }

        return candidateScores.values().stream()
                .sorted((left, right) -> Integer.compare(right.matchScore(), left.matchScore()))
                .limit(properties.getMaxCandidateCount())
                .map(CandidateAccumulator::toCandidate)
                .toList();
    }

    @Override
    public GitLabRepositoryFileContent readFile(
            String group,
            String projectName,
            String branch,
            String filePath,
            int maxCharacters
    ) {
        var content = fetchRawFile(group, projectName, branch, filePath);
        var limitedContent = limitCharacters(content, maxCharacters);
        var truncated = limitedContent.length() != content.length();

        return new GitLabRepositoryFileContent(
                group,
                projectName,
                branch,
                filePath,
                limitedContent,
                truncated
        );
    }

    @Override
    public GitLabRepositoryFileChunk readFileChunk(
            String group,
            String projectName,
            String branch,
            String filePath,
            int startLine,
            int endLine,
            int maxCharacters
    ) {
        var content = fetchRawFile(group, projectName, branch, filePath);
        var lines = content.lines().toList();
        var totalLines = lines.size();
        var requestedStartLine = Math.max(1, startLine);
        var requestedEndLine = Math.max(requestedStartLine, endLine);

        if (totalLines == 0 || requestedStartLine > totalLines) {
            return new GitLabRepositoryFileChunk(
                    group,
                    projectName,
                    branch,
                    filePath,
                    requestedStartLine,
                    requestedEndLine,
                    0,
                    0,
                    totalLines,
                    "",
                    false
            );
        }

        var returnedStartLine = requestedStartLine;
        var returnedEndLine = Math.min(requestedEndLine, totalLines);
        var chunkContent = String.join("\n", lines.subList(returnedStartLine - 1, returnedEndLine));
        var limitedContent = limitCharacters(chunkContent, maxCharacters);
        var truncated = limitedContent.length() != chunkContent.length();

        return new GitLabRepositoryFileChunk(
                group,
                projectName,
                branch,
                filePath,
                requestedStartLine,
                requestedEndLine,
                returnedStartLine,
                returnedEndLine,
                totalLines,
                limitedContent,
                truncated
        );
    }

    private List<GitLabGroupProjectResult> searchGroupProjects(String group, String searchToken) {
        var results = new ArrayList<GitLabGroupProjectResult>();
        var page = "1";

        while (StringUtils.hasText(page)) {
            try {
                var entity = restClient().get()
                        .uri(groupProjectsUri(group, searchToken, page))
                        .retrieve()
                        .toEntity(GitLabGroupProjectResult[].class);

                var body = entity.getBody();
                if (body != null) {
                    results.addAll(List.of(body));
                }

                page = entity.getHeaders().getFirst("X-Next-Page");
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() == 404) {
                    return List.of();
                }

                throw new IllegalStateException("GitLab group project search failed for " + group, exception);
            }
        }

        return List.copyOf(results);
    }

    private List<GitLabBlobSearchResult> searchProjectBlobs(String group, String projectName, String branch, String searchTerm) {
        try {
            var results = restClient().get()
                    .uri(projectSearchUri(group, projectName, branch, searchTerm))
                    .retrieve()
                    .body(GitLabBlobSearchResult[].class);

            if (results == null) {
                return List.of();
            }

            return List.of(results);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return List.of();
            }

            throw new IllegalStateException("GitLab project search failed for " + group + "/" + projectName, exception);
        }
    }

    private String fetchRawFile(String group, String projectName, String branch, String filePath) {
        try {
            return restClient().get()
                    .uri(rawFileUri(group, projectName, branch, filePath))
                    .accept(MediaType.TEXT_PLAIN)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "GitLab file read failed for " + group + "/" + projectName + "@" + branch + " :: " + filePath,
                    exception
            );
        }
    }

    private RestClient restClient() {
        return gitLabRestClientFactory.create();
    }

    private URI groupProjectsUri(String group, String searchToken, String page) {
        return URI.create(apiBaseUrl()
                + "/groups/" + encodePathSegment(group)
                + "/projects?include_subgroups=true"
                + "&simple=true"
                + "&per_page=" + PROJECT_SEARCH_PAGE_SIZE
                + "&search=" + encodeQueryParam(searchToken)
                + "&page=" + encodeQueryParam(page));
    }

    private URI projectSearchUri(String group, String projectName, String branch, String searchTerm) {
        return URI.create(apiBaseUrl()
                + "/projects/" + encodePathSegment(group + "/" + projectName)
                + "/search?scope=blobs"
                + "&search=" + encodeQueryParam(searchTerm)
                + "&ref=" + encodeQueryParam(branch)
                + "&per_page=" + properties.getSearchResultsPerTerm());
    }

    private URI rawFileUri(String group, String projectName, String branch, String filePath) {
        return URI.create(apiBaseUrl()
                + "/projects/" + encodePathSegment(group + "/" + projectName)
                + "/repository/files/" + encodePathSegment(filePath)
                + "/raw?ref=" + encodeQueryParam(branch));
    }

    private String apiBaseUrl() {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("analysis.gitlab.base-url must be configured for REST mode.");
        }

        return properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl() + "api/v4"
                : properties.getBaseUrl() + "/api/v4";
    }

    private List<String> resolveProjectSearchTargets(String group, List<String> projectHints) {
        var directProjectNames = distinctProjectNames(projectHints);
        var resolvedProjectNames = new LinkedHashSet<String>();

        for (var projectCandidate : searchProjects(group, directProjectNames)) {
            if (StringUtils.hasText(projectCandidate.projectPath())) {
                resolvedProjectNames.add(projectCandidate.projectPath().trim());
            }
        }

        resolvedProjectNames.addAll(directProjectNames);
        return List.copyOf(resolvedProjectNames);
    }

    private List<String> distinctProjectNames(List<String> projectNames) {
        var values = new LinkedHashSet<String>();

        for (var projectName : projectNames != null ? projectNames : List.<String>of()) {
            if (StringUtils.hasText(projectName)) {
                values.add(projectName.trim());
            }
        }

        return List.copyOf(values);
    }

    private List<String> distinctProjectSearchTokens(String group, List<String> projectHints) {
        var values = new LinkedHashSet<String>();

        for (var projectHint : projectHints != null ? projectHints : List.<String>of()) {
            addProjectSearchToken(values, group, projectHint);
        }

        return List.copyOf(values);
    }

    private List<String> distinctSearchTerms(List<String> keywords, List<String> operationNames) {
        var values = new LinkedHashSet<String>();

        for (var keyword : keywords != null ? keywords : List.<String>of()) {
            if (StringUtils.hasText(keyword)) {
                values.add(keyword.trim());
            }
        }

        for (var operationName : operationNames != null ? operationNames : List.<String>of()) {
            if (StringUtils.hasText(operationName)) {
                values.add(operationName.trim());
            }
        }

        return List.copyOf(values);
    }

    private String limitCharacters(String content, int maxCharacters) {
        var safeLimit = maxCharacters > 0 ? maxCharacters : 4_000;
        return content.length() > safeLimit ? content.substring(0, safeLimit) : content;
    }

    private String toRelativeProjectPath(String group, String pathWithNamespace) {
        if (!StringUtils.hasText(group) || !StringUtils.hasText(pathWithNamespace)) {
            return null;
        }

        var normalizedGroup = trimSlashes(group.trim());
        var normalizedPath = trimSlashes(pathWithNamespace.trim());
        var prefix = normalizedGroup + "/";

        if (!normalizedPath.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }

        return normalizedPath.substring(prefix.length());
    }

    private String trimSlashes(String value) {
        var start = 0;
        var end = value.length();

        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }

        return value.substring(start, end);
    }

    private int projectMatchScore(String projectPath, String projectName, String searchToken) {
        var normalizedProjectPath = normalizeProjectComparable(projectPath);
        var normalizedProjectName = normalizeProjectComparable(projectName);
        var normalizedSearchToken = normalizeProjectComparable(searchToken);

        if (!StringUtils.hasText(normalizedSearchToken)) {
            return 0;
        }
        if (normalizedSearchToken.equals(normalizedProjectName)
                || normalizedSearchToken.equals(normalizedProjectPath)
                || (normalizedProjectPath != null && normalizedProjectPath.endsWith("/" + normalizedSearchToken))) {
            return 120;
        }
        if (normalizedProjectName != null && normalizedProjectName.endsWith("_" + normalizedSearchToken)) {
            return 110;
        }
        if (normalizedProjectName != null && normalizedProjectName.contains(normalizedSearchToken)) {
            return 100;
        }
        if (normalizedProjectPath != null && normalizedProjectPath.contains(normalizedSearchToken)) {
            return 90;
        }

        return 0;
    }

    private void addProjectSearchToken(LinkedHashSet<String> values, String group, String projectHint) {
        if (!StringUtils.hasText(projectHint)) {
            return;
        }

        var trimmedHint = projectHint.trim();
        values.add(trimmedHint);

        var normalizedHint = normalizeProjectComparable(trimmedHint);
        if (StringUtils.hasText(normalizedHint)) {
            values.add(normalizedHint);

            var normalizedGroupPrefix = normalizeProjectComparable(groupLeafToken(group));
            if (StringUtils.hasText(normalizedGroupPrefix)) {
                var prefix = normalizedGroupPrefix + "_";
                if (normalizedHint.startsWith(prefix) && normalizedHint.length() > prefix.length()) {
                    values.add(normalizedHint.substring(prefix.length()));
                }
            }
        }
    }

    private String groupLeafToken(String group) {
        if (!StringUtils.hasText(group)) {
            return null;
        }

        var trimmedGroup = group.trim();
        var lastSlash = trimmedGroup.lastIndexOf('/');
        return lastSlash >= 0 ? trimmedGroup.substring(lastSlash + 1) : trimmedGroup;
    }

    private String normalizeProjectComparable(String value) {
        return value == null
                ? null
                : value.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace('-', '_')
                        .replaceAll("[^a-z0-9/_]+", "_");
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private record GitLabBlobSearchResult(
            String path
    ) {
    }

    private record GitLabGroupProjectResult(
            String name,
            String path,
            @JsonProperty("path_with_namespace")
            String pathWithNamespace
    ) {
    }

    private static final class CandidateAccumulator {

        private final String group;
        private final String projectName;
        private final String branch;
        private final String filePath;
        private final List<String> matchedTerms = new ArrayList<>();

        private CandidateAccumulator(String group, String projectName, String branch, String filePath) {
            this.group = group;
            this.projectName = projectName;
            this.branch = branch;
            this.filePath = filePath;
        }

        private void registerMatch(String term) {
            if (!matchedTerms.contains(term)) {
                matchedTerms.add(term);
            }
        }

        private int matchScore() {
            return matchedTerms.size() * 10;
        }

        private GitLabRepositoryFileCandidate toCandidate() {
            return new GitLabRepositoryFileCandidate(
                    group,
                    projectName,
                    branch,
                    filePath,
                    "Matched GitLab search terms " + matchedTerms + " on branch " + branch + ".",
                    matchScore()
            );
        }
    }

    private static final class ProjectCandidateAccumulator {

        private final String group;
        private final String projectPath;
        private final List<String> matchedTerms = new ArrayList<>();
        private int score;

        private ProjectCandidateAccumulator(String group, String projectPath) {
            this.group = group;
            this.projectPath = projectPath;
        }

        private void registerMatch(String term, int termScore) {
            if (!matchedTerms.contains(term)) {
                matchedTerms.add(term);
                score += termScore;
            }
        }

        private int matchScore() {
            return score;
        }

        private GitLabRepositoryProjectCandidate toCandidate() {
            return new GitLabRepositoryProjectCandidate(
                    group,
                    projectPath,
                    "Matched GitLab project hints " + matchedTerms + ".",
                    matchScore()
            );
        }
    }

}
