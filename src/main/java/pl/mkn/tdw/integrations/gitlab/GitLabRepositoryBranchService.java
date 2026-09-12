package pl.mkn.tdw.integrations.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GitLabRepositoryBranchService {

    private static final int PAGE_SIZE = 100;

    private final GitLabProperties properties;
    private final GitLabRestClientFactory restClientFactory;

    public BranchPage listBranches(String projectPath, String search) {
        if (!StringUtils.hasText(projectPath)) {
            throw new IllegalArgumentException("GitLab project path is required.");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("analysis.gitlab.base-url must be configured.");
        }

        var baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        var uri = URI.create(baseUrl + "/api/v4/projects/"
                + UriUtils.encodePathSegment(projectPath.trim(), StandardCharsets.UTF_8)
                + "/repository/branches?per_page=" + PAGE_SIZE
                + (StringUtils.hasText(search)
                ? "&search=" + UriUtils.encodeQueryParam(search.trim(), StandardCharsets.UTF_8)
                : ""));
        var response = restClientFactory.create().get().uri(uri).retrieve().toEntity(BranchPayload[].class);
        var payload = response.getBody() != null ? response.getBody() : new BranchPayload[0];
        var branches = Arrays.stream(payload)
                .filter(branch -> branch != null && StringUtils.hasText(branch.name()))
                .map(branch -> new Branch(branch.name(), branch.defaultBranch()))
                .toList();
        var nextPage = response.getHeaders().getFirst("X-Next-Page");
        return new BranchPage(branches, StringUtils.hasText(nextPage) || payload.length >= PAGE_SIZE);
    }

    public record Branch(String name, boolean isDefault) {
    }

    public record BranchPage(List<Branch> branches, boolean truncated) {
        public BranchPage {
            branches = branches != null ? List.copyOf(branches) : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BranchPayload(String name, @JsonProperty("default") boolean defaultBranch) {
    }
}
