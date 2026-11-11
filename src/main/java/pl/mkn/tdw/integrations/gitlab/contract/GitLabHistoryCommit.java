package pl.mkn.tdw.integrations.gitlab.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GitLabHistoryCommit(String id, List<String> parentIds, Instant committedAt,
                                 String title, String webUrl, String committerName, String committerEmail) {
    public GitLabHistoryCommit {
        requireSha(id);
        if (parentIds == null || committedAt == null || title == null) {
            throw new IllegalArgumentException("Incomplete commit metadata.");
        }
        parentIds = List.copyOf(parentIds);
        parentIds.forEach(GitLabHistoryCommit::requireSha);
        committerName = optional(committerName);
        committerEmail = optional(committerEmail);
    }

    public static void requireSha(String value) {
        if (value == null || !value.matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")) {
            throw new IllegalArgumentException("A full commit SHA is required.");
        }
    }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value; }
}
