package pl.mkn.tdw.integrations.gitlab.adapter.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabCommitHistoryPort;
import pl.mkn.tdw.integrations.gitlab.config.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.config.GitLabRestClientFactory;
import pl.mkn.tdw.integrations.gitlab.contract.*;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestDiffSnapshot.FileDiff;
import pl.mkn.tdw.integrations.gitlab.internal.GitLabReadRetry;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GitLabRestCommitHistoryAdapter implements GitLabCommitHistoryPort {
    private static final int PAGE_SIZE = 20;
    private final GitLabProperties properties;
    private final GitLabRestClientFactory clients;

    @Override
    public GitLabCommitPage listCommits(String group, String project, String headSha, String path,
                                       Instant since, Instant until, int page) {
        var base = endpoint(group, project, headSha, page);
        if (path != null && !path.isEmpty() && !GitLabRepositoryPath.isSafePath(path, false)) {
            throw new IllegalArgumentException("A safe repository path is required.");
        }
        if (since != null && until != null && since.isAfter(until)) {
            throw new IllegalArgumentException("since must not exceed until.");
        }
        var url = base + "?ref_name=" + headSha + "&order=default&per_page=" + PAGE_SIZE + "&page=" + page
                + (path == null || path.isEmpty() ? "" : "&path=" + encode(path))
                + (since == null ? "" : "&since=" + encode(since.toString()))
                + (until == null ? "" : "&until=" + encode(until.toString()));
        var response = GitLabReadRetry.execute("commit-history", () -> clients.create().get().uri(URI.create(url))
                .retrieve().toEntity(CommitResponse[].class));
        var body = response.getBody();
        if (body == null || body.length > PAGE_SIZE) throw new IllegalStateException("Invalid commit page.");
        var commits = Arrays.stream(body).map(item -> {
            if (item == null) throw new IllegalStateException("Missing commit metadata.");
            var commit = new GitLabHistoryCommit(item.id(), item.parents(), Instant.parse(item.committedAt()),
                    item.title(), item.webUrl(), item.committerName(), item.committerEmail());
            if (since != null && commit.committedAt().isBefore(since)
                    || until != null && commit.committedAt().isAfter(until)) {
                throw new IllegalStateException("Commit exceeds requested date range.");
            }
            return commit;
        }).toList();
        return new GitLabCommitPage(commits, nextPage(response.getHeaders(), body.length, page));
    }

    @Override
    public GitLabCommitDiffPage readDiff(String group, String project, String commitSha, int page) {
        var url = endpoint(group, project, commitSha, page) + "/" + commitSha
                + "/diff?per_page=" + PAGE_SIZE + "&page=" + page;
        var response = GitLabReadRetry.execute("commit-diff", () -> clients.create().get().uri(URI.create(url))
                .retrieve().toEntity(DiffResponse[].class));
        var body = response.getBody();
        if (body == null || body.length > PAGE_SIZE) throw new IllegalStateException("Invalid commit diff page.");
        var files = Arrays.stream(body).map(item -> {
            if (item == null || !GitLabRepositoryPath.isSafePath(item.oldPath(), false)
                    || !GitLabRepositoryPath.isSafePath(item.newPath(), false)) {
                throw new IllegalStateException("Invalid diff paths.");
            }
            return new FileDiff(item.oldPath(), item.newPath(), item.newFile(), item.renamedFile(),
                    item.deletedFile(), item.diff(), item.collapsed(), item.tooLarge());
        }).toList();
        return new GitLabCommitDiffPage(files, nextPage(response.getHeaders(), body.length, page));
    }

    private String endpoint(String group, String project, String sha, int page) {
        GitLabHistoryCommit.requireSha(sha);
        if (!GitLabRepositoryPath.isSafePath(group, false) || !GitLabRepositoryPath.isSafePath(project, false)
                || page < 1 || page == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid history coordinates.");
        var base = properties.getBaseUrl().replaceFirst("/+$", "");
        if (!base.endsWith("/api/v4")) base += "/api/v4";
        return base + "/projects/" + encode(group + "/" + project) + "/repository/commits";
    }

    private Integer nextPage(HttpHeaders headers, int count, int page) {
        var next = headers.getFirst("X-Next-Page");
        if (next != null) {
            if (next.isBlank()) return null;
            try {
                var value = Integer.parseInt(next);
                if (value == page + 1) return value;
            } catch (NumberFormatException ignored) { }
            throw new IllegalStateException("Invalid GitLab pagination.");
        }
        // No total-count assumption: a full page without pagination headers requires another read.
        return count == PAGE_SIZE ? page + 1 : null;
    }

    private static String encode(String value) { return UriUtils.encode(value, StandardCharsets.UTF_8); }
    private record CommitResponse(String id, @JsonProperty("parent_ids") List<String> parents,
            @JsonProperty("committed_date") String committedAt, String title,
            @JsonProperty("web_url") String webUrl, @JsonProperty("committer_name") String committerName,
            @JsonProperty("committer_email") String committerEmail) { }
    private record DiffResponse(@JsonProperty("old_path") String oldPath, @JsonProperty("new_path") String newPath,
            @JsonProperty("new_file") boolean newFile, @JsonProperty("renamed_file") boolean renamedFile,
            @JsonProperty("deleted_file") boolean deletedFile, String diff, boolean collapsed,
            @JsonProperty("too_large") boolean tooLarge) { }
}
