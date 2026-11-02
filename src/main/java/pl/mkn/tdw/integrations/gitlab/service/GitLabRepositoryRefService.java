package pl.mkn.tdw.integrations.gitlab.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryRefPort;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.integrations.gitlab.contract.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class GitLabRepositoryRefService implements GitLabRepositoryRefPort {
    private final GitLabProperties properties;
    private final GitLabRestClientFactory restClientFactory;
    @Override
    public GitLabRefPage listRefs(String projectPath, GitLabRef.Kind kind, String search, int page) {
        if (projectPath == null || projectPath.isBlank() || page < 1 || kind == null || kind == GitLabRef.Kind.COMMIT)
            throw new IllegalArgumentException("Project, branch/tag kind and positive page required");
        var endpoint = kind == GitLabRef.Kind.BRANCH ? "branches" : "tags";
        var uri = properties.getBaseUrl().replaceAll("/+$", "") + "/api/v4/projects/" + encode(projectPath)
                + "/repository/" + endpoint + "?per_page=100&page=" + page
                + (search == null || search.isBlank() ? "" : "&search=" + encode(search));
        var response = restClientFactory.create().get().uri(URI.create(uri)).retrieve().toEntity(RefPayload[].class);
        var body = response.getBody() == null ? new RefPayload[0] : response.getBody();
        var next = response.getHeaders().getFirst("X-Next-Page");
        Integer nextPage = next != null && !next.isBlank() ? Integer.valueOf(next) : null;
        if (nextPage != null && nextPage <= page) throw new IllegalStateException("Invalid GitLab pagination");
        return new GitLabRefPage(Arrays.stream(body).map(r -> new GitLabRefOption(new GitLabRef(kind, r.name()), r.commit() == null ? null : r.commit().committedDate(), r.defaultBranch())).toList(), nextPage);
    }
    private String encode(String value) { return UriUtils.encode(value, StandardCharsets.UTF_8); }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RefPayload(String name, @JsonProperty("default") boolean defaultBranch, CommitPayload commit) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommitPayload(@JsonProperty("committed_date") String committedDate) {}
}

