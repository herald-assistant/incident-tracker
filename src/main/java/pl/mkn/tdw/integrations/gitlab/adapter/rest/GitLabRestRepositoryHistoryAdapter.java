package pl.mkn.tdw.integrations.gitlab.adapter.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryHistoryPort;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabCommit;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryPath;
import pl.mkn.tdw.integrations.gitlab.internal.GitLabReadRetry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GitLabRestRepositoryHistoryAdapter implements GitLabRepositoryHistoryPort {
    private final GitLabProperties properties;
    private final GitLabRestClientFactory clients;

    @Override
    public Optional<GitLabCommit> latestBefore(String group, String project, String headCommit, Instant exclusive) {
        if (!GitLabRepositoryPath.isSafePath(group, false) || !GitLabRepositoryPath.isSafePath(project, false)
                || headCommit == null || !headCommit.matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")
                || exclusive == null || exclusive.getNano() != 0)
            throw new IllegalArgumentException("A project, pinned revision and second-precision boundary are required.");
        var base = properties.getBaseUrl().replaceFirst("/+$", "");
        if (!base.endsWith("/api/v4")) base += "/api/v4";
        var uri = URI.create(base + "/projects/" + encode(group + "/" + project)
                + "/repository/commits?ref_name=" + headCommit + "&until=" + encode(exclusive.minusSeconds(1).toString())
                + "&first_parent=true&order=default&per_page=1&page=1");
        var body = GitLabReadRetry.execute("repository-history", () -> clients.create().get().uri(uri)
                .retrieve().body(CommitResponse[].class));
        if (body == null || body.length > 1) throw new IllegalStateException("Invalid commit history response.");
        if (body.length == 0) return Optional.empty();
        var item = body[0];
        if (item == null) throw new IllegalStateException("Missing commit metadata.");
        var commit = new GitLabCommit(item.id(), Instant.parse(item.committedAt()), item.title());
        if (!commit.committedAt().isBefore(exclusive)) throw new IllegalStateException("Commit exceeds requested boundary.");
        return Optional.of(commit);
    }
    private String encode(String value) { return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8); }
    private record CommitResponse(String id, @JsonProperty("committed_date") String committedAt, String title) {}
}
